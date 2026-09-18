package com.aurora.player.library

import com.aurora.player.domain.model.Track

/**
 * Grupowanie utworów wg albumu/wykonawcy — DESIGN.md Etap 22/23 ("taby Utwory/Albumy/Wykonawcy"
 * z pierwotnej wizji, sekcja 3.1). Czysto derywowane z [Track] w warstwie UI (bez nowej tabeli
 * Room ani zmian w repozytorium) — ten sam wzorzec co filtr wyszukiwarki w LibraryScreen.
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

/**
 * Etap 42, zgłoszenie: jeden album z wieloma utworami potrafił rozpaść się na ~20 osobnych
 * "albumów" z tą samą okładką. Przyczyna: dotychczasowy klucz grupowania to para (nazwa albumu,
 * wykonawca) jako STRING — a tag wykonawcy potrafi się różnić utwór-od-utworu w obrębie tego
 * samego albumu (features, niespójne tagowanie różnych rippów/źródeł), więc każda odmiana stringa
 * dostawała własną grupę mimo identycznego albumu. Poprawka: dla lokalnych utworów [Track.albumId]
 * (przyznane przez MediaStore przy skanowaniu, DESIGN.md sekcja 1.2) jest autorytatywne i odporne
 * na te niespójności — system i tak już zdecydował, które pliki należą do tego samego albumu.
 * Dla źródeł bez `albumId` (chmura/WebDAV/radio/podkasty) zostaje poprzedni klucz tekstowy — tam
 * nie ma odpowiednika MediaStore, który mógłby dać coś lepszego.
 */
private sealed interface AlbumKey {
    data class ById(val albumId: Long) : AlbumKey
    data class ByNameAndArtist(val name: String, val artist: String) : AlbumKey
}

private fun Track.albumKey(): AlbumKey =
    albumId?.let { AlbumKey.ById(it) } ?: AlbumKey.ByNameAndArtist(album.ifBlank { UNKNOWN_ALBUM }, artist)

fun groupTracksByAlbum(tracks: List<Track>): List<AlbumGroup> =
    tracks
        .groupBy { it.albumKey() }
        .map { (_, groupTracks) ->
            val first = groupTracks.first()
            AlbumGroup(
                name = first.album.ifBlank { UNKNOWN_ALBUM },
                artist = first.artist,
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
