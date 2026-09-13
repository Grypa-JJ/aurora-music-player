package com.aurora.player.data.database.entity

import androidx.room.Entity

/**
 * Symetryczna para (trackIdA < trackIdB zawsze, normalizowane przy zapisie) — ile razy dwa
 * utwory zagrały bezpośrednio po sobie. Patrz DESIGN.md sekcja 5.1/5.4.
 */
@Entity(tableName = "track_cooccurrence", primaryKeys = ["trackIdA", "trackIdB"])
data class TrackCooccurrenceEntity(
    val trackIdA: Long,
    val trackIdB: Long,
    val count: Int,
    val lastSeenAt: Long,
)
