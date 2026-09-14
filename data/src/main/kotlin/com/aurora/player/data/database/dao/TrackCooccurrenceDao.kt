package com.aurora.player.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aurora.player.data.database.entity.TrackCooccurrenceEntity

@Dao
interface TrackCooccurrenceDao {
    /** Co historycznie grało jako NASTĘPNE po [trackId] — kierunkowe, patrz encja. */
    @Query("SELECT * FROM track_cooccurrence WHERE fromTrackId = :trackId")
    suspend fun getFrom(trackId: Long): List<TrackCooccurrenceEntity>

    @Query("SELECT * FROM track_cooccurrence WHERE fromTrackId = :from AND toTrackId = :to")
    suspend fun get(from: Long, to: Long): TrackCooccurrenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrackCooccurrenceEntity)
}
