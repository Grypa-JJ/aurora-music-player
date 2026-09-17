package com.aurora.player.independent

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Search
import com.aurora.player.designsystem.components.TrackListItem
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.ContentDomain
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.library.LibraryViewModel

private fun formatSecondsLabel(durationSec: Int): String {
    if (durationSec <= 0) return ""
    val minutes = durationSec / 60
    val seconds = durationSec % 60
    return "%d:%02d".format(minutes, seconds)
}

/** Gatunki na start — najpopularniejsze tagi Jamendo, żeby przeglądanie miało od czego zacząć. */
private val GENRE_TAGS = listOf("rock", "electronic", "ambient", "acoustic", "jazz", "hiphop", "classical", "folk")

/**
 * "Muzyka niezależna" (Jamendo pod spodem, DESIGN.md Etap 37) — katalog Creative Commons od
 * niezależnych artystów. Etykieta dostawcy NIGDZIE nie pojawia się w UI, tylko atrybucja licencji
 * per utwór (wymóg przyzwoitości wobec artystów CC).
 */
@Composable
fun IndependentMusicScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tracks by viewModel.independentTracks.collectAsState()
    val isLoading by viewModel.isLoadingIndependentMusic.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val tokens = LocalAuroraTokens.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadTrendingIndependentMusic() }

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
                text = ContentDomain.MuzykaNiezalezna.label,
                style = AuroraTextStyles.Headline,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(start = tokens.spacing.xs),
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            singleLine = true,
            placeholder = { Text("Szukaj utworu albo artysty") },
            leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    selectedTag = null
                    if (searchQuery.isBlank()) viewModel.loadTrendingIndependentMusic() else viewModel.searchIndependentMusic(searchQuery)
                },
            ),
            shape = RoundedCornerShape(999.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = tokens.spacing.m),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
        ) {
            items(GENRE_TAGS) { tag ->
                FilterChip(
                    selected = selectedTag == tag,
                    onClick = {
                        searchQuery = ""
                        selectedTag = if (selectedTag == tag) null else tag
                        if (selectedTag == null) viewModel.loadTrendingIndependentMusic() else viewModel.browseIndependentMusicByTag(tag)
                    },
                    label = { Text(tag) },
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
                        text = "Brak wyników. Sprawdź, czy w local.properties jest ustawiony JAMENDO_CLIENT_ID.",
                        style = AuroraTextStyles.Body,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(tokens.spacing.l),
                    )
                }
            }

            else -> {
                LazyColumn(contentPadding = PaddingValues(horizontal = tokens.spacing.s, vertical = tokens.spacing.s)) {
                    items(tracks, key = { it.id }) { independentTrack ->
                        val track = remember(independentTrack) { independentTrack.toTrack() }
                        Column {
                            TrackListItem(
                                title = track.title,
                                artist = track.artist,
                                albumArtUrl = track.albumArtUri,
                                durationLabel = formatSecondsLabel(independentTrack.durationSec),
                                isCurrentlyPlaying = playbackState.currentTrack?.id == track.id,
                                onClick = { viewModel.onPlayIndependentTrack(independentTrack, tracks) },
                            )
                            if (independentTrack.licenseCcUrl != null) {
                                Text(
                                    text = "Creative Commons — ${independentTrack.artistName}",
                                    style = AuroraTextStyles.Caption,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                                    modifier = Modifier.padding(start = tokens.spacing.m, bottom = tokens.spacing.xs),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
