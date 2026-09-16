package com.aurora.player.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Osobny od `com.aurora.player.di.ApplicationScope` (app-moduł) — `:data` zależy tylko od
 * `:domain`, nie od `:app`, więc nie może użyć tamtej adnotacji. Dwa niezależne scope'y na
 * granicy modułów to świadomy koszt tej granicy, nie duplikacja do posprzątania.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DataCoroutineModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
