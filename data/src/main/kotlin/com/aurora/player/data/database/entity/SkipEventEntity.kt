package com.aurora.player.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "skip_events",
    indices = [Index(value = ["trackId"])],
)
data class SkipEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val timestamp: Long,
    val playedMs: Long,
    val dayOfWeek: Int,
    val hourOfDay: Int,
)
