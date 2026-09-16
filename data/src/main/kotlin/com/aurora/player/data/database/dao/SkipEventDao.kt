package com.aurora.player.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aurora.player.data.database.entity.SkipEventEntity

@Dao
interface SkipEventDao {
    @Insert
    suspend fun insert(event: SkipEventEntity)

    /** DESIGN.md Etap 27 — czytane przez `GeniusRepositoryImpl`, żeby unikać niedawno pominiętych. */
    @Query("SELECT * FROM skip_events")
    suspend fun getAll(): List<SkipEventEntity>
}
