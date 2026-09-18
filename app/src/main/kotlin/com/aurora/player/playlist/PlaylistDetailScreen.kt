package com.aurora.player.playlist

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.player.designsystem.components.TrackAction
import com.aurora.player.designsystem.components.TrackActionsSheet
import com.aurora.player.designsystem.components.TrackListItem
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TrackSource
import com.aurora.player.library.LibraryViewModel
import java.util.concurrent.TimeUnit
import sh.calvin.reorderable.*

/** Widok jednej playlisty: odtwarzanie, dodawanie/usuwanie utworów, zmiana nazwy — DESIGN.md Etap 22. */
@Composable
fun PlaylistDetailScreen(
    viewModel: LibraryViewModel,
    playlistId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playlists by viewModel.playlists.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsState()
    val tokens = LocalAuroraTokens.current

    val playlist = playlists.find { it.id == playlistId }
    val tracks = remember(playlist?.trackIds, uiState.allTracks) {
        val byId = uiState.allTracks.associateBy { it.id }
        playlist?.trackIds.orEmpty().mapNotNull { byId[it] }
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddTracksSheet by remember { mutableStateOf(false) }
    var trackForMenu by remember { mutableStateOf<Track?>(null) }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.onMoveTrackInPlaylist(playlistId, from.index, to.index)
    }

    if (playlist == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Playlista nie istnieje (mogła zostać usunięta).",
                style = AuroraTextStyles.Body,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
        return
    }

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
                text = playlist.name,
                style = AuroraTextStyles.Headline,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = tokens.spacing.xs),
            )
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Zmień nazwę",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.size(22.dp).clickable { showRenameDialog = true },
            )
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Usuń playlistę",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(start = tokens.spacing.m, end = tokens.spacing.m)
                    .size(22.dp)
                    .clickable { showDeleteConfirm = true },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.m),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${tracks.size} utworów",
                style = AuroraTextStyles.Label,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Dodaj utwory",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .padding(end = tokens.spacing.m)
                        .size(24.dp)
                        .clickable { showAddTracksSheet = true },
                )
                if (tracks.isNotEmpty()) {
                    Button(onClick = { viewModel.onPlayTracks(tracks) }) {
                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(text = "Odtwórz", modifier = Modifier.padding(start = tokens.spacing.xs))
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Playlista jest pusta. Dotknij +, żeby dodać utwory z biblioteki.",
                    style = AuroraTextStyles.Body,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(tokens.spacing.l),
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(horizontal = tokens.spacing.s, vertical = tokens.spacing.s),
            ) {
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    ReorderableItem(reorderableState, key = track.id) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "playlistItemElevation")
                        Surface(shadowElevation = elevation, color = MaterialTheme.colorScheme.background) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector = Icons.Filled.DragHandle,
                                    contentDescription = "Przeciągnij, żeby zmienić kolejność",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .padding(end = tokens.spacing.s)
                                        .size(22.dp)
                                        .draggableHandle(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        var name by remember { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Zmień nazwę playlisty") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onRenamePlaylist(playlist.id, name)
                        showRenameDialog = false
                    },
                    enabled = name.isNotBlank(),
                ) { Text("Zapisz") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Anuluj") } },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Usunąć playlistę „${playlist.name}”?") },
            text = { Text("Tej operacji nie można cofnąć. Same utwory zostają w bibliotece.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onDeletePlaylist(playlist.id)
                        showDeleteConfirm = false
                        onBack()
                    },
                ) { Text("Usuń") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Anuluj") } },
        )
    }

    if (showAddTracksSheet) {
        AddTracksSheet(
            allTracks = uiState.allTracks,
            alreadyAddedTrackIds = playlist.trackIds.toSet(),
            onAddTrack = { track -> viewModel.onAddTrackToPlaylist(playlist.id, track.id) },
            onDismiss = { showAddTracksSheet = false },
        )
    }

    trackForMenu?.let { track ->
        val isFavorite = track.id in favoriteTrackIds
        TrackActionsSheet(
            title = track.title,
            subtitle = track.artist,
            albumArtUrl = track.albumArtUri,
            actions = listOf(
                TrackAction(
                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    label = if (isFavorite) "Usuń z ulubionych" else "Dodaj do ulubionych",
                    onClick = { viewModel.onToggleFavorite(track.id); trackForMenu = null },
                ),
                TrackAction(
                    icon = Icons.Filled.AddToQueue,
                    label = "Dodaj do kolejki",
                    onClick = { viewModel.onAddToQueue(track); trackForMenu = null },
                ),
                TrackAction(
                    icon = Icons.Filled.RemoveCircleOutline,
                    label = "Usuń z playlisty",
                    onClick = {
                        viewModel.onRemoveTrackFromPlaylist(playlist.id, track.id)
                        trackForMenu = null
                    },
                ),
            ),
            onDismiss = { trackForMenu = null },
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
