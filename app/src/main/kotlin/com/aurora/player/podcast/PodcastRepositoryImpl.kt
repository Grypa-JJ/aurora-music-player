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

    /**
     * Duże feedy (np. JRE: ~5MB/2700+ odcinków, gzipowane) czasem obrywają przejściowym błędem
     * sieci w trakcie ściągania (np. `DataFormatException: invalid block type` — urwany/uszkodzony
     * strumień gzip), nie tylko przy złym URL-u. Bez retry jeden taki zonk = trwałe "Brak odcinków"
     * mimo że feed jest sprawny (zweryfikowane ręcznie — ten sam feed, ten sam parser, drugie
     * podejście się udaje). 3 próby, krótki odstęp — już na wątku IO, więc blokujące `Thread.sleep`
     * jest tu tak samo bezpieczne jak reszta tego pliku.
     */
    private fun fetchXml(url: String): String? {
        var lastError: Exception? = null
        repeat(MAX_FETCH_ATTEMPTS) { attempt ->
            try {
                val request = Request.Builder().url(url).header("User-Agent", "AuroraMusicPlayer/1.0").build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "fetchXml(): HTTP ${response.code} dla $url (próba ${attempt + 1})")
                    } else {
                        return response.body?.string()
                    }
                }
            } catch (e: Exception) {
                lastError = e
                Log.e(TAG, "fetchXml(): błąd sieci dla $url (próba ${attempt + 1}/$MAX_FETCH_ATTEMPTS)", e)
            }
            if (attempt < MAX_FETCH_ATTEMPTS - 1) Thread.sleep(FETCH_RETRY_DELAY_MS)
        }
        lastError?.let { Log.e(TAG, "fetchXml(): wszystkie próby nieudane dla $url", it) }
        return null
    }

    private fun PodcastSubscriptionEntity.toPodcast() = Podcast(feedUrl, title, author, artworkUrl, description)

    private companion object {
        const val TAG = "PodcastRepositoryImpl"
        const val MAX_FETCH_ATTEMPTS = 3
        const val FETCH_RETRY_DELAY_MS = 500L
    }
}
