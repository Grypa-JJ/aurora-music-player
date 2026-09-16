package com.aurora.player.queue

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.library.LibraryViewModel
import sh.calvin.reorderable.*

/**
 * Kolejka odtwarzania — DESIGN.md Etap 22/23 (obecne w wizji od sekcji 3.2 jako ikona "kolejka
 * odtwarzania", nigdy nie zbudowane). Reorder przez przeciąganie uchwytu (`sh.calvin.reorderable`,
 * Etap 23) — kolejka w [com.aurora.player.domain.model.PlaybackState] jest synchroniczną
 * kopią w pamięci (patrz PlayerController), więc wywołanie [LibraryViewModel.onMoveQueueItem]
 * bezpośrednio na każdym zdarzeniu przeciągania jest wystarczająco responsywne bez lokalnego
 * stanu pośredniego.
 */
@Composable
fun QueueScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val tokens = LocalAuroraTokens.current
    val queue = playbackState.queue
    val currentIndex = queue.indexOfFirst { it.id == playbackState.currentTrack?.id }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.onMoveQueueItem(from.index, to.index)
    }

    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.s, vertical = tokens.spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = "Wstecz",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(40.dp).clickable(onClick = onBack),
            )
            Text(
                text = "Kolejka",
                style = AuroraTextStyles.Headline,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = tokens.spacing.xs),
            )
        }

        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Kolejka jest pusta.",
                    style = AuroraTextStyles.Body,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(horizontal = tokens.spacing.s, vertical = tokens.spacing.s),
            ) {
                itemsIndexed(queue, key = { _, track -> track.id }) { index, track ->
                    val isPlaying = index == currentIndex
                    ReorderableItem(reorderableState, key = track.id) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "queueItemElevation")
                        Surface(
                            shadowElevation = elevation,
                            shape = RoundedCornerShape(12.dp),
                            color = if (isPlaying) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                            } else {
                                MaterialTheme.colorScheme.background
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.onPlayQueueIndex(index) }
                                    .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s + 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface),
                                ) {
                                    if (track.albumArtUri != null) {
                                        AsyncImage(model = track.albumArtUri, contentDescription = null, modifier = Modifier.size(44.dp))
                                    }
                                }
                                Column(modifier = Modifier.padding(horizontal = tokens.spacing.m).weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = AuroraTextStyles.Body,
                                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = track.artist,
                                        style = AuroraTextStyles.Label,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Usuń z kolejki",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { viewModel.onRemoveFromQueue(index) },
                                )
                                Icon(
                                    imageVector = Icons.Filled.DragHandle,
                                    contentDescription = "Przeciągnij, żeby zmienić kolejność",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .padding(start = tokens.spacing.s)
                                        .size(22.dp)
                                        .draggableHandle(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
