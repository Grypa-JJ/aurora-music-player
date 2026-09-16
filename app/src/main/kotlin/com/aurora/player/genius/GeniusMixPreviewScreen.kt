package com.aurora.player.genius

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.player.designsystem.components.TrackListItem
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.TrackSource
import com.aurora.player.library.LibraryViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Podgląd playlisty Geniusa PRZED odtworzeniem — DESIGN.md Etap 22 (user: "brak podglądu
 * playlist"). Wcześniej tap na karcie w [GeniusMixesScreen] odtwarzał miks od razu, bez pokazania
 * jego zawartości; okrągła ikona play na karcie nadal działa jako szybka ścieżka bezpośredniego
 * odtwarzania bez wchodzenia tutaj.
 */
@Composable
fun GeniusMixPreviewScreen(
    viewModel: LibraryViewModel,
    mixIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mixes by viewModel.geniusMixes.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val tokens = LocalAuroraTokens.current
    val mix = mixes.getOrNull(mixIndex)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (mix == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Ten miks nie jest już dostępny.",
                style = AuroraTextStyles.Body,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
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
                    text = mix.name,
                    style = AuroraTextStyles.Headline,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = tokens.spacing.xs),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
            ) {
                Button(
                    onClick = { viewModel.onPlayTracks(mix.tracks) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(text = "Odtwórz", modifier = Modifier.padding(start = tokens.spacing.xs))
                }
                OutlinedButton(
                    onClick = {
                        viewModel.onSaveMixAsPlaylist(mix) {
                            scope.launch { snackbarHostState.showSnackbar("Zapisano jako playlistę „${mix.name}”") }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(imageVector = Icons.Filled.LibraryAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(text = "Zapisz jako playlistę", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = tokens.spacing.xs))
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = tokens.spacing.s, vertical = tokens.spacing.s),
            ) {
                itemsIndexed(mix.tracks, key = { _, track -> track.id }) { index, track ->
                    TrackListItem(
                        title = track.title,
                        artist = track.artist,
                        albumArtUrl = track.albumArtUri,
                        durationLabel = formatDuration(track.durationMs),
                        isCurrentlyPlaying = playbackState.currentTrack?.id == track.id,
                        onClick = { viewModel.onPlayTracks(mix.tracks, index) },
                        isCloudTrack = track.source == TrackSource.CLOUD,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(tokens.spacing.m),
        ) { data -> Snackbar(snackbarData = data) }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
