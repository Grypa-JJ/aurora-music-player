package com.aurora.player.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.aurora.player.domain.model.PlaybackState
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.repository.PlayerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementacja [PlayerRepository] oparta o Media3 ExoPlayer.
 *
 * Etap 0: playback w procesie aplikacji, bez MediaSessionService/powiadomienia —
 * to świadomie odłożone do etapu 1 razem z ekranem Now Playing (patrz DESIGN.md,
 * sekcja "Status implementacji"). Docelowo ten wrapper przenosi się do modułu :audio
 * i integruje z MediaSessionService.
 */
@Singleton
class PlayerController @Inject constructor(
    private val exoPlayer: ExoPlayer,
) : PlayerRepository {

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update { it.copy(isPlaying = isPlaying) }
            }
        })
    }

    override fun play(track: Track) {
        val mediaItem = MediaItem.fromUri(track.uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
        _playbackState.update { it.copy(currentTrack = track, isPlaying = true) }
    }

    override fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }
}
