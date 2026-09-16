package com.aurora.player.domain.repository

import com.aurora.player.domain.model.PlaybackState
import com.aurora.player.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

interface PlayerRepository {
    val playbackState: StateFlow<PlaybackState>

    /** Kolejka utworów do odtworzenia po kolei — patrz DESIGN.md sekcja 5.3 (Instant Mix). */
    fun playQueue(tracks: List<Track>, startIndex: Int = 0)
    fun togglePlayPause()

    /** Jawna pauza (nie toggle) — potrzebna dla timera snu, żeby nigdy nie WZNOWIŁ odtwarzania. */
    fun pause()
    fun seekTo(positionMs: Long)
    fun skipToNext()
    fun skipToPrevious()

    // --- Kolejka (ekran "Kolejka") — DESIGN.md Etap 22 ---

    /** Dogrywa utwór na koniec bieżącej kolejki; jeśli nic nie gra, odtwarza go od razu. */
    fun addToQueue(track: Track)
    fun removeFromQueue(index: Int)
    fun moveQueueItem(fromIndex: Int, toIndex: Int)

    /** Przeskakuje do utworu na danym indeksie w bieżącej kolejce, bez jej zmiany. */
    fun playAt(index: Int)

    // --- Powtarzanie/losowa kolejność — DESIGN.md Etap 26 ---

    /** OFF → ALL → ONE → OFF. */
    fun cycleRepeatMode()
    fun toggleShuffle()

    /** Prędkość odtwarzania (1.0 = normalna) — DESIGN.md Etap 25, głównie dla podcastów. */
    fun setPlaybackSpeed(speed: Float)
}
