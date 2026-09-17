package com.aurora.player.di

import com.aurora.player.domain.repository.IndependentMusicRepository
import com.aurora.player.independent.JamendoRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class IndependentMusicModule {
    @Binds
    abstract fun bindIndependentMusicRepository(impl: JamendoRepositoryImpl): IndependentMusicRepository
}
