package com.aurora.player.domain.model

/** Pozycja w Internet Archive (koncert/nagranie/audycja) — jeden "item" może mieć wiele ścieżek audio. */
data class ArchiveItem(
    val identifier: String,
    val title: String,
    val creator: String,
    val year: Int?,
    val coverUrl: String?,
)

/** Jedna ścieżka audio wewnątrz [ArchiveItem], rozwiązana z `archive.org/metadata/{identifier}`. */
data class ArchiveTrack(
    val identifier: String,
    val fileName: String,
    val title: String,
    val audioUrl: String,
    val durationMs: Long,
)

/** Kuratorowane kolekcje pokazywane jako kafle w UI — patrz DESIGN.md ("Archiwum"). */
object ArchiveCollections {
    const val LIVE_MUSIC = "etree"
    const val NETLABELS = "netlabels"
    const val OLD_TIME_RADIO = "oldtimeradio"
}
