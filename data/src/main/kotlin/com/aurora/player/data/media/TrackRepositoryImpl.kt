package com.aurora.player.data.media

import com.aurora.player.domain.model.Track
import com.aurora.player.domain.repository.TrackRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackRepositoryImpl @Inject constructor(
    private val mediaStoreScanner: MediaStoreScanner,
) : TrackRepository {
    override suspend fun getAllTracks(): List<Track> = mediaStoreScanner.scanTracks()
}
