package com.aurora.player.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "play_events",
    indices = [Index(value = ["trackId"])],
)
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val timestampStart: Long,
    val timestampEnd: Long,
    val playedMs: Long,
    val completed: Boolean,
    val dayOfWeek: Int,
    val hourOfDay: Int,
)
