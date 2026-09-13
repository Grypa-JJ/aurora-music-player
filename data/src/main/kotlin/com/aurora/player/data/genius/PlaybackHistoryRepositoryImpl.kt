package com.aurora.player.data.genius

import com.aurora.player.data.database.dao.PlayEventDao
import com.aurora.player.data.database.dao.SkipEventDao
import com.aurora.player.data.database.dao.TrackAffinityDao
import com.aurora.player.data.database.dao.TrackCooccurrenceDao
import com.aurora.player.data.database.entity.PlayEventEntity
import com.aurora.player.data.database.entity.SkipEventEntity
import com.aurora.player.data.database.entity.TrackAffinityEntity
import com.aurora.player.data.database.entity.TrackCooccurrenceEntity
import com.aurora.player.domain.repository.PlaybackHistoryRepository
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/** Próg "odsłuchane" wg DESIGN.md sekcja 5.1 (>~80% długości = completed, inaczej skip). */
private const val COMPLETION_THRESHOLD = 0.8f

/** Odtworzenia krótsze niż to traktujemy jako szum (przypadkowe stuknięcie), nie jako skip. */
private const val MIN_MEANINGFUL_PLAYED_MS = 3_000L

@Singleton
class PlaybackHistoryRepositoryImpl @Inject constructor(
    private val playEventDao: PlayEventDao,
    private val skipEventDao: SkipEventDao,
    private val trackAffinityDao: TrackAffinityDao,
    private val trackCooccurrenceDao: TrackCooccurrenceDao,
) : PlaybackHistoryRepository {

    override suspend fun recordPlaybackEnded(trackId: Long, playedMs: Long, durationMs: Long) {
        if (durationMs <= 0L || playedMs < MIN_MEANINGFUL_PLAYED_MS) return

        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
        val completionRatio = (playedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        val completed = completionRatio >= COMPLETION_THRESHOLD

        if (completed) {
            playEventDao.insert(
                PlayEventEntity(
                    trackId = trackId,
                    timestampStart = now - playedMs,
                    timestampEnd = now,
                    playedMs = playedMs,
                    completed = true,
                    dayOfWeek = dayOfWeek,
                    hourOfDay = hourOfDay,
                ),
            )
        } else {
            skipEventDao.insert(
                SkipEventEntity(
                    trackId = trackId,
                    timestamp = now,
                    playedMs = playedMs,
                    dayOfWeek = dayOfWeek,
                    hourOfDay = hourOfDay,
                ),
            )
        }

        updateAffinity(trackId, completed, completionRatio, now)
    }

    override suspend fun recordTransition(fromTrackId: Long, toTrackId: Long) {
        if (fromTrackId == toTrackId) return
        val trackIdA = minOf(fromTrackId, toTrackId)
        val trackIdB = maxOf(fromTrackId, toTrackId)
        val existing = trackCooccurrenceDao.get(trackIdA, trackIdB)
        trackCooccurrenceDao.upsert(
            TrackCooccurrenceEntity(
                trackIdA = trackIdA,
                trackIdB = trackIdB,
                count = (existing?.count ?: 0) + 1,
                lastSeenAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun updateAffinity(trackId: Long, completed: Boolean, completionRatio: Float, now: Long) {
        val existing = trackAffinityDao.get(trackId)
        val playCount = (existing?.playCount ?: 0) + if (completed) 1 else 0
        val skipCount = (existing?.skipCount ?: 0) + if (!completed) 1 else 0
        val avgCompletionRatio = if (existing == null) {
            completionRatio
        } else {
            (existing.avgCompletionRatio + completionRatio) / 2f
        }
        // Wygładzone (Bayesian-like) — patrz DESIGN.md sekcja 5.2, ta sama formuła co w GeniusScoring.
        val affinityScore = (
            0.6f * (playCount / (playCount + 5f)) +
                0.4f * (1f - skipCount / (playCount + skipCount + 1f))
            ).coerceIn(0f, 1f)

        trackAffinityDao.upsert(
            TrackAffinityEntity(
                trackId = trackId,
                playCount = playCount,
                skipCount = skipCount,
                avgCompletionRatio = avgCompletionRatio,
                lastPlayedAt = now,
                affinityScore = affinityScore,
            ),
        )
    }
}
