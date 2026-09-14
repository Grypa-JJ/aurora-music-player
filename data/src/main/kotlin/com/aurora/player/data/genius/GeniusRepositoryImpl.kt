package com.aurora.player.data.genius

import com.aurora.player.data.database.dao.TrackAffinityDao
import com.aurora.player.data.database.dao.TrackCooccurrenceDao
import com.aurora.player.data.database.dao.PlayEventDao
import com.aurora.player.data.database.entity.TrackCooccurrenceEntity
import com.aurora.player.domain.model.GeniusMix
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.repository.GeniusRepository
import com.aurora.player.domain.repository.TrackRepository
import com.aurora.player.domain.usecase.genius.GeniusClustering
import com.aurora.player.domain.usecase.genius.GeniusDiversifier
import com.aurora.player.domain.usecase.genius.GeniusScoring
import com.aurora.player.domain.usecase.genius.GeniusTimeContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.exp
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

        // Kierunkowe (patrz DESIGN.md Etap 14): tylko "co grało PO seedzie", nie w obie strony —
        // trafniejsze dla "co puścić dalej" niż wcześniejsza symetryczna para. Waga każdego
        // przejścia wygaszana wykładniczo wg [lastSeenAt] (skala ~90 dni), żeby dawno nieaktualne
        // sąsiedztwa traciły znaczenie bez osobnego joba czyszczącego.
        val cooccurrenceRows = trackCooccurrenceDao.getFrom(seed.id)
        val cooccurrenceWeightByTrackId = cooccurrenceRows.associate { row ->
            row.toTrackId to decayedCooccurrenceWeight(row.weight, row.lastSeenAt, now)
        }
        val maxCooccurrence = cooccurrenceWeightByTrackId.values.maxOrNull() ?: 0f

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
                val cooccurrence = normalizedLog(cooccurrenceWeightByTrackId[candidate.id] ?: 0f, maxCooccurrence)
                val context = normalizedLog(
                    (contextCountByTrackId[candidate.id] ?: 0).toFloat(),
                    maxContextCount.toFloat(),
                )
                candidate to GeniusScoring.score(seed, candidate, affinity, cooccurrence, context, now)
            }
            .sortedByDescending { (_, score) -> score }
            .map { (track, _) -> track }
            .toList()

        return GeniusDiversifier.diversify(scoredDescending, length)
    }

    /** Wygaszanie starych przejść w czasie — patrz [TrackCooccurrenceEntity]. */
    private fun decayedCooccurrenceWeight(weight: Float, lastSeenAt: Long, nowMs: Long): Float {
        val ageDays = (nowMs - lastSeenAt) / 86_400_000f
        return weight * exp(-ageDays / COOCCURRENCE_DECAY_DAYS)
    }

    override suspend fun generateGeniusMixes(maxMixes: Int, tracksPerMix: Int): List<GeniusMix> {
        val allTracks = trackRepository.getAllTracks()
        if (allTracks.size < maxMixes) return emptyList()

        val affinityByTrackId = trackAffinityDao.getAll().associate { it.trackId to it.affinityScore }
        val clusters = GeniusClustering.cluster(allTracks, affinityByTrackId, k = maxMixes)

        val clusterMixes = clusters.mapIndexed { index, cluster ->
            val sortedByAffinity = cluster.sortedByDescending {
                affinityByTrackId[it.id] ?: GeniusScoring.DEFAULT_AFFINITY
            }
            GeniusMix(
                name = GeniusClustering.nameFor(cluster, index),
                tracks = sortedByAffinity.take(tracksPerMix),
            )
        }

        return clusterMixes + buildContextMixes(allTracks, affinityByTrackId, tracksPerMix)
    }

    /**
     * Miksy nazwane wg pory dnia (patrz DESIGN.md Etap 15, [GeniusTimeContext]) — np. "Nocna
     * jazda" dla utworów historycznie granych najczęściej wieczorem/w nocy. Pomijamy okno, jeśli
     * nie ma dla niego jeszcze wystarczającej historii (świeża biblioteka bez odsłuchań) —
     * lepiej brak miksu niż miks z 1-2 przypadkowymi utworami.
     */
    private suspend fun buildContextMixes(
        allTracks: List<Track>,
        affinityByTrackId: Map<Long, Float>,
        tracksPerMix: Int,
    ): List<GeniusMix> {
        val tracksById = allTracks.associateBy { it.id }
        val playCountByContextAndTrack = playEventDao.getAll()
            .groupingBy { GeniusTimeContext.forHour(it.hourOfDay) to it.trackId }
            .eachCount()

        return GeniusTimeContext.entries.mapNotNull { context ->
            val ranked = playCountByContextAndTrack.entries
                .filter { (key, _) -> key.first == context }
                .mapNotNull { (key, playCount) -> tracksById[key.second]?.let { it to playCount } }
                .sortedWith(
                    compareByDescending<Pair<Track, Int>> { it.second }
                        .thenByDescending { affinityByTrackId[it.first.id] ?: GeniusScoring.DEFAULT_AFFINITY },
                )
                .map { it.first }

            if (ranked.size < MIN_TRACKS_FOR_CONTEXT_MIX) return@mapNotNull null
            GeniusMix(name = context.displayName, tracks = ranked.take(tracksPerMix))
        }
    }

    private fun normalizedLog(value: Float, maxValue: Float): Float {
        if (maxValue <= 0f || value <= 0f) return 0f
        return (ln(1f + value) / ln(1f + maxValue)).coerceIn(0f, 1f)
    }

    /** Okno ±[windowHours] wokół bieżącej godziny, z zawinięciem przez północ. */
    private fun isWithinHourWindow(eventHour: Int, currentHour: Int, windowHours: Int = 2): Boolean {
        val diff = abs(eventHour - currentHour)
        return minOf(diff, 24 - diff) <= windowHours
    }

    private companion object {
        const val COOCCURRENCE_DECAY_DAYS = 90f
        const val MIN_TRACKS_FOR_CONTEXT_MIX = 5
    }
}
