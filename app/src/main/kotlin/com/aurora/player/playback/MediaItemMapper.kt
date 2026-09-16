package com.aurora.player.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.aurora.player.domain.model.Track

/**
 * [Track] → [MediaItem] z pełnymi metadanymi (nie tylko `MediaItem.fromUri`) — Now Playing na
 * Android Auto/powiadomieniu systemowym czyta tytuł/wykonawcę/okładkę WYŁĄCZNIE z
 * [MediaMetadata], nie z URI. `mediaId = track.id` dodatkowo pozwala serwisowi rozpoznać, który
 * utwór aktualnie gra (np. do stanu przycisku "Ulubione" w custom layout Android Auto), bez
 * dopasowywania po URI jak wcześniej w [PlayerController].
 */
fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArtUri?.let(Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()
