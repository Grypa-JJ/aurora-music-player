package com.aurora.player.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aurora.player.data.database.entity.TrackMetadataOverrideEntity

@Dao
interface TrackMetadataOverrideDao {
    @Query("SELECT * FROM track_metadata_override")
    suspend fun getAll(): List<TrackMetadataOverrideEntity>

    @Query("SELECT * FROM track_metadata_override WHERE trackId = :trackId")
    suspend fun get(trackId: Long): TrackMetadataOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TrackMetadataOverrideEntity)
}
