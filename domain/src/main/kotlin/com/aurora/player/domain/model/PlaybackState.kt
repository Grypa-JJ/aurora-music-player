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
    /** Aktualna kolejka odtwarzania, w kolejności — patrz DESIGN.md Etap 22 (ekran Kolejka). */
    val queue: List<Track> = emptyList(),
    /** Tryb powtarzania — DESIGN.md Etap 26. Odzwierciedla `Player.repeatMode`, także gdy zmieniony
     *  z zewnątrz (np. kontrolki na powiadomieniu systemowym), nie tylko z naszego UI. */
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** Losowa kolejność odtwarzania — DESIGN.md Etap 26. Tak samo odzwierciedla `Player.shuffleModeEnabled`. */
    val isShuffleEnabled: Boolean = false,
    /** Prędkość odtwarzania (1.0 = normalna) — DESIGN.md Etap 25, głównie dla podcastów. */
    val playbackSpeed: Float = 1f,
)
