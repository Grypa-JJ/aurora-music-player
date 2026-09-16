package com.aurora.player.domain.repository

import com.aurora.player.domain.model.PodcastSearchResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Wyszukiwanie w katalogach podcastów — dwa NIEZALEŻNE źródła, żeby appka zawsze miała działające
 * wyszukiwanie bez wymagania klucza API od użytkownika (patrz DESIGN.md Etap 32, decyzja usera:
 * "zróbmy obie opcje").
 */
interface PodcastCatalogRepository {
    /** iTunes Search API — publiczne, bez klucza, zawsze dostępne. */
    suspend fun searchITunes(query: String): List<PodcastSearchResult>

    /** Podcast Index — tylko gdy user skonfigurował WŁASNY klucz w ustawieniach (patrz [isPodcastIndexConfigured]). */
    suspend fun searchPodcastIndex(query: String): List<PodcastSearchResult>

    val isPodcastIndexConfigured: StateFlow<Boolean>
    fun setPodcastIndexCredentials(apiKey: String, apiSecret: String)
    fun clearPodcastIndexCredentials()
}
