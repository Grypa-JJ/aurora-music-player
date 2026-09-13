package com.aurora.player.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import com.aurora.player.data.database.entity.SkipEventEntity

@Dao
interface SkipEventDao {
    @Insert
    suspend fun insert(event: SkipEventEntity)
}
