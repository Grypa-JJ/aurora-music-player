package com.aurora.player.di

import com.aurora.player.domain.repository.PodcastCatalogRepository
import com.aurora.player.domain.repository.PodcastRepository
import com.aurora.player.podcast.PodcastCatalogRepositoryImpl
import com.aurora.player.podcast.PodcastRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PodcastModule {
    @Binds
    abstract fun bindPodcastRepository(impl: PodcastRepositoryImpl): PodcastRepository

    @Binds
    abstract fun bindPodcastCatalogRepository(impl: PodcastCatalogRepositoryImpl): PodcastCatalogRepository
}
