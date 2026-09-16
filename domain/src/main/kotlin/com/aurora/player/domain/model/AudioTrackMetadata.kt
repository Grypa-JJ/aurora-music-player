package com.aurora.player.domain.model

/**
 * Realne parametry audio odczytane wprost z kontenera pliku (MediaStore ich nie ma) — DESIGN.md
 * Etap 20h/28, prerekwizyt pod przyszły Audio Lab. Każde pole osobno nullable — appka nie
 * zgaduje/nie estymuje niczego, czego ekstraktor faktycznie nie wystawił (ta sama zasada
 * uczciwości co `AudioCapabilities`/`BitPerfectStatus` z Etapu 21).
 */
data class AudioTrackMetadata(
    val codec: String?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val bitDepth: Int?,
)
