package com.aurora.player.metadata

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pilnuje ~1 zapytanie/sekundę do MusicBrainz (limit dla anonimowych klientów) — DESIGN.md
 * Etap 25. Singleton (nie stan lokalny w kliencie), żeby limit obowiązywał globalnie nawet gdyby
 * w przyszłości powstał drugi wywołujący.
 */
@Singleton
class MusicBrainzRateLimiter @Inject constructor() {
    private val mutex = Mutex()
    private var lastRequestAtMs = 0L

    suspend fun awaitTurn() = mutex.withLock {
        val elapsed = System.currentTimeMillis() - lastRequestAtMs
        if (elapsed < MIN_INTERVAL_MS) delay(MIN_INTERVAL_MS - elapsed)
        lastRequestAtMs = System.currentTimeMillis()
    }

    private companion object {
        const val MIN_INTERVAL_MS = 1100L
    }
}
