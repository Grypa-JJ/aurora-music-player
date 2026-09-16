package com.aurora.player.data.di

import com.aurora.player.data.genius.GeniusRepositoryImpl
import com.aurora.player.data.genius.PlaybackHistoryRepositoryImpl
import com.aurora.player.data.media.TrackRepositoryImpl
import com.aurora.player.data.playlist.FavoritesRepositoryImpl
import com.aurora.player.data.playlist.PlaylistRepositoryImpl
import com.aurora.player.domain.repository.FavoritesRepository
import com.aurora.player.domain.repository.GeniusRepository
import com.aurora.player.domain.repository.PlaybackHistoryRepository
import com.aurora.player.domain.repository.PlaylistRepository
import com.aurora.player.domain.repository.TrackRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindTrackRepository(impl: TrackRepositoryImpl): TrackRepository

    @Binds
    abstract fun bindGeniusRepository(impl: GeniusRepositoryImpl): GeniusRepository

    @Binds
    abstract fun bindPlaybackHistoryRepository(impl: PlaybackHistoryRepositoryImpl): PlaybackHistoryRepository

    @Binds
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    abstract fun bindFavoritesRepository(impl: FavoritesRepositoryImpl): FavoritesRepository
}
