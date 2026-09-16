package com.aurora.player.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.Playlist

/**
 * Arkusz "Dodaj do playlisty" — DESIGN.md Etap 22. Playlisty, w których utwór już jest, mają
 * ptaszek i nie reagują na tap (usuwanie z playlisty ma osobne, jawne miejsce — menu "..." w
 * PlaylistDetailScreen — żeby ten sam gest nie oznaczał tu "dodaj", a tam "usuń").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    trackId: Long,
    playlists: List<Playlist>,
    onAddToPlaylist: (playlistId: Long) -> Unit,
    onCreatePlaylist: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalAuroraTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCreateDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = "Dodaj do playlisty",
            style = AuroraTextStyles.Title,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
        )

        LazyColumn(modifier = Modifier.padding(bottom = tokens.spacing.l)) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreateDialog = true }
                        .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = "Nowa playlista",
                        style = AuroraTextStyles.Body,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = tokens.spacing.m),
                    )
                }
            }

            items(playlists, key = { it.id }) { playlist ->
                val alreadyAdded = trackId in playlist.trackIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !alreadyAdded) { onAddToPlaylist(playlist.id) }
                        .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp),
                    )
                    Column(modifier = Modifier.padding(start = tokens.spacing.m).weight(1f)) {
                        Text(
                            text = playlist.name,
                            style = AuroraTextStyles.Body,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${playlist.trackIds.size} utworów",
                            style = AuroraTextStyles.Label,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onConfirm = { name ->
                onCreatePlaylist(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}
