package com.aurora.player.independent

import com.aurora.player.domain.model.IndependentTrack
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TrackSource
import com.aurora.player.domain.util.TrackIdHasher

/** Utwór z "Muzyki niezależnej" jako [Track] — ten sam wzorzec co [com.aurora.player.podcast.toTrack]. */
fun IndependentTrack.toTrack(): Track = Track(
    id = TrackIdHasher.deriveId(INDEPENDENT_SOURCE_DISCRIMINATOR, id),
    uri = audioUrl,
    title = name,
    artist = artistName,
    album = albumName.orEmpty(),
    genre = null,
    year = null,
    durationMs = durationSec * 1000L,
    dateAddedMs = 0L,
    albumArtUri = imageUrl,
    source = TrackSource.INDEPENDENT,
)

private const val INDEPENDENT_SOURCE_DISCRIMINATOR = "jamendo"
