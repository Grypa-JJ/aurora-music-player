package com.aurora.player.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Adres i klucz anon projektu Supabase — NIE jest to sekret produkcyjny (anon key jest z
 * założenia bezpieczny do trzymania w kliencie, cały dostęp do danych i tak idzie przez RLS
 * per-user w Postgresie), ale mimo to wstrzykiwany z lokalnej konfiguracji (local.properties na
 * Androidzie, zmienna środowiskowa na desktopie), NIE zahardkodowany w źródle — tak samo jak
 * traktowany jest klucz Podcast Index (DESIGN.md Etap 32).
 */
data class SupabaseConfig(
    val url: String,
    val anonKey: String,
)

fun createAuroraSupabaseClient(config: SupabaseConfig): SupabaseClient =
    createSupabaseClient(supabaseUrl = config.url, supabaseKey = config.anonKey) {
        install(Auth)
        install(Postgrest)
    }
