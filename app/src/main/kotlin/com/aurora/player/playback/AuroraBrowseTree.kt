package com.aurora.player.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants
import com.aurora.player.domain.model.GeniusMix
import com.aurora.player.domain.model.Podcast
import com.aurora.player.domain.model.PodcastEpisode
import com.aurora.player.domain.repository.FavoritesRepository
import com.aurora.player.domain.repository.GeniusRepository
import com.aurora.player.domain.repository.PlaybackHistoryRepository
import com.aurora.player.domain.repository.PlaylistRepository
import com.aurora.player.domain.repository.PodcastRepository
import com.aurora.player.domain.repository.TrackRepository
import com.aurora.player.library.groupTracksByAlbum
import com.aurora.player.library.groupTracksByArtist
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** mediaId-y drzewa przeglądania Android Auto — patrz DESIGN.md sekcja 6. */
object AuroraMediaIds {
    const val ROOT = "root"
    const val GENIUS_ROOT = "genius_root"
    const val LIBRARY_ROOT = "library_root"
    const val LIBRARY_FAVORITES = "library_favorites"
    const val LIBRARY_TRACKS = "library_tracks"
    const val LIBRARY_ALBUMS = "library_albums"
    const val LIBRARY_ARTISTS = "library_artists"
    const val PLAYLISTS_ROOT = "playlists_root"
    const val PODCASTS_ROOT = "podcasts_root"

    const val GENIUS_MIX_PREFIX = "genius_mix:"
    const val LIBRARY_ALBUM_PREFIX = "library_album:"
    const val LIBRARY_ARTIST_PREFIX = "library_artist:"
    const val PLAYLIST_PREFIX = "playlist:"
    const val PODCAST_PREFIX = "podcast:"
    const val PODCAST_EPISODE_PREFIX = "podcast_episode:"
}

private fun encodeKey(value: String): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

private fun decodeKey(value: String): String =
    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)

/**
 * Buduje drzewo `MediaItem` dla Android Auto z tych samych repozytoriów domenowych, których
 * używa reszta apki (żadnej nowej logiki biznesowej) — patrz DESIGN.md sekcja 6. Wszystko liczone
 * z lokalnego Room/cache, bez sieci poza podcastami (RSS na żywo, tak jak w [PodcastRepository]),
 * więc `onGetChildren` odpowiada natychmiast.
 *
 * Genius mixy są cache'owane w pamięci procesu przez cały czas życia serwisu — [GeniusRepository]
 * je losowo/heurystycznie generuje, a lista pokazana userowi (`onGetChildren`) musi być IDENTYCZNA
 * z tą, którą [resolveGeniusMix] odnajdzie po tapnięciu (`onAddMediaItems`), inaczej tap odtworzy
 * inny miks niż widoczny na kafelku.
 */
@Singleton
class AuroraBrowseTree @Inject constructor(
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
    private val geniusRepository: GeniusRepository,
    private val favoritesRepository: FavoritesRepository,
    private val podcastRepository: PodcastRepository,
    private val playbackHistoryRepository: PlaybackHistoryRepository,
) {
    @Volatile
    private var cachedGeniusMixes: List<GeniusMix>? = null

    fun rootItem(): MediaItem = folderItem(
        id = AuroraMediaIds.ROOT,
        title = "Aurora",
        browsableStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM,
    )

    suspend fun item(mediaId: String): MediaItem? = when {
        mediaId == AuroraMediaIds.ROOT -> rootItem()
        mediaId == AuroraMediaIds.GENIUS_ROOT -> folderItem(mediaId, "Genius", playable = false)
        mediaId == AuroraMediaIds.LIBRARY_ROOT -> folderItem(mediaId, "Biblioteka")
        mediaId == AuroraMediaIds.LIBRARY_FAVORITES -> folderItem(mediaId, "Ulubione")
        mediaId == AuroraMediaIds.LIBRARY_TRACKS -> folderItem(mediaId, "Utwory")
        mediaId == AuroraMediaIds.LIBRARY_ALBUMS -> folderItem(mediaId, "Albumy")
        mediaId == AuroraMediaIds.LIBRARY_ARTISTS -> folderItem(mediaId, "Wykonawcy")
        mediaId == AuroraMediaIds.PLAYLISTS_ROOT -> folderItem(mediaId, "Playlisty")
        mediaId == AuroraMediaIds.PODCASTS_ROOT -> folderItem(mediaId, "Podcasty")
        else -> children(mediaId)?.let { folderItem(mediaId, mediaId) }
    }

    /** `null` = nieznany rodzic (błąd), pusta lista = znany, ale bez zawartości. */
    suspend fun children(parentId: String): List<MediaItem>? = when {
        parentId == AuroraMediaIds.ROOT -> rootChildren()
        parentId == AuroraMediaIds.GENIUS_ROOT -> geniusChildren()
        parentId == AuroraMediaIds.LIBRARY_ROOT -> libraryChildren()
        parentId == AuroraMediaIds.LIBRARY_FAVORITES -> favoriteTrackItems()
        parentId == AuroraMediaIds.LIBRARY_TRACKS -> allTrackItems()
        parentId == AuroraMediaIds.LIBRARY_ALBUMS -> albumItems()
        parentId == AuroraMediaIds.LIBRARY_ARTISTS -> artistItems()
        parentId == AuroraMediaIds.PLAYLISTS_ROOT -> playlistItems()
        parentId == AuroraMediaIds.PODCASTS_ROOT -> podcastItems()
        parentId.startsWith(AuroraMediaIds.LIBRARY_ALBUM_PREFIX) -> albumTrackItems(parentId)
        parentId.startsWith(AuroraMediaIds.LIBRARY_ARTIST_PREFIX) -> artistTrackItems(parentId)
        parentId.startsWith(AuroraMediaIds.PLAYLIST_PREFIX) -> playlistTrackItems(parentId)
        parentId.startsWith(AuroraMediaIds.PODCAST_PREFIX) -> podcastEpisodeItems(parentId)
        else -> null
    }

    /** Lokalne wyszukiwanie tekstowe/głosowe po tytule/wykonawcy/albumie. */
    suspend fun search(query: String): List<MediaItem> {
        if (query.isBlank()) return emptyList()
        val needle = query.trim()
        return trackRepository.getAllTracks()
            .filter {
                it.title.contains(needle, ignoreCase = true) ||
                    it.artist.contains(needle, ignoreCase = true) ||
                    it.album.contains(needle, ignoreCase = true)
            }
            .take(50)
            .map { it.toMediaItem() }
    }

    /** Rozwiązuje kafelek Genius Mixa na pełną, odtwarzalną kolejkę — patrz DESIGN.md sekcja 6. */
    suspend fun resolveGeniusMix(mediaId: String): List<MediaItem>? {
        val index = mediaId.removePrefix(AuroraMediaIds.GENIUS_MIX_PREFIX).toIntOrNull() ?: return null
        val mixes = geniusMixes()
        return mixes.getOrNull(index)?.tracks?.map { it.toMediaItem() }
    }

    private suspend fun geniusMixes() =
        cachedGeniusMixes ?: geniusRepository.generateGeniusMixes().also { cachedGeniusMixes = it }

    private fun rootChildren(): List<MediaItem> = listOf(
        folderItem(AuroraMediaIds.GENIUS_ROOT, "Genius", browsableStyle = GRID, playable = false),
        folderItem(AuroraMediaIds.LIBRARY_ROOT, "Biblioteka"),
        folderItem(AuroraMediaIds.PLAYLISTS_ROOT, "Playlisty", browsableStyle = GRID),
        folderItem(AuroraMediaIds.PODCASTS_ROOT, "Podcasty", browsableStyle = GRID),
    )

    private suspend fun geniusChildren(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        playbackHistoryRepository.getLastPlayedTrackId()?.let { lastTrackId ->
            trackRepository.getAllTracks().find { it.id == lastTrackId }?.let { track ->
                items += track.copy(title = "Kontynuuj: ${track.title}").toMediaItem()
            }
        }
        items += geniusMixes().mapIndexed { index, mix ->
            folderItem(
                id = "${AuroraMediaIds.GENIUS_MIX_PREFIX}$index",
                title = mix.name,
                artworkUri = mix.tracks.firstOrNull { it.albumArtUri != null }?.albumArtUri,
                playable = true,
            )
        }
        return items
    }

    private fun libraryChildren(): List<MediaItem> = listOf(
        folderItem(AuroraMediaIds.LIBRARY_FAVORITES, "Ulubione"),
        folderItem(AuroraMediaIds.LIBRARY_TRACKS, "Utwory"),
        folderItem(AuroraMediaIds.LIBRARY_ALBUMS, "Albumy", browsableStyle = GRID),
        folderItem(AuroraMediaIds.LIBRARY_ARTISTS, "Wykonawcy"),
    )

    private suspend fun favoriteTrackItems(): List<MediaItem> {
        val favoriteIds = favoritesRepository.favoriteTrackIds.value
        return trackRepository.getAllTracks()
            .filter { it.id in favoriteIds }
            .sortedBy { it.title.lowercase() }
            .map { it.toMediaItem() }
    }

    private suspend fun allTrackItems(): List<MediaItem> =
        trackRepository.getAllTracks().sortedBy { it.title.lowercase() }.map { it.toMediaItem() }

    private suspend fun albumItems(): List<MediaItem> =
        groupTracksByAlbum(trackRepository.getAllTracks()).map { group ->
            folderItem(
                id = AuroraMediaIds.LIBRARY_ALBUM_PREFIX + encodeKey("${group.name}|||${group.artist}"),
                title = group.name,
                subtitle = group.artist,
                artworkUri = group.albumArtUri,
            )
        }

    private suspend fun albumTrackItems(parentId: String): List<MediaItem>? {
        val key = decodeKey(parentId.removePrefix(AuroraMediaIds.LIBRARY_ALBUM_PREFIX))
        val (name, artist) = key.split("|||", limit = 2).let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
        return groupTracksByAlbum(trackRepository.getAllTracks())
            .find { it.name == name && it.artist == artist }
            ?.tracks
            ?.map { it.toMediaItem() }
    }

    private suspend fun artistItems(): List<MediaItem> =
        groupTracksByArtist(trackRepository.getAllTracks()).map { group ->
            folderItem(
                id = AuroraMediaIds.LIBRARY_ARTIST_PREFIX + encodeKey(group.name),
                title = group.name,
            )
        }

    private suspend fun artistTrackItems(parentId: String): List<MediaItem>? {
        val name = decodeKey(parentId.removePrefix(AuroraMediaIds.LIBRARY_ARTIST_PREFIX))
        return groupTracksByArtist(trackRepository.getAllTracks())
            .find { it.name == name }
            ?.tracks
            ?.sortedBy { it.title.lowercase() }
            ?.map { it.toMediaItem() }
    }

    private fun playlistItems(): List<MediaItem> =
        playlistRepository.playlists.value.map { playlist ->
            folderItem(id = AuroraMediaIds.PLAYLIST_PREFIX + playlist.id, title = playlist.name)
        }

    private suspend fun playlistTrackItems(parentId: String): List<MediaItem>? {
        val playlistId = parentId.removePrefix(AuroraMediaIds.PLAYLIST_PREFIX).toLongOrNull() ?: return null
        val playlist = playlistRepository.playlists.value.find { it.id == playlistId } ?: return null
        val tracksById = trackRepository.getAllTracks().associateBy { it.id }
        return playlist.trackIds.mapNotNull { tracksById[it] }.map { it.toMediaItem() }
    }

    private fun podcastItems(): List<MediaItem> =
        podcastRepository.subscriptions.value.map { podcast ->
            folderItem(
                id = AuroraMediaIds.PODCAST_PREFIX + encodeKey(podcast.feedUrl),
                title = podcast.title,
                subtitle = podcast.author,
                artworkUri = podcast.artworkUrl,
            )
        }

    private suspend fun podcastEpisodeItems(parentId: String): List<MediaItem>? {
        val feedUrl = decodeKey(parentId.removePrefix(AuroraMediaIds.PODCAST_PREFIX))
        val podcast = podcastRepository.subscriptions.value.find { it.feedUrl == feedUrl } ?: return null
        return podcastRepository.fetchEpisodes(feedUrl).map { it.toMediaItem(podcast) }
    }

    private fun folderItem(
        id: String,
        title: String,
        subtitle: String? = null,
        artworkUri: String? = null,
        playable: Boolean = false,
        browsableStyle: Int = LIST,
    ): MediaItem {
        val extras = Bundle().apply {
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, browsableStyle)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, browsableStyle)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(playable)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .setExtras(extras)
            .apply {
                subtitle?.let(::setSubtitle)
                artworkUri?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build()
    }

    private companion object {
        const val LIST = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        const val GRID = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
    }
}

private fun PodcastEpisode.toMediaItem(podcast: Podcast): MediaItem =
    MediaItem.Builder()
        .setMediaId(AuroraMediaIds.PODCAST_EPISODE_PREFIX + guid)
        .setUri(audioUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(podcast.title)
                .setArtworkUri(podcast.artworkUrl?.let(Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                .build(),
        )
        .build()
