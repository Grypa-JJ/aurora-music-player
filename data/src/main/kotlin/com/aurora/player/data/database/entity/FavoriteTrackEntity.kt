package com.aurora.player.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Utwór oznaczony sercem — DESIGN.md Etap 22. */
@Entity(tableName = "favorite_tracks")
data class FavoriteTrackEntity(
    @PrimaryKey val trackId: Long,
    val addedAtMs: Long,
)
