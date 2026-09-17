package com.aurora.player.domain.repository

import com.aurora.player.domain.model.IndependentTrack

/**
 * "Muzyka niezależna" w UI (Jamendo pod spodem) — katalog Creative Commons, klucz aplikacji
 * wbudowany na stałe przez BuildConfig (patrz app/build.gradle.kts), nie wpisywany przez usera.
 */
interface IndependentMusicRepository {
    suspend fun search(query: String, limit: Int = 40): List<IndependentTrack>
    suspend fun tracksByTag(tag: String, limit: Int = 40): List<IndependentTrack>

    /** Domyślna, niepusta lista przy wejściu w ekran — ten sam wzorzec co Radio/Podcasty "top". */
    suspend fun trending(limit: Int = 40): List<IndependentTrack>
}
