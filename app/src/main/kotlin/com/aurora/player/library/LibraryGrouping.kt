package com.aurora.player.library

import com.aurora.player.domain.model.Track

/**
 * Grupowanie utworów wg albumu/wykonawcy — DESIGN.md Etap 22/23 ("taby Utwory/Albumy/Wykonawcy"
 * z pierwotnej wizji, sekcja 3.1). Czysto derywowane z [Track] w warstwie UI (bez nowej tabeli
 * Room ani zmian w repozytorium) — ten sam wzorzec co filtr wyszukiwarki w LibraryScreen.
 * Kluczowane po parze (album, artysta), NIE samym tytule albumu — inaczej dwa różne albumy o tym
 * samym tytule (np. self-titled od różnych wykonawców) zlałyby się w jedną grupę.
 */
data class AlbumGroup(
    val name: String,
    val artist: String,
    val albumArtUri: String?,
    val tracks: List<Track>,
)

data class ArtistGroup(
    val name: String,
    val tracks: List<Track>,
)

private const val UNKNOWN_ALBUM = "Nieznany album"
private const val UNKNOWN_ARTIST = "Nieznany wykonawca"

fun groupTracksByAlbum(tracks: List<Track>): List<AlbumGroup> =
    tracks
        .groupBy { (it.album.ifBlank { UNKNOWN_ALBUM }) to it.artist }
        .map { (key, groupTracks) ->
            AlbumGroup(
                name = key.first,
                artist = key.second,
                albumArtUri = groupTracks.firstOrNull { it.albumArtUri != null }?.albumArtUri,
                tracks = groupTracks.sortedBy { it.title.lowercase() },
            )
        }
        .sortedBy { it.name.lowercase() }

fun groupTracksByArtist(tracks: List<Track>): List<ArtistGroup> =
    tracks
        .groupBy { it.artist.ifBlank { UNKNOWN_ARTIST } }
        .map { (name, groupTracks) -> ArtistGroup(name = name, tracks = groupTracks) }
        .sortedBy { it.name.lowercase() }
