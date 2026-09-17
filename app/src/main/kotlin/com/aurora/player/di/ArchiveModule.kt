package com.aurora.player.di

import com.aurora.player.archive.ArchiveRepositoryImpl
import com.aurora.player.domain.repository.ArchiveRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ArchiveModule {
    @Binds
    abstract fun bindArchiveRepository(impl: ArchiveRepositoryImpl): ArchiveRepository
}
