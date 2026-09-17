package com.aurora.player.archive

import android.util.Log
import com.aurora.player.domain.model.ArchiveItem
import com.aurora.player.domain.model.ArchiveTrack
import com.aurora.player.domain.repository.ArchiveRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Internet Archive (`archive.org`) — koncerty na żywo (`etree`), netlabele, stare radio. Zero
 * kluczy API, zero konta. `:app` bo potrzebuje OkHttp (ten sam podział co Radio/Podcasty — `:data`
 * nie ma HTTP jako zależności). Wyniki przeglądania/wyszukiwania cache'owane w pamięci procesu na
 * czas życia serwisu (ten sam wzorzec co `cachedGeniusMixes` w `AuroraBrowseTree`) — inaczej
 * przełączanie się między kaflami kolekcji odpytywałoby `archive.org` przy każdym wejściu.
 */
@Singleton
class ArchiveRepositoryImpl @Inject constructor() : ArchiveRepository {

    private val httpClient = OkHttpClient.Builder().build()
    private val itemsCache = ConcurrentHashMap<String, List<ArchiveItem>>()
    private val tracksCache = ConcurrentHashMap<String, List<ArchiveTrack>>()

    override suspend fun browseCollection(collection: String, limit: Int): List<ArchiveItem> =
        withContext(Dispatchers.IO) {
            itemsCache.getOrPut("collection:$collection:$limit") {
                fetchItems("collection:$collection AND mediatype:audio", limit, sort = "date+desc")
            }
        }

    override suspend fun search(query: String, limit: Int): List<ArchiveItem> {
        if (query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val escaped = escapeLucene(query.trim())
            itemsCache.getOrPut("search:$escaped:$limit") {
                fetchItems("mediatype:audio AND ($escaped)", limit, sort = null)
            }
        }
    }

    override suspend fun tracksForItem(identifier: String): List<ArchiveTrack> = withContext(Dispatchers.IO) {
        tracksCache.getOrPut(identifier) { fetchTracks(identifier) }
    }

    /**
     * "Dla Ciebie" — patrz KDoc interfejsu dla pełnego algorytmu. Dopasowanie po wykonawcy (waga
     * wyższa — Internet Archive ma pełne archiwa koncertowe tysięcy zespołów, głównie
     * jam-bandowych/rockowych) idzie PIERWSZE w wyniku, więc przy `distinctBy` i `take(limit)`
     * naturalnie wygrywa nad dopasowaniem po gatunku, bez osobnego numerycznego ważenia.
     */
    override suspend fun personalizedForYou(
        favoriteArtists: List<String>,
        favoriteGenres: List<String>,
        limit: Int,
    ): List<ArchiveItem> = withContext(Dispatchers.IO) {
        if (favoriteArtists.isEmpty() && favoriteGenres.isEmpty()) return@withContext emptyList()

        val artistMatches = favoriteArtists.take(MAX_TASTE_ARTISTS).flatMap { artist ->
            val escaped = escapeLucene(artist)
            fetchItems(
                query = "creator:(\"$escaped\") AND mediatype:audio AND (collection:etree OR collection:netlabels)",
                limit = PER_QUERY_LIMIT,
                sort = "downloads+desc",
            )
        }

        val genreMatches = favoriteGenres.take(MAX_TASTE_GENRES).flatMap { genre ->
            val collectionQuery = collectionsForGenre(genre).joinToString(" OR ") { "collection:$it" }
            fetchItems(
                query = "($collectionQuery) AND mediatype:audio",
                limit = PER_QUERY_LIMIT,
                sort = "downloads+desc",
            )
        }

        (artistMatches + genreMatches).distinctBy { it.identifier }.take(limit)
    }

    private fun collectionsForGenre(genre: String): List<String> {
        val normalized = genre.lowercase()
        return GENRE_TO_COLLECTIONS.entries.firstOrNull { (keyword, _) -> normalized.contains(keyword) }?.value
            ?: DEFAULT_GENRE_COLLECTIONS
    }

    private fun fetchItems(query: String, limit: Int, sort: String?): List<ArchiveItem> {
        val fields = "identifier,title,creator,year,collection"
        val sortParam = sort?.let { "&sort[]=$it" }.orEmpty()
        val url = "$SEARCH_URL?q=${URLEncoder.encode(query, "UTF-8")}" +
            "&fl[]=${fields.replace(",", "&fl[]=")}&rows=$limit&output=json$sortParam"
        val json = fetchJson(url) ?: return emptyList()
        val docs = try {
            JSONObject(json).optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
        val items = mutableListOf<ArchiveItem>()
        for (i in 0 until docs.length()) {
            val doc = docs.optJSONObject(i) ?: continue
            val identifier = doc.optString("identifier")
            if (identifier.isBlank()) continue
            items += ArchiveItem(
                identifier = identifier,
                title = doc.optString("title").ifBlank { identifier },
                creator = doc.opt("creator").toDisplayString(),
                year = doc.optString("year").toIntOrNull(),
                coverUrl = "https://archive.org/services/img/$identifier",
            )
        }
        return items
    }

    private fun fetchTracks(identifier: String): List<ArchiveTrack> {
        val json = fetchJson("$METADATA_URL/$identifier") ?: return emptyList()
        val files = try {
            JSONObject(json).optJSONArray("files") ?: return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }
        // Jeden "logiczny" utwór to zwykle kilka plików (oryginalny FLAC + pochodne MP3/PNG/
        // spektrogram) pod tą samą nazwą bez rozszerzenia — grupujemy i wybieramy NAJLEPSZY
        // skompresowany format, żeby nie dublować ścieżek i nie serwować surowego FLAC-a domyślnie.
        val bestByTrack = LinkedHashMap<String, JSONObject>()
        for (i in 0 until files.length()) {
            val file = files.optJSONObject(i) ?: continue
            val format = file.optString("format")
            if (AUDIO_FORMAT_PRIORITY.indexOf(format) < 0) continue
            val name = file.optString("name")
            if (name.isBlank()) continue
            val trackKey = name.substringBeforeLast('.')
            val existing = bestByTrack[trackKey]
            if (existing == null || formatRank(format) < formatRank(existing.optString("format"))) {
                bestByTrack[trackKey] = file
            }
        }
        return bestByTrack.entries.mapIndexedNotNull { index, (trackKey, file) ->
            val name = file.optString("name").ifBlank { return@mapIndexedNotNull null }
            ArchiveTrack(
                identifier = identifier,
                fileName = name,
                title = file.optString("title").ifBlank { trackKey.substringAfterLast('/') },
                audioUrl = "https://archive.org/download/$identifier/${name.encodePathSegments()}",
                durationMs = parseLengthMs(file.optString("length")),
            )
        }
    }

    private fun formatRank(format: String): Int = AUDIO_FORMAT_PRIORITY.indexOf(format).let { if (it < 0) Int.MAX_VALUE else it }

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

    /** `creator` bywa Stringiem albo JSONArray (niespójne metadane IA między itemami). */
    private fun Any?.toDisplayString(): String = when (this) {
        is String -> this
        is JSONArray -> (0 until length()).mapNotNull { optString(it).ifBlank { null } }.joinToString(", ")
        else -> ""
    }

    /** `length` bywa sekundami jako liczba ("157.13") albo "MM:SS"/"HH:MM:SS" — niespójne między formatami plików. */
    private fun parseLengthMs(raw: String): Long {
        val value = raw.trim()
        if (value.isEmpty()) return 0L
        return if (':' in value) {
            val parts = value.split(':').mapNotNull { it.toDoubleOrNull() }
            var seconds = 0.0
            for (part in parts) seconds = seconds * 60 + part
            (seconds * 1000).toLong()
        } else {
            ((value.toDoubleOrNull() ?: 0.0) * 1000).toLong()
        }
    }

    /** Nazwy plików IA mogą zawierać spacje/apostrofy/`/` (podkatalogi) — każdy segment osobno. */
    private fun String.encodePathSegments(): String =
        split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    /** Ucieczka znaków specjalnych Lucene, żeby wolny tekst usera nie łamał składni zapytania. */
    private fun escapeLucene(query: String): String =
        query.replace(Regex("[+\\-&|!(){}\\[\\]^\"~*?:\\\\/]"), "\\\\$0")

    private companion object {
        const val TAG = "ArchiveRepositoryImpl"
        const val SEARCH_URL = "https://archive.org/advancedsearch.php"
        const val METADATA_URL = "https://archive.org/metadata"
        const val MAX_TASTE_ARTISTS = 5
        const val MAX_TASTE_GENRES = 3
        const val PER_QUERY_LIMIT = 8

        /** Priorytet formatów audio (indeks = ranga, niższy = lepszy) — nigdy surowy FLAC jako domyślny. */
        val AUDIO_FORMAT_PRIORITY = listOf(
            "VBR MP3", "128Kbps MP3", "64Kbps MP3", "MP3", "Ogg Vorbis", "Flac", "24bit Flac",
        )

        /**
         * Mapa gatunek→kolekcja IA — dopasowanie CZĘŚCIOWE stringa (tagi ID3 są bałaganiarskie,
         * np. "Prog Rock" musi trafić w klucz "rock"). Kolejność ma znaczenie: pierwszy pasujący
         * klucz wygrywa, więc bardziej specyficzne słowa kluczowe idą przed ogólnymi.
         */
        val GENRE_TO_COLLECTIONS = linkedMapOf(
            "jazz" to listOf("78rpm", "etree"),
            "classical" to listOf("78rpm"),
            "orchestral" to listOf("78rpm"),
            "opera" to listOf("78rpm"),
            "comedy" to listOf("oldtimeradio"),
            "talk" to listOf("oldtimeradio"),
            "spoken word" to listOf("oldtimeradio"),
            "electronic" to listOf("netlabels"),
            "ambient" to listOf("netlabels"),
            "experimental" to listOf("netlabels"),
            "techno" to listOf("netlabels"),
            "house" to listOf("netlabels"),
            "idm" to listOf("netlabels"),
            "rock" to listOf("etree"),
            "jam" to listOf("etree"),
            "blues" to listOf("etree"),
            "folk" to listOf("etree"),
            "country" to listOf("etree"),
            "bluegrass" to listOf("etree"),
        )

        /** Gatunek bez dopasowania — netlabele jako bezpieczny, WSPÓŁCZESNY domyślny wybór. */
        val DEFAULT_GENRE_COLLECTIONS = listOf("netlabels")
    }
}
