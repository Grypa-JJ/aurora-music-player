package com.aurora.player.domain.repository

import com.aurora.player.domain.model.GeniusMix
import com.aurora.player.domain.model.Track

/**
 * "Genius" — lokalne rekomendacje muzyczne bez sieci/chmury, patrz DESIGN.md sekcja 5.
 */
interface GeniusRepository {
    /** Buduje "Instant Mix" z utworu-ziarna (bez samego seeda w wyniku) — DESIGN.md sekcja 5.3. */
    suspend fun generateInstantMix(seedTrackId: Long, length: Int = 30): List<Track>

    /** Gotowe playlisty z klastrowania, bez wskazywania utworu-ziarna — DESIGN.md sekcja 5.5. */
    suspend fun generateGeniusMixes(maxMixes: Int = 6, tracksPerMix: Int = 25): List<GeniusMix>
}
