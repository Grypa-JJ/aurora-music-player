package com.aurora.player.lyrics

import android.util.Log
import com.aurora.player.domain.model.Track
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
 * Klient LRCLIB (lrclib.net) — darmowe, otwarte API bez klucza, zwraca zsynchronizowany `.lrc`
 * gdy dostępny — DESIGN.md Etap 24. Bez Retrofit/Moshi: jedno proste GET, ten sam minimalistyczny
 * wzorzec co [com.aurora.player.cloud.WebDavLibraryRepository] (OkHttp bezpośrednio, `org.json`
 * wbudowany w Android zamiast nowej zależności JSON).
 */
@Singleton
class LrcLibClient @Inject constructor() {
    private val httpClient = OkHttpClient.Builder().build()

    /** `null` = błąd sieci LUB brak wyniku (404) — appka traktuje obie sytuacje jednakowo. */
    suspend fun fetch(track: Track): LrcLibResponse? = withContext(Dispatchers.IO) {
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", track.title)
            .addQueryParameter("artist_name", track.artist)
            .addQueryParameter("album_name", track.album)
            .addQueryParameter("duration", (track.durationMs / 1000).toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AuroraMusicPlayer/1.0 (Android)")
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string() ?: return@withContext null)
                LrcLibResponse(
                    syncedLyrics = json.optString("syncedLyrics").takeIf { it.isNotBlank() },
                    plainLyrics = json.optString("plainLyrics").takeIf { it.isNotBlank() },
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "fetch(): błąd sieci dla „${track.title}”", e)
            null
        }
    }

    private companion object {
        const val TAG = "LrcLibClient"
    }
}

data class LrcLibResponse(val syncedLyrics: String?, val plainLyrics: String?)
