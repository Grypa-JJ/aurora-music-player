package com.aurora.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aurora.player.di.ApplicationScope
import com.aurora.player.domain.model.PlaybackState
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.repository.PlaybackHistoryRepository
import com.aurora.player.domain.repository.PlayerRepository
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementacja [PlayerRepository] — UI nie trzyma ExoPlayera bezpośrednio, tylko
 * [MediaController] połączony z [PlaybackService] (MediaSessionService). Dzięki temu
 * odtwarzanie przeżywa zamknięcie ekranu i ma powiadomienie/kontrolki systemowe za darmo.
 * Patrz DESIGN.md etap 1.
 *
 * Etap 3: prawdziwa kolejka (nie tylko jeden utwór na raz) — potrzebna do Instant Mix z Genius.
 * Każde przejście między utworami (naturalny koniec, next/prev, wybór nowego utworu z listy)
 * przechodzi przez [Player.Listener.onMediaItemTransition] i zgłasza zakończone odtworzenie
 * do [PlaybackHistoryRepository] — to zamyka pętlę uczenia Genius (DESIGN.md sekcja 5.4).
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val playbackHistoryRepository: PlaybackHistoryRepository,
) : PlayerRepository {

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState

    private var controller: MediaController? = null
    private var pendingQueue: Pair<List<Track>, Int>? = null
    private var positionTickerJob: Job? = null

    private var currentQueue: List<Track> = emptyList()
    private var lastKnownPositionMs: Long = 0L

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener(
            {
                val mediaController = future.get()
                controller = mediaController
                attachListener(mediaController)
                pendingQueue?.let { (tracks, startIndex) -> playQueue(tracks, startIndex) }
                pendingQueue = null
            },
            MoreExecutors.directExecutor(),
        )
    }

    private fun attachListener(mediaController: MediaController) {
        mediaController.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startPositionTicker() else positionTickerJob?.cancel()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val previousTrack = _playbackState.value.currentTrack
                val uri = mediaItem?.localConfiguration?.uri?.toString()
                val newTrack = currentQueue.find { it.uri == uri }

                if (previousTrack != null) {
                    val forcedCompleted = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                    val playedMs = if (forcedCompleted) previousTrack.durationMs else lastKnownPositionMs
                    scope.launch {
                        playbackHistoryRepository.recordPlaybackEnded(
                            trackId = previousTrack.id,
                            playedMs = playedMs,
                            durationMs = previousTrack.durationMs,
                        )
                    }
                    if (newTrack != null) {
                        scope.launch {
                            playbackHistoryRepository.recordTransition(
                                fromTrackId = previousTrack.id,
                                toTrackId = newTrack.id,
                                fromPlayedMs = playedMs,
                                fromDurationMs = previousTrack.durationMs,
                            )
                        }
                    }
                }

                lastKnownPositionMs = 0L
                _playbackState.update {
                    it.copy(currentTrack = newTrack ?: it.currentTrack, positionMs = 0L, durationMs = 0L)
                }
            }
        })
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            while (isActive) {
                val current = controller
                if (current == null || !current.isPlaying) break
                lastKnownPositionMs = current.currentPosition
                val liveDurationMs = current.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
                _playbackState.update {
                    it.copy(positionMs = current.currentPosition, durationMs = liveDurationMs)
                }
                delay(300)
            }
        }
    }

    override fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val mediaController = controller
        if (mediaController == null) {
            pendingQueue = tracks to startIndex
            return
        }
        currentQueue = tracks
        _playbackState.update { it.copy(queue = currentQueue) }
        mediaController.setMediaItems(tracks.map { MediaItem.fromUri(it.uri) }, startIndex, 0L)
        mediaController.prepare()
        mediaController.play()
        startPositionTicker()
    }

    override fun togglePlayPause() {
        val mediaController = controller ?: return
        if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
    }

    override fun pause() {
        controller?.pause()
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _playbackState.update { it.copy(positionMs = positionMs) }
    }

    override fun skipToNext() {
        controller?.seekToNextMediaItem()
    }

    override fun skipToPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    override fun addToQueue(track: Track) {
        if (currentQueue.isEmpty()) {
            playQueue(listOf(track))
            return
        }
        val mediaController = controller ?: return
        currentQueue = currentQueue + track
        mediaController.addMediaItem(MediaItem.fromUri(track.uri))
        _playbackState.update { it.copy(queue = currentQueue) }
    }

    override fun removeFromQueue(index: Int) {
        val mediaController = controller ?: return
        if (index !in currentQueue.indices) return
        currentQueue = currentQueue.toMutableList().apply { removeAt(index) }
        mediaController.removeMediaItem(index)
        _playbackState.update { it.copy(queue = currentQueue) }
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val mediaController = controller ?: return
        if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices) return
        currentQueue = currentQueue.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        mediaController.moveMediaItem(fromIndex, toIndex)
        _playbackState.update { it.copy(queue = currentQueue) }
    }

    override fun playAt(index: Int) {
        if (index !in currentQueue.indices) return
        controller?.seekTo(index, 0L)
    }
}
