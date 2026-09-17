package com.aurora.player.audiobook

import android.util.Log
import com.aurora.player.data.database.dao.AudiobookDao
import com.aurora.player.data.database.entity.AudiobookEntity
import com.aurora.player.data.database.entity.AudiobookPlaybackPositionEntity
import com.aurora.player.di.ApplicationScope
import com.aurora.player.domain.model.Audiobook
import com.aurora.player.domain.model.AudiobookChapter
import com.aurora.player.domain.model.AudiobookSearchResult
import com.aurora.player.domain.repository.AudiobookRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audiobooki — LibriVox, domena publiczna. Ich REST API (`librivox.org/api/feed/audiobooks`)
 * zwraca z `extended=1` gotową, już posortowaną listę rozdziałów (`sections[]`, z bezpośrednim
 * `listen_url`) razem z metadanymi książki w JEDNYM zapytaniu — nie ma tu potrzeby osobnego
 * parsowania RSS jak przy podkastach (Etap 32), mimo że `url_rss` też istnieje w odpowiedzi.
 * `:data` moduł dostarcza `AudiobookDao` (Hilt), samo HTTP/JSON żyje w `:app` (ten sam podział co
 * `PodcastRepositoryImpl`) — `:data` nie ma OkHttp jako zależności.
 */
@Singleton
class AudiobookRepositoryImpl @Inject constructor(
    private val audiobookDao: AudiobookDao,
    @ApplicationScope scope: CoroutineScope,
) : AudiobookRepository {

    private val httpClient = OkHttpClient.Builder().build()

    override val library: StateFlow<List<Audiobook>> = audiobookDao.observeLibrary()
        .map { entities -> entities.map { it.toAudiobook() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun search(query: String, limit: Int): List<AudiobookSearchResult> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val json = fetchJson("$BASE_URL?title=^$encoded&extended=1&format=json&limit=$limit")
                ?: return@withContext emptyList()
            parseBooks(json).map { it.toSearchResult() }
        }

    override suspend fun trending(languageIso3: String, limit: Int): List<AudiobookSearchResult> =
        withContext(Dispatchers.IO) {
            val results = fetchTrendingFromArchive(languageIso3, limit)
            if (results.isNotEmpty() || languageIso3 == FALLBACK_LANGUAGE) {
                results
            } else {
                Log.w(TAG, "trending(): brak wyników dla języka '$languageIso3', fallback na '$FALLBACK_LANGUAGE'")
                fetchTrendingFromArchive(FALLBACK_LANGUAGE, limit)
            }
        }

    /**
     * LibriVox samo nie sortuje po popularności ani nie filtruje po języku (zweryfikowane w
     * `librivox.org/api/info` — jedyne parametry to id/since/author/title/genre/extended/coverart).
     * Te same nagrania są jednak w kolekcji `librivoxaudio` na Internet Archive, gdzie `downloads`
     * daje realny ranking popularności, a `language` (kod ISO 639, np. "eng") pozwala filtrować.
     * `call_number` w metadanych IA to dokładnie numeryczne id z API LibriVox (zweryfikowane na
     * kilku pozycjach), więc wynik trafia od razu do [addToLibrary] bez dodatkowego zapytania.
     */
    private fun fetchTrendingFromArchive(languageIso3: String, limit: Int): List<AudiobookSearchResult> {
        val query = "collection:librivoxaudio AND mediatype:audio AND language:($languageIso3)"
        val fields = "identifier,title,creator,call_number"
        val url = "$ARCHIVE_SEARCH_URL?q=${URLEncoder.encode(query, "UTF-8")}" +
            "&fl[]=${fields.replace(",", "&fl[]=")}&sort[]=downloads+desc&rows=$limit&output=json"
        val json = fetchJson(url) ?: return emptyList()
        val docs = try {
            JSONObject(json).optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
        val results = mutableListOf<AudiobookSearchResult>()
        for (i in 0 until docs.length()) {
            val doc = docs.optJSONObject(i) ?: continue
            val id = doc.optString("call_number")
            val identifier = doc.optString("identifier")
            val title = doc.optString("title")
            if (id.isBlank() || identifier.isBlank() || title.isBlank()) continue
            results += AudiobookSearchResult(
                id = id,
                title = title,
                author = doc.opt("creator").toAuthorDisplayString(),
                language = languageIso3,
                coverUrl = "https://archive.org/services/img/$identifier",
            )
        }
        return results
    }

    private fun Any?.toAuthorDisplayString(): String = when (this) {
        is String -> this
        is JSONArray -> (0 until length()).mapNotNull { optString(it).ifBlank { null } }.joinToString(", ")
        else -> "Nieznany autor"
    }

    override suspend fun addToLibrary(id: String): Audiobook? = withContext(Dispatchers.IO) {
        val book = fetchBookById(id) ?: return@withContext null
        val audiobook = book.toAudiobook()
        audiobookDao.insertAudiobook(audiobook.toEntity())
        audiobook
    }

    override suspend fun removeFromLibrary(id: String) {
        audiobookDao.deleteAudiobook(id)
    }

    override suspend fun fetchChapters(id: String): List<AudiobookChapter> = withContext(Dispatchers.IO) {
        fetchBookById(id)?.sections.orEmpty()
    }

    override suspend fun getPlaybackPosition(trackId: Long): Long =
        audiobookDao.getPlaybackPosition(trackId) ?: 0L

    override suspend fun savePlaybackPosition(trackId: Long, positionMs: Long) {
        audiobookDao.upsertPlaybackPosition(
            AudiobookPlaybackPositionEntity(trackId, positionMs, System.currentTimeMillis()),
        )
    }

    private suspend fun fetchBookById(id: String): ParsedBook? {
        val json = fetchJson("$BASE_URL?id=$id&extended=1&format=json") ?: return null
        return parseBooks(json).firstOrNull()
    }

    private fun fetchJson(url: String): String? = try {
        val request = Request.Builder().url(url).header("User-Agent", "AuroraMusicPlayer/1.0").build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "fetchJson(): HTTP ${response.code} dla $url")
                null
            } else {
                response.body?.string()
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "fetchJson(): błąd sieci dla $url", e)
        null
    }

    private fun parseBooks(json: String): List<ParsedBook> {
        val books = try {
            JSONObject(json).optJSONArray("books") ?: return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
        val result = mutableListOf<ParsedBook>()
        for (i in 0 until books.length()) {
            val book = books.optJSONObject(i) ?: continue
            val id = book.optString("id")
            if (id.isBlank()) continue
            val title = book.optString("title")
            if (title.isBlank()) continue
            result += ParsedBook(
                id = id,
                title = title,
                author = book.optJSONArray("authors")?.toAuthorNames().orEmpty(),
                language = book.optString("language").ifBlank { null },
                description = book.optString("description").stripHtml(),
                coverUrl = book.optString("url_iarchive").ifBlank { null }?.toArchiveCoverUrl(),
                sections = book.optJSONArray("sections")?.toChapters(id).orEmpty(),
            )
        }
        return result
    }

    private fun JSONArray.toAuthorNames(): String {
        val names = mutableListOf<String>()
        for (i in 0 until length()) {
            val author = optJSONObject(i) ?: continue
            val name = "${author.optString("first_name")} ${author.optString("last_name")}".trim()
            if (name.isNotEmpty()) names += name
        }
        return names.joinToString(", ")
    }

    private fun JSONArray.toChapters(bookId: String): List<AudiobookChapter> {
        val chapters = mutableListOf<AudiobookChapter>()
        for (i in 0 until length()) {
            val section = optJSONObject(i) ?: continue
            val audioUrl = section.optString("listen_url")
            if (audioUrl.isBlank()) continue
            chapters += AudiobookChapter(
                id = section.optString("id").ifBlank { audioUrl },
                bookId = bookId,
                title = section.optString("title").ifBlank { "Rozdział ${chapters.size + 1}" },
                audioUrl = audioUrl,
                durationMs = (section.optString("playtime").toLongOrNull() ?: 0L) * 1000L,
                chapterIndex = chapters.size,
            )
        }
        return chapters
    }

    /** `url_iarchive` to strona-details ("…/details/<identifier>") — archive.org serwuje okładkę pod stałym wzorcem. */
    private fun String.toArchiveCoverUrl(): String? =
        substringAfterLast('/').ifBlank { null }?.let { "https://archive.org/services/img/$it" }

    private fun String.stripHtml(): String = replace(Regex("<[^>]*>"), "").trim()

    private data class ParsedBook(
        val id: String,
        val title: String,
        val author: String,
        val language: String?,
        val description: String,
        val coverUrl: String?,
        val sections: List<AudiobookChapter>,
    )

    private fun ParsedBook.toAudiobook() = Audiobook(id, title, author, language, coverUrl, description)

    private fun ParsedBook.toSearchResult() = AudiobookSearchResult(id, title, author, language, coverUrl)

    private fun AudiobookEntity.toAudiobook() = Audiobook(id, title, author, language, coverUrl, description)

    private fun Audiobook.toEntity() = AudiobookEntity(
        id = id,
        title = title,
        author = author,
        language = language,
        coverUrl = coverUrl,
        description = description,
        addedAtMs = System.currentTimeMillis(),
    )

    private companion object {
        const val TAG = "AudiobookRepositoryImpl"
        const val BASE_URL = "https://librivox.org/api/feed/audiobooks/"
        const val ARCHIVE_SEARCH_URL = "https://archive.org/advancedsearch.php"
        const val FALLBACK_LANGUAGE = "eng"
    }
}
