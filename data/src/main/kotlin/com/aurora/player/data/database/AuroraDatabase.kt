package com.aurora.player.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aurora.player.data.database.dao.PlayEventDao
import com.aurora.player.data.database.dao.SkipEventDao
import com.aurora.player.data.database.dao.TrackAffinityDao
import com.aurora.player.data.database.entity.PlayEventEntity
import com.aurora.player.data.database.entity.SkipEventEntity
import com.aurora.player.data.database.entity.TrackAffinityEntity

/**
 * Etap 3: tylko tabele potrzebne do Genius (patrz DESIGN.md sekcja 5.1). `exportSchema = false`
 * bo to wczesny etap projektu — historia migracji schematu dojdzie, gdy struktura się ustabilizuje.
 */
@Database(
    entities = [PlayEventEntity::class, SkipEventEntity::class, TrackAffinityEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AuroraDatabase : RoomDatabase() {
    abstract fun playEventDao(): PlayEventDao
    abstract fun skipEventDao(): SkipEventDao
    abstract fun trackAffinityDao(): TrackAffinityDao
}
