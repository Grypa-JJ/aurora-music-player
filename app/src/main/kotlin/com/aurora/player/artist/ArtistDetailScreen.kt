package com.aurora.player.artist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import com.aurora.player.library.groupTracksByArtist
import com.aurora.player.playlist.AddToPlaylistSheet
import java.util.concurrent.TimeUnit

/**
 * Widok wszystkich utworów jednego wykonawcy — DESIGN.md Etap 22/23. Świadomie płaska lista
 * (nie grupowana per album) — "wszystkie utwory X" to sedno tego, po co ktoś w ogóle wchodzi w
 * wykonawcę. Etap 43: banner/bio/gatunek dociągnięte z TheAudioDB dołożone NAD tą listą (opcjonalny
 * nagłówek, znika bez śladu gdy TheAudioDB nic nie znajdzie) — pełny ekran "à la Spotify" z
 * dyskografią to wciąż większy koszt niż wartość na obecnym etapie appki, ale samo bio/gatunek
 * było tanie dołożyć do już istniejącego ekranu.
 */
@Composable
fun ArtistDetailScreen(
    viewModel: LibraryViewModel,
    artistName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val artistInfo by viewModel.artistInfo.collectAsState()
    val tokens = LocalAuroraTokens.current

    LaunchedEffect(artistName) { viewModel.loadArtistInfo(artistName) }

    val artist = remember(uiState.allTracks, artistName) {
        groupTracksByArtist(uiState.allTracks).find { it.name == artistName }
    }

    var trackForMenu by remember { mutableStateOf<Track?>(null) }
    var trackForPlaylistSheet by remember { mutableStateOf<Track?>(null) }

    if (artist == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Wykonawca nie jest już dostępny.",
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
            Text(
                text = artist.name,
                style = AuroraTextStyles.Headline,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = tokens.spacing.xs),
            )
        }

        artistInfo?.bannerUrl?.let { bannerUrl ->
            AsyncImage(
                model = bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.m)
                    .height(100.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }

        val tags = listOfNotNull(artistInfo?.genre, artistInfo?.style, artistInfo?.mood).distinct()
        if (tags.isNotEmpty()) {
            Text(
                text = tags.joinToString(" • "),
                style = AuroraTextStyles.Label,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.xs),
            )
        }

        artistInfo?.biography?.let { biography ->
            var isBiographyExpanded by remember(artistName) { mutableStateOf(false) }
            Text(
                text = biography,
                style = AuroraTextStyles.Body,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                maxLines = if (isBiographyExpanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.xs)
                    .clickable { isBiographyExpanded = !isBiographyExpanded },
            )
            Text(
                text = if (isBiographyExpanded) "Pokaż mniej" else "Pokaż więcej",
                style = AuroraTextStyles.Label,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.m)
                    .clickable { isBiographyExpanded = !isBiographyExpanded },
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.m)) {
            Text(
                text = "${artist.tracks.size} utworów",
                style = AuroraTextStyles.Label,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { viewModel.onPlayTracks(artist.tracks) }) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = "Odtwórz", modifier = Modifier.padding(start = tokens.spacing.xs))
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = tokens.spacing.s, vertical = tokens.spacing.m),
        ) {
            itemsIndexed(artist.tracks, key = { _, track -> track.id }) { index, track ->
                TrackListItem(
                    title = track.title,
                    artist = track.album,
                    albumArtUrl = track.albumArtUri,
                    durationLabel = formatDuration(track.durationMs),
                    isCurrentlyPlaying = playbackState.currentTrack?.id == track.id,
                    onClick = { viewModel.onPlayTracks(artist.tracks, index) },
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
