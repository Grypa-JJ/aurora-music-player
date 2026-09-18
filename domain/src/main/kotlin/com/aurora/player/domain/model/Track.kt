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
    /** `null` dopóki [com.aurora.player.domain.repository.AudioMetadataRepository] jej nie
     *  dociągnie w tle — DESIGN.md Etap 29. */
    val audioMetadata: AudioTrackMetadata? = null,
    /**
     * Id albumu przyznane przez MediaStore (`MediaStore.Audio.Media.ALBUM_ID`) — TYLKO dla
     * [TrackSource.LOCAL]. Autorytatywny, odporny na niespójne tagi identyfikator "ten sam
     * album" (system Androida już grupuje po nim przy skanowaniu plików), w przeciwieństwie do
     * pary (nazwa albumu, wykonawca) jako string — patrz `LibraryGrouping.groupTracksByAlbum`,
     * DESIGN.md Etap 42.
     */
    val albumId: Long? = null,
)
