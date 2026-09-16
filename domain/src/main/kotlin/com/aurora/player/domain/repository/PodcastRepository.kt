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

    suspend fun getPlaybackPosition(trackId: Long): Long
    suspend fun savePlaybackPosition(trackId: Long, positionMs: Long)
}
