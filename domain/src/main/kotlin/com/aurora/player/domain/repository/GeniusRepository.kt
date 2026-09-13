package com.aurora.player.domain.repository

import com.aurora.player.domain.model.Track

/**
 * "Genius" — lokalne rekomendacje muzyczne bez sieci/chmury, patrz DESIGN.md sekcja 5.
 */
interface GeniusRepository {
    /** Buduje "Instant Mix" z utworu-ziarna (bez samego seeda w wyniku) — DESIGN.md sekcja 5.3. */
    suspend fun generateInstantMix(seedTrackId: Long, length: Int = 30): List<Track>
}
