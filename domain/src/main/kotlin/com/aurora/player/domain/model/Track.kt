package com.aurora.player.domain.model

data class Track(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String?,
    val year: Int?,
    val durationMs: Long,
    val dateAddedMs: Long,
    val albumArtUri: String?,
    val source: TrackSource = TrackSource.LOCAL,
)
