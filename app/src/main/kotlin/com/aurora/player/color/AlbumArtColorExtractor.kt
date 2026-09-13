package com.aurora.player.color

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AlbumArtPalette(
    val vibrant: Color? = null,
    val darkMuted: Color? = null,
    val darkVibrant: Color? = null,
    val lightVibrant: Color? = null,
)

/**
 * Wyciąga dominujące kolory z okładki albumu (Palette API) — patrz DESIGN.md sekcja 2.1,
 * "dwuwarstwowy model akcentu". Ekstrakcja jest asynchroniczna i cache'owana per trackId,
 * bo liczenie Palette na każdej recompozycji zacinałoby scroll/przełączanie utworów.
 */
@Singleton
class AlbumArtColorExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = LinkedHashMap<Long, AlbumArtPalette>()

    suspend fun extract(trackId: Long, albumArtUri: String?): AlbumArtPalette {
        cache[trackId]?.let { return it }
        if (albumArtUri == null) return AlbumArtPalette()

        val result = withContext(Dispatchers.Default) {
            runCatching {
                val bitmap = context.contentResolver.openInputStream(Uri.parse(albumArtUri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: return@runCatching AlbumArtPalette()

                val palette = Palette.Builder(bitmap).generate()
                AlbumArtPalette(
                    vibrant = palette.vibrantSwatch?.rgb?.let(::Color),
                    darkMuted = palette.darkMutedSwatch?.rgb?.let(::Color),
                    darkVibrant = palette.darkVibrantSwatch?.rgb?.let(::Color),
                    lightVibrant = palette.lightVibrantSwatch?.rgb?.let(::Color),
                )
            }.getOrDefault(AlbumArtPalette())
        }

        // Prosty limit wielkości cache'u — pełny LRU nie jest tu potrzebny (biblioteki rzędu tysięcy
        // utworów i tak nie mają tylu jednocześnie oglądanych okładek Now Playing w jednej sesji).
        if (cache.size > 50) cache.clear()
        cache[trackId] = result
        return result
    }
}
