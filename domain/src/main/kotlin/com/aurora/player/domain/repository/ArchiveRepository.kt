package com.aurora.player.domain.repository

import com.aurora.player.domain.model.ArchiveItem
import com.aurora.player.domain.model.ArchiveTrack

/** Internet Archive (`archive.org`) — koncerty na żywo, netlabele, stare radio. Zero kluczy API. */
interface ArchiveRepository {
    suspend fun browseCollection(collection: String, limit: Int = 50): List<ArchiveItem>

    /** Wyszukiwanie globalne po `mediatype:audio`, poza kuratorowanymi kolekcjami. */
    suspend fun search(query: String, limit: Int = 50): List<ArchiveItem>

    suspend fun tracksForItem(identifier: String): List<ArchiveTrack>

    /**
     * "Dla Ciebie" — dopasowane do gustu z lokalnej biblioteki (wykonawca + gatunek, ulubione liczone
     * mocniej), patrz DESIGN.md Etap 37 (algorytm personalizacji). Puste listy = brak sygnału,
     * wywołujący powinien wtedy pokazać zwykły domyślny [browseCollection].
     */
    suspend fun personalizedForYou(
        favoriteArtists: List<String>,
        favoriteGenres: List<String>,
        limit: Int = 20,
    ): List<ArchiveItem>
}
