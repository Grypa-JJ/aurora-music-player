package com.aurora.player.domain.model

/**
 * Pozycja w Internet Archive (koncert/nagranie/audycja) — jeden "item" może mieć wiele ścieżek audio.
 * [filesCount] to `files_count` z metadanych IA (liczba WSZYSTKICH plików w itemie — audio + pochodne
 * miniatury/spektrogramy, nie sam licznik utworów) — używane jako przybliżenie "pojedyncza ścieżka vs
 * pełny album" przy sortowaniu wyników (patrz `ArchiveRepositoryImpl.rankBySize`). [collections] —
 * surowe id kolekcji IA (`collection` z metadanych, np. "etree"/"podcasts") — do wywnioskowania
 * [ArchiveCategory] przy dodawaniu do biblioteki (patrz `ArchiveRepositoryImpl.inferCategory`),
 * niezależnie od tego, w jakim kontekście UI user trafił na ten item (chip kategorii, wyszukiwanie,
 * "Dla Ciebie" na Home) — dane z samego itemu, nie z nawigacji.
 */
data class ArchiveItem(
    val identifier: String,
    val title: String,
    val creator: String,
    val year: Int?,
    val coverUrl: String?,
    val filesCount: Int = 0,
    val collections: List<String> = emptyList(),
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

/**
 * Jeden pobrany/streamowany [ArchiveItem] z biblioteki, pogrupowany z powrotem z płaskich wpisów
 * (patrz `ArchiveRepository.libraryByCategory`) — do sekcji "Pobrane z Archiwum" w AudiobooksScreen/
 * PodcastsScreen (Etap 53, zgłoszenie: "pobieranie ma dodawać audiobooki/podcasty z Archiwum obok
 * tych z LibriVox/realnych subskrypcji"). Świadomie NIE wchodzi w domenowy model `Audiobook`/
 * `Podcast` — te zakładają na sztywno sieciowe dociąganie rozdziałów/odcinków (LibriVox/RSS), a tu
 * ścieżki są już konkretnymi, gotowymi do grania [Track] (lokalny plik lub stream z archive.org).
 */
data class ArchiveLibraryGroup(
    val identifier: String,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val tracks: List<Track>,
)
