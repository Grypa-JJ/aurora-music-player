package com.aurora.player.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Playlista budowana ręcznie przez użytkownika — patrz DESIGN.md Etap 22. */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtMs: Long,
)
