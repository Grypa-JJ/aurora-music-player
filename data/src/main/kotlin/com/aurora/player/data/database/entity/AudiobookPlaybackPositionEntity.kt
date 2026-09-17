package com.aurora.player.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * "Gdzie skończyłem" per rozdział — ten sam wzorzec co PodcastPlaybackPositionEntity. Klucz to
 * [trackId] (hash rozdziału przez `TrackIdHasher`), nie surowy `guid` z RSS.
 */
@Entity(tableName = "audiobook_playback_positions")
data class AudiobookPlaybackPositionEntity(
    @PrimaryKey val trackId: Long,
    val positionMs: Long,
    val updatedAtMs: Long,
)
