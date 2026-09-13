package com.aurora.player.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aurora.player.data.database.entity.TrackAffinityEntity

@Dao
interface TrackAffinityDao {
    @Query("SELECT * FROM track_affinity")
    suspend fun getAll(): List<TrackAffinityEntity>

    @Query("SELECT * FROM track_affinity WHERE trackId = :trackId")
    suspend fun get(trackId: Long): TrackAffinityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrackAffinityEntity)
}
