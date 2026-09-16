package com.aurora.player.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.Track

/**
 * Arkusz "Dodaj utwory" (odwrotność [AddToPlaylistSheet]: tu wybieramy WIELE utworów z biblioteki
 * do JEDNEJ już znanej playlisty) — DESIGN.md Etap 22. Celowo nie zamyka się po każdym dodaniu,
 * żeby dało się dodać wiele utworów pod rząd (zamyka X / tap poza arkuszem).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTracksSheet(
    allTracks: List<Track>,
    alreadyAddedTrackIds: Set<Long>,
    onAddTrack: (Track) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalAuroraTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = "Dodaj utwory",
            style = AuroraTextStyles.Title,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
        )

        if (allTracks.isEmpty()) {
            Text(
                text = "Biblioteka jest pusta.",
                style = AuroraTextStyles.Body,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(tokens.spacing.m),
            )
            return@ModalBottomSheet
        }

        LazyColumn(modifier = Modifier.padding(bottom = tokens.spacing.l)) {
            items(allTracks, key = { it.id }) { track ->
                val alreadyAdded = track.id in alreadyAddedTrackIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !alreadyAdded) { onAddTrack(track) }
                        .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s + 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        if (track.albumArtUri != null) {
                            AsyncImage(model = track.albumArtUri, contentDescription = null, modifier = Modifier.size(44.dp))
                        }
                    }
                    Column(modifier = Modifier.padding(horizontal = tokens.spacing.m).weight(1f)) {
                        Text(
                            text = track.title,
                            style = AuroraTextStyles.Body,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (alreadyAdded) 0.4f else 1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist,
                            style = AuroraTextStyles.Label,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (alreadyAdded) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Już dodane",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
