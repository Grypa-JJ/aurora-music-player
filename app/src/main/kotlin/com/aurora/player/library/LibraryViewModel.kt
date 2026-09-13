package com.aurora.player.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.player.color.AlbumArtColorExtractor
import com.aurora.player.color.AlbumArtPalette
import com.aurora.player.domain.model.EqState
import com.aurora.player.domain.model.GeniusMix
import com.aurora.player.domain.model.PlaybackState
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.repository.EqRepository
import com.aurora.player.domain.repository.GeniusRepository
import com.aurora.player.domain.repository.PlayerRepository
import com.aurora.player.domain.usecase.GetTracksUseCase
import com.aurora.player.visualizer.AudioVisualizerAnalyzer
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
    val isLoading: Boolean = false,
    val hasPermission: Boolean = false,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getTracksUseCase: GetTracksUseCase,
    private val colorExtractor: AlbumArtColorExtractor,
    val playerRepository: PlayerRepository,
    val eqRepository: EqRepository,
    private val geniusRepository: GeniusRepository,
    private val visualizerAnalyzer: AudioVisualizerAnalyzer,
) : ViewModel() {

    /** Wizualizer widmowy Now Playing — patrz DESIGN.md, `AudioVisualizerAnalyzer`. */
    val visualizerMagnitudes: StateFlow<FloatArray> = visualizerAnalyzer.magnitudes

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

    fun onPlayMix(mix: GeniusMix) {
        playerRepository.playQueue(mix.tracks)
    }
}
