package com.aurora.player.domain.repository

import com.aurora.player.domain.model.Track

interface TrackRepository {
    suspend fun getAllTracks(): List<Track>
}
