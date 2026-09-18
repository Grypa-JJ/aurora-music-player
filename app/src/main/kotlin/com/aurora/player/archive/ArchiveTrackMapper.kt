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
    id = archiveTrackId(identifier, fileName),
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

/**
 * Id współdzielone z [com.aurora.player.data.database.entity.ArchiveLibraryTrackEntity] (patrz
 * `ArchiveRepositoryImpl.addTrackToLibrary`) — ta sama ścieżka MUSI dostać ten sam `Track.id`
 * niezależnie, czy gra bezpośrednio ze streamingu, czy z pliku pobranego na stałe do biblioteki,
 * inaczej ulubione/playlisty (identyfikują utwór tylko po `Long`) zgubiłyby dopasowanie po pobraniu.
 */
internal fun archiveTrackId(identifier: String, fileName: String): Long =
    TrackIdHasher.deriveId(ARCHIVE_SOURCE_DISCRIMINATOR, "$identifier/$fileName")

private const val ARCHIVE_SOURCE_DISCRIMINATOR = "archive_org"
