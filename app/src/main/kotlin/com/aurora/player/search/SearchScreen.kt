package com.aurora.player.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.aurora.player.designsystem.components.TrackListItem
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.ContentDomain
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.TrackSource
import com.aurora.player.library.LibraryViewModel
import java.util.concurrent.TimeUnit

/**
 * "Szukaj" (Etap 37) — globalne, cross-domain wyszukiwanie z filtrem domeny. Realne wyniki na
 * razie TYLKO dla filtra "Biblioteka" (ten sam predykat co dawny inline filtr w `LibraryScreen`) —
 * pozostałe domeny nie mają jeszcze przeszukiwalnego backendu (Audiobooki/Jamendo/Archiwum) albo
 * mają własną wyszukiwarkę w swoim ekranie (Podkasty: iTunes/Podcast Index w `PodcastsScreen`),
 * więc pokazują komunikat zamiast fałszywych/pustych wyników.
 */
@Composable
fun SearchScreen(
    viewModel: LibraryViewModel,
    onTrackClick: (com.aurora.player.domain.model.Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAuroraTokens.current
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedDomain by remember { mutableStateOf(ContentDomain.Biblioteka) }

    val results = remember(uiState.allTracks, query, selectedDomain) {
        if (selectedDomain != ContentDomain.Biblioteka || query.isBlank()) {
            emptyList()
        } else {
            uiState.allTracks.filter { track ->
                track.title.contains(query, ignoreCase = true) ||
                    track.artist.contains(query, ignoreCase = true) ||
                    track.album.contains(query, ignoreCase = true)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Text(
            text = "Szukaj",
            style = AuroraTextStyles.Headline,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.l),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Szukaj we wszystkich domenach") },
            leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(imageVector = Icons.Filled.Clear, contentDescription = "Wyczyść")
                    }
                }
            },
            shape = RoundedCornerShape(999.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.m),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = tokens.spacing.m),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
            modifier = Modifier.padding(vertical = tokens.spacing.m),
        ) {
            items(ContentDomain.entries) { domain ->
                DomainFilterChip(
                    domain = domain,
                    selected = domain == selectedDomain,
                    onClick = { selectedDomain = domain },
                )
            }
        }

        when {
            query.isBlank() -> Unit

            selectedDomain != ContentDomain.Biblioteka -> {
                Box(modifier = Modifier.fillMaxSize().padding(tokens.spacing.l), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Domena „${selectedDomain.label}” jeszcze nie obsługuje wyszukiwania.",
                        style = AuroraTextStyles.Body,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            results.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Brak wyników dla „$query”",
                        style = AuroraTextStyles.Body,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }

            else -> {
                LazyColumn(contentPadding = PaddingValues(horizontal = tokens.spacing.s, vertical = tokens.spacing.s)) {
                    items(results, key = { it.id }) { track ->
                        TrackListItem(
                            title = track.title,
                            artist = track.artist,
                            albumArtUrl = track.albumArtUri,
                            durationLabel = formatDuration(track.durationMs),
                            isCurrentlyPlaying = playbackState.currentTrack?.id == track.id,
                            onClick = { onTrackClick(track) },
                            isCloudTrack = track.source == TrackSource.CLOUD,
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun DomainFilterChip(domain: ContentDomain, selected: Boolean, onClick: () -> Unit) {
    val tokens = LocalAuroraTokens.current
    Text(
        text = domain.label,
        style = AuroraTextStyles.Label,
        color = if (selected) domain.accentColor else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) domain.accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
    )
}
