package com.aurora.player.di

import com.aurora.player.cloud.GoogleDriveLibraryRepository
import com.aurora.player.domain.repository.CloudLibraryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CloudModule {
    @Binds
    abstract fun bindCloudLibraryRepository(impl: GoogleDriveLibraryRepository): CloudLibraryRepository
}
