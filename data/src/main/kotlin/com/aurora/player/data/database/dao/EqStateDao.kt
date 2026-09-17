package com.aurora.player.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aurora.player.data.database.entity.EqStateEntity

@Dao
interface EqStateDao {
    @Query("SELECT * FROM eq_state WHERE id = 0")
    suspend fun get(): EqStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EqStateEntity)
}
