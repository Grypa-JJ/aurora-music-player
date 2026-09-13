package com.aurora.player.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aurora.player.designsystem.components.MiniPlayerBar
import com.aurora.player.designsystem.components.TrackListItem
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import java.util.concurrent.TimeUnit

private val audioPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val tokens = LocalAuroraTokens.current

    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onPermissionResult(granted) }

    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(context, audioPermission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            viewModel.onPermissionResult(true)
        } else if (!permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(audioPermission)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Biblioteka",
                style = AuroraTextStyles.Headline,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(
                    horizontal = tokens.spacing.m,
                    vertical = tokens.spacing.l,
                ),
            )

            when {
                !uiState.hasPermission -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(tokens.spacing.l),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Aurora potrzebuje dostępu do muzyki na urządzeniu, żeby zbudować Twoją bibliotekę.",
                            style = AuroraTextStyles.Body,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                        Button(
                            onClick = { permissionLauncher.launch(audioPermission) },
                            modifier = Modifier.padding(top = tokens.spacing.m),
                        ) {
                            Text("Zezwól na dostęp")
                        }
                    }
                }

                uiState.tracks.isEmpty() && !uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Nie znaleziono muzyki na urządzeniu.",
                            style = AuroraTextStyles.Body,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            horizontal = tokens.spacing.s,
                            vertical = tokens.spacing.s,
                        ),
                    ) {
                        items(uiState.tracks, key = { it.id }) { track ->
                            TrackListItem(
                                title = track.title,
                                artist = track.artist,
                                albumArtUrl = track.albumArtUri,
                                durationLabel = formatDuration(track.durationMs),
                                isCurrentlyPlaying = playbackState.currentTrack?.id == track.id,
                                onClick = { viewModel.onTrackClick(track) },
                            )
                        }
                    }
                }
            }
        }

        playbackState.currentTrack?.let { track ->
            MiniPlayerBar(
                title = track.title,
                artist = track.artist,
                albumArtUrl = track.albumArtUri,
                isPlaying = playbackState.isPlaying,
                onTogglePlayPause = viewModel::onTogglePlayPause,
                onClick = { /* TODO(etap 1): otwórz Now Playing */ },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(tokens.spacing.m),
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
