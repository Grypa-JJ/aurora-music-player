package com.aurora.player.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.library.ArtistGroup
import com.aurora.player.library.LibraryViewModel
import com.aurora.player.library.groupTracksByArtist

/** Lista wykonawców — DESIGN.md Etap 22/23 (tab "Wykonawcy" z pierwotnej wizji, sekcja 3.1). */
@Composable
fun ArtistsScreen(
    viewModel: LibraryViewModel,
    searchQuery: String,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val tokens = LocalAuroraTokens.current

    val artists = remember(uiState.allTracks) { groupTracksByArtist(uiState.allTracks) }
    val filteredArtists = remember(artists, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) artists else artists.filter { it.name.contains(query, ignoreCase = true) }
    }

    if (filteredArtists.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (searchQuery.isBlank()) "Brak wykonawców." else "Brak wyników dla „$searchQuery”",
                style = AuroraTextStyles.Body,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = tokens.spacing.s, vertical = tokens.spacing.s),
    ) {
        items(filteredArtists, key = { it.name }) { artist ->
            ArtistRow(artist = artist, onClick = { onOpenArtist(artist.name) })
        }
    }
}

@Composable
private fun ArtistRow(artist: ArtistGroup, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalAuroraTokens.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s + 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp),
            )
        }
        Column(modifier = Modifier.padding(start = tokens.spacing.m)) {
            Text(
                text = artist.name,
                style = AuroraTextStyles.Body,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${artist.tracks.size} utworów",
                style = AuroraTextStyles.Label,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}
