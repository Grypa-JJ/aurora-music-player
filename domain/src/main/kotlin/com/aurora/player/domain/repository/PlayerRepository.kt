package com.aurora.player.domain.repository

import com.aurora.player.domain.model.PlaybackState
import com.aurora.player.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

interface PlayerRepository {
    val playbackState: StateFlow<PlaybackState>

    fun play(track: Track)
    fun togglePlayPause()
}
