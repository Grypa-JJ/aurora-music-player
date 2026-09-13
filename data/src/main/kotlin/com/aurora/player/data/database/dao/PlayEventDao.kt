package com.aurora.player.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aurora.player.data.database.entity.PlayEventEntity

@Dao
interface PlayEventDao {
    @Insert
    suspend fun insert(event: PlayEventEntity)

    @Query("SELECT * FROM play_events WHERE trackId = :trackId")
    suspend fun forTrack(trackId: Long): List<PlayEventEntity>
}
