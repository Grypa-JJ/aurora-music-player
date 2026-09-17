package com.aurora.player.independent

import android.util.Log
import com.aurora.player.BuildConfig
import com.aurora.player.domain.model.IndependentTrack
import com.aurora.player.domain.repository.IndependentMusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Muzyka niezależna" w UI — Jamendo pod spodem (500k utworów CC, katalog niezależnych artystów).
 * `client_id` wbudowany na stałe przez `BuildConfig.JAMENDO_CLIENT_ID` (patrz app/build.gradle.kts)
 * — to client_id APLIKACJI (limit 35k zapytań/mies. jest per-aplikacja, nie per-user), inaczej niż
 * celowo nie-wbudowany klucz Podcast Index. `:app` bo potrzebuje OkHttp, ten sam podział co
 * Radio/Podcasty/Archiwum.
 */
@Singleton
class JamendoRepositoryImpl @Inject constructor() : IndependentMusicRepository {

    private val httpClient = OkHttpClient.Builder().build()

    override suspend fun search(query: String, limit: Int): List<IndependentTrack> {
        if (query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            fetchTracks("namesearch=${URLEncoder.encode(query.trim(), "UTF-8")}", limit)
        }
    }

    override suspend fun tracksByTag(tag: String, limit: Int): List<IndependentTrack> =
        withContext(Dispatchers.IO) {
            fetchTracks("tags=${URLEncoder.encode(tag, "UTF-8")}&order=popularity_total", limit)
        }

    override suspend fun trending(limit: Int): List<IndependentTrack> = withContext(Dispatchers.IO) {
        fetchTracks("order=popularity_month", limit)
    }

    private fun fetchTracks(queryParams: String, limit: Int): List<IndependentTrack> {
        val clientId = BuildConfig.JAMENDO_CLIENT_ID
        if (clientId.isBlank()) {
            Log.w(TAG, "fetchTracks(): brak JAMENDO_CLIENT_ID w local.properties — sekcja nieaktywna")
            return emptyList()
        }
        val url = "$BASE_URL?client_id=$clientId&format=json&limit=$limit" +
            "&include=musicinfo&audioformat=mp31&$queryParams"
        val json = fetchJson(url) ?: return emptyList()
        return try {
            val root = JSONObject(json)
            if (root.optJSONObject("headers")?.optString("status") == "failed") {
                Log.e(TAG, "fetchTracks(): Jamendo zwróciło błąd — ${root.optJSONObject("headers")?.optString("error_message")}")
                return emptyList()
            }
            root.optJSONArray("results")?.toTracks().orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "fetchTracks(): błąd parsowania odpowiedzi", e)
            emptyList()
        }
    }

    private fun JSONArray.toTracks(): List<IndependentTrack> {
        val tracks = mutableListOf<IndependentTrack>()
        for (i in 0 until length()) {
            val result = optJSONObject(i) ?: continue
            val id = result.optString("id")
            if (id.isBlank()) continue
            val audioUrl = result.optString("audio")
            if (audioUrl.isBlank()) continue
            tracks += IndependentTrack(
                id = id,
                name = result.optString("name").ifBlank { "Bez tytułu" },
                artistName = result.optString("artist_name").ifBlank { "Nieznany artysta" },
                albumName = result.optString("album_name").ifBlank { null },
                imageUrl = result.optString("image").ifBlank { null },
                audioUrl = audioUrl,
                durationSec = result.optInt("duration"),
                licenseCcUrl = result.optString("license_ccurl").ifBlank { null },
            )
        }
        return tracks
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

    private companion object {
        const val TAG = "JamendoRepositoryImpl"
        const val BASE_URL = "https://api.jamendo.com/v3.0/tracks/"
    }
}
