package com.aurora.player.di

import com.aurora.player.audiobook.AudiobookRepositoryImpl
import com.aurora.player.domain.repository.AudiobookRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AudiobookModule {
    @Binds
    abstract fun bindAudiobookRepository(impl: AudiobookRepositoryImpl): AudiobookRepository
}
