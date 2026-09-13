package com.aurora.player.di

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.aurora.player.playback.PlayerController
import com.aurora.player.domain.repository.PlayerRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerProvidesModule {
    @Provides
    @Singleton
    fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer =
        ExoPlayer.Builder(context).build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerBindsModule {
    @Binds
    abstract fun bindPlayerRepository(impl: PlayerController): PlayerRepository
}
