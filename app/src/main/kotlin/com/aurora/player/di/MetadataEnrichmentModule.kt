package com.aurora.player.di

import com.aurora.player.domain.repository.MetadataEnrichmentRepository
import com.aurora.player.metadata.MetadataEnrichmentRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class MetadataEnrichmentModule {
    @Binds
    abstract fun bindMetadataEnrichmentRepository(
        impl: MetadataEnrichmentRepositoryImpl,
    ): MetadataEnrichmentRepository
}
