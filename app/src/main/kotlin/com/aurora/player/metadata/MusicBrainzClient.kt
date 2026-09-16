package com.aurora.player.metadata

import android.util.Log
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.util.TrackMetadataHeuristics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Klient MusicBrainz (`musicbrainz.org/ws/2/recording`) — dopasowanie nagrania, gdy appce brakuje
 * czegoś o utworze: złe/puste tagi (DESIGN.md Etap 25) LUB brak lokalnej okładki (Etap 28,
 * [com.aurora.player.domain.util.TrackMetadataHeuristics]). Dwa RÓŻNE zapytania zależnie od
 * powodu — [queryFor]. [MusicBrainzRateLimiter] pilnuje limitu ~1 zapytanie/s wymaganego przez
 * MusicBrainz dla anonimowych klientów — appka i tak odpytuje sekwencyjnie, jeden utwór na raz.
 *
 * UWAGA przed publicznym wydaniem: [USER_AGENT] nie ma realnego, publicznego URL-a kontaktowego —
 * MusicBrainz może zacząć throttlować/blokować identyfikację bez tego w produkcji, nie tylko przy
 * hobbystycznym użyciu lokalnym (patrz ich zasady dot. `User-Agent`).
 */
@Singleton
class MusicBrainzClient @Inject constructor(
    private val rateLimiter: MusicBrainzRateLimiter,
) {
    private val httpClient = OkHttpClient.Builder().build()

    suspend fun findBestMatch(track: Track): MusicBrainzMatch? = withContext(Dispatchers.IO) {
        rateLimiter.awaitTurn()
        val url = "https://musicbrainz.org/ws/2/recording/".toHttpUrl().newBuilder()
            .addQueryParameter("query", queryFor(track))
            .addQueryParameter("fmt", "json")
            .addQueryParameter("limit", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                parseBestRecording(response.body?.string() ?: return@withContext null)
            }
        } catch (e: IOException) {
            Log.e(TAG, "findBestMatch(): błąd sieci dla „${track.uri}”", e)
            null
        }
    }

    /**
     * Tagi wyglądają źle ([TrackMetadataHeuristics.looksIncomplete]) → jedyny sygnał to nazwa
     * pliku, wolny tekst (Lucene). Tagi wyglądają DOBRZE (utwór trafił tu tylko po okładkę,
     * Etap 28) → zapytanie polowe `artist:"..." AND recording:"..."`, znacznie trafniejsze niż
     * zgadywanie z nazwy pliku, skoro appka już ma wiarygodny tytuł/wykonawcę.
     */
    private fun queryFor(track: Track): String =
        if (TrackMetadataHeuristics.looksIncomplete(track)) {
            track.uri.substringAfterLast('/').substringBeforeLast('.').replace('_', ' ').replace('-', ' ')
        } else {
            "artist:\"${track.artist.replace("\"", "")}\" AND recording:\"${track.title.replace("\"", "")}\""
        }

    private fun parseBestRecording(body: String): MusicBrainzMatch? {
        val recordings = JSONObject(body).optJSONArray("recordings") ?: return null
        if (recordings.length() == 0) return null
        val best = recordings.getJSONObject(0)
        val title = best.optString("title").takeIf { it.isNotBlank() } ?: return null
        val artist = best.optJSONArray("artist-credit")
            ?.optJSONObject(0)
            ?.optString("name")
            ?.takeIf { it.isNotBlank() }
        val release = best.optJSONArray("releases")?.optJSONObject(0)
        val album = release?.optString("title")?.takeIf { it.isNotBlank() }
        // MBID wydania — jedyny sposób, żeby potem odpytać Cover Art Archive o okładkę (Etap 28).
        val releaseMbid = release?.optString("id")?.takeIf { it.isNotBlank() }
        return MusicBrainzMatch(title = title, artist = artist, album = album, releaseMbid = releaseMbid)
    }

    private companion object {
        const val TAG = "MusicBrainzClient"
        const val USER_AGENT = "AuroraMusicPlayer/0.1.0 (local build, no public contact yet)"
    }
}

data class MusicBrainzMatch(
    val title: String,
    val artist: String?,
    val album: String?,
    val releaseMbid: String?,
)
