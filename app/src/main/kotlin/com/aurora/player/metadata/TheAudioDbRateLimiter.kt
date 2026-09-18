package com.aurora.player.metadata

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pilnuje limitu TheAudioDB (30 zapytań/min na darmowy klucz, DESIGN.md Etap 43) — ten sam
 * wzorzec co [MusicBrainzRateLimiter]. Klucz użyty w [TheAudioDbClient] to WSPÓLNY, publiczny
 * klucz testowy TheAudioDB (celowo, w przeciwieństwie do Podcast Index — patrz komentarz w
 * [TheAudioDbClient] o różnicy w modelu obu API), więc odstęp ma margines poniżej 2000ms
 * teoretycznego limitu — appka to tylko JEDEN z wielu klientów dzielących ten sam klucz.
 */
@Singleton
class TheAudioDbRateLimiter @Inject constructor() {
    private val mutex = Mutex()
    private var lastRequestAtMs = 0L

    suspend fun awaitTurn() = mutex.withLock {
        val elapsed = System.currentTimeMillis() - lastRequestAtMs
        if (elapsed < MIN_INTERVAL_MS) delay(MIN_INTERVAL_MS - elapsed)
        lastRequestAtMs = System.currentTimeMillis()
    }

    private companion object {
        const val MIN_INTERVAL_MS = 2500L
    }
}
