package com.aurora.player.domain.model

/**
 * Utwór z katalogu "Muzyka niezależna" (Jamendo w kodzie — user w UI ma widzieć tylko tę
 * polską etykietę, nigdy nazwę dostawcy) — muzyka na licencji Creative Commons od niezależnych
 * artystów. [licenseCcUrl] pokazywany w UI jako wymóg przyzwoitości/atrybucji licencji.
 */
data class IndependentTrack(
    val id: String,
    val name: String,
    val artistName: String,
    val albumName: String?,
    val imageUrl: String?,
    val audioUrl: String,
    val durationSec: Int,
    val licenseCcUrl: String?,
)
