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

        // Zgłoszenie: `NetworkError` (wyczerpane próby, patrz `LrcLibClient`) NIE jest
        // cache'owany — inaczej przejściowy błąd sieci trwale "zamrażał" utwór jako "bez tekstu"
        // w Room, nawet po restarcie appki. Tylko realne `NotFound` (404 z LRCLIB) jest
        // ostateczne i bezpieczne do zapisania na stałe.
        return when (val result = client.fetch(track)) {
            is LrcLibFetchResult.NetworkError -> LyricsResult.NotFound
            is LrcLibFetchResult.NotFound -> {
                dao.insert(LyricsCacheEntity(track.id, null, null, System.currentTimeMillis()))
                LyricsResult.NotFound
            }
            is LrcLibFetchResult.Found -> {
                val entity = LyricsCacheEntity(
                    trackId = track.id,
                    syncedLrc = result.response.syncedLyrics,
                    plainText = result.response.plainLyrics,
                    fetchedAtMs = System.currentTimeMillis(),
                )
                dao.insert(entity)
                entity.toResult()
            }
        }
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
