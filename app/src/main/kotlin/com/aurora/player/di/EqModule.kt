package com.aurora.player.di

import com.aurora.player.domain.repository.EqRepository
import com.aurora.player.eq.EqRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class EqModule {
    @Binds
    abstract fun bindEqRepository(impl: EqRepositoryImpl): EqRepository
}
