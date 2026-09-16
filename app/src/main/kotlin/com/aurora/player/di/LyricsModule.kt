package com.aurora.player.di

import com.aurora.player.domain.repository.LyricsRepository
import com.aurora.player.lyrics.LyricsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LyricsModule {
    @Binds
    abstract fun bindLyricsRepository(impl: LyricsRepositoryImpl): LyricsRepository
}
