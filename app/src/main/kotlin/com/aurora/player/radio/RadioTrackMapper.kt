package com.aurora.player.radio

import com.aurora.player.domain.model.RadioStation
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TrackSource
import com.aurora.player.domain.util.TrackIdHasher

/**
 * Stacja radiowa jako [Track] "na chwilę" — pozwala odtwarzać ją przez ten sam, już istniejący
 * [com.aurora.player.domain.repository.PlayerRepository]/kolejkę/Now Playing, zamiast budować
 * osobną ścieżkę odtwarzania tylko dla radia. `durationMs = 0` (strumień na żywo, bez długości) —
 * NowPlayingScreen/TrackListItem już traktują 0 jako "nieznane" dla utworów z chmury.
 */
fun RadioStation.toTrack(): Track = Track(
    id = TrackIdHasher.deriveId(RADIO_SOURCE_DISCRIMINATOR, stationUuid),
    uri = streamUrl,
    title = name,
    artist = "Radio na żywo",
    album = tags.substringBefore(',').ifBlank { countryCode },
    genre = null,
    year = null,
    durationMs = 0L,
    dateAddedMs = 0L,
    albumArtUri = faviconUrl,
    source = TrackSource.RADIO,
)

private const val RADIO_SOURCE_DISCRIMINATOR = "radio"
