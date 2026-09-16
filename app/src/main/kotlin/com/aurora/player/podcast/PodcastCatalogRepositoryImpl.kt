package com.aurora.player.podcast

import android.content.Context
import android.util.Log
import com.aurora.player.domain.model.PodcastSearchResult
import com.aurora.player.domain.model.PodcastSearchSource
import com.aurora.player.domain.repository.PodcastCatalogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dwa NIEZALEŻNE katalogi wyszukiwania podcastów — DESIGN.md Etap 32. iTunes Search zawsze
 * działa (publiczne, bez klucza); Podcast Index tylko gdy user poda WŁASNY klucz — patrz
 * [PodcastIndexCredentialStore] po uzasadnienie, czemu appka nigdy nie trzyma jednego wspólnego.
 */
@Singleton
class PodcastCatalogRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PodcastCatalogRepository {

    private val httpClient = OkHttpClient.Builder().build()

    private val _isPodcastIndexConfigured = MutableStateFlow(PodcastIndexCredentialStore.get(context) != null)
    override val isPodcastIndexConfigured: StateFlow<Boolean> = _isPodcastIndexConfigured

    override fun setPodcastIndexCredentials(apiKey: String, apiSecret: String) {
        PodcastIndexCredentialStore.save(context, PodcastIndexCredentials(apiKey, apiSecret))
        _isPodcastIndexConfigured.value = true
    }

    override fun clearPodcastIndexCredentials() {
        PodcastIndexCredentialStore.clear(context)
        _isPodcastIndexConfigured.value = false
    }

    override suspend fun searchITunes(query: String): List<PodcastSearchResult> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://itunes.apple.com/search?media=podcast&limit=25&term=$encoded"
        val json = get(url) ?: return@withContext emptyList()
        try {
            val results = org.json.JSONObject(json).optJSONArray("results") ?: JSONArray()
            (0 until results.length()).mapNotNull { i ->
                val obj = results.getJSONObject(i)
                val feedUrl = obj.optString("feedUrl").ifBlank { return@mapNotNull null }
                PodcastSearchResult(
                    feedUrl = feedUrl,
                    title = obj.optString("collectionName").ifBlank { "Bez tytułu" },
                    author = obj.optString("artistName"),
                    artworkUrl = obj.optString("artworkUrl600").ifBlank { obj.optString("artworkUrl100") }.takeIf { it.isNotBlank() },
                    source = PodcastSearchSource.ITUNES,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchITunes(): parsowanie nieudane", e)
            emptyList()
        }
    }

    override suspend fun searchPodcastIndex(query: String): List<PodcastSearchResult> = withContext(Dispatchers.IO) {
        val credentials = PodcastIndexCredentialStore.get(context) ?: return@withContext emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.podcastindex.org/api/1.0/search/byterm?q=$encoded"

        val unixTime = (System.currentTimeMillis() / 1000).toString()
        val authHash = sha1Hex(credentials.apiKey + credentials.apiSecret + unixTime)

        val request = Request.Builder()
            .url(url)
            .header("X-Auth-Date", unixTime)
            .header("X-Auth-Key", credentials.apiKey)
            .header("Authorization", authHash)
            .header("User-Agent", "AuroraMusicPlayer/1.0")
            .build()

        val json = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "searchPodcastIndex(): HTTP ${response.code}")
                    null
                } else {
                    response.body?.string()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchPodcastIndex(): błąd sieci", e)
            null
        } ?: return@withContext emptyList()

        try {
            val feeds = org.json.JSONObject(json).optJSONArray("feeds") ?: JSONArray()
            (0 until feeds.length()).mapNotNull { i ->
                val obj = feeds.getJSONObject(i)
                val feedUrl = obj.optString("url").ifBlank { return@mapNotNull null }
                PodcastSearchResult(
                    feedUrl = feedUrl,
                    title = obj.optString("title").ifBlank { "Bez tytułu" },
                    author = obj.optString("author"),
                    artworkUrl = obj.optString("image").ifBlank { obj.optString("artwork") }.takeIf { it.isNotBlank() },
                    source = PodcastSearchSource.PODCAST_INDEX,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchPodcastIndex(): parsowanie nieudane", e)
            emptyList()
        }
    }

    override suspend fun topPodcastsByCountry(countryCode: String, limit: Int): List<PodcastSearchResult> =
        withContext(Dispatchers.IO) {
            // Apple "Top Charts" (rss.applemarketingtools.com) — darmowe, bez klucza, ale daje
            // tylko numeryczne ID kolekcji w kolejności rankingu, NIE realny `feedUrl`. Trzeba
            // dociągnąć iTunes Lookup (ten sam endpoint co searchITunes) jednym zapytaniem
            // wsadowym po przecinku, więc to nadal dwa zapytania sieciowe na jedno odświeżenie.
            val chartJson = get("https://rss.applemarketingtools.com/api/v2/${countryCode.lowercase()}/podcasts/top/$limit/podcasts.json")
                ?: return@withContext emptyList()
            val rankedIds = try {
                val results = org.json.JSONObject(chartJson).optJSONObject("feed")?.optJSONArray("results") ?: JSONArray()
                (0 until results.length()).map { i -> results.getJSONObject(i).optString("id") }.filter { it.isNotBlank() }
            } catch (e: Exception) {
                Log.e(TAG, "topPodcastsByCountry(): parsowanie listy top nieudane", e)
                return@withContext emptyList()
            }
            if (rankedIds.isEmpty()) return@withContext emptyList()

            val lookupJson = get("https://itunes.apple.com/lookup?id=${rankedIds.joinToString(",")}&entity=podcast")
                ?: return@withContext emptyList()
            val resultById = try {
                val results = org.json.JSONObject(lookupJson).optJSONArray("results") ?: JSONArray()
                (0 until results.length()).mapNotNull { i ->
                    val obj = results.getJSONObject(i)
                    val feedUrl = obj.optString("feedUrl").ifBlank { return@mapNotNull null }
                    val id = obj.optString("collectionId")
                    id to PodcastSearchResult(
                        feedUrl = feedUrl,
                        title = obj.optString("collectionName").ifBlank { "Bez tytułu" },
                        author = obj.optString("artistName"),
                        artworkUrl = obj.optString("artworkUrl600").ifBlank { obj.optString("artworkUrl100") }.takeIf { it.isNotBlank() },
                        source = PodcastSearchSource.ITUNES,
                    )
                }.toMap()
            } catch (e: Exception) {
                Log.e(TAG, "topPodcastsByCountry(): parsowanie lookup nieudane", e)
                return@withContext emptyList()
            }
            // Zachowaj kolejność rankingu z listy TOP — odpowiedź Lookup NIE gwarantuje kolejności.
            rankedIds.mapNotNull { resultById[it] }
        }

    private fun get(url: String): String? = try {
        val request = Request.Builder().url(url).header("User-Agent", "AuroraMusicPlayer/1.0").build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    } catch (e: Exception) {
        Log.e(TAG, "get(): błąd sieci dla $url", e)
        null
    }

    private fun sha1Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "PodcastCatalogRepo"
    }
}
