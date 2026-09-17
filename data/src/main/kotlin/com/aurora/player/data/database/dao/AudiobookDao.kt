package com.aurora.player.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aurora.player.data.database.entity.AudiobookEntity
import com.aurora.player.data.database.entity.AudiobookPlaybackPositionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudiobookDao {
    @Query("SELECT * FROM audiobooks ORDER BY addedAtMs DESC")
    fun observeLibrary(): Flow<List<AudiobookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudiobook(entity: AudiobookEntity)

    @Query("DELETE FROM audiobooks WHERE id = :id")
    suspend fun deleteAudiobook(id: String)

    @Query("SELECT positionMs FROM audiobook_playback_positions WHERE trackId = :trackId")
    suspend fun getPlaybackPosition(trackId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaybackPosition(entity: AudiobookPlaybackPositionEntity)
}
