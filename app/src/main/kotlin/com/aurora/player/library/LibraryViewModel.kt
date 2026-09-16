package com.aurora.player.library

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.player.cloud.GoogleDriveLibraryRepository
import com.aurora.player.cloud.WebDavLibraryRepository
import com.aurora.player.color.AlbumArtColorExtractor
import com.aurora.player.color.AlbumArtPalette
import com.aurora.player.domain.model.EqState
import com.aurora.player.domain.model.GeniusMix
import com.aurora.player.domain.model.LyricsResult
import com.aurora.player.domain.model.PlaybackState
import com.aurora.player.domain.model.Playlist
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.repository.EqRepository
import com.aurora.player.domain.repository.FavoritesRepository
import com.aurora.player.domain.repository.GeniusRepository
import com.aurora.player.domain.repository.LyricsRepository
import com.aurora.player.domain.repository.PlayerRepository
import com.aurora.player.domain.repository.PlaylistRepository
import com.aurora.player.domain.usecase.GetTracksUseCase
import com.aurora.player.playback.SleepTimerController
import com.aurora.player.visualizer.AudioVisualizerAnalyzer
import com.aurora.player.visualizer.VisualizerFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val tracks: List<Track> = emptyList(),
    val cloudTracks: List<Track> = emptyList(),
    val webDavTracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingCloud: Boolean = false,
    val isLoadingWebDav: Boolean = false,
    val hasPermission: Boolean = false,
) {
    /** Biblioteka z urządzenia + z chmury + z NAS/WebDAV złączona w jedną listę do wyświetlenia. */
    val allTracks: List<Track> get() = tracks + cloudTracks + webDavTracks
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getTracksUseCase: GetTracksUseCase,
    private val colorExtractor: AlbumArtColorExtractor,
    val playerRepository: PlayerRepository,
    val eqRepository: EqRepository,
    private val geniusRepository: GeniusRepository,
    private val visualizerAnalyzer: AudioVisualizerAnalyzer,
    private val googleDriveLibraryRepository: GoogleDriveLibraryRepository,
    private val webDavLibraryRepository: WebDavLibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val favoritesRepository: FavoritesRepository,
    private val sleepTimerController: SleepTimerController,
    private val lyricsRepository: LyricsRepository,
) : ViewModel() {

    val isCloudSignedIn: StateFlow<Boolean> = googleDriveLibraryRepository.isSignedIn
    val cloudAccountEmail: StateFlow<String?> = googleDriveLibraryRepository.accountEmail
    val cloudLastError: StateFlow<String?> = googleDriveLibraryRepository.lastError
    fun clearCloudError() = googleDriveLibraryRepository.clearLastError()

    val isWebDavConnected: StateFlow<Boolean> = webDavLibraryRepository.isConnected
    val webDavServerLabel: StateFlow<String?> = webDavLibraryRepository.serverLabel
    val webDavLastError: StateFlow<String?> = webDavLibraryRepository.lastError
    fun clearWebDavError() = webDavLibraryRepository.clearLastError()

    /** Wizualizer widmowy Now Playing — patrz DESIGN.md, `AudioVisualizerAnalyzer`. */
    val visualizerFrame: StateFlow<VisualizerFrame> = visualizerAnalyzer.frame

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = playerRepository.playbackState
    val eqState: StateFlow<EqState> = eqRepository.eqState

    private val _albumArtPalette = MutableStateFlow(AlbumArtPalette())
    val albumArtPalette: StateFlow<AlbumArtPalette> = _albumArtPalette.asStateFlow()

    private val _isGeneratingMix = MutableStateFlow(false)
    val isGeneratingMix: StateFlow<Boolean> = _isGeneratingMix.asStateFlow()

    private val _geniusMixes = MutableStateFlow<List<GeniusMix>>(emptyList())
    val geniusMixes: StateFlow<List<GeniusMix>> = _geniusMixes.asStateFlow()

    private val _isLoadingMixes = MutableStateFlow(false)
    val isLoadingMixes: StateFlow<Boolean> = _isLoadingMixes.asStateFlow()

    private val _lyricsResult = MutableStateFlow<LyricsResult?>(null)
    val lyricsResult: StateFlow<LyricsResult?> = _lyricsResult.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    init {
        viewModelScope.launch {
            playbackState
                .map { it.currentTrack }
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .collect { track ->
                    _albumArtPalette.value = AlbumArtPalette()
                    if (track != null) {
                        _albumArtPalette.value = colorExtractor.extract(track.id, track.albumArtUri)
                    }
                }
        }
        // Napisy — DESIGN.md Etap 24. Ten sam wzorzec co paleta koloru wyżej: reaguje na zmianę
        // ID bieżącego utworu, nie na każdą emisję playbackState (ta zmienia się co ~300ms przy
        // odtwarzaniu — distinctUntilChanged po ID chroni przed odpytywaniem cache'u w kółko).
        viewModelScope.launch {
            playbackState
                .map { it.currentTrack }
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .collect { track ->
                    _lyricsResult.value = null
                    if (track != null) {
                        _isLoadingLyrics.value = true
                        _lyricsResult.value = lyricsRepository.getLyrics(track)
                        _isLoadingLyrics.value = false
                    }
                }
        }
        // Sesja Google potrafi przetrwać restart appki (GoogleSignIn.getLastSignedInAccount) —
        // jeśli tak, dociągnij bibliotekę z chmury bez czekania na akcję użytkownika.
        if (isCloudSignedIn.value) refreshCloud()
        // Dane logowania WebDAV są trwałe (SharedPreferences) — jeśli user już się kiedyś
        // połączył, dociągnij bibliotekę od razu, tak samo jak Google Drive powyżej.
        if (isWebDavConnected.value) refreshWebDav()
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasPermission = granted) }
        if (granted) refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val tracks = getTracksUseCase()
            _uiState.update { it.copy(tracks = tracks, isLoading = false) }
        }
    }

    fun onTrackClick(track: Track) {
        if (playbackState.value.currentTrack?.id == track.id) {
            playerRepository.togglePlayPause()
        } else {
            playerRepository.playQueue(listOf(track))
        }
    }

    fun onTogglePlayPause() {
        playerRepository.togglePlayPause()
    }

    fun onSeek(positionMs: Long) {
        playerRepository.seekTo(positionMs)
    }

    fun onSkipNext() {
        playerRepository.skipToNext()
    }

    fun onSkipPrevious() {
        playerRepository.skipToPrevious()
    }

    /** Odtwarza dowolną listę utworów od [startIndex] — playlisty, ulubione, podgląd Geniusa. */
    fun onPlayTracks(tracks: List<Track>, startIndex: Int = 0) {
        playerRepository.playQueue(tracks, startIndex)
    }

    /** Instant Mix z utworu-ziarna — patrz DESIGN.md sekcja 5.3. Seed gra jako pierwszy. */
    fun onGeniusClick(seedTrack: Track) {
        viewModelScope.launch {
            _isGeneratingMix.value = true
            val mix = geniusRepository.generateInstantMix(seedTrack.id)
            _isGeneratingMix.value = false
            playerRepository.playQueue(listOf(seedTrack) + mix)
        }
    }

    fun onEqSetEnabled(enabled: Boolean) {
        eqRepository.setEnabled(enabled)
    }

    fun onEqSetBandGain(bandIndex: Int, gainDb: Float) {
        eqRepository.setBandGain(bandIndex, gainDb)
    }

    fun onEqApplyPreset(presetName: String) {
        eqRepository.applyPreset(presetName)
    }

    fun onEqReset() {
        eqRepository.reset()
    }

    /** Gotowe playlisty z klastrowania, bez wskazywania utworu-ziarna — DESIGN.md sekcja 5.5. */
    fun loadGeniusMixes() {
        viewModelScope.launch {
            _isLoadingMixes.value = true
            _geniusMixes.value = geniusRepository.generateGeniusMixes()
            _isLoadingMixes.value = false
        }
    }

    // --- Chmura (Google Drive) — patrz DESIGN.md, sekcja "Chmura" ---

    /**
     * Próbuje połączyć się z Google Drive. Jeśli Google wymaga ekranu zgody, [onNeedsConsent]
     * dostaje [android.content.IntentSender] do odpalenia przez `ActivityResultLauncher`
     * (patrz LibraryScreen) — inaczej (już autoryzowany/błąd) biblioteka po prostu się odświeża.
     */
    fun onCloudConnectClick(onNeedsConsent: (android.content.IntentSender) -> Unit) {
        viewModelScope.launch {
            val intentSender = googleDriveLibraryRepository.connect()
            if (intentSender != null) {
                onNeedsConsent(intentSender)
            } else if (googleDriveLibraryRepository.isSignedIn.value) {
                refreshCloud()
            }
        }
    }

    /** Wołane z `ActivityResultLauncher` w LibraryScreen po ekranie zgody Google. */
    fun onCloudConsentResult(data: Intent?) {
        if (googleDriveLibraryRepository.handleAuthorizationResult(data)) {
            refreshCloud()
        }
    }

    fun refreshCloud() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCloud = true) }
            val tracks = googleDriveLibraryRepository.refreshCloudTracks()
            _uiState.update { it.copy(cloudTracks = tracks, isLoadingCloud = false) }
        }
    }

    fun onCloudSignOut() {
        googleDriveLibraryRepository.signOut()
        _uiState.update { it.copy(cloudTracks = emptyList()) }
    }

    // --- NAS/WebDAV — DESIGN.md Etap 12/22 ---

    /** `true` = połączono i biblioteka odświeżona; `false` = błąd, patrz [webDavLastError]. */
    fun onWebDavConnectClick(serverUrl: String, username: String, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val connected = webDavLibraryRepository.connect(serverUrl, username, password)
            if (connected) refreshWebDav()
            onResult(connected)
        }
    }

    fun refreshWebDav() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingWebDav = true) }
            val tracks = webDavLibraryRepository.refreshTracks()
            _uiState.update { it.copy(webDavTracks = tracks, isLoadingWebDav = false) }
        }
    }

    fun onWebDavDisconnect() {
        webDavLibraryRepository.disconnect()
        _uiState.update { it.copy(webDavTracks = emptyList()) }
    }

    // --- Playlisty — DESIGN.md Etap 22 ---

    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists

    fun onCreatePlaylist(name: String, onCreated: (Long) -> Unit = {}) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = playlistRepository.createPlaylist(trimmed)
            onCreated(id)
        }
    }

    fun onRenamePlaylist(playlistId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { playlistRepository.renamePlaylist(playlistId, trimmed) }
    }

    fun onDeletePlaylist(playlistId: Long) {
        viewModelScope.launch { playlistRepository.deletePlaylist(playlistId) }
    }

    fun onAddTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch { playlistRepository.addTrack(playlistId, trackId) }
    }

    fun onRemoveTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch { playlistRepository.removeTrack(playlistId, trackId) }
    }

    fun onMoveTrackInPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch { playlistRepository.moveTrack(playlistId, fromIndex, toIndex) }
    }

    /** Podgląd Geniusa → "Zapisz jako playlistę" (patrz GeniusMixPreviewScreen). */
    fun onSaveMixAsPlaylist(mix: GeniusMix, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = playlistRepository.createPlaylist(mix.name)
            mix.tracks.forEach { playlistRepository.addTrack(id, it.id) }
            onSaved(id)
        }
    }

    // --- Ulubione — DESIGN.md Etap 22 ---

    val favoriteTrackIds: StateFlow<Set<Long>> = favoritesRepository.favoriteTrackIds

    fun onToggleFavorite(trackId: Long) {
        viewModelScope.launch { favoritesRepository.toggleFavorite(trackId) }
    }

    // --- Kolejka — DESIGN.md Etap 22 ---

    fun onAddToQueue(track: Track) = playerRepository.addToQueue(track)
    fun onRemoveFromQueue(index: Int) = playerRepository.removeFromQueue(index)
    fun onMoveQueueItem(fromIndex: Int, toIndex: Int) = playerRepository.moveQueueItem(fromIndex, toIndex)
    fun onPlayQueueIndex(index: Int) = playerRepository.playAt(index)

    // --- Timer snu — DESIGN.md Etap 22 ---

    val sleepTimerRemainingMs: StateFlow<Long?> = sleepTimerController.remainingMs
    fun onStartSleepTimer(durationMs: Long) = sleepTimerController.start(durationMs)
    fun onCancelSleepTimer() = sleepTimerController.cancel()
}
