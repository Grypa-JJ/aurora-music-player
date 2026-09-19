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
 *
 * Zgłoszenie: ten sam bug co przy JRE (`PodcastRepositoryImpl`) i metadanych Archiwum
 * (`ArchiveRepositoryImpl`) — pojedynczy przejściowy błąd sieci wyglądał identycznie jak realny
 * "brak tekstu" (404), a [LyricsRepositoryImpl] cache'uje wynik NA STAŁE w Room (offline-first,
 * świadomie). Efekt: jeden zonk sieciowy = utwór trwale oznaczony jako "bez tekstu", nawet po
 * restarcie appki, mimo że LRCLIB realnie ma tekst. [LrcLibFetchResult] rozróżnia teraz "naprawdę
 * nie znaleziono" (404, bezpieczne do cache'owania) od "nie udało się sprawdzić" (błąd
 * sieci/serwera PO 3 próbach — repozytorium tego NIE cache'uje, więc kolejne odtworzenie spróbuje
 * ponownie).
 */
@Singleton
class LrcLibClient @Inject constructor() {
    private val httpClient = OkHttpClient.Builder().build()

    suspend fun fetch(track: Track): LrcLibFetchResult = withContext(Dispatchers.IO) {
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

        var lastError: Exception? = null
        repeat(MAX_FETCH_ATTEMPTS) { attempt ->
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.code == 404) return@withContext LrcLibFetchResult.NotFound
                    if (!response.isSuccessful) {
                        Log.e(TAG, "fetch(): HTTP ${response.code} dla „${track.title}” (próba ${attempt + 1})")
                    } else {
                        val json = JSONObject(response.body?.string() ?: return@withContext LrcLibFetchResult.NotFound)
                        return@withContext LrcLibFetchResult.Found(
                            LrcLibResponse(
                                syncedLyrics = json.optString("syncedLyrics").takeIf { it.isNotBlank() },
                                plainLyrics = json.optString("plainLyrics").takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }
            } catch (e: IOException) {
                lastError = e
                Log.e(TAG, "fetch(): błąd sieci dla „${track.title}” (próba ${attempt + 1}/$MAX_FETCH_ATTEMPTS)", e)
            }
            if (attempt < MAX_FETCH_ATTEMPTS - 1) Thread.sleep(FETCH_RETRY_DELAY_MS)
        }
        lastError?.let { Log.e(TAG, "fetch(): wszystkie próby nieudane dla „${track.title}”", it) }
        LrcLibFetchResult.NetworkError
    }

    private companion object {
        const val TAG = "LrcLibClient"
        const val MAX_FETCH_ATTEMPTS = 3
        const val FETCH_RETRY_DELAY_MS = 500L
    }
}

data class LrcLibResponse(val syncedLyrics: String?, val plainLyrics: String?)

sealed interface LrcLibFetchResult {
    data class Found(val response: LrcLibResponse) : LrcLibFetchResult
    data object NotFound : LrcLibFetchResult
    data object NetworkError : LrcLibFetchResult
}
