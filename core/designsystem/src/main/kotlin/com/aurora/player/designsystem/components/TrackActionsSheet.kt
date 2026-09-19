package com.aurora.player.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens

data class TrackAction(
    val icon: ImageVector,
    val label: String,
    val tint: Color? = null,
    val onClick: () -> Unit,
)

/**
 * Menu "..." dla wiersza utworu — DESIGN.md Etap 22 (obecne w wizji od sekcji 3.2, nigdy nie
 * zbudowane). Przyjmuje surowe wartości + listę [actions], nie model domenowy — jak
 * [TrackListItem], żeby core:designsystem został niezależny od :domain. Każdy ekran (Biblioteka,
 * Playlista, Ulubione, Kolejka, podgląd Geniusa) składa własną listę akcji kontekstowo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(
    title: String,
    subtitle: String,
    albumArtUrl: String?,
    actions: List<TrackAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAuroraTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Ten sam jawny promień co EqualizerSheet — bez niego ModalBottomSheet dziedziczy
        // MaterialTheme.shapes.extraLarge (999dp, myślany do pigułek) i tworzy kopułę.
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = tokens.spacing.l)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    if (albumArtUrl != null) {
                        AsyncImage(model = albumArtUrl, contentDescription = null, modifier = Modifier.size(44.dp))
                    }
                }
                Column(modifier = Modifier.padding(start = tokens.spacing.m)) {
                    Text(
                        text = title,
                        style = AuroraTextStyles.Body,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        text = subtitle,
                        style = AuroraTextStyles.Label,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            actions.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = action.onClick)
                        .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = action.tint ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = action.label,
                        style = AuroraTextStyles.Body,
                        color = action.tint ?: MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = tokens.spacing.m),
                    )
                }
            }
        }
    }
}
