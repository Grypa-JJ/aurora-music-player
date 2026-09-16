package com.aurora.player.domain.model

/** Stacja radia internetowego (Radio-Browser) — DESIGN.md Etap 31. */
data class RadioStation(
    val stationUuid: String,
    val name: String,
    val streamUrl: String,
    val faviconUrl: String?,
    val countryCode: String,
    val tags: String,
    val votes: Int,
)
