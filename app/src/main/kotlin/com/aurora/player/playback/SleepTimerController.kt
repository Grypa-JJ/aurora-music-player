package com.aurora.player.playback

import com.aurora.player.di.ApplicationScope
import com.aurora.player.domain.repository.PlayerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Timer snu — DESIGN.md Etap 22 (obecny w wizji od sekcji 3.2 "..." menu, nigdy nie zbudowany).
 * Celowo osobna klasa, nie metoda na [PlayerRepository] — to inny rodzaj stanu (odliczanie w
 * czasie), a [PlayerController] i tak już robi dużo. Jawne [PlayerRepository.pause] (nie
 * togglePlayPause) — odpalenie timera nigdy nie powinno przypadkiem WZNOWIĆ odtwarzania, gdyby
 * użytkownik sam wcześniej zapauzował.
 */
@Singleton
class SleepTimerController @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val playerRepository: PlayerRepository,
) {
    private val _remainingMs = MutableStateFlow<Long?>(null)

    /** null = brak aktywnego timera. */
    val remainingMs: StateFlow<Long?> = _remainingMs

    private var job: Job? = null

    fun start(durationMs: Long) {
        job?.cancel()
        job = scope.launch {
            var remaining = durationMs
            _remainingMs.value = remaining
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1_000
                _remainingMs.value = remaining.coerceAtLeast(0)
            }
            playerRepository.pause()
            _remainingMs.value = null
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _remainingMs.value = null
    }
}
