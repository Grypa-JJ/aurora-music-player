package com.aurora.player.audiobook

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aurora.player.designsystem.components.TrackListItem
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.library.LibraryViewModel
import java.util.concurrent.TimeUnit

/** Widok jednego audiobooka: okładka + lista rozdziałów — DESIGN.md Etap 37, ten sam wzorzec co PodcastDetailScreen. */
@Composable
fun AudiobookDetailScreen(
    viewModel: LibraryViewModel,
    id: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val library by viewModel.audiobookLibrary.collectAsState()
    val chapters by viewModel.audiobookChapters.collectAsState()
    val isLoading by viewModel.isLoadingAudiobookChapters.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val tokens = LocalAuroraTokens.current

    val audiobook = remember(library, id) { library.find { it.id == id } }

    LaunchedEffect(id) { viewModel.loadAudiobookChapters(id) }

    if (audiobook == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Audiobook nie jest już dostępny.",
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
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Wstecz",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(40.dp).clickable(onClick = onBack),
            )
            Text(
                text = audiobook.title,
                style = AuroraTextStyles.Headline,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = tokens.spacing.xs),
            )
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Usuń z biblioteki",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(end = tokens.spacing.m).size(22.dp).clickable {
                    viewModel.removeAudiobookFromLibrary(id)
                    onBack()
                },
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s)) {
            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)) {
                if (audiobook.coverUrl != null) {
                    AsyncImage(model = audiobook.coverUrl, contentDescription = null, modifier = Modifier.size(56.dp))
                }
            }
            Column(modifier = Modifier.padding(start = tokens.spacing.m)) {
                Text(text = audiobook.author, style = AuroraTextStyles.Body, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    text = "${chapters.size} rozdziałów",
                    style = AuroraTextStyles.Label,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
        }

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            chapters.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Brak rozdziałów (albo LibriVox chwilowo niedostępny).",
                        style = AuroraTextStyles.Body,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }

            else -> {
                LazyColumn(contentPadding = PaddingValues(horizontal = tokens.spacing.s, vertical = tokens.spacing.s)) {
                    items(chapters, key = { it.id }) { chapter ->
                        val chapterTrackId = remember(chapter, audiobook) { chapter.toTrack(audiobook).id }
                        TrackListItem(
                            title = chapter.title,
                            artist = audiobook.author,
                            albumArtUrl = audiobook.coverUrl,
                            durationLabel = formatDuration(chapter.durationMs),
                            isCurrentlyPlaying = playbackState.currentTrack?.id == chapterTrackId,
                            onClick = { viewModel.onPlayAudiobookChapter(chapter, audiobook) },
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return ""
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
}
