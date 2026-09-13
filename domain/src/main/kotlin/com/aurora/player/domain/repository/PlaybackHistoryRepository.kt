package com.aurora.player.domain.repository

/**
 * Zamyka pętlę uczenia Genius — patrz DESIGN.md sekcja 5.4. Każde zakończenie odtwarzania
 * (naturalny koniec, przejście do następnego, ręczna zmiana) jest zgłaszane tutaj i aktualizuje
 * affinity utworu (play/skip ratio) odczytywane potem przez [GeniusRepository].
 */
interface PlaybackHistoryRepository {
    /**
     * @param playedMs ile faktycznie odsłuchano tego odtworzenia
     * @param durationMs pełna długość utworu — używana do wyliczenia completionRatio (próg 80%)
     */
    suspend fun recordPlaybackEnded(trackId: Long, playedMs: Long, durationMs: Long)
}
