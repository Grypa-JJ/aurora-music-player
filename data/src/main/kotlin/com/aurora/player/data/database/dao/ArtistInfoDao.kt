package com.aurora.player.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aurora.player.data.database.entity.ArtistInfoEntity

@Dao
interface ArtistInfoDao {
    @Query("SELECT * FROM artist_info WHERE artistNameKey = :key")
    suspend fun get(key: String): ArtistInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ArtistInfoEntity)
}
