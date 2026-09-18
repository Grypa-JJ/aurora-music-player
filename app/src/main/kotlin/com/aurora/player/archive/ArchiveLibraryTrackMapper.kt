package com.aurora.player.archive

import com.aurora.player.data.database.entity.ArchiveLibraryTrackEntity
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TrackSource

/** Ścieżka pobrana na stałe do biblioteki jako [Track] — `uri` wskazuje lokalny plik, nie sieć. */
fun ArchiveLibraryTrackEntity.toTrack(): Track = Track(
    id = trackId,
    uri = localFileUri,
    title = title,
    artist = artist,
    album = album,
    genre = null,
    year = year,
    durationMs = durationMs,
    dateAddedMs = addedAtMs,
    albumArtUri = albumArtUrl,
    source = TrackSource.ARCHIVE,
)
