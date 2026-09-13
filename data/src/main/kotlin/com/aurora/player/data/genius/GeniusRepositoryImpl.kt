package com.aurora.player.data.genius

import com.aurora.player.data.database.dao.TrackAffinityDao
import com.aurora.player.data.database.dao.TrackCooccurrenceDao
import com.aurora.player.data.database.dao.PlayEventDao
import com.aurora.player.domain.model.GeniusMix
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.repository.GeniusRepository
import com.aurora.player.domain.repository.TrackRepository
import com.aurora.player.domain.usecase.genius.GeniusClustering
import com.aurora.player.domain.usecase.genius.GeniusDiversifier
import com.aurora.player.domain.usecase.genius.GeniusScoring
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.ln

@Singleton
class GeniusRepositoryImpl @Inject constructor(
    private val trackRepository: TrackRepository,
    private val trackAffinityDao: TrackAffinityDao,
    private val trackCooccurrenceDao: TrackCooccurrenceDao,
    private val playEventDao: PlayEventDao,
) : GeniusRepository {

    override suspend fun generateInstantMix(seedTrackId: Long, length: Int): List<Track> {
        val allTracks = trackRepository.getAllTracks()
        val seed = allTracks.find { it.id == seedTrackId } ?: return emptyList()
        val affinityByTrackId = trackAffinityDao.getAll().associate { it.trackId to it.affinityScore }
        val now = System.currentTimeMillis()

        val cooccurrenceRows = trackCooccurrenceDao.forTrack(seed.id)
        val cooccurrenceCountByTrackId = cooccurrenceRows.associate { row ->
            val otherTrackId = if (row.trackIdA == seed.id) row.trackIdB else row.trackIdA
            otherTrackId to row.count
        }
        val maxCooccurrence = cooccurrenceCountByTrackId.values.maxOrNull() ?: 0

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val contextCountByTrackId = playEventDao.getAll()
            .filter { isWithinHourWindow(it.hourOfDay, currentHour) }
            .groupingBy { it.trackId }
            .eachCount()
        val maxContextCount = contextCountByTrackId.values.maxOrNull() ?: 0

        val scoredDescending = allTracks
            .asSequence()
            .filter { it.id != seed.id }
            .map { candidate ->
                val affinity = affinityByTrackId[candidate.id] ?: GeniusScoring.DEFAULT_AFFINITY
                val cooccurrence = normalizedLog(cooccurrenceCountByTrackId[candidate.id] ?: 0, maxCooccurrence)
                val context = normalizedLog(contextCountByTrackId[candidate.id] ?: 0, maxContextCount)
                candidate to GeniusScoring.score(seed, candidate, affinity, cooccurrence, context, now)
            }
            .sortedByDescending { (_, score) -> score }
            .map { (track, _) -> track }
            .toList()

        return GeniusDiversifier.diversify(scoredDescending, length)
    }

    override suspend fun generateGeniusMixes(maxMixes: Int, tracksPerMix: Int): List<GeniusMix> {
        val allTracks = trackRepository.getAllTracks()
        if (allTracks.size < maxMixes) return emptyList()

        val affinityByTrackId = trackAffinityDao.getAll().associate { it.trackId to it.affinityScore }
        val clusters = GeniusClustering.cluster(allTracks, affinityByTrackId, k = maxMixes)

        return clusters.mapIndexed { index, cluster ->
            val sortedByAffinity = cluster.sortedByDescending {
                affinityByTrackId[it.id] ?: GeniusScoring.DEFAULT_AFFINITY
            }
            GeniusMix(
                name = GeniusClustering.nameFor(cluster, index),
                tracks = sortedByAffinity.take(tracksPerMix),
            )
        }
    }

    private fun normalizedLog(count: Int, maxCount: Int): Float {
        if (maxCount <= 0 || count <= 0) return 0f
        return (ln(1f + count) / ln(1f + maxCount)).coerceIn(0f, 1f)
    }

    /** Okno ±[windowHours] wokół bieżącej godziny, z zawinięciem przez północ. */
    private fun isWithinHourWindow(eventHour: Int, currentHour: Int, windowHours: Int = 2): Boolean {
        val diff = abs(eventHour - currentHour)
        return minOf(diff, 24 - diff) <= windowHours
    }
}
