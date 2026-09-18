package com.aurora.player.podcast

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PlayCircle
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
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.Podcast
import com.aurora.player.domain.model.PodcastEpisode
import com.aurora.player.library.LibraryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Widok jednego podcastu: opis + lista odcinków — DESIGN.md Etap 32. */
@Composable
fun PodcastDetailScreen(
    viewModel: LibraryViewModel,
    feedUrl: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subscriptions by viewModel.podcastSubscriptions.collectAsState()
    val searchResults by viewModel.podcastSearchResults.collectAsState()
    val episodes by viewModel.podcastEpisodes.collectAsState()
    val isLoading by viewModel.isLoadingPodcastEpisodes.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val tokens = LocalAuroraTokens.current

    val subscribedPodcast = remember(subscriptions, feedUrl) { subscriptions.find { it.feedUrl == feedUrl } }
    // Karty "Podkasty dla Ciebie" na Home wołają ten ekran z feedUrl jeszcze NIEZASUBSKRYBOWANYM
    // (to tylko propozycja z katalogu) — bez tego fallbacku każde takie kliknięcie pokazywało
    // "Podcast nie jest już dostępny", bo szukaliśmy tylko wśród subskrypcji. `description` nie jest
    // nigdzie renderowany na tym ekranie ani używany w [PodcastEpisode.toTrack], więc pusty string
    // jest bezpieczny dla podglądu przed subskrypcją.
    val podcast = remember(subscribedPodcast, searchResults, feedUrl) {
        subscribedPodcast ?: searchResults.find { it.feedUrl == feedUrl }?.let {
            Podcast(feedUrl = it.feedUrl, title = it.title, author = it.author, artworkUrl = it.artworkUrl, description = "")
        }
    }
    val isSubscribed = subscribedPodcast != null

    LaunchedEffect(feedUrl) { viewModel.loadPodcastEpisodes(feedUrl) }

    if (podcast == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Podcast nie jest już dostępny.",
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
                text = podcast.title,
                style = AuroraTextStyles.Headline,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = tokens.spacing.xs),
            )
            if (isSubscribed) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Odsubskrybuj",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(end = tokens.spacing.m).size(22.dp).clickable {
                        viewModel.unsubscribeFromPodcast(feedUrl)
                        onBack()
                    },
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.LibraryAdd,
                    contentDescription = "Subskrybuj",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(end = tokens.spacing.m).size(22.dp).clickable {
                        viewModel.subscribeToPodcast(feedUrl)
                    },
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s)) {
            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)) {
                if (podcast.artworkUrl != null) {
                    AsyncImage(model = podcast.artworkUrl, contentDescription = null, modifier = Modifier.size(56.dp))
                }
            }
            Column(modifier = Modifier.padding(start = tokens.spacing.m)) {
                Text(text = podcast.author, style = AuroraTextStyles.Body, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    text = "${episodes.size} odcinków",
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

            episodes.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Brak odcinków (albo feed chwilowo niedostępny).",
                            style = AuroraTextStyles.Body,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                        // Duże feedy (np. JRE: ~5MB) czasem obrywają przejściowym błędem sieci przy
                        // ściąganiu — bez tego przycisku jedyny sposób ponowienia to wyjście i
                        // wejście z powrotem na ekran (LaunchedEffect(feedUrl) na nowej kompozycji).
                        Text(
                            text = "Spróbuj ponownie",
                            style = AuroraTextStyles.Body,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = tokens.spacing.m)
                                .clickable { viewModel.loadPodcastEpisodes(feedUrl) },
                        )
                    }
                }
            }

            else -> {
                LazyColumn(contentPadding = PaddingValues(horizontal = tokens.spacing.s, vertical = tokens.spacing.s)) {
                    items(episodes, key = { it.guid }) { episode ->
                        val episodeTrackId = remember(episode, podcast) { episode.toTrack(podcast).id }
                        EpisodeRow(
                            episode = episode,
                            isCurrentlyPlaying = playbackState.currentTrack?.id == episodeTrackId,
                            onClick = { viewModel.onPlayPodcastEpisode(episode, podcast) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: PodcastEpisode, isCurrentlyPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalAuroraTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.07f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s + 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayCircle,
            contentDescription = null,
            tint = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.size(32.dp),
        )
        Column(modifier = Modifier.padding(start = tokens.spacing.m).weight(1f)) {
            Text(
                text = episode.title,
                style = AuroraTextStyles.Body,
                color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                Text(
                    text = formatPublishedDate(episode.publishedAtMs),
                    style = AuroraTextStyles.Label,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
                if (episode.durationMs > 0) {
                    Text(
                        text = "• ${formatDuration(episode.durationMs)}",
                        style = AuroraTextStyles.Label,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
}

private fun formatPublishedDate(publishedAtMs: Long): String {
    if (publishedAtMs <= 0L) return ""
    return try {
        SimpleDateFormat("d MMM yyyy", Locale("pl")).format(Date(publishedAtMs))
    } catch (e: Exception) {
        ""
    }
}
