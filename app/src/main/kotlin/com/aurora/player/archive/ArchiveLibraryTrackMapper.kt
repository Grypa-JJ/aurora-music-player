package com.aurora.player.archive

import com.aurora.player.data.database.entity.ArchiveLibraryTrackEntity
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TrackSource

/**
 * Pozycja z biblioteki Archiwum jako [Track] — `uri` to lokalny plik gdy pobrana na stałe, albo
 * [ArchiveLibraryTrackEntity.remoteUrl] (streaming z archive.org) gdy dodana tylko jako stream
 * (`localFileUri == null`, patrz KDoc encji).
 */
fun ArchiveLibraryTrackEntity.toTrack(): Track = Track(
    id = trackId,
    uri = localFileUri ?: remoteUrl,
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
