package com.aurora.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aurora.player.di.ApplicationScope
import com.aurora.player.domain.model.PlaybackState
import com.aurora.player.domain.model.Track
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
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) : PlayerRepository {

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState

    private var controller: MediaController? = null
    private var pendingTrack: Track? = null
    private var positionTickerJob: Job? = null

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener(
            {
                val mediaController = future.get()
                controller = mediaController
                attachListener(mediaController)
                pendingTrack?.let { play(it) }
                pendingTrack = null
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
        })
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            while (isActive) {
                val current = controller
                if (current == null || !current.isPlaying) break
                _playbackState.update { it.copy(positionMs = current.currentPosition) }
                delay(300)
            }
        }
    }

    override fun play(track: Track) {
        val mediaController = controller
        if (mediaController == null) {
            pendingTrack = track
            return
        }
        mediaController.setMediaItem(MediaItem.fromUri(track.uri))
        mediaController.prepare()
        mediaController.play()
        _playbackState.update { it.copy(currentTrack = track, isPlaying = true, positionMs = 0L) }
        startPositionTicker()
    }

    override fun togglePlayPause() {
        val mediaController = controller ?: return
        if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _playbackState.update { it.copy(positionMs = positionMs) }
    }
}
