package com.aurora.player.domain.repository

import com.aurora.player.domain.model.ArchiveCategory
import com.aurora.player.domain.model.ArchiveDownload
import com.aurora.player.domain.model.ArchiveItem
import com.aurora.player.domain.model.ArchiveTrack
import com.aurora.player.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

/** Internet Archive (`archive.org`) — koncerty na żywo, netlabele, stare radio. Zero kluczy API. */
interface ArchiveRepository {
    suspend fun browseCategory(category: ArchiveCategory, limit: Int = 50): List<ArchiveItem>

    /**
     * Top pozycje wg liczby pobrań (`downloads+desc`), priorytetowo z regionu/języka urządzenia —
     * domyślny widok bez sygnału gustu (patrz implementacja dla dokładnego mapowania regionu).
     */
    suspend fun topPopular(limit: Int = 20): List<ArchiveItem>

    /**
     * Wyszukiwanie po `mediatype:audio`, opcjonalnie zawężone do [category]. Tokeny zaczynające się
     * od `#` (np. `#pl`, `#rap`) są traktowane jak tagi — patrz implementacja dla mapowania na pola
     * `subject:`/`language:` w metadanych Internet Archive.
     */
    suspend fun search(query: String, category: ArchiveCategory? = null, limit: Int = 50): List<ArchiveItem>

    suspend fun tracksForItem(identifier: String): List<ArchiveTrack>

    /**
     * "Dla Ciebie" — dopasowane do gustu z lokalnej biblioteki (wykonawca + gatunek, ulubione liczone
     * mocniej), patrz DESIGN.md Etap 37 (algorytm personalizacji). Puste listy = brak sygnału,
     * wywołujący powinien wtedy pokazać zwykły domyślny [browseCategory].
     */
    suspend fun personalizedForYou(
        favoriteArtists: List<String>,
        favoriteGenres: List<String>,
        limit: Int = 20,
    ): List<ArchiveItem>

    /**
     * Jak [personalizedForYou], ale zawężone do [category] — dla ekranu "Przeglądaj kategorię"
     * (DESIGN.md Etap 46), nie tylko karuzeli "Dla Ciebie" na Home. Dopasowanie po gatunku ma sens
     * tylko dla [com.aurora.player.domain.model.ArchiveCategory.MUSIC] (mapa gatunek→kolekcja jest
     * muzyczna) — dla pozostałych kategorii liczy się wyłącznie dopasowanie po wykonawcy. Puste listy
     * = brak sygnału, wywołujący powinien wtedy pokazać zwykły domyślny [browseCategory].
     */
    suspend fun personalizedForCategory(
        category: ArchiveCategory,
        favoriteArtists: List<String>,
        favoriteGenres: List<String>,
        limit: Int = 20,
    ): List<ArchiveItem>

    /**
     * Ścieżki pobrane NA STAŁE do biblioteki (patrz [addTrackToLibrary]) — grają offline, bez sieci,
     * dokładnie jak lokalne pliki. DESIGN.md Etap 40.
     */
    val library: StateFlow<List<Track>>

    /** `Track.id` ścieżek aktualnie pobieranych — do pokazania spinnera w UI. */
    val downloadingTrackIds: StateFlow<Set<Long>>

    /** Jak [downloadingTrackIds], ale z tytułem/wykonawcą/okładką — do ekranu "Pobrane". */
    val activeDownloads: StateFlow<List<ArchiveDownload>>

    val lastError: StateFlow<String?>
    fun clearLastError()

    /**
     * Pobiera plik audio z archive.org na dysk urządzenia i dodaje wpis do [library] — jednorazowy
     * download, nie streaming z cache'em. Zwraca `false` przy błędzie sieci/zapisu (patrz [lastError]).
     * Bezpieczne wywołać wielokrotnie dla tej samej ścieżki (no-op, gdy już pobrana/w trakcie).
     */
    suspend fun addTrackToLibrary(track: ArchiveTrack, item: ArchiveItem): Boolean

    /** Usuwa lokalny plik i wpis z [library]. */
    suspend fun removeTrackFromLibrary(trackId: Long)
}
