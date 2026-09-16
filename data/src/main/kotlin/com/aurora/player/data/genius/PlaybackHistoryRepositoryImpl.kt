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

/** Waga przejścia po natychmiastowym skipie (completionRatio=0) — patrz [TrackCooccurrenceEntity]. */
private const val MIN_TRANSITION_WEIGHT = 0.2f

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

    override suspend fun getLastPlayedTrackId(): Long? = playEventDao.getLastPlayedTrackId()

    override suspend fun recordTransition(
        fromTrackId: Long,
        toTrackId: Long,
        fromPlayedMs: Long,
        fromDurationMs: Long,
    ) {
        if (fromTrackId == toTrackId) return
        val completionRatio = if (fromDurationMs > 0L) {
            (fromPlayedMs.toFloat() / fromDurationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        // Nawet natychmiastowy skip (completionRatio=0) niesie jakiś sygnał (użytkownik i tak
        // wybrał wtedy TEN konkretny kolejny utwór) — pełne dosłuchanie waży niemal 2x mocniej.
        val weightIncrement = MIN_TRANSITION_WEIGHT + (1f - MIN_TRANSITION_WEIGHT) * completionRatio

        val existing = trackCooccurrenceDao.get(fromTrackId, toTrackId)
        trackCooccurrenceDao.upsert(
            TrackCooccurrenceEntity(
                fromTrackId = fromTrackId,
                toTrackId = toTrackId,
                weight = (existing?.weight ?: 0f) + weightIncrement,
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
