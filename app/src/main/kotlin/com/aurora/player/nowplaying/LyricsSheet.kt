package com.aurora.player.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.LyricsLine
import com.aurora.player.domain.model.LyricsResult

/**
 * Panel tekstu utworu (Now Playing) — DESIGN.md Etap 24. [LyricsResult.Synced] auto-przewija się
 * i podświetla bieżącą linię wg [positionMs]; [LyricsResult.Plain] to statyczny blok (LRCLIB nie
 * zawsze ma zsynchronizowaną wersję utworu, tylko sam tekst).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    result: LyricsResult?,
    isLoading: Boolean,
    positionMs: Long,
    accentColor: Color,
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
            text = "Tekst utworu",
            style = AuroraTextStyles.Title,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = tokens.spacing.m)
                .padding(bottom = tokens.spacing.l),
        ) {
            when {
                isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = accentColor,
                )
                result == null || result is LyricsResult.NotFound -> Text(
                    text = "Brak tekstu dla tego utworu",
                    style = AuroraTextStyles.Body,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center),
                )
                result is LyricsResult.Plain -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = result.text,
                            style = AuroraTextStyles.Body,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                result is LyricsResult.Synced -> SyncedLyricsList(
                    lines = result.lines,
                    positionMs = positionMs,
                    accentColor = accentColor,
                )
            }
        }
    }
}

@Composable
private fun SyncedLyricsList(lines: List<LyricsLine>, positionMs: Long, accentColor: Color) {
    val tokens = LocalAuroraTokens.current
    val listState = rememberLazyListState()
    val currentIndex by remember(lines) {
        derivedStateOf { lines.indexOfLast { it.timestampMs <= positionMs }.coerceAtLeast(0) }
    }

    LaunchedEffect(currentIndex) {
        // Ujemny offset zamiast samego scrollToItem — bieżąca linia ląduje bliżej środka widocznego
        // obszaru zamiast przyklejać się do samej góry przy każdej zmianie.
        listState.animateScrollToItem(index = currentIndex, scrollOffset = -300)
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(lines) { index, line ->
            Text(
                text = line.text,
                style = if (index == currentIndex) AuroraTextStyles.Title else AuroraTextStyles.Body,
                color = if (index == currentIndex) {
                    accentColor
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.s),
            )
        }
    }
}
