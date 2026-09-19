package com.aurora.player.domain.repository

import com.aurora.player.domain.model.ArchiveCategory
import com.aurora.player.domain.model.ArchiveDownload
import com.aurora.player.domain.model.ArchiveItem
import com.aurora.player.domain.model.ArchiveLibraryGroup
import com.aurora.player.domain.model.ArchiveTrack
import com.aurora.player.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

/** Internet Archive (`archive.org`) — koncerty na żywo, netlabele, stare radio. Zero kluczy API. */
interface ArchiveRepository {
    /** [offset] = strona wyników ("nieskończone przewijanie" w UI) — patrz `LibraryViewModel.loadMoreArchiveItems`. */
    suspend fun browseCategory(category: ArchiveCategory, limit: Int = 50, offset: Int = 0): List<ArchiveItem>

    /**
     * Top pozycje wg liczby pobrań (`downloads+desc`), priorytetowo z regionu/języka urządzenia —
     * domyślny widok bez sygnału gustu (patrz implementacja dla dokładnego mapowania regionu).
     */
    suspend fun topPopular(limit: Int = 20): List<ArchiveItem>

    /**
     * Wyszukiwanie po `mediatype:audio`, opcjonalnie zawężone do [category]. Tokeny zaczynające się
     * od `#` (np. `#pl`, `#rap`) są traktowane jak tagi — patrz implementacja dla mapowania na pola
     * `subject:`/`language:` w metadanych Internet Archive. [offset] jak w [browseCategory].
     */
    suspend fun search(query: String, category: ArchiveCategory? = null, limit: Int = 50, offset: Int = 0): List<ArchiveItem>

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
     * Ścieżki w bibliotece Archiwum — pobrane NA STAŁE (grają offline) LUB dodane jako stream (patrz
     * [addTrackToLibraryAsStream]). DESIGN.md Etap 40, Etap 53.
     */
    val library: StateFlow<List<Track>>

    /**
     * Jak [library], ale pogrupowane z powrotem w [ArchiveLibraryGroup] (jeden item = jedna grupa)
     * i podzielone wg [ArchiveCategory] wywnioskowanej przy dodawaniu — do sekcji "Pobrane z
     * Archiwum" w ekranach Audiobooki/Podkasty (Etap 53, zgłoszenie: "pobieranie ma dodawać
     * audiobooki/podcasty z Archiwum obok tych z LibriVox/realnych subskrypcji"). Pozycje bez
     * rozpoznanej kategorii są pominięte (nie trafiają do żadnego kubełka).
     */
    val libraryByCategory: StateFlow<Map<ArchiveCategory, List<ArchiveLibraryGroup>>>

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

    /**
     * Jak [addTrackToLibrary], ale BEZ pobierania pliku — wpis w [library] gra bezpośrednio z URL-a
     * archive.org (patrz `ArchiveLibraryTrackEntity.remoteUrl`). User: "przycisk streaming, dodaje
     * tylko do biblioteki w postaci streamingu, żeby cross między urządzeniami mógł jakoś działać".
     * Sam wpis lokalny (Room) działa od razu; realna synchronizacja tego wpisu NA INNE zalogowane
     * urządzenie wymaga osobnej warstwy backendu (Supabase Postgrest jest zainstalowany, ale
     * NIEUŻYWANY do żadnych danych poza samym logowaniem — patrz audyt Etap 53) i nie jest tu
     * jeszcze zrobiona.
     */
    suspend fun addTrackToLibraryAsStream(track: ArchiveTrack, item: ArchiveItem): Boolean

    /** Usuwa lokalny plik (jeśli był) i wpis z [library]. */
    suspend fun removeTrackFromLibrary(trackId: Long)
}
