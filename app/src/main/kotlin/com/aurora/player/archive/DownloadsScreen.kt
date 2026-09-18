package com.aurora.player.archive

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.CircularProgressIndicator
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
import com.aurora.player.designsystem.components.ShelfHeading
import com.aurora.player.designsystem.components.TrackAction
import com.aurora.player.designsystem.components.TrackActionsSheet
import com.aurora.player.designsystem.components.TrackListItem
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.ArchiveDownload
import com.aurora.player.domain.model.Track
import com.aurora.player.library.LibraryViewModel
import com.aurora.player.navigation.LocalBottomChromeInset
import java.util.concurrent.TimeUnit

/**
 * "Pobrane" — ścieżki z Archiwum zapisane NA STAŁE na dysku (offline) + te aktualnie w trakcie
 * pobierania (DESIGN.md Etap 40, user: "zróbmy zakładkę pobierane w apce żeby widzieć co się
 * pobiera a co się pobrało"). Jedyne realne "pobieranie na stałe" w appce jest tu, w Archiwum —
 * podcasty/audiobooki tylko streamują, nie mają osobnego trwałego zapisu na dysk.
 */
@Composable
fun DownloadsScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeDownloads by viewModel.archiveActiveDownloads.collectAsState()
    val downloadedTracks by viewModel.archiveLibraryTracks.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val tokens = LocalAuroraTokens.current

    var trackForMenu by remember { mutableStateOf<Track?>(null) }

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
                text = "Pobrane",
                style = AuroraTextStyles.Headline,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = tokens.spacing.xs),
            )
        }

        if (activeDownloads.isEmpty() && downloadedTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Brak pobranych utworów. W Archiwum dotknij „Pobierz” przy ścieżce, żeby zapisać ją offline.",
                    style = AuroraTextStyles.Body,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(tokens.spacing.l),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = tokens.spacing.s,
                    end = tokens.spacing.s,
                    top = tokens.spacing.s,
                    bottom = LocalBottomChromeInset.current + tokens.spacing.s,
                ),
            ) {
                if (activeDownloads.isNotEmpty()) {
                    item { ShelfHeading(title = "Pobierane") }
                    items(activeDownloads, key = { "active_${it.trackId}" }) { download ->
                        DownloadingRow(download)
                    }
                }

                if (downloadedTracks.isNotEmpty()) {
                    item { ShelfHeading(title = "Pobrane") }
                    itemsIndexed(downloadedTracks, key = { _, track -> track.id }) { index, track ->
                        TrackListItem(
                            title = track.title,
                            artist = track.artist,
                            albumArtUrl = track.albumArtUri,
                            durationLabel = formatDuration(track.durationMs),
                            isCurrentlyPlaying = playbackState.currentTrack?.id == track.id,
                            isSavedOffline = true,
                            onClick = { viewModel.onPlayTracks(downloadedTracks, index) },
                            onMoreClick = { trackForMenu = track },
                        )
                    }
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
                    icon = Icons.Filled.AddToQueue,
                    label = "Dodaj do kolejki",
                    onClick = { viewModel.onAddToQueue(track); trackForMenu = null },
                ),
                TrackAction(
                    icon = Icons.Filled.DeleteOutline,
                    label = "Usuń z pobranych",
                    onClick = { viewModel.onRemoveArchiveTrackFromLibrary(track.id); trackForMenu = null },
                ),
            ),
            onDismiss = { trackForMenu = null },
        )
    }
}

@Composable
private fun DownloadingRow(download: ArchiveDownload, modifier: Modifier = Modifier) {
    val tokens = LocalAuroraTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s + 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            if (download.coverUrl != null) {
                AsyncImage(model = download.coverUrl, contentDescription = null, modifier = Modifier.size(48.dp))
            }
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = tokens.spacing.m)) {
            Text(
                text = download.title,
                style = AuroraTextStyles.Body,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = download.subtitle,
                style = AuroraTextStyles.Label,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
