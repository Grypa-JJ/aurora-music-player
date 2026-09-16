package com.aurora.player.di

import com.aurora.player.domain.repository.RadioRepository
import com.aurora.player.radio.RadioRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RadioModule {
    @Binds
    abstract fun bindRadioRepository(impl: RadioRepositoryImpl): RadioRepository
}
