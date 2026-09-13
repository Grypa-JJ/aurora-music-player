package com.aurora.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aurora.player.MainActivity
import com.aurora.player.domain.repository.EqRepository
import com.aurora.player.eq.EqualizerAudioProcessor
import com.aurora.player.eq.EqualizerRenderersFactory
import com.aurora.player.visualizer.AudioVisualizerAnalyzer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Odtwarzanie w tle + powiadomienie z kontrolkami (MediaSession) — patrz DESIGN.md etap 1.
 * UI (PlayerController) łączy się z tym serwisem przez MediaController, nie trzyma
 * własnego ExoPlayera — dzięki temu muzyka gra dalej po zamknięciu ekranu/aplikacji.
 *
 * @AndroidEntryPoint, bo silnik EQ ([EqualizerAudioProcessor]) musi czytać ten sam
 * [EqRepository] (Hilt singleton) co ekran equalizera — patrz DESIGN.md sekcja 4. Ten sam
 * mechanizm zasila [AudioVisualizerAnalyzer] danymi do wizualizera widmowego.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var eqRepository: EqRepository

    @Inject
    lateinit var visualizerAnalyzer: AudioVisualizerAnalyzer

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val equalizerAudioProcessor = EqualizerAudioProcessor(eqRepository, visualizerAnalyzer)
        val renderersFactory = EqualizerRenderersFactory(this, equalizerAudioProcessor)
        val player = ExoPlayer.Builder(this, renderersFactory).build()

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = mediaSession ?: return
        if (!session.player.playWhenReady || session.player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
