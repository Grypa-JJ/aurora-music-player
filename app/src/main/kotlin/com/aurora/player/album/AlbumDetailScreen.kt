package com.aurora.player.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aurora.player.designsystem.components.TrackAction
import com.aurora.player.designsystem.components.TrackActionsSheet
import com.aurora.player.designsystem.components.TrackListItem
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TrackSource
import com.aurora.player.library.LibraryViewModel
import com.aurora.player.library.groupTracksByAlbum
import com.aurora.player.playlist.AddToPlaylistSheet
import java.util.concurrent.TimeUnit

/** Widok jednego albumu — DESIGN.md Etap 22/23. */
@Composable
fun AlbumDetailScreen(
    viewModel: LibraryViewModel,
    albumName: String,
    albumArtist: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val tokens = LocalAuroraTokens.current

    val album = remember(uiState.allTracks, albumName, albumArtist) {
        groupTracksByAlbum(uiState.allTracks).find { it.name == albumName && it.artist == albumArtist }
    }

    var trackForMenu by remember { mutableStateOf<Track?>(null) }
    var trackForPlaylistSheet by remember { mutableStateOf<Track?>(null) }

    if (album == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Album nie jest już dostępny.",
                style = AuroraTextStyles.Body,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.s, vertical = tokens.spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = "Wstecz",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(40.dp).clickable(onClick = onBack),
            )
        }

        // Zgłoszenie: okładka + "Odtwórz" siedziały w osobnym `Column` NAD `LazyColumn` — zawsze
        // zajmowały tę samą przestrzeń, na mniejszych ekranach zostawiając mało miejsca na listę
        // utworów. Jako pierwszy element `LazyColumn` przewijają się razem z listą (chowają się do
        // góry), bez osobnego tła — blenduje się z tłem ekranu zamiast tworzyć stały pasek.
        LazyColumn(
            contentPadding = PaddingValues(horizontal = tokens.spacing.s, vertical = tokens.spacing.m),
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.xs)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        if (album.albumArtUri != null) {
                            AsyncImage(model = album.albumArtUri, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                        }
                    }
                    Text(
                        text = album.name,
                        style = AuroraTextStyles.Headline,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = tokens.spacing.m),
                    )
                    Text(
                        text = "${album.artist} • ${album.tracks.size} utworów",
                        style = AuroraTextStyles.Label,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                    Button(onClick = { viewModel.onPlayTracks(album.tracks) }, modifier = Modifier.padding(top = tokens.spacing.m, bottom = tokens.spacing.s)) {
                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(text = "Odtwórz", modifier = Modifier.padding(start = tokens.spacing.xs))
                    }
                }
            }
            itemsIndexed(album.tracks, key = { _, track -> track.id }) { index, track ->
                TrackListItem(
                    title = track.title,
                    artist = track.artist,
                    albumArtUrl = track.albumArtUri,
                    durationLabel = formatDuration(track.durationMs),
                    isCurrentlyPlaying = playbackState.currentTrack?.id == track.id,
                    onClick = { viewModel.onPlayTracks(album.tracks, index) },
                    onMoreClick = { trackForMenu = track },
                    isCloudTrack = track.source == TrackSource.CLOUD,
                )
            }
        }
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
