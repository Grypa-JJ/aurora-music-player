package com.aurora.player.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Subskrybowany podcast — DESIGN.md Etap 32. */
@Entity(tableName = "podcast_subscriptions")
data class PodcastSubscriptionEntity(
    @PrimaryKey val feedUrl: String,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val description: String,
    val addedAtMs: Long,
)
