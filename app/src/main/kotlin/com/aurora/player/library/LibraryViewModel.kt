package com.aurora.player.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.player.domain.model.PlaybackState
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.repository.PlayerRepository
import com.aurora.player.domain.usecase.GetTracksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val playerRepository: PlayerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = playerRepository.playbackState

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
            playerRepository.play(track)
        }
    }

    fun onTogglePlayPause() {
        playerRepository.togglePlayPause()
    }
}
