package com.aurora.player.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aurora.player.data.database.entity.ArchiveLibraryTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArchiveLibraryDao {
    @Query("SELECT * FROM archive_library_tracks ORDER BY addedAtMs DESC")
    fun observeAll(): Flow<List<ArchiveLibraryTrackEntity>>

    @Query("SELECT * FROM archive_library_tracks WHERE trackId = :trackId")
    suspend fun getByTrackId(trackId: Long): ArchiveLibraryTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ArchiveLibraryTrackEntity)

    @Query("DELETE FROM archive_library_tracks WHERE trackId = :trackId")
    suspend fun delete(trackId: Long)
}
