package com.aurora.player.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import java.util.concurrent.TimeUnit

private val PRESETS_MINUTES = listOf(15, 30, 45, 60)

/** Timer snu — DESIGN.md Etap 22 (obecny w wizji od sekcji 3.2 "..." menu, nigdy nie zbudowany). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    remainingMs: Long?,
    onStart: (durationMs: Long) -> Unit,
    onCancel: () -> Unit,
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
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s)) {
            Text(
                text = "Timer snu",
                style = AuroraTextStyles.Title,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (remainingMs != null) {
                Text(
                    text = "Odtwarzanie zatrzyma się za ${formatRemaining(remainingMs)}",
                    style = AuroraTextStyles.Body,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = tokens.spacing.s),
                )
                Text(
                    text = "Wyłącz timer",
                    style = AuroraTextStyles.Body,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(top = tokens.spacing.l, bottom = tokens.spacing.l)
                        .clickable {
                            onCancel()
                            onDismiss()
                        },
                )
            } else {
                Text(
                    text = "Zatrzymaj odtwarzanie automatycznie po:",
                    style = AuroraTextStyles.Label,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = tokens.spacing.s, bottom = tokens.spacing.m),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = tokens.spacing.l),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
                ) {
                    PRESETS_MINUTES.forEach { minutes ->
                        Text(
                            text = "$minutes min",
                            style = AuroraTextStyles.Label,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .clickable {
                                    onStart(TimeUnit.MINUTES.toMillis(minutes.toLong()))
                                    onDismiss()
                                }
                                .padding(vertical = tokens.spacing.m),
                        )
                    }
                }
            }
        }
    }
}

private fun formatRemaining(ms: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (totalMinutes > 0) "$totalMinutes min" else "$seconds s"
}
