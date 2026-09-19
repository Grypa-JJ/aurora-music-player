package com.aurora.player.archive

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.ArchiveLibraryGroup

/**
 * Jeden pobrany/streamowany item z Archiwum w sekcji "Pobrane z Archiwum" (AudiobooksScreen,
 * PodcastsScreen) — DESIGN.md Etap 53, zgłoszenie: "pobieranie ma dodawać audiobooki/podcasty z
 * Archiwum obok tych z LibriVox/realnych subskrypcji". Tap prowadzi do [ArchiveItemDetailScreen]
 * (ten sam ekran co z Archiwum — pełna lista ścieżek, pobieranie/stream per ścieżka, usuwanie) —
 * świadomie NIE duplikujemy tej logiki tutaj, tylko dajemy wejście z drugiego miejsca.
 */
@Composable
fun ArchiveLibraryGroupRow(
    group: ArchiveLibraryGroup,
    fallbackIcon: ImageVector = Icons.Filled.Museum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAuroraTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(tokens.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.background)) {
            if (group.coverUrl != null) {
                AsyncImage(model = group.coverUrl, contentDescription = null, modifier = Modifier.size(48.dp))
            } else {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp).padding(12.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(start = tokens.spacing.m)) {
            Text(
                text = group.title,
                style = AuroraTextStyles.Title,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
            Text(
                text = listOfNotNull(group.author.ifBlank { null }, "${group.tracks.size} ścieżek").joinToString(" • "),
                style = AuroraTextStyles.Label,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
