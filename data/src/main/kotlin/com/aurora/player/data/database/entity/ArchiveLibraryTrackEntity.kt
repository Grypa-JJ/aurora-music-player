package com.aurora.player.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ścieżka z Internet Archive pobrana NA STAŁE do biblioteki (offline) — DESIGN.md Etap 40.
 * [trackId] to ten sam hash co `Track.id` budowany przez `TrackIdHasher` w `:app` (dyskryminator
 * `archive_org`, patrz `ArchiveTrackMapper`) — dzięki temu ulubione/playlisty (które trzymają
 * tylko surowy `Long`) rozpoznają ten sam utwór niezależnie, czy trafia tu, czy gra bezpośrednio
 * ze streamingu bez zapisu. [localFileUri] to `file://...` w prywatnym magazynie appki
 * (`Context.filesDir`, NIE MediaStore) — plik nie jest widoczny dla innych aplikacji ani skanera
 * multimediów, znika automatycznie przy odinstalowaniu.
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
    val localFileUri: String,
    val addedAtMs: Long,
)
