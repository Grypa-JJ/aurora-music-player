package com.aurora.player.podcast

import android.util.Log
import com.aurora.player.data.database.dao.PodcastDao
import com.aurora.player.data.database.entity.PodcastPlaybackPositionEntity
import com.aurora.player.data.database.entity.PodcastSubscriptionEntity
import com.aurora.player.di.ApplicationScope
import com.aurora.player.domain.model.Podcast
import com.aurora.player.domain.model.PodcastEpisode
import com.aurora.player.domain.repository.PodcastRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Subskrypcje + odcinki podkastów — DESIGN.md Etap 32. `:data` moduł dostarcza [PodcastDao]
 * (Hilt), ale samo parsowanie RSS/HTTP żyje tutaj w `:app` (tak jak WebDavLibraryRepository) —
 * `:data` nie ma OkHttp jako zależności, `:app` ma.
 */
@Singleton
class PodcastRepositoryImpl @Inject constructor(
    private val podcastDao: PodcastDao,
    @ApplicationScope scope: CoroutineScope,
) : PodcastRepository {

    private val httpClient = OkHttpClient.Builder().build()

    override val subscriptions: StateFlow<List<Podcast>> = podcastDao.observeSubscriptions()
        .map { entities -> entities.map { it.toPodcast() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun subscribeByFeedUrl(feedUrl: String): Podcast? = withContext(Dispatchers.IO) {
        val xml = fetchXml(feedUrl.trim()) ?: return@withContext null
        val podcast = PodcastRssParser.parseChannel(feedUrl.trim(), xml) ?: return@withContext null
        podcastDao.insertSubscription(
            PodcastSubscriptionEntity(
                feedUrl = podcast.feedUrl,
                title = podcast.title,
                author = podcast.author,
                artworkUrl = podcast.artworkUrl,
                description = podcast.description,
                addedAtMs = System.currentTimeMillis(),
            ),
        )
        podcast
    }

    override suspend fun unsubscribe(feedUrl: String) {
        podcastDao.deleteSubscription(feedUrl)
    }

    override suspend fun fetchEpisodes(feedUrl: String): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        val xml = fetchXml(feedUrl) ?: return@withContext emptyList()
        PodcastRssParser.parseEpisodes(feedUrl, xml)
    }

    override suspend fun getPlaybackPosition(trackId: Long): Long =
        podcastDao.getPlaybackPosition(trackId) ?: 0L

    override suspend fun savePlaybackPosition(trackId: Long, positionMs: Long) {
        podcastDao.upsertPlaybackPosition(
            PodcastPlaybackPositionEntity(trackId, positionMs, System.currentTimeMillis()),
        )
    }

    private fun fetchXml(url: String): String? = try {
        val request = Request.Builder().url(url).header("User-Agent", "AuroraMusicPlayer/1.0").build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "fetchXml(): HTTP ${response.code} dla $url")
                null
            } else {
                response.body?.string()
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "fetchXml(): błąd sieci dla $url", e)
        null
    }

    private fun PodcastSubscriptionEntity.toPodcast() = Podcast(feedUrl, title, author, artworkUrl, description)

    private companion object {
        const val TAG = "PodcastRepositoryImpl"
    }
}
