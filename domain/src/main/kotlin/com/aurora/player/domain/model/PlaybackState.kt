package com.aurora.player.domain.model

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    /**
     * Realny czas trwania z playera (Media3 `Player.getDuration()`), a nie z metadanych utworu —
     * dla utworów z chmury (Google Drive) długość nie jest znana z góry, więc slider Now Playing
     * musi czekać, aż player sam ją odkryje po rozpoczęciu buforowania.
     */
    val durationMs: Long = 0L,
)
