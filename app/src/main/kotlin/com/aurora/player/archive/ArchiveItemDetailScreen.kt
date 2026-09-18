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
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.player.designsystem.components.TrackAction
import com.aurora.player.designsystem.components.TrackActionsSheet
import com.aurora.player.designsystem.components.TrackListItem
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.ArchiveTrack
import com.aurora.player.library.LibraryViewModel
import com.aurora.player.navigation.LocalBottomChromeInset
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
    val libraryTracks by viewModel.archiveLibraryTracks.collectAsState()
    val downloadingTrackIds by viewModel.archiveDownloadingTrackIds.collectAsState()
    val archiveError by viewModel.archiveLastError.collectAsState()
    val tokens = LocalAuroraTokens.current

    val item = remember(allItems, identifier) { allItems.find { it.identifier == identifier } }
    val libraryTrackIds = remember(libraryTracks) { libraryTracks.map { it.id }.toSet() }

    var trackForMenu by remember { mutableStateOf<ArchiveTrack?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(identifier) { viewModel.loadArchiveTracks(identifier) }

    LaunchedEffect(archiveError) {
        archiveError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearArchiveError()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
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
                // Zgłoszenie: pobieranie utworów jeden po jednym z menu "..." to dużo klikania przy
                // albumie/koncercie z kilkudziesięcioma ścieżkami — jeden przycisk pobiera od razu
                // wszystkie, które jeszcze nie są w bibliotece (per-ścieżkowy dedup i tak już jest w
                // `addTrackToLibrary`, więc bezpiecznie wywołać go dla całej listy naraz).
                if (item != null && tracks.size > 1) {
                    val trackIds = remember(tracks, item) { tracks.map { it.toTrack(item).id } }
                    val allDownloaded = trackIds.all { it in libraryTrackIds }
                    val anyDownloading = trackIds.any { it in downloadingTrackIds }
                    Icon(
                        imageVector = when {
                            anyDownloading -> Icons.Filled.Downloading
                            allDownloaded -> Icons.Filled.OfflinePin
                            else -> Icons.Filled.Download
                        },
                        contentDescription = if (allDownloaded) "Cały album pobrany" else "Pobierz cały album",
                        tint = if (allDownloaded) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(enabled = !allDownloaded && !anyDownloading) {
                                tracks.forEachIndexed { index, track ->
                                    if (trackIds[index] !in libraryTrackIds) {
                                        viewModel.onAddArchiveTrackToLibrary(track, item)
                                    }
                                }
                            },
                    )
                }
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
                                onMoreClick = { trackForMenu = archiveTrack },
                                isSavedOffline = trackId in libraryTrackIds,
                            )
                        }
                    }
                }
            }
        }

        // Etap 40, zgłoszenie: Snackbar chował się pod pływającym mini-playerem/nawigacją —
        // NavHost jest pełnoekranowy (patrz AuroraNavHost/LocalBottomChromeInset), więc bez tego
        // dolny padding liczył tylko `spacing.m`, za mało, żeby wyjść spod paska.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = LocalBottomChromeInset.current + tokens.spacing.m),
        ) { data ->
            Snackbar(snackbarData = data)
        }
    }

    if (item != null) {
        trackForMenu?.let { menuTrack ->
            val menuTrackId = remember(menuTrack, item) { menuTrack.toTrack(item).id }
            val isInLibrary = menuTrackId in libraryTrackIds
            val isDownloading = menuTrackId in downloadingTrackIds

            TrackActionsSheet(
                title = menuTrack.title,
                subtitle = item.creator,
                albumArtUrl = item.coverUrl,
                actions = listOf(
                    when {
                        isDownloading -> TrackAction(
                            icon = Icons.Filled.Downloading,
                            label = "Pobieranie…",
                            onClick = {},
                        )
                        isInLibrary -> TrackAction(
                            icon = Icons.Filled.DeleteOutline,
                            label = "Usuń z biblioteki (offline)",
                            onClick = {
                                viewModel.onRemoveArchiveTrackFromLibrary(menuTrackId)
                                trackForMenu = null
                            },
                        )
                        else -> TrackAction(
                            icon = Icons.Filled.Download,
                            label = "Dodaj do biblioteki (pobierz na stałe)",
                            onClick = {
                                viewModel.onAddArchiveTrackToLibrary(menuTrack, item)
                                trackForMenu = null
                            },
                        )
                    },
                    TrackAction(
                        icon = Icons.Filled.AddToQueue,
                        label = "Dodaj do kolejki",
                        onClick = {
                            viewModel.onAddToQueue(menuTrack.toTrack(item))
                            trackForMenu = null
                        },
                    ),
                ),
                onDismiss = { trackForMenu = null },
            )
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
