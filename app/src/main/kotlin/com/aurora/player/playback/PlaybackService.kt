package com.aurora.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.aurora.player.MainActivity
import com.aurora.player.R
import com.aurora.player.cloud.AuthenticatingHttpDataSourceFactory
import com.aurora.player.cloud.WebDavCredentialStore
import com.aurora.player.di.ApplicationScope
import com.aurora.player.domain.audio.AudioOutput
import com.aurora.player.domain.repository.CloudLibraryRepository
import com.aurora.player.domain.repository.EqRepository
import com.aurora.player.domain.repository.FavoritesRepository
import com.aurora.player.eq.EqualizerAudioProcessor
import com.aurora.player.eq.EqualizerRenderersFactory
import com.aurora.player.visualizer.AudioVisualizerAnalyzer
import com.google.common.collect.ImmutableList
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Odtwarzanie w tle + powiadomienie z kontrolkami + drzewo przeglądania Android Auto
 * (`MediaLibraryService` — superset `MediaSessionService`) — patrz DESIGN.md etap 1 i sekcja 6.
 * UI (PlayerController) łączy się z tym serwisem przez MediaController, nie trzyma
 * własnego ExoPlayera — dzięki temu muzyka gra dalej po zamknięciu ekranu/aplikacji.
 *
 * @AndroidEntryPoint, bo silnik EQ ([EqualizerAudioProcessor]) musi czytać ten sam
 * [EqRepository] (Hilt singleton) co ekran equalizera — patrz DESIGN.md sekcja 4. Ten sam
 * mechanizm zasila [AudioVisualizerAnalyzer] danymi do wizualizera widmowego.
 */
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var eqRepository: EqRepository

    @Inject
    lateinit var visualizerAnalyzer: AudioVisualizerAnalyzer

    @Inject
    lateinit var cloudLibraryRepository: CloudLibraryRepository

    @Inject
    lateinit var audioOutput: AudioOutput

    @Inject
    lateinit var favoritesRepository: FavoritesRepository

    @Inject
    lateinit var browseTree: AuroraBrowseTree

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    private var mediaSession: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch { audioOutput.connect() }

        val equalizerAudioProcessor = EqualizerAudioProcessor(eqRepository, visualizerAnalyzer)
        val renderersFactory = EqualizerRenderersFactory(this, listOf(equalizerAudioProcessor))

        // Utwory z Google Drive i NAS/WebDAV (oba https://) idą przez jedną fabrykę, która
        // dobiera nagłówek Authorization PER-ŻĄDANIE wg hosta (Bearer token Google vs Basic Auth
        // WebDAV) — patrz komentarz w AuthenticatingHttpDataSourceFactory (Etap 12/22). Lokalne
        // (content://) obsługuje sam DefaultDataSource.Factory jak dotąd.
        val cloudHttpDataSourceFactory = AuthenticatingHttpDataSourceFactory(
            getGoogleAccessToken = { cloudLibraryRepository.currentAccessTokenBlocking() },
            getWebDavAuthHeader = { uri ->
                val credentials = WebDavCredentialStore.get(this)
                if (credentials != null && uri.host == android.net.Uri.parse(credentials.serverUrl).host) {
                    okhttp3.Credentials.basic(credentials.username, credentials.password)
                } else {
                    null
                }
            },
        )
        val dataSourceFactory = DefaultDataSource.Factory(this, cloudHttpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaLibrarySession.Builder(
            this,
            player,
            AuroraLibrarySessionCallback(applicationScope, browseTree, favoritesRepository),
        )
            .setSessionActivity(sessionActivityIntent)
            .setCustomLayout(ImmutableList.of(favoriteCommandButton(isFavorite = false)))
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                refreshFavoriteButton(mediaItem?.mediaId)
            }
        })

        // Odświeża ikonę serca na Android Auto, gdy ulubione zmienią się z innego miejsca (np.
        // telefon podłączony do auta, user odznacza utwór na ekranie Biblioteki) — nie tylko po
        // tapnięciu samego przycisku w Now Playing.
        applicationScope.launch {
            favoritesRepository.favoriteTrackIds.collect {
                refreshFavoriteButton(mediaSession?.player?.currentMediaItem?.mediaId)
            }
        }
    }

    private fun refreshFavoriteButton(trackIdString: String?) {
        val trackId = trackIdString?.toLongOrNull()
        val isFavorite = trackId != null && trackId in favoritesRepository.favoriteTrackIds.value
        mediaSession?.setCustomLayout(ImmutableList.of(favoriteCommandButton(isFavorite)))
    }

    private fun favoriteCommandButton(isFavorite: Boolean): CommandButton =
        CommandButton.Builder()
            .setDisplayName(getString(if (isFavorite) R.string.favorite_remove else R.string.favorite_add))
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_TOGGLE_FAVORITE, android.os.Bundle.EMPTY))
            .setIconResId(if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_outline)
            .build()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

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
        applicationScope.launch { audioOutput.disconnect() }
        super.onDestroy()
    }
}
