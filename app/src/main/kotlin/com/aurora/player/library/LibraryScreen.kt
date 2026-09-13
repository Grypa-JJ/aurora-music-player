package com.aurora.player.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aurora.player.designsystem.components.MiniPlayerBar
import com.aurora.player.designsystem.components.TrackListItem
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.TrackSource
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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
    onOpenNowPlaying: () -> Unit,
    onOpenGeniusMixes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isCloudSignedIn by viewModel.isCloudSignedIn.collectAsState()
    val tokens = LocalAuroraTokens.current
    val hazeState = rememberHazeState()

    var permissionRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onPermissionResult(granted) }

    val cloudConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onCloudConsentResult(result.data) }

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.l),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Biblioteka",
                    style = AuroraTextStyles.Headline,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isCloudSignedIn) Icons.Filled.Cloud else Icons.Filled.CloudOff,
                        contentDescription = if (isCloudSignedIn) {
                            "Chmura połączona (dotknij, żeby się wylogować)"
                        } else {
                            "Zaloguj do Google Drive"
                        },
                        tint = if (isCloudSignedIn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        },
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                if (isCloudSignedIn) {
                                    viewModel.onCloudSignOut()
                                } else {
                                    viewModel.onCloudConnectClick { intentSender ->
                                        cloudConsentLauncher.launch(
                                            IntentSenderRequest.Builder(intentSender).build(),
                                        )
                                    }
                                }
                            },
                    )
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "Genius",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(start = tokens.spacing.m)
                            .size(26.dp)
                            .clickable(onClick = onOpenGeniusMixes),
                    )
                }
            }

            when {
                // Nie blokuj widoku permission-promptem, jeśli mamy już czym wypełnić listę
                // (np. same utwory z chmury bez zgody na lokalny storage).
                !uiState.hasPermission && uiState.allTracks.isEmpty() -> {
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

                uiState.allTracks.isEmpty() && !uiState.isLoading && !uiState.isLoadingCloud -> {
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
                        modifier = Modifier
                            .weight(1f)
                            .hazeSource(state = hazeState),
                        contentPadding = PaddingValues(
                            horizontal = tokens.spacing.s,
                            vertical = tokens.spacing.s,
                        ),
                    ) {
                        items(uiState.allTracks, key = { it.id }) { track ->
                            TrackListItem(
                                title = track.title,
                                artist = track.artist,
                                albumArtUrl = track.albumArtUri,
                                durationLabel = formatDuration(track.durationMs),
                                isCurrentlyPlaying = playbackState.currentTrack?.id == track.id,
                                onClick = { viewModel.onTrackClick(track) },
                                onGeniusClick = { viewModel.onGeniusClick(track) },
                                isCloudTrack = track.source == TrackSource.CLOUD,
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
                onClick = onOpenNowPlaying,
                hazeState = hazeState,
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
