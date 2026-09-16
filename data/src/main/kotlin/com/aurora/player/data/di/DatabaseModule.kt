package com.aurora.player.data.di

import android.content.Context
import androidx.room.Room
import com.aurora.player.data.database.AuroraDatabase
import com.aurora.player.data.database.dao.FavoriteTrackDao
import com.aurora.player.data.database.dao.LyricsCacheDao
import com.aurora.player.data.database.dao.PlayEventDao
import com.aurora.player.data.database.dao.PlaylistDao
import com.aurora.player.data.database.dao.SkipEventDao
import com.aurora.player.data.database.dao.TrackAffinityDao
import com.aurora.player.data.database.dao.TrackCooccurrenceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAuroraDatabase(@ApplicationContext context: Context): AuroraDatabase =
        Room.databaseBuilder(context, AuroraDatabase::class.java, "aurora.db")
            // Wczesny etap projektu, schemat się jeszcze zmienia — brak realnych migracji jest
            // świadomy. Dane lokalnej historii są odtwarzalne (zbierają się od nowa), więc
            // destrukcyjna migracja przy zmianie wersji jest tańsza niż pisanie Migration teraz.
            // Do zastąpienia prawdziwymi Migration przed pierwszym publicznym wydaniem.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providePlayEventDao(database: AuroraDatabase): PlayEventDao = database.playEventDao()

    @Provides
    fun provideSkipEventDao(database: AuroraDatabase): SkipEventDao = database.skipEventDao()

    @Provides
    fun provideTrackAffinityDao(database: AuroraDatabase): TrackAffinityDao = database.trackAffinityDao()

    @Provides
    fun provideTrackCooccurrenceDao(database: AuroraDatabase): TrackCooccurrenceDao = database.trackCooccurrenceDao()

    @Provides
    fun providePlaylistDao(database: AuroraDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideFavoriteTrackDao(database: AuroraDatabase): FavoriteTrackDao = database.favoriteTrackDao()

    @Provides
    fun provideLyricsCacheDao(database: AuroraDatabase): LyricsCacheDao = database.lyricsCacheDao()
}
