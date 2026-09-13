package com.aurora.player.di

import com.aurora.player.domain.repository.PlayerRepository
import com.aurora.player.playback.PlayerController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerBindsModule {
    @Binds
    abstract fun bindPlayerRepository(impl: PlayerController): PlayerRepository
}
