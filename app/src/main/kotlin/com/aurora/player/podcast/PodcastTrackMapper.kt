package com.aurora.player.podcast

import com.aurora.player.domain.model.Podcast
import com.aurora.player.domain.model.PodcastEpisode
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TrackSource
import com.aurora.player.domain.util.TrackIdHasher

/**
 * Odcinek podkastu jako [Track] — ten sam wzorzec co [com.aurora.player.radio.toTrack] dla Radia:
 * odtwarzanie przez istniejący [com.aurora.player.domain.repository.PlayerRepository], zero
 * osobnej ścieżki odtwarzania.
 */
fun PodcastEpisode.toTrack(podcast: Podcast): Track = Track(
    id = TrackIdHasher.deriveId(PODCAST_SOURCE_DISCRIMINATOR, guid),
    uri = audioUrl,
    title = title,
    artist = podcast.title,
    album = podcast.title,
    genre = null,
    year = null,
    durationMs = durationMs,
    dateAddedMs = publishedAtMs,
    albumArtUri = podcast.artworkUrl,
    source = TrackSource.PODCAST,
)

private const val PODCAST_SOURCE_DISCRIMINATOR = "podcast"
