package com.aurora.player.archive

import android.content.Context
import android.net.Uri
import android.util.Log
import com.aurora.player.data.database.dao.ArchiveLibraryDao
import com.aurora.player.data.database.entity.ArchiveLibraryTrackEntity
import com.aurora.player.di.ApplicationScope
import com.aurora.player.domain.model.ArchiveCategory
import com.aurora.player.domain.model.ArchiveDownload
import com.aurora.player.domain.model.ArchiveItem
import com.aurora.player.domain.model.ArchiveTrack
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.repository.ArchiveRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
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
 *
 * [library]/[addTrackToLibrary] (Etap 40) to osobna sprawa od przeglądania wyżej — realne pobranie
 * pliku audio do prywatnego magazynu appki (`context.filesDir`, NIE MediaStore, NIE wymaga
 * uprawnień), żeby dodana ścieżka grała offline jak lokalny plik, nie tylko streamingowała.
 */
@Singleton
class ArchiveRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val archiveLibraryDao: ArchiveLibraryDao,
    @ApplicationScope scope: CoroutineScope,
) : ArchiveRepository {

    private val httpClient = OkHttpClient.Builder().build()
    private val itemsCache = ConcurrentHashMap<String, List<ArchiveItem>>()
    private val tracksCache = ConcurrentHashMap<String, List<ArchiveTrack>>()

    override val library: StateFlow<List<Track>> = archiveLibraryDao.observeAll()
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _downloadingTrackIds = MutableStateFlow<Set<Long>>(emptySet())
    override val downloadingTrackIds: StateFlow<Set<Long>> = _downloadingTrackIds

    private val _activeDownloads = MutableStateFlow<List<ArchiveDownload>>(emptyList())
    override val activeDownloads: StateFlow<List<ArchiveDownload>> = _activeDownloads

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError

    override fun clearLastError() {
        _lastError.value = null
    }

    override suspend fun addTrackToLibrary(track: ArchiveTrack, item: ArchiveItem): Boolean =
        withContext(Dispatchers.IO) {
            val trackId = archiveTrackId(track.identifier, track.fileName)
            if (trackId in _downloadingTrackIds.value || archiveLibraryDao.getByTrackId(trackId) != null) {
                return@withContext true
            }
            _downloadingTrackIds.update { it + trackId }
            _activeDownloads.update {
                it + ArchiveDownload(
                    trackId = trackId,
                    title = track.title,
                    subtitle = item.creator.ifBlank { item.title },
                    coverUrl = item.coverUrl,
                )
            }
            try {
                val dir = File(context.filesDir, "archive_library/${track.identifier}")
                dir.mkdirs()
                val destination = File(dir, track.fileName.substringAfterLast('/').ifBlank { "$trackId.audio" })
                downloadToFile(track.audioUrl, destination)
                archiveLibraryDao.insert(
                    ArchiveLibraryTrackEntity(
                        trackId = trackId,
                        identifier = track.identifier,
                        fileName = track.fileName,
                        title = track.title,
                        artist = item.creator,
                        album = item.title,
                        year = item.year,
                        durationMs = track.durationMs,
                        albumArtUrl = item.coverUrl,
                        localFileUri = Uri.fromFile(destination).toString(),
                        addedAtMs = System.currentTimeMillis(),
                    ),
                )
                _lastError.value = null
                true
            } catch (e: Exception) {
                Log.e(TAG, "addTrackToLibrary(): błąd pobierania ${track.audioUrl}", e)
                // Realny powód (np. "HTTP 403"/"Unable to resolve host") w komunikacie, nie tylko w
                // Logcacie — bez adb/telefonu pod ręką generyczny tekst nie dawał nic do diagnozy.
                val reason = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                _lastError.value = "Nie udało się pobrać „${track.title}” do biblioteki ($reason)."
                false
            } finally {
                _downloadingTrackIds.update { it - trackId }
                _activeDownloads.update { list -> list.filterNot { it.trackId == trackId } }
            }
        }

    override suspend fun removeTrackFromLibrary(trackId: Long) = withContext(Dispatchers.IO) {
        val entity = archiveLibraryDao.getByTrackId(trackId) ?: return@withContext
        runCatching { Uri.parse(entity.localFileUri).path?.let { File(it).delete() } }
        archiveLibraryDao.delete(trackId)
    }

    /** Strumieniuje odpowiedź prosto na dysk — bez ładowania całego pliku (mogą to być dziesiątki MB) do pamięci. */
    private fun downloadToFile(url: String, destination: File) {
        val request = Request.Builder().url(url).header("User-Agent", "AuroraMusicPlayer/1.0").build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Pusta odpowiedź serwera")
            try {
                body.byteStream().use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                destination.delete()
                throw e
            }
        }
    }

    override suspend fun browseCategory(category: ArchiveCategory, limit: Int): List<ArchiveItem> =
        withContext(Dispatchers.IO) {
            itemsCache.getOrPut("category:$category:$limit") {
                // Etap 46, zgłoszenie: było `sort = "date+desc"` (najnowsze UPLOADY) — na Internet
                // Archive każdy może wgrać cokolwiek w dowolnej chwili, więc "najnowsze" to głównie
                // szum (testowe pliki, przypadkowe nazwy), nie coś wartego pokazania. `downloads+desc`
                // to ten sam sygnał popularności, którego już używa [topPopular]/[personalizedForYou].
                fetchItems("${category.collectionQuery()} AND mediatype:audio", limit, sort = "downloads+desc")
            }
        }

    /**
     * Top popularne wg regionu urządzenia — IA nie ma pola geograficznego, więc "region" przybliżamy
     * językiem systemowym (`Locale` → `language:` w metadanych, ta sama mapa co `#tag` w [search]).
     * Gdy region nie da wyników (niszowy język / brak treści w tym języku na IA), spadamy na Zachód
     * (UE/USA/Australia, patrz [WESTERN_FALLBACK_LANGUAGES]) z wykluczeniem rosyjskojęzycznych —
     * a dopiero jeśli i to nic nie zwróci, na zupełnie globalne top popularne.
     */
    override suspend fun topPopular(limit: Int): List<ArchiveItem> = withContext(Dispatchers.IO) {
        val regionLanguage = LANGUAGE_TAGS[context.resources.configuration.locales[0].language.lowercase()]
        val regional = regionLanguage?.let {
            itemsCache.getOrPut("popular:$it:$limit") {
                fetchItems("mediatype:audio AND language:($it)", limit, sort = "downloads+desc")
            }
        }
        if (!regional.isNullOrEmpty()) return@withContext regional

        val western = itemsCache.getOrPut("popular:western:$limit") {
            fetchItems(WESTERN_FALLBACK_QUERY, limit, sort = "downloads+desc")
        }
        if (western.isNotEmpty()) return@withContext western

        itemsCache.getOrPut("popular:$limit") {
            fetchItems("mediatype:audio", limit, sort = "downloads+desc")
        }
    }

    override suspend fun search(query: String, category: ArchiveCategory?, limit: Int): List<ArchiveItem> {
        if (query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            val tokens = trimmed.split(Regex("\\s+"))
            val tagClauses = tokens.filter { it.length > 1 && it.startsWith("#") }.map { tagClause(it.substring(1)) }
            val freeText = tokens.filterNot { it.startsWith("#") }.joinToString(" ").trim()
            val textClause = freeText.takeIf { it.isNotBlank() }?.let { "(${escapeLucene(it)})" }
            val clauses = listOfNotNull(textClause) + tagClauses
            if (clauses.isEmpty()) return@withContext emptyList()
            val categoryFilter = category?.let { "${it.collectionQuery()} AND " }.orEmpty()
            itemsCache.getOrPut("search:$category:$trimmed:$limit") {
                fetchItems("${categoryFilter}mediatype:audio AND (${clauses.joinToString(" AND ")})", limit, sort = null)
            }
        }
    }

    /**
     * `#tag` w wyszukiwarce (np. `#pl`, `#rap`) — Internet Archive nie ma hashtagów, więc dwuliterowe
     * kody językowe idą do pola `language:` (`#pl` → `Polish`), reszta do `subject:` (temat/tag nadany
     * przez uploadera). Pokrycie metadanych bywa niepełne, więc brak wyników nie znaczy braku treści.
     */
    private fun tagClause(rawTag: String): String {
        val normalized = rawTag.lowercase()
        val escaped = escapeLucene(normalized)
        val language = LANGUAGE_TAGS[normalized]
        return if (language != null) "(language:($language) OR subject:($escaped))" else "subject:($escaped)"
    }

    /**
     * Mapowanie kategorii UI na realne kolekcje Internet Archive (nawigacja archive.org/details/audio):
     * Live Music Archive + Netlabele + Community Audio dla muzyki, Podcasts, Audio Books & Poetry,
     * Radio Programs + Old Time Radio dla radia.
     */
    private fun ArchiveCategory.collectionQuery(): String = when (this) {
        ArchiveCategory.MUSIC -> "(collection:etree OR collection:netlabels OR collection:opensource_audio)"
        ArchiveCategory.PODCASTS -> "collection:podcasts"
        ArchiveCategory.AUDIOBOOKS -> "collection:audio_bookspoetry"
        ArchiveCategory.RADIO -> "(collection:radioprograms OR collection:oldtimeradio)"
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

        val genreMatches = favoriteGenres.take(MAX_TASTE_GENRES).mapNotNull { collectionsForGenre(it) }.flatMap {
            val collectionQuery = it.joinToString(" OR ") { collection -> "collection:$collection" }
            fetchItems(
                query = "($collectionQuery) AND mediatype:audio",
                limit = PER_QUERY_LIMIT,
                sort = "downloads+desc",
            )
        }

        (artistMatches + genreMatches).distinctBy { it.identifier }.take(limit)
    }

    override suspend fun personalizedForCategory(
        category: ArchiveCategory,
        favoriteArtists: List<String>,
        favoriteGenres: List<String>,
        limit: Int,
    ): List<ArchiveItem> = withContext(Dispatchers.IO) {
        if (favoriteArtists.isEmpty() && favoriteGenres.isEmpty()) return@withContext emptyList()
        val categoryFilter = category.collectionQuery()

        val artistMatches = favoriteArtists.take(MAX_TASTE_ARTISTS).flatMap { artist ->
            val escaped = escapeLucene(artist)
            fetchItems(
                query = "creator:(\"$escaped\") AND mediatype:audio AND $categoryFilter",
                limit = PER_QUERY_LIMIT,
                sort = "downloads+desc",
            )
        }

        // Mapa gatunek→kolekcja jest muzyczna (patrz GENRE_TO_COLLECTIONS) — dla innych kategorii
        // (Podcasty/Audiobooki/Radio) dopasowanie po gatunku nic by nie znaczyło.
        val genreMatches = if (category == ArchiveCategory.MUSIC) {
            favoriteGenres.take(MAX_TASTE_GENRES).mapNotNull { collectionsForGenre(it) }.flatMap {
                val collectionQuery = it.joinToString(" OR ") { collection -> "collection:$collection" }
                fetchItems(
                    query = "($collectionQuery) AND mediatype:audio",
                    limit = PER_QUERY_LIMIT,
                    sort = "downloads+desc",
                )
            }
        } else {
            emptyList()
        }

        (artistMatches + genreMatches).distinctBy { it.identifier }.take(limit)
    }

    /**
     * `null` = gatunek nie pasuje do żadnego znanego słowa kluczowego — zgłoszenie: wcześniejszy
     * fallback na `netlabels` sprawiał, że "personalizacja" dla usera z mainstreamową biblioteką
     * (Eminem/Metallica/Linkin Park — nic z tego nie ma szans trafić w etree/netlabels) zawsze
     * cichcem zwracała ten sam generyczny browse netlabels, wyglądający jak dopasowanie, a
     * faktycznie z niczym niepowiązany. `null` pozwala wywołującemu (patrz `personalizedForYou`)
     * pominąć taki gatunek zamiast doklejać fałszywy sygnał.
     */
    private fun collectionsForGenre(genre: String): List<String>? {
        val normalized = genre.lowercase()
        return GENRE_TO_COLLECTIONS.entries.firstOrNull { (keyword, _) -> normalized.contains(keyword) }?.value
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

        /** Dwuliterowe kody `#tag` rozpoznawane jako język (pole `language:` w metadanych IA) zamiast `subject:`. */
        val LANGUAGE_TAGS = mapOf(
            "pl" to "Polish", "en" to "English", "de" to "German", "fr" to "French",
            "es" to "Spanish", "it" to "Italian", "ru" to "Russian", "pt" to "Portuguese",
            "nl" to "Dutch", "uk" to "Ukrainian", "cs" to "Czech", "sk" to "Slovak",
            "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese", "sv" to "Swedish",
        )

        /** Fallback regionu dla [topPopular], gdy język urządzenia nie ma dopasowania na IA — Zachód (UE/USA/Australia), świadomie bez rosyjskiego. */
        val WESTERN_FALLBACK_LANGUAGES = listOf(
            "English", "German", "French", "Spanish", "Italian", "Polish", "Dutch", "Portuguese", "Swedish", "Czech", "Slovak",
        )

        /** `NOT language:(Russian)` jako dodatkowe zabezpieczenie — pozycja może mieć kilka języków w metadanych (np. "English, Russian"). */
        val WESTERN_FALLBACK_QUERY =
            "mediatype:audio AND (${WESTERN_FALLBACK_LANGUAGES.joinToString(" OR ") { "language:($it)" }}) " +
                "AND NOT language:(Russian)"

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
    }
}
