package com.aurora.player.archive

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.player.designsystem.components.TrackListItem
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.library.LibraryViewModel
import java.util.concurrent.TimeUnit

/** Lista ścieżek jednego itemu z Internet Archive (koncert/audycja) — DESIGN.md Etap 37. */
@Composable
fun ArchiveItemDetailScreen(
    viewModel: LibraryViewModel,
    identifier: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allItems by viewModel.archiveItems.collectAsState()
    val tracks by viewModel.archiveTracks.collectAsState()
    val isLoading by viewModel.isLoadingArchiveTracks.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val tokens = LocalAuroraTokens.current

    val item = remember(allItems, identifier) { allItems.find { it.identifier == identifier } }

    LaunchedEffect(identifier) { viewModel.loadArchiveTracks(identifier) }

    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.s, vertical = tokens.spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Wstecz",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(40.dp).clickable(onClick = onBack),
            )
            Text(
                text = item?.title ?: identifier,
                style = AuroraTextStyles.Headline,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = tokens.spacing.xs),
            )
        }

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            tracks.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Brak dostępnych ścieżek audio dla tej pozycji.",
                        style = AuroraTextStyles.Body,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(tokens.spacing.l),
                    )
                }
            }

            item == null -> Unit

            else -> {
                LazyColumn(contentPadding = PaddingValues(horizontal = tokens.spacing.s, vertical = tokens.spacing.s)) {
                    items(tracks, key = { it.fileName }) { archiveTrack ->
                        val trackId = remember(archiveTrack, item) { archiveTrack.toTrack(item).id }
                        TrackListItem(
                            title = archiveTrack.title,
                            artist = item.creator,
                            albumArtUrl = item.coverUrl,
                            durationLabel = formatDuration(archiveTrack.durationMs),
                            isCurrentlyPlaying = playbackState.currentTrack?.id == trackId,
                            onClick = { viewModel.onPlayArchiveTrack(archiveTrack, item, tracks) },
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return ""
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
