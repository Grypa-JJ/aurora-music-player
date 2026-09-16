package com.aurora.player.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aurora.player.data.database.entity.TrackAudioMetadataEntity

@Dao
interface TrackAudioMetadataDao {
    @Query("SELECT * FROM track_audio_metadata")
    suspend fun getAll(): List<TrackAudioMetadataEntity>

    @Query("SELECT * FROM track_audio_metadata WHERE trackId = :trackId")
    suspend fun get(trackId: Long): TrackAudioMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TrackAudioMetadataEntity)
}
