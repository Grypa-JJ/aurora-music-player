package com.aurora.player.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.aurora.player.domain.repository.FavoritesRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Custom action serca (Ulubione) w Now Playing na Android Auto — patrz DESIGN.md sekcja 6. */
const val CUSTOM_COMMAND_TOGGLE_FAVORITE = "com.aurora.player.TOGGLE_FAVORITE"

/**
 * Callback `MediaLibrarySession` dla Android Auto/Assistant — całe drzewo przeglądania deleguje
 * do [AuroraBrowseTree], czysto liczonej z lokalnych repozytoriów. Patrz DESIGN.md sekcja 6.
 *
 * Wszystkie metody są `suspend`-friendly przez [futureAsync] (Guava `SettableFuture` odpalany na
 * [scope]) — Android Auto traktuje blokujące wywołania jako zawieszenie i chowa appkę z listy.
 */
class AuroraLibrarySessionCallback(
    private val scope: CoroutineScope,
    private val browseTree: AuroraBrowseTree,
    private val favoritesRepository: FavoritesRepository,
) : MediaLibrarySession.Callback {

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val connectionResult = super.onConnect(session, controller)
        val sessionCommands = connectionResult.availableSessionCommands.buildUpon()
            .add(SessionCommand(CUSTOM_COMMAND_TOGGLE_FAVORITE, Bundle.EMPTY))
            .build()
        return MediaSession.ConnectionResult.accept(sessionCommands, connectionResult.availablePlayerCommands)
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (customCommand.customAction == CUSTOM_COMMAND_TOGGLE_FAVORITE) {
            session.player.currentMediaItem?.mediaId?.toLongOrNull()?.let { trackId ->
                scope.launch { favoritesRepository.toggleFavorite(trackId) }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        return super.onCustomCommand(session, controller, customCommand, args)
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(browseTree.rootItem(), params))

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = futureAsync {
        browseTree.item(mediaId)?.let { LibraryResult.ofItem(it, null) }
            ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = futureAsync {
        val children = browseTree.children(parentId)
        if (children == null) {
            LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        } else {
            val fromIndex = (page * pageSize).coerceIn(0, children.size)
            val toIndex = if (pageSize <= 0) children.size else (fromIndex + pageSize).coerceIn(fromIndex, children.size)
            LibraryResult.ofItemList(ImmutableList.copyOf(children.subList(fromIndex, toIndex)), params)
        }
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = futureAsync {
        val resultCount = browseTree.search(query).size
        session.notifySearchResultChanged(browser, query, resultCount, params)
        LibraryResult.ofVoid(params)
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = futureAsync {
        val results = browseTree.search(query)
        val fromIndex = (page * pageSize).coerceIn(0, results.size)
        val toIndex = if (pageSize <= 0) results.size else (fromIndex + pageSize).coerceIn(fromIndex, results.size)
        LibraryResult.ofItemList(ImmutableList.copyOf(results.subList(fromIndex, toIndex)), params)
    }

    /**
     * Rozwiązuje "wirtualne" kafelki bez własnego URI (na razie tylko Genius Mixy — patrz
     * DESIGN.md sekcja 6) na pełną kolejkę realnych utworów. Zwykłe utwory (mają URI od razu z
     * [AuroraBrowseTree]) przechodzą bez zmian.
     */
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> = futureAsync {
        mediaItems.flatMap { item ->
            if (item.mediaId.startsWith(AuroraMediaIds.GENIUS_MIX_PREFIX)) {
                browseTree.resolveGeniusMix(item.mediaId) ?: listOf(item)
            } else {
                listOf(item)
            }
        }.toMutableList()
    }

    private fun <T> futureAsync(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope.launch {
            try {
                future.set(block())
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }
}
