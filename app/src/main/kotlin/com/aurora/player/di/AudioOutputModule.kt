package com.aurora.player.di

import com.aurora.player.domain.audio.AudioOutput
import com.aurora.player.playback.LocalAudioOutput
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioOutputModule {
    @Binds
    abstract fun bindAudioOutput(impl: LocalAudioOutput): AudioOutput
}
