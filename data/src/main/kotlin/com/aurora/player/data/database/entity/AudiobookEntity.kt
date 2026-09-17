package com.aurora.player.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Audiobook dodany do biblioteki usera — LibriVox. Ten sam kształt co PodcastSubscriptionEntity,
 * ale klucz to numeryczne [id] książki z API LibriVox, nie feed URL — LibriVox zwraca metadane +
 * gotową listę rozdziałów jednym zapytaniem JSON po `id`, więc nie ma tu osobnego RSS do trzymania.
 */
@Entity(tableName = "audiobooks")
data class AudiobookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val language: String?,
    val coverUrl: String?,
    val description: String,
    val addedAtMs: Long,
)
