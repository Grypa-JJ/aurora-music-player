package com.aurora.player.data.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.aurora.player.domain.model.AudioTrackMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Odczyt realnych parametrów audio (codec/bitrate/sample rate/bit depth) wprost z kontenera
 * pliku przez `MediaExtractor` — MediaStore tych danych nie ma. DESIGN.md Etap 29. Świadomie NIE
 * liczy bit depth przez estymację z rozmiaru pliku/czasu trwania — appka ma milczeć (`null`), nie
 * zgadywać (ta sama zasada co `AudioCapabilities`/`BitPerfectStatus` z Etapu 21).
 */
@Singleton
class AudioMetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun extract(uri: String): AudioTrackMetadata? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            val audioTrackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext null
            val format = extractor.getTrackFormat(audioTrackIndex)
            AudioTrackMetadata(
                codec = codecLabel(format.getString(MediaFormat.KEY_MIME)),
                sampleRateHz = format.getIntOrNull(MediaFormat.KEY_SAMPLE_RATE),
                bitrateKbps = format.getIntOrNull(MediaFormat.KEY_BIT_RATE)?.let { it / 1000 },
                // Nie każdy kontener/dekoder to wystawia (głównie FLAC/WAV) — brak nie znaczy błędu.
                bitDepth = format.getIntOrNull("bits-per-sample"),
            )
        } catch (e: Exception) {
            Log.w(TAG, "extract(): nie udało się odczytać $uri", e)
            null
        } finally {
            extractor.release()
        }
    }

    private fun codecLabel(mime: String?): String? = when (mime) {
        "audio/mpeg" -> "MP3"
        "audio/flac" -> "FLAC"
        "audio/mp4a-latm" -> "AAC"
        "audio/x-wav", "audio/wav" -> "WAV"
        "audio/opus" -> "Opus"
        "audio/ogg", "audio/vorbis" -> "Vorbis"
        null -> null
        else -> mime.substringAfter('/').uppercase()
    }

    private fun MediaFormat.getIntOrNull(key: String): Int? = if (containsKey(key)) getInteger(key) else null

    private companion object {
        const val TAG = "AudioMetadataExtractor"
    }
}
