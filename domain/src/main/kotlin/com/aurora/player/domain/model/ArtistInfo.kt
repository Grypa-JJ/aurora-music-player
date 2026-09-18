package com.aurora.player.domain.model

/**
 * Informacje o wykonawcy z TheAudioDB (DESIGN.md Etap 43) — biografia, gatunek/styl/nastrój,
 * grafika. Wszystkie pola `null` = appka już próbowała i NIC nie znalazła (ten sam sygnał "zapisz
 * też negatyw" co [com.aurora.player.domain.repository.MetadataEnrichmentRepository]), nie
 * "jeszcze nie sprawdzono".
 */
data class ArtistInfo(
    val biography: String?,
    val genre: String?,
    val style: String?,
    val mood: String?,
    val bannerUrl: String?,
    val thumbUrl: String?,
)
