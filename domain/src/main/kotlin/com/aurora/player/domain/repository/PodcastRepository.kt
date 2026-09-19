package com.aurora.player.domain.repository

import com.aurora.player.domain.model.Podcast
import com.aurora.player.domain.model.PodcastEpisode
import kotlinx.coroutines.flow.StateFlow

interface PodcastRepository {
    val subscriptions: StateFlow<List<Podcast>>

    /** Pobiera i parsuje RSS pod [feedUrl], zapisuje jako subskrypcję. `null` gdy feed nieprawidłowy. */
    suspend fun subscribeByFeedUrl(feedUrl: String): Podcast?
    suspend fun unsubscribe(feedUrl: String)

    /** Świeże parsowanie RSS przy każdym wejściu — patrz DESIGN.md Etap 32 (świadomie bez cache'u odcinków). */
    suspend fun fetchEpisodes(feedUrl: String): List<PodcastEpisode>

    /**
     * Czy JAKIKOLWIEK odcinek tego feedu publikuje `<podcast:transcript>` — DESIGN.md Etap 54,
     * zgłoszenie: "promuj podcasty z transkrypcją, pokaż to już w wyszukiwaniu". Wynik
     * cache'owany w pamięci procesu per `feedUrl` (rzadko się zmienia) — patrz implementacja.
     */
    suspend fun feedHasTranscript(feedUrl: String): Boolean

    suspend fun getPlaybackPosition(trackId: Long): Long
    suspend fun savePlaybackPosition(trackId: Long, positionMs: Long)
}
