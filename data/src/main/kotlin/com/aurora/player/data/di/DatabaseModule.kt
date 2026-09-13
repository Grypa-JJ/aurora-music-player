package com.aurora.player.data.di

import android.content.Context
import androidx.room.Room
import com.aurora.player.data.database.AuroraDatabase
import com.aurora.player.data.database.dao.PlayEventDao
import com.aurora.player.data.database.dao.SkipEventDao
import com.aurora.player.data.database.dao.TrackAffinityDao
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
        Room.databaseBuilder(context, AuroraDatabase::class.java, "aurora.db").build()

    @Provides
    fun providePlayEventDao(database: AuroraDatabase): PlayEventDao = database.playEventDao()

    @Provides
    fun provideSkipEventDao(database: AuroraDatabase): SkipEventDao = database.skipEventDao()

    @Provides
    fun provideTrackAffinityDao(database: AuroraDatabase): TrackAffinityDao = database.trackAffinityDao()
}
