package com.aurora.player.domain.model

/** Gotowa playlista z klastrowania — "Genius Mixes" bez wskazywania utworu-ziarna, DESIGN.md 5.5. */
data class GeniusMix(
    val name: String,
    val tracks: List<Track>,
)
