package com.aurora.player.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ścieżka z Internet Archive dodana do biblioteki — DESIGN.md Etap 40, Etap 53. [trackId] to ten
 * sam hash co `Track.id` budowany przez `TrackIdHasher` w `:app` (dyskryminator `archive_org`,
 * patrz `ArchiveTrackMapper`) — dzięki temu ulubione/playlisty (które trzymają tylko surowy `Long`)
 * rozpoznają ten sam utwór niezależnie, czy trafia tu, czy gra bezpośrednio ze streamingu bez zapisu.
 *
 * [localFileUri] `null` = pozycja dodana jako STREAM (user: "przycisk streaming, dodaje tylko do
 * biblioteki w postaci streamingu") — nie ma lokalnego pliku, odtwarzanie leci bezpośrednio z
 * [remoteUrl] (ten sam URL co przy zwykłym graniu bez dodawania do biblioteki). Niepusty
 * [localFileUri] = `file://...` w prywatnym magazynie appki (`Context.filesDir`, NIE MediaStore) —
 * plik nie jest widoczny dla innych aplikacji ani skanera multimediów, znika automatycznie przy
 * odinstalowaniu. [remoteUrl] zapisywany ZAWSZE (nie tylko dla streamu) — jedno źródło prawdy do
 * odtwarzania niezależnie od trybu, i do ewentualnego ponownego pobrania po ręcznym usunięciu pliku.
 *
 * [category] — z jakiej zakładki Archiwum (Muzyka/Podcasty/Audiobooki/Radio) pochodzi pozycja,
 * wywnioskowane z `collection` w metadanych IA przy dodawaniu (patrz `ArchiveRepositoryImpl`) —
 * `null` gdy nie udało się dopasować do żadnej z czterech kategorii. Pozwala pokazać pobrane
 * audiobooki/podcasty z Archiwum w tych samych ekranach co Audiobooki/Subskrypcje, nie tylko w
 * generycznym "Pobrane" (Etap 53, zgłoszenie).
 */
@Entity(tableName = "archive_library_tracks")
data class ArchiveLibraryTrackEntity(
    @PrimaryKey val trackId: Long,
    val identifier: String,
    val fileName: String,
    val title: String,
    val artist: String,
    val album: String,
    val year: Int?,
    val durationMs: Long,
    val albumArtUrl: String?,
    val localFileUri: String?,
    val remoteUrl: String,
    val category: String?,
    val addedAtMs: Long,
)
