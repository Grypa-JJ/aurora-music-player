package com.aurora.player.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache tekstu utworu (LRCLIB) — DESIGN.md Etap 24. [syncedLrc]/[plainText] oba `null` = appka
 * już zapytała i NIE znalazła — świadomie zapisane, żeby offline-first nie odpytywała sieci
 * ponownie przy każdym odtworzeniu utworu bez tekstu.
 */
@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val trackId: Long,
    val syncedLrc: String?,
    val plainText: String?,
    val fetchedAtMs: Long,
)
