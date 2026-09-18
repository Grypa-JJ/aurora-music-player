package com.aurora.player.data.media

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Size
import androidx.core.net.toUri
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TrackSource
import com.aurora.player.domain.repository.LocalAlbumArtRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Etap 42/44: `ContentResolver.loadThumbnail()` (API 29+) zamiast starego, zawodnego
 * `content://media/external/audio/albumart/{id}`. Wynik cache'owany RAZ NA ALBUM jako plik JPEG
 * we własnym cache appki — sprawdzany PIERWSZY (tani `File.exists()`), więc kolejne utwory tego
 * samego albumu i kolejne uruchomienia appki nie odtwarzają dekodowania bitmapy. `withTimeoutOrNull`
 * na każde wywołanie — wcześniejsza wersja tej poprawki robiła to SYNCHRONICZNIE wewnątrz
 * `MediaStoreScanner.scanTracks()` i wieszała ładowanie biblioteki; ten sam błąd nie może się
 * powtórzyć nawet tutaj.
 *
 * Etap 44: metoda per-utwór (nie per-lista) — WOŁAJĄCY ([MetadataEnrichmentRepositoryImpl])
 * decyduje, kiedy próbować lokalnie a kiedy przejść do sieci, w JEDNYM wspólnym potoku per
 * utwór, zamiast dwóch niezależnych przebiegów po całej bibliotece wyścigujących się o to samo
 * pole `albumArtUri` (patrz DESIGN.md Etap 44 — to była realna regresja poprzedniej wersji).
 */
@Singleton
class LocalAlbumArtRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalAlbumArtRepository {

    override suspend fun resolveOne(track: Track): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (track.source != TrackSource.LOCAL) return null
        val albumId = track.albumId ?: return null

        val cacheFile = File(albumArtCacheDir, "$albumId.jpg")
        if (cacheFile.exists()) return cacheFile.toUri().toString()

        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(THUMBNAIL_TIMEOUT_MS) {
                try {
                    val bitmap = context.contentResolver.loadThumbnail(track.uri.toUri(), THUMBNAIL_SIZE, null)
                    albumArtCacheDir.mkdirs()
                    FileOutputStream(cacheFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    cacheFile.toUri().toString()
                } catch (_: Exception) {
                    // Utwór faktycznie nie ma okładki (ani wbudowanej, ani skojarzonej w
                    // MediaStore) — `null` to ten sam sygnał co dotąd dla dalszego fallbacku.
                    null
                }
            }
        }
    }

    private val albumArtCacheDir: File
        get() = File(context.cacheDir, "album_art")

    private companion object {
        val THUMBNAIL_SIZE = Size(512, 512)
        const val THUMBNAIL_TIMEOUT_MS = 1500L
    }
}
