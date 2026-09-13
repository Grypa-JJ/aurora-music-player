package com.aurora.player.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aurora.player.data.database.entity.TrackCooccurrenceEntity

@Dao
interface TrackCooccurrenceDao {
    @Query("SELECT * FROM track_cooccurrence WHERE trackIdA = :trackId OR trackIdB = :trackId")
    suspend fun forTrack(trackId: Long): List<TrackCooccurrenceEntity>

    @Query(
        "SELECT * FROM track_cooccurrence WHERE (trackIdA = :a AND trackIdB = :b) OR (trackIdA = :b AND trackIdB = :a)",
    )
    suspend fun get(a: Long, b: Long): TrackCooccurrenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrackCooccurrenceEntity)
}
