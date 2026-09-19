package com.aurora.player.album

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.library.AlbumGroup
import com.aurora.player.library.LibraryViewModel
import com.aurora.player.library.groupTracksByAlbum

/**
 * Siatka albumów — DESIGN.md Etap 22/23 (tab "Albumy" z pierwotnej wizji, sekcja 3.1).
 *
 * Zgłoszenie: [LibraryScreen] ma nad tabem wyszukiwarkę/skróty/przełącznik tabów, które dotąd
 * były NA STAŁE nad listą — na mniejszych ekranach zostawiały mało miejsca. `header` to opcjonalny
 * slot na tę treść, renderowany jako pierwszy element siatki (pełna szerokość), więc przewija się
 * razem z albumami zamiast zajmować stałą przestrzeń — ten sam wzorzec co w `ArtistsScreen`.
 */
@Composable
fun AlbumsScreen(
    viewModel: LibraryViewModel,
    searchQuery: String,
    onOpenAlbum: (name: String, artist: String) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val tokens = LocalAuroraTokens.current

    val albums = remember(uiState.allTracks) { groupTracksByAlbum(uiState.allTracks) }
    val filteredAlbums = remember(albums, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            albums
        } else {
            albums.filter { it.name.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
        }
    }

    if (filteredAlbums.isEmpty()) {
        Column(modifier = modifier.fillMaxSize()) {
            header?.invoke()
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (searchQuery.isBlank()) "Brak albumów." else "Brak wyników dla „$searchQuery”",
                    style = AuroraTextStyles.Body,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.m),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.m),
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { header() }
        }
        items(filteredAlbums, key = { it.name + "|" + it.artist }) { album ->
            AlbumCard(album = album, onClick = { onOpenAlbum(album.name, album.artist) })
        }
    }
}

@Composable
private fun AlbumCard(album: AlbumGroup, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalAuroraTokens.current

    Column(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            if (album.albumArtUri != null) {
                AsyncImage(
                    model = album.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            }
        }
        Text(
            text = album.name,
            style = AuroraTextStyles.Body,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = tokens.spacing.s).basicMarquee(),
        )
        Text(
            text = album.artist,
            style = AuroraTextStyles.Label,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
