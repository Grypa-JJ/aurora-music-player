package com.aurora.player.cloud

import android.content.Context
import android.util.Log
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TrackSource
import com.aurora.player.domain.util.TrackIdHasher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.IOException
import java.net.URLDecoder
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Biblioteka z serwera NAS/WebDAV (Etap 12/22, DESIGN.md) — obok lokalnej (MediaStore) i Google
 * Drive. WebDAV to zwykły protokół HTTP (metoda PROPFIND = "wylistuj ten katalog", Basic Auth) —
 * ŻADNEGO OAuth/rejestracji aplikacji deweloperskiej u zewnętrznego dostawcy, w przeciwieństwie
 * do OneDrive/Dropbox (patrz DESIGN.md Etap 12) — użytkownik podaje adres serwera + login/hasło
 * bezpośrednio, appka łączy się z nim wprost.
 *
 * Świadomie NIE używa `Depth: infinity` (część serwerów WebDAV je blokuje ze względów
 * wydajnościowych/bezpieczeństwa) — zamiast tego robi własny przeszukiwanie wszerz (BFS) po
 * katalogach, jeden PROPFIND na katalog z `Depth: 1`.
 */
@Singleton
class WebDavLibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val httpClient = OkHttpClient.Builder().build()

    private val _isConnected = MutableStateFlow(WebDavCredentialStore.get(context) != null)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _serverLabel = MutableStateFlow(WebDavCredentialStore.get(context)?.serverUrl)
    val serverLabel: StateFlow<String?> = _serverLabel

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError
    fun clearLastError() {
        _lastError.value = null
    }

    /** Zapisuje dane logowania i próbuje jednego PROPFIND, żeby od razu zweryfikować że działają. */
    suspend fun connect(serverUrl: String, username: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            _lastError.value = null
            val normalizedUrl = serverUrl.trimEnd('/')
            val credentials = WebDavCredentials(normalizedUrl, username, password)
            val entries = try {
                propfind(credentials, normalizedUrl)
            } catch (e: IOException) {
                Log.e(TAG, "connect(): błąd sieci przy weryfikacji", e)
                _lastError.value = "Nie udało się połączyć z serwerem: ${e.message}"
                return@withContext false
            } catch (e: WebDavException) {
                Log.e(TAG, "connect(): serwer zwrócił błąd, kod ${e.httpCode}", e)
                _lastError.value = "Serwer odrzucił połączenie (kod ${e.httpCode}) — sprawdź adres/login/hasło."
                return@withContext false
            }
            if (entries == null) {
                _lastError.value = "Serwer nie zwrócił poprawnej odpowiedzi WebDAV (PROPFIND)."
                return@withContext false
            }
            WebDavCredentialStore.save(context, credentials)
            _isConnected.value = true
            _serverLabel.value = normalizedUrl
            true
        }

    fun disconnect() {
        WebDavCredentialStore.clear(context)
        _isConnected.value = false
        _serverLabel.value = null
    }

    /** Utwory audio z serwera, przeszukane rekurencyjnie (BFS); pusta lista gdy niepołączony. */
    suspend fun refreshTracks(): List<Track> = withContext(Dispatchers.IO) {
        val credentials = WebDavCredentialStore.get(context) ?: return@withContext emptyList()
        val tracks = mutableListOf<Track>()
        val queue = ArrayDeque<String>()
        queue.add(credentials.serverUrl)
        var visitedDirectories = 0

        while (queue.isNotEmpty() && visitedDirectories < MAX_DIRECTORIES && tracks.size < MAX_FILES) {
            val dirUrl = queue.removeFirst()
            visitedDirectories++
            val entries = try {
                propfind(credentials, dirUrl)
            } catch (e: Exception) {
                Log.e(TAG, "refreshTracks(): PROPFIND nieudany dla $dirUrl, pomijam katalog", e)
                _lastError.value = "Błąd odczytu katalogu na serwerze: ${e.message}"
                continue
            } ?: continue

            for (entry in entries) {
                if (entry.isCollection) {
                    queue.add(entry.href)
                } else if (isAudioFile(entry.href)) {
                    tracks += entry.toTrack(credentials.serverUrl)
                }
            }
        }
        tracks
    }

    private fun isAudioFile(href: String): Boolean {
        val lower = href.lowercase()
        return AUDIO_EXTENSIONS.any { lower.endsWith(it) }
    }

    private fun WebDavEntry.toTrack(serverUrl: String): Track {
        val fileName = URLDecoder.decode(href.substringAfterLast('/'), "UTF-8")
        val displayName = fileName.substringBeforeLast('.')
        return Track(
            id = TrackIdHasher.deriveId(SOURCE_DISCRIMINATOR, href),
            uri = resolveUrl(serverUrl, href),
            title = displayName,
            artist = "NAS/WebDAV",
            album = serverUrl.substringAfter("://").substringBefore('/'),
            genre = null,
            year = null,
            durationMs = 0L,
            dateAddedMs = 0L,
            albumArtUri = null,
            source = TrackSource.WEBDAV,
        )
    }

    /** [href] z odpowiedzi PROPFIND bywa ścieżką bezwzględną (np. `/muzyka/a.mp3`) — doklej hosta. */
    private fun resolveUrl(serverUrl: String, href: String): String =
        if (href.startsWith("http://") || href.startsWith("https://")) {
            href
        } else {
            val base = serverUrl.substringBefore("://")
            val host = serverUrl.substringAfter("://").substringBefore('/')
            "$base://$host$href"
        }

    /** Jeden PROPFIND (Depth: 1) na [dirUrl]; `null` gdy odpowiedź nie sparsowała się poprawnie. */
    private fun propfind(credentials: WebDavCredentials, dirUrl: String): List<WebDavEntry>? {
        val body = PROPFIND_BODY.toRequestBody("application/xml".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(dirUrl)
            .method("PROPFIND", body)
            .header("Depth", "1")
            .header("Authorization", Credentials.basic(credentials.username, credentials.password))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw WebDavException(response.code)
            }
            val xml = response.body?.string() ?: return null
            return parseMultistatus(xml)
        }
    }

    private fun parseMultistatus(xml: String): List<WebDavEntry> {
        // `namespaceAware = true` jest KONIECZNE dla `getElementsByTagNameNS` poniżej — serwery
        // WebDAV różnie prefiksują namespace DAV: (D:, d:, czasem brak prefiksu), a bez tego
        // ustawienia parser w ogóle nie rozróżnia namespace'ów i dopasowanie przez "*" nic nie
        // znajdzie.
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val responseNodes = document.getElementsByTagNameNS("*", "response")
        val entries = mutableListOf<WebDavEntry>()
        for (i in 0 until responseNodes.length) {
            val responseElement = responseNodes.item(i) as? Element ?: continue
            val href = responseElement.firstElementByLocalName("href")?.textContent?.trim() ?: continue
            val isCollection = responseElement.firstElementByLocalName("resourcetype")
                ?.firstElementByLocalName("collection") != null
            entries += WebDavEntry(href, isCollection)
        }
        // Pierwszy wpis w multistatus to zwykle sam katalog nadrzędny (self) — pomiń go, inaczej
        // BFS wpadłby w nieskończoną pętlę odwiedzając ten sam katalog w kółko.
        return entries.drop(1)
    }

    private fun Element.firstElementByLocalName(localName: String): Element? {
        val nodes = getElementsByTagNameNS("*", localName)
        return if (nodes.length > 0) nodes.item(0) as? Element else null
    }

    private data class WebDavEntry(val href: String, val isCollection: Boolean)

    private class WebDavException(val httpCode: Int) : IOException("HTTP $httpCode")

    private companion object {
        const val TAG = "WebDavLibraryRepo"
        const val SOURCE_DISCRIMINATOR = "webdav"
        const val MAX_DIRECTORIES = 500
        const val MAX_FILES = 5000
        val AUDIO_EXTENSIONS = listOf(".mp3", ".flac", ".wav", ".ogg", ".m4a", ".aac", ".opus")
        const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:">
  <D:prop>
    <D:resourcetype/>
    <D:getcontentlength/>
  </D:prop>
</D:propfind>"""
    }
}
