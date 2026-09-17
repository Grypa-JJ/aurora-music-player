package com.aurora.player.di

import com.aurora.player.BuildConfig
import com.aurora.player.sync.AuthRepository
import com.aurora.player.sync.SupabaseAuthRepository
import com.aurora.player.sync.SupabaseConfig
import com.aurora.player.sync.createAuroraSupabaseClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

/**
 * Etap 36: konta + sync — `SUPABASE_URL`/`SUPABASE_ANON_KEY` z `BuildConfig` (wstrzyknięte z
 * local.properties w app/build.gradle.kts, patrz komentarz tam). `:core:sync` to jedyny moduł w
 * projekcie bez adnotacji Hilt (jest prawdziwie multiplatformowy — dzielony z desktopem, który
 * nie ma Hilta) — wiązanie robimy tu ręcznie, dokładnie tym samym wzorcem co
 * `DatabaseModule.provideAuroraDatabase` dla Roomowego `Room.databaseBuilder`.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient =
        createAuroraSupabaseClient(
            SupabaseConfig(url = BuildConfig.SUPABASE_URL, anonKey = BuildConfig.SUPABASE_ANON_KEY),
        )

    @Provides
    @Singleton
    fun provideAuthRepository(client: SupabaseClient): AuthRepository = SupabaseAuthRepository(client)
}
