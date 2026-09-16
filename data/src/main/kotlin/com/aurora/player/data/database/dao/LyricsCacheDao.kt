package com.aurora.player.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aurora.player.data.database.entity.LyricsCacheEntity

@Dao
interface LyricsCacheDao {
    @Query("SELECT * FROM lyrics_cache WHERE trackId = :trackId")
    suspend fun get(trackId: Long): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LyricsCacheEntity)
}
