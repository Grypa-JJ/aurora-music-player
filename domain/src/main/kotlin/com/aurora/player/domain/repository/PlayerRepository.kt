package com.aurora.player.domain.repository

import com.aurora.player.domain.model.PlaybackState
import com.aurora.player.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

interface PlayerRepository {
    val playbackState: StateFlow<PlaybackState>

    /** Kolejka utworów do odtworzenia po kolei — patrz DESIGN.md sekcja 5.3 (Instant Mix). */
    fun playQueue(tracks: List<Track>, startIndex: Int = 0)
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun skipToNext()
    fun skipToPrevious()
}
