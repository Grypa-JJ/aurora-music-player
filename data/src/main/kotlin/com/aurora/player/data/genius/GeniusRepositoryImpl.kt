package com.aurora.player.data.genius

import com.aurora.player.data.database.dao.TrackAffinityDao
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.repository.GeniusRepository
import com.aurora.player.domain.repository.TrackRepository
import com.aurora.player.domain.usecase.genius.GeniusDiversifier
import com.aurora.player.domain.usecase.genius.GeniusScoring
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeniusRepositoryImpl @Inject constructor(
    private val trackRepository: TrackRepository,
    private val trackAffinityDao: TrackAffinityDao,
) : GeniusRepository {

    override suspend fun generateInstantMix(seedTrackId: Long, length: Int): List<Track> {
        val allTracks = trackRepository.getAllTracks()
        val seed = allTracks.find { it.id == seedTrackId } ?: return emptyList()
        val affinityByTrackId = trackAffinityDao.getAll().associateBy { it.trackId }
        val now = System.currentTimeMillis()

        val scoredDescending = allTracks
            .asSequence()
            .filter { it.id != seed.id }
            .map { candidate ->
                val affinity = affinityByTrackId[candidate.id]?.affinityScore ?: GeniusScoring.DEFAULT_AFFINITY
                candidate to GeniusScoring.score(seed, candidate, affinity, now)
            }
            .sortedByDescending { (_, score) -> score }
            .map { (track, _) -> track }
            .toList()

        return GeniusDiversifier.diversify(scoredDescending, length)
    }
}
