package com.aurora.player.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aurora.player.data.database.dao.FavoriteTrackDao
import com.aurora.player.data.database.dao.LyricsCacheDao
import com.aurora.player.data.database.dao.PlayEventDao
import com.aurora.player.data.database.dao.PlaylistDao
import com.aurora.player.data.database.dao.SkipEventDao
import com.aurora.player.data.database.dao.TrackAffinityDao
import com.aurora.player.data.database.dao.TrackCooccurrenceDao
import com.aurora.player.data.database.entity.FavoriteTrackEntity
import com.aurora.player.data.database.entity.LyricsCacheEntity
import com.aurora.player.data.database.entity.PlayEventEntity
import com.aurora.player.data.database.entity.PlaylistEntity
import com.aurora.player.data.database.entity.PlaylistTrackEntity
import com.aurora.player.data.database.entity.SkipEventEntity
import com.aurora.player.data.database.entity.TrackAffinityEntity
import com.aurora.player.data.database.entity.TrackCooccurrenceEntity

/**
 * Etap 3-4: tabele potrzebne do Genius (patrz DESIGN.md sekcja 5.1). `exportSchema = false`
 * bo to wczesny etap projektu — historia migracji schematu dojdzie, gdy struktura się ustabilizuje.
 * Etap 22: playlisty + ulubione (`fallbackToDestructiveMigration` w DatabaseModule kasuje tylko
 * odtwarzalną historię Genius, nie ma tam jeszcze nic nieodtwarzalnego, ale playlisty/ulubione OD
 * TERAZ już są danymi użytkownika, nie cache'em — patrz komentarz w DatabaseModule przy migracji.
 * Etap 24: cache tekstów LRCLIB — odtwarzalny (można dociągnąć ponownie z sieci), więc destrukcyjna
 * migracja przy wersji 5 jest nadal bezpieczna z tych samych powodów co Genius.
 */
@Database(
    entities = [
        PlayEventEntity::class,
        SkipEventEntity::class,
        TrackAffinityEntity::class,
        TrackCooccurrenceEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        FavoriteTrackEntity::class,
        LyricsCacheEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AuroraDatabase : RoomDatabase() {
    abstract fun playEventDao(): PlayEventDao
    abstract fun skipEventDao(): SkipEventDao
    abstract fun trackAffinityDao(): TrackAffinityDao
    abstract fun trackCooccurrenceDao(): TrackCooccurrenceDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteTrackDao(): FavoriteTrackDao
    abstract fun lyricsCacheDao(): LyricsCacheDao
}
