package com.aurora.player.podcast

import android.util.Log
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TranscriptResult
import com.aurora.player.domain.repository.TranscriptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transkrypcja odcinka podkastu (`<podcast:transcript>`, patrz [PodcastRssParser.bestTranscript]) —
 * DESIGN.md Etap 54, zgłoszenie: "czy da się wbudować tłumaczenie z ang na polski". Odcinki
 * podkastów NIE są cache'owane w Room (parsowane na żywo z RSS przy każdym wejściu — patrz
 * [com.aurora.player.domain.model.PodcastEpisode]), więc transkrypcja też zostaje w pamięci
 * procesu — ten sam wzorzec co `itemsCache`/`tracksCache` w `ArchiveRepositoryImpl`.
 *
 * Retry + brak cache'owania błędów sieci: ten sam bug i to samo lekarstwo co
 * `LrcLibClient`/`PodcastRepositoryImpl.fetchXml`/`ArchiveRepositoryImpl.fetchJson` w tej samej
 * sesji — pojedynczy zonk sieciowy nie ma trwale oznaczać odcinka jako "bez transkrypcji".
 */
@Singleton
class TranscriptRepositoryImpl @Inject constructor() : TranscriptRepository {
    private val httpClient = OkHttpClient.Builder().build()
    private val cache = ConcurrentHashMap<Long, String>()

    override suspend fun getTranscript(track: Track): TranscriptResult = withContext(Dispatchers.IO) {
        val url = track.transcriptUrl ?: return@withContext TranscriptResult.NotAvailable
        cache[track.id]?.let { return@withContext TranscriptResult.Loaded(it) }

        val raw = fetchText(url) ?: return@withContext TranscriptResult.NotAvailable
        val text = parseTranscript(raw, track.transcriptType)
        if (text.isBlank()) return@withContext TranscriptResult.NotAvailable
        cache[track.id] = text
        TranscriptResult.Loaded(text)
    }

    private fun fetchText(url: String): String? {
        var lastError: Exception? = null
        repeat(MAX_FETCH_ATTEMPTS) { attempt ->
            try {
                val request = Request.Builder().url(url).header("User-Agent", "AuroraMusicPlayer/1.0").build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "fetchText(): HTTP ${response.code} dla $url (próba ${attempt + 1})")
                    } else {
                        return response.body?.string()
                    }
                }
            } catch (e: IOException) {
                lastError = e
                Log.e(TAG, "fetchText(): błąd sieci dla $url (próba ${attempt + 1}/$MAX_FETCH_ATTEMPTS)", e)
            }
            if (attempt < MAX_FETCH_ATTEMPTS - 1) Thread.sleep(FETCH_RETRY_DELAY_MS)
        }
        lastError?.let { Log.e(TAG, "fetchText(): wszystkie próby nieudane dla $url", it) }
        return null
    }

    /**
     * `text/plain` leci bez zmian. `text/vtt`/`application/srt` to napisy — zdejmujemy nagłówek
     * `WEBVTT`, numery/indeksy cue i linie timecode (`00:00:01.000 --> 00:00:04.000`), zostawiając
     * sam tekst mówiony jako ciągły akapit (transkrypcja do CZYTANIA, nie karaoke jak w Lyrics —
     * nie ma tu potrzeby zachowywać synchronizacji).
     */
    private fun parseTranscript(raw: String, type: String?): String {
        if (type != null && !type.contains("vtt") && !type.contains("srt")) return raw.trim()
        val timecodeRegex = Regex("""\d{2}:\d{2}:\d{2}[.,]\d{3}\s*-->\s*\d{2}:\d{2}:\d{2}[.,]\d{3}.*""")
        val cueNumberRegex = Regex("""^\d+$""")
        return raw.lineSequence()
            .map { it.trim() }
            .filterNot { line ->
                line.isEmpty() ||
                    line.equals("WEBVTT", ignoreCase = true) ||
                    line.startsWith("NOTE") ||
                    timecodeRegex.matches(line) ||
                    cueNumberRegex.matches(line)
            }
            .joinToString(" ")
            .trim()
    }

    private companion object {
        const val TAG = "TranscriptRepository"
        const val MAX_FETCH_ATTEMPTS = 3
        const val FETCH_RETRY_DELAY_MS = 500L
    }
}
