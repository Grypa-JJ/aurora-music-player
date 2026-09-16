package com.aurora.player.domain.repository

import com.aurora.player.domain.model.LyricsResult
import com.aurora.player.domain.model.Track

/**
 * Tekst utworu — offline-first: implementacja czyta lokalny cache PRZED siecią i zapisuje wynik
 * (także "nie znaleziono"), żeby appka nigdy nie odpytywała tego samego utworu dwa razy —
 * DESIGN.md Etap 24, decyzja usera: "ma pobierać za 1 razem, zapisywać i używać offline".
 */
interface LyricsRepository {
    suspend fun getLyrics(track: Track): LyricsResult
}
