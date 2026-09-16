package com.aurora.player.radio

import android.util.Log
import com.aurora.player.domain.model.RadioStation
import com.aurora.player.domain.repository.RadioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stacje radiowe przez Radio-Browser (radio-browser.info) — DESIGN.md Etap 31. Otwarta,
 * darmowa baza ~40k stacji, ZERO klucza API/rejestracji (w przeciwieństwie do Podcast Index,
 * patrz PodcastRepositoryImpl). Stały, dobrze znany mirror zamiast round-robin przez DNS SRV
 * (oficjalnie zalecane dla dużej skali, ale wymagałoby dodatkowej biblioteki DNS-nad-HTTPS) —
 * wystarcza dla skali jednej appki mobilnej; ten sam kompromis co pojedynczy host w innych
 * integracjach sieciowych appki.
 */
@Singleton
class RadioRepositoryImpl @Inject constructor() : RadioRepository {
    private val httpClient = OkHttpClient.Builder().build()

    override suspend fun topStationsByCountry(countryCode: String, limit: Int): List<RadioStation> =
        withContext(Dispatchers.IO) {
            fetch("$BASE_URL/json/stations/bycountrycodeexact/$countryCode?order=votes&reverse=true&limit=$limit&hidebroken=true")
        }

    override suspend fun search(query: String, limit: Int): List<RadioStation> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            fetch("$BASE_URL/json/stations/search?name=$encoded&limit=$limit&hidebroken=true&order=votes&reverse=true")
        }

    private fun fetch(url: String): List<RadioStation> {
        // Radio-Browser prosi klientów o identyfikowalny User-Agent (dokumentacja API) — bez
        // tego część zapytań bywa ograniczana.
        val request = Request.Builder().url(url).header("User-Agent", "AuroraMusicPlayer/1.0").build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "fetch(): HTTP ${response.code} dla $url")
                    return emptyList()
                }
                parseStations(response.body?.string() ?: return emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetch(): błąd sieci dla $url", e)
            emptyList()
        }
    }

    private fun parseStations(json: String): List<RadioStation> {
        val array = JSONArray(json)
        val stations = mutableListOf<RadioStation>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            // `url_resolved` to URL PO podążeniu za przekierowaniami (serwer Radio-Browser już to
            // zrobił za nas) — preferowany nad surowym `url`, gdy dostępny.
            val streamUrl = obj.optString("url_resolved").ifBlank { obj.optString("url") }
            if (streamUrl.isBlank()) continue
            stations += RadioStation(
                stationUuid = obj.optString("stationuuid"),
                name = obj.optString("name").ifBlank { "Nienazwana stacja" },
                streamUrl = streamUrl,
                faviconUrl = obj.optString("favicon").takeIf { it.isNotBlank() },
                countryCode = obj.optString("countrycode"),
                tags = obj.optString("tags"),
                votes = obj.optInt("votes"),
            )
        }
        return stations
    }

    private companion object {
        const val TAG = "RadioRepositoryImpl"
        const val BASE_URL = "https://de1.api.radio-browser.info"
    }
}
