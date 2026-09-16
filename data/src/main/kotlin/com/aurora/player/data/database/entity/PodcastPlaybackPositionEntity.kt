package com.aurora.player.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * "Gdzie skończyłem" per odcinek — DESIGN.md Etap 32. Klucz to [trackId] (hash odcinka przez
 * `TrackIdHasher`, ten sam identyfikator co wszędzie indziej w appce — Ulubione/Playlisty też
 * kluczują po Track.id), nie surowy `guid` z RSS — appka nie musi nigdzie odwracać hasha.
 */
@Entity(tableName = "podcast_playback_positions")
data class PodcastPlaybackPositionEntity(
    @PrimaryKey val trackId: Long,
    val positionMs: Long,
    val updatedAtMs: Long,
)
