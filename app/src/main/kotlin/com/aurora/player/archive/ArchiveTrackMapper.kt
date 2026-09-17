package com.aurora.player.archive

import com.aurora.player.domain.model.ArchiveItem
import com.aurora.player.domain.model.ArchiveTrack
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TrackSource
import com.aurora.player.domain.util.TrackIdHasher

/**
 * Ścieżka z Internet Archive jako [Track] — ten sam wzorzec co [com.aurora.player.podcast.toTrack].
 * Dyskryminator id łączy [ArchiveTrack.identifier] i [ArchiveTrack.fileName], bo pliki audio są
 * unikalne dopiero w obrębie jednego itemu (numer ścieżki sam w sobie się powtarza między itemami).
 */
fun ArchiveTrack.toTrack(item: ArchiveItem): Track = Track(
    id = TrackIdHasher.deriveId(ARCHIVE_SOURCE_DISCRIMINATOR, "$identifier/$fileName"),
    uri = audioUrl,
    title = title,
    artist = item.creator,
    album = item.title,
    genre = null,
    year = item.year,
    durationMs = durationMs,
    dateAddedMs = 0L,
    albumArtUri = item.coverUrl,
    source = TrackSource.ARCHIVE,
)

private const val ARCHIVE_SOURCE_DISCRIMINATOR = "archive_org"
