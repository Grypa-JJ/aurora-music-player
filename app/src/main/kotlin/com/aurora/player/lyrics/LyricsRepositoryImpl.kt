package com.aurora.player.lyrics

import com.aurora.player.data.database.dao.LyricsCacheDao
import com.aurora.player.data.database.entity.LyricsCacheEntity
import com.aurora.player.domain.model.LyricsResult
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.repository.LyricsRepository
import com.aurora.player.domain.util.LrcParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first: [LyricsCacheDao] (Room) czytany PRZED siecią — jeśli utwór był już kiedyś
 * odpytany (także z wynikiem "nie znaleziono"), appka nigdy nie strzela do LRCLIB drugi raz dla
 * tego samego `trackId` — DESIGN.md Etap 24, decyzja usera: "ma odtwarzać i pobierać za 1 razem,
 * ale ma też działać offline, więc musimy je zapisywać i używać offline".
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val dao: LyricsCacheDao,
    private val client: LrcLibClient,
) : LyricsRepository {

    override suspend fun getLyrics(track: Track): LyricsResult {
        dao.get(track.id)?.let { cached -> return cached.toResult() }

        val response = client.fetch(track)
        val entity = LyricsCacheEntity(
            trackId = track.id,
            syncedLrc = response?.syncedLyrics,
            plainText = response?.plainLyrics,
            fetchedAtMs = System.currentTimeMillis(),
        )
        dao.insert(entity)
        return entity.toResult()
    }

    private fun LyricsCacheEntity.toResult(): LyricsResult {
        val synced = syncedLrc
        val plain = plainText
        return when {
            synced != null -> LyricsResult.Synced(LrcParser.parse(synced))
            plain != null -> LyricsResult.Plain(plain)
            else -> LyricsResult.NotFound
        }
    }
}
