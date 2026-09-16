package com.aurora.player.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aurora.player.data.database.entity.PodcastPlaybackPositionEntity
import com.aurora.player.data.database.entity.PodcastSubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcast_subscriptions ORDER BY addedAtMs DESC")
    fun observeSubscriptions(): Flow<List<PodcastSubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(entity: PodcastSubscriptionEntity)

    @Query("DELETE FROM podcast_subscriptions WHERE feedUrl = :feedUrl")
    suspend fun deleteSubscription(feedUrl: String)

    @Query("SELECT positionMs FROM podcast_playback_positions WHERE trackId = :trackId")
    suspend fun getPlaybackPosition(trackId: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaybackPosition(entity: PodcastPlaybackPositionEntity)
}
