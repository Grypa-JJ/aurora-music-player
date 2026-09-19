package com.aurora.player.di

import com.aurora.player.domain.repository.TranscriptRepository
import com.aurora.player.domain.repository.TranslationRepository
import com.aurora.player.podcast.TranscriptRepositoryImpl
import com.aurora.player.translation.MlKitTranslationRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TranscriptModule {
    @Binds
    abstract fun bindTranscriptRepository(impl: TranscriptRepositoryImpl): TranscriptRepository

    @Binds
    abstract fun bindTranslationRepository(impl: MlKitTranslationRepositoryImpl): TranslationRepository
}
