package com.aurora.player.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurora.player.designsystem.components.TrackAction
import com.aurora.player.designsystem.components.TrackActionsSheet
import com.aurora.player.designsystem.components.TrackListItem
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TrackSource
import com.aurora.player.library.LibraryViewModel
import com.aurora.player.playlist.AddToPlaylistSheet
import java.util.concurrent.TimeUnit

/** Ulubione (serduszko) — DESIGN.md Etap 22 (obecne w wizji od sekcji 3.2, nigdy nie zbudowane). */
@Composable
fun FavoritesScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val tokens = LocalAuroraTokens.current

    val tracks = remember(favoriteTrackIds, uiState.allTracks) {
        uiState.allTracks.filter { it.id in favoriteTrackIds }
    }

    var trackForMenu by remember { mutableStateOf<Track?>(null) }
    var trackForPlaylistSheet by remember { mutableStateOf<Track?>(null) }

    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.s, vertical = tokens.spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = "Wstecz",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(40.dp).clickable(onClick = onBack),
            )
            Text(
                text = "Ulubione",
                style = AuroraTextStyles.Headline,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = tokens.spacing.xs),
            )
        }

        if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Brak ulubionych utworów. Dotknij ♡ przy utworze, żeby go tu dodać.",
                    style = AuroraTextStyles.Body,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(tokens.spacing.l),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = tokens.spacing.s, vertical = tokens.spacing.s),
            ) {
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    TrackListItem(
                        title = track.title,
                        artist = track.artist,
                        albumArtUrl = track.albumArtUri,
                        durationLabel = formatDuration(track.durationMs),
                        isCurrentlyPlaying = playbackState.currentTrack?.id == track.id,
                        onClick = { viewModel.onPlayTracks(tracks, index) },
                        onMoreClick = { trackForMenu = track },
                        isCloudTrack = track.source == TrackSource.CLOUD,
                        isSavedOffline = track.source == TrackSource.ARCHIVE,
                    )
                }
            }
        }
    }

    trackForMenu?.let { track ->
        TrackActionsSheet(
            title = track.title,
            subtitle = track.artist,
            albumArtUrl = track.albumArtUri,
            actions = listOf(
                TrackAction(
                    icon = Icons.Filled.RemoveCircleOutline,
                    label = "Usuń z ulubionych",
                    onClick = { viewModel.onToggleFavorite(track.id); trackForMenu = null },
                ),
                TrackAction(
                    icon = Icons.Filled.AddToQueue,
                    label = "Dodaj do kolejki",
                    onClick = { viewModel.onAddToQueue(track); trackForMenu = null },
                ),
                TrackAction(
                    icon = Icons.Filled.PlaylistAdd,
                    label = "Dodaj do playlisty",
                    onClick = { trackForPlaylistSheet = track; trackForMenu = null },
                ),
            ),
            onDismiss = { trackForMenu = null },
        )
    }

    trackForPlaylistSheet?.let { track ->
        AddToPlaylistSheet(
            trackId = track.id,
            playlists = playlists,
            onAddToPlaylist = { playlistId ->
                viewModel.onAddTrackToPlaylist(playlistId, track.id)
                trackForPlaylistSheet = null
            },
            onCreatePlaylist = { name ->
                viewModel.onCreatePlaylist(name) { newId -> viewModel.onAddTrackToPlaylist(newId, track.id) }
                trackForPlaylistSheet = null
            },
            onDismiss = { trackForPlaylistSheet = null },
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
