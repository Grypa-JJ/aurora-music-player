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

    /**
     * Dwa utwory zagrane bezpośrednio po sobie — lokalny odpowiednik collaborative filtering.
     * Kierunkowe (from -> to, patrz DESIGN.md Etap 14) i ważone tym, jak dużą część [fromTrackId]
     * faktycznie odsłuchano przed przejściem — dosłuchanie do końca i przejście dalej to dużo
     * mocniejszy sygnał "ten utwór dobrze prowadzi do następnego" niż gwałtowny skip.
     *
     * @param fromPlayedMs ile odsłuchano [fromTrackId] przed przejściem na [toTrackId]
     * @param fromDurationMs pełna długość [fromTrackId]
     */
    suspend fun recordTransition(fromTrackId: Long, toTrackId: Long, fromPlayedMs: Long, fromDurationMs: Long)

    /** Utwór z ostatniego zakończonego odtworzenia — zasila kafel "Kontynuuj" na Android Auto. */
    suspend fun getLastPlayedTrackId(): Long?
}
