package com.aurora.player.audiobook

import com.aurora.player.domain.model.Audiobook
import com.aurora.player.domain.model.AudiobookChapter
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TrackSource
import com.aurora.player.domain.util.TrackIdHasher

/**
 * Rozdział audiobooka jako [Track] — ten sam wzorzec co [com.aurora.player.podcast.toTrack] dla
 * podkastów: odtwarzanie przez istniejący [com.aurora.player.domain.repository.PlayerRepository],
 * zero osobnej ścieżki odtwarzania.
 */
fun AudiobookChapter.toTrack(audiobook: Audiobook): Track = Track(
    id = TrackIdHasher.deriveId(AUDIOBOOK_SOURCE_DISCRIMINATOR, id),
    uri = audioUrl,
    title = title,
    artist = audiobook.author,
    album = audiobook.title,
    genre = null,
    year = null,
    durationMs = durationMs,
    dateAddedMs = 0L,
    albumArtUri = audiobook.coverUrl,
    source = TrackSource.AUDIOBOOK,
)

private const val AUDIOBOOK_SOURCE_DISCRIMINATOR = "audiobook"
