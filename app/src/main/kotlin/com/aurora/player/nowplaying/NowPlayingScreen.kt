package com.aurora.player.nowplaying

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aurora.player.designsystem.components.VisualizerBars
import com.aurora.player.designsystem.components.sharedElementOrSelf
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.eq.EqualizerSheet
import com.aurora.player.library.LibraryViewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.concurrent.TimeUnit

/**
 * Odtwarzacz pełnoekranowy — patrz DESIGN.md sekcja 3.2. Tło i akcent koloru są wyprowadzone
 * dynamicznie z okładki albumu (Palette API, DarkMuted/Vibrant swatch) i animowane przy zmianie
 * utworu — patrz DESIGN.md sekcja 2.1 ("dwuwarstwowy model akcentu").
 * [sharedTransitionScope]/[animatedVisibilityScope] (z `AuroraNavHost`) dają płynne przejście
 * okładki z mini-playera — patrz `sharedElementOrSelf`, DESIGN.md 2.4 pkt 7.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    albumArtSharedKey: Any = "album_art",
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val palette by viewModel.albumArtPalette.collectAsState()
    val tokens = LocalAuroraTokens.current
    val track = playbackState.currentTrack

    val fallbackAccent = MaterialTheme.colorScheme.primary
    val backgroundTop by animateColorAsState(
        targetValue = palette.darkMuted ?: palette.darkVibrant ?: MaterialTheme.colorScheme.background,
        animationSpec = tween(400),
        label = "nowPlayingBackgroundTop",
    )
    val accentColor by animateColorAsState(
        targetValue = palette.vibrant ?: fallbackAccent,
        animationSpec = tween(400),
        label = "nowPlayingAccent",
    )
    val backgroundBrush = Brush.verticalGradient(listOf(backgroundTop, Color(0xFF06060A)))
    var showEqSheet by remember { mutableStateOf(false) }
    val hazeState = rememberHazeState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .hazeSource(state = hazeState)
            .padding(tokens.spacing.m),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Zwiń",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onBack),
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Equalizer",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { showEqSheet = true },
            )
        }

        if (showEqSheet) {
            EqualizerSheet(viewModel = viewModel, onDismiss = { showEqSheet = false }, hazeState = hazeState)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = tokens.spacing.xl)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .sharedElementOrSelf(sharedTransitionScope, animatedVisibilityScope, albumArtSharedKey),
            contentAlignment = Alignment.Center,
        ) {
            if (track?.albumArtUri != null) {
                AsyncImage(
                    model = track.albumArtUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(modifier = Modifier.padding(top = tokens.spacing.xl)) {
            Text(
                text = track?.title ?: "Nic nie gra",
                style = AuroraTextStyles.Display,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track?.artist.orEmpty(),
                style = AuroraTextStyles.Body,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = tokens.spacing.xs),
            )
        }

        val visualizerMagnitudes by viewModel.visualizerMagnitudes.collectAsState()
        if (playbackState.isPlaying) {
            VisualizerBars(
                magnitudes = visualizerMagnitudes,
                color = accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(top = tokens.spacing.l),
            )
        }

        // Dla utworów z chmury (Google Drive) długość nie jest znana z metadanych z góry —
        // playbackState.durationMs (z samego playera) ma pierwszeństwo, gdy już jest dostępna.
        val durationMs = playbackState.durationMs.takeIf { it > 0L } ?: track?.durationMs ?: 0L
        var isDragging by remember { mutableStateOf(false) }
        var dragPositionMs by remember { mutableStateOf(0f) }
        val sliderPosition = if (isDragging) dragPositionMs else playbackState.positionMs.toFloat()

        Column(modifier = Modifier.padding(top = tokens.spacing.l)) {
            Slider(
                value = sliderPosition.coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                onValueChange = {
                    isDragging = true
                    dragPositionMs = it
                },
                onValueChangeFinished = {
                    viewModel.onSeek(dragPositionMs.toLong())
                    isDragging = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTime(sliderPosition.toLong()),
                    style = AuroraTextStyles.TimeTabular,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
                Text(
                    text = formatTime(durationMs),
                    style = AuroraTextStyles.TimeTabular,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = tokens.spacing.l),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Poprzedni",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = viewModel::onSkipPrevious),
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.xl)
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(accentColor)
                    .clickable(onClick = viewModel::onTogglePlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pauza" else "Odtwórz",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }

            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Następny",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = viewModel::onSkipNext),
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0))
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
