package com.aurora.player.data.media

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.aurora.player.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Skanuje lokalną bibliotekę muzyczną przez MediaStore — patrz DESIGN.md sekcja 1.2/6
 * (`data/media/MediaStoreScanner.kt`). Nie wymaga sieci ani chmury.
 */
@Singleton
class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun scanTracks(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val genreCol = cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
            val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val trackUri = ContentUris.withAppendedId(collection, id)
                val albumId = cursor.getLong(albumIdCol)
                val albumArtUri = ContentUris.withAppendedId(
                    android.net.Uri.parse("content://media/external/audio/albumart"),
                    albumId,
                )

                tracks += Track(
                    id = id,
                    uri = trackUri.toString(),
                    title = cursor.getString(titleCol) ?: "Nieznany utwór",
                    artist = cursor.getString(artistCol) ?: "Nieznany wykonawca",
                    album = cursor.getString(albumCol) ?: "",
                    genre = if (genreCol >= 0) cursor.getString(genreCol) else null,
                    year = if (yearCol >= 0) cursor.getInt(yearCol).takeIf { it > 0 } else null,
                    durationMs = cursor.getLong(durationCol),
                    dateAddedMs = cursor.getLong(dateAddedCol) * 1000L,
                    albumArtUri = albumArtUri.toString(),
                )
            }
        }

        tracks
    }
}
