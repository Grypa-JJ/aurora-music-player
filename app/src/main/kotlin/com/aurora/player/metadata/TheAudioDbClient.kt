package com.aurora.player.metadata

import android.util.Log
import com.aurora.player.domain.model.ArtistInfo
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
 * Klient TheAudioDB (`theaudiodb.com/api/v1/json/{key}/search.php`) — bio/gatunek/styl/nastrój/
 * grafika wykonawcy, DESIGN.md Etap 43.
 *
 * [API_KEY] to PUBLICZNY klucz testowy TheAudioDB, nie prywatny klucz jednego developera — celowo
 * inny przypadek niż Podcast Index (Etap 32), gdzie appka NIE mogła wbudować jednego wspólnego
 * klucza dla wszystkich userów, bo ToS Podcast Index wymaga rejestracji PER developer, a limit
 * per-klucz byłby dzielony między wszystkich userów appki jednocześnie. TheAudioDB sam publikuje
 * ten klucz w swojej dokumentacji jako ogólnodostępny, bez rejestracji — to inny model biznesowy
 * (świadomie współdzielony klucz "darmowego poziomu", nie klucz developera). Realny kompromis:
 * appka to jeden z wielu klientów na świecie dzielących ten sam limit 30 zapytań/min — stąd
 * [TheAudioDbRateLimiter] z marginesem, i appka MUSI znosić błędy/rate-limit bez crashowania
 * (zwraca `null`, ten sam sygnał co "nic nie znaleziono" — patrz [ArtistInfoRepositoryImpl]).
 */
@Singleton
class TheAudioDbClient @Inject constructor(
    private val rateLimiter: TheAudioDbRateLimiter,
) {
    private val httpClient = OkHttpClient.Builder().build()

    suspend fun findArtist(artistName: String): ArtistInfo? = withContext(Dispatchers.IO) {
        rateLimiter.awaitTurn()
        val url = "https://www.theaudiodb.com/api/v1/json/$API_KEY/search.php".toHttpUrl().newBuilder()
            .addQueryParameter("s", artistName)
            .build()
        try {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                parseArtist(response.body?.string() ?: return@withContext null)
            }
        } catch (e: IOException) {
            Log.e(TAG, "findArtist(): błąd sieci dla „$artistName”", e)
            null
        }
    }

    private fun parseArtist(body: String): ArtistInfo? {
        val artists = JSONObject(body).optJSONArray("artists") ?: return null
        if (artists.length() == 0) return null
        val artist = artists.getJSONObject(0)
        // Biografia PL, jeśli TheAudioDB ją ma — appka jest PL-pierwsza (patrz reszta UI); pusty
        // string traktowany jak brak (TheAudioDB potrafi zwrócić "" zamiast pominąć pole).
        val biography = artist.optString("strBiographyPL").takeIf { it.isNotBlank() }
            ?: artist.optString("strBiography").takeIf { it.isNotBlank() }
        return ArtistInfo(
            biography = biography,
            genre = artist.optString("strGenre").takeIf { it.isNotBlank() },
            style = artist.optString("strStyle").takeIf { it.isNotBlank() },
            mood = artist.optString("strMood").takeIf { it.isNotBlank() },
            bannerUrl = artist.optString("strArtistBanner").takeIf { it.isNotBlank() },
            thumbUrl = artist.optString("strArtistThumb").takeIf { it.isNotBlank() },
        )
    }

    private companion object {
        const val TAG = "TheAudioDbClient"
        const val API_KEY = "523532"
    }
}
