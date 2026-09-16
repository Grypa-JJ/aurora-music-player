package com.aurora.player.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens

/**
 * Wiersz utworu w bibliotece — patrz DESIGN.md sekcja 3.1.
 * Przyjmuje surowe wartości (nie domyślny model domenowy), żeby core:designsystem
 * pozostał niezależny od modułu :domain.
 */
@Composable
fun TrackListItem(
    title: String,
    artist: String,
    albumArtUrl: String?,
    durationLabel: String,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit,
    onGeniusClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    isCloudTrack: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAuroraTokens.current
    val rowBackground = if (isCurrentlyPlaying) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s + 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            if (albumArtUrl != null) {
                AsyncImage(
                    model = albumArtUrl,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = tokens.spacing.m),
        ) {
            Text(
                text = title,
                style = AuroraTextStyles.Body,
                color = if (isCurrentlyPlaying) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                style = AuroraTextStyles.Label,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isCloudTrack) {
            Icon(
                imageVector = Icons.Filled.Cloud,
                contentDescription = "Z Google Drive",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                modifier = Modifier
                    .padding(end = tokens.spacing.xs)
                    .size(14.dp),
            )
        }

        Text(
            text = durationLabel,
            style = AuroraTextStyles.TimeTabular,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )

        if (onGeniusClick != null) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "Genius: stwórz playlistę na podstawie tego utworu",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier
                    .padding(start = tokens.spacing.s)
                    .size(18.dp)
                    .clickable(onClick = onGeniusClick),
            )
        }

        if (onMoreClick != null) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                    .clickable(onClick = onMoreClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Więcej opcji",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
