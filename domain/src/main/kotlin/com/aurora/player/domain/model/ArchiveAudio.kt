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

/** Kategorie treści audio w "Archiwum" — filtr przeglądania i wyszukiwania, patrz DESIGN.md. */
enum class ArchiveCategory {
    MUSIC,
    PODCASTS,
    AUDIOBOOKS,
    RADIO,
}

/**
 * Pozycja w trakcie pobierania na stałe do biblioteki (patrz `ArchiveRepository.activeDownloads`)
 * — do ekranu "Pobrane", żeby user widział co się aktualnie ściąga, nie tylko co już ma.
 */
data class ArchiveDownload(
    val trackId: Long,
    val title: String,
    val subtitle: String,
    val coverUrl: String?,
)
