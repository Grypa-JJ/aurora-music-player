package com.aurora.player.domain.usecase

import com.aurora.player.domain.model.Track
import com.aurora.player.domain.repository.TrackRepository
import javax.inject.Inject

class GetTracksUseCase @Inject constructor(
    private val trackRepository: TrackRepository,
) {
    suspend operator fun invoke(): List<Track> = trackRepository.getAllTracks()
}
