package com.aurora.player.metadata

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cover Art Archive (coverartarchive.org) — darmowe, bez klucza, okładki powiązane z wydaniami
 * MusicBrainz przez `releaseMbid`. DESIGN.md Etap 28: dociąga okładkę dla utworów bez lokalnej
 * (`Track.albumArtUri == null`) — inne kryterium kandydowania niż poprawa tytułu/wykonawcy w
 * [MusicBrainzClient]/[com.aurora.player.domain.util.TrackMetadataHeuristics.looksIncomplete].
 */
@Singleton
class CoverArtArchiveClient @Inject constructor() {
    private val httpClient = OkHttpClient.Builder().build()

    /**
     * `null` = brak okładki dla tego wydania (częste — CAA nie ma kompletu) LUB błąd sieci.
     * Appka NIE pobiera bajtów obrazu sama — zwraca sam URL, Coil (już użyty w `AsyncImage` na
     * Now Playing/liście utworów) dociąga go leniwie przy renderze, tym samym mechanizmem co
     * lokalne `content://`/zdalne okładki Google Drive.
     */
    suspend fun frontCoverUrl(releaseMbid: String): String? = withContext(Dispatchers.IO) {
        val url = "https://coverartarchive.org/release/$releaseMbid/front-250"
        val request = Request.Builder().url(url).head().build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) url else null
            }
        } catch (e: IOException) {
            Log.e(TAG, "frontCoverUrl(): błąd sieci dla wydania $releaseMbid", e)
            null
        }
    }

    private companion object {
        const val TAG = "CoverArtArchiveClient"
    }
}
