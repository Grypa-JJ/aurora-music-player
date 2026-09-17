package com.aurora.player.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
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

/** Promień "shelfów" Home (Etap 39) — ostrzejszy niż `large`=24dp z Biblioteki, celowo inny
 *  język wizualny dla dashboardu niż dla listy utworów. Współdzielony przez [AlbumArtCard] i
 *  [ArtworkOverlayCard], żeby wszystkie karty na Home wyglądały jak jedna rodzina. */
internal val HomeShelfCardShape = RoundedCornerShape(10.dp)

/**
 * Karta albumu w poziomym shelfie na Home ("Biblioteka" → shelf albumów, Etap 39, DESIGN.md).
 * Okładka + tytuł/wykonawca pod nią — w przeciwieństwie do [ArtworkOverlayCard] (tekst na
 * okładce), bo album ma naturalną parę linii tekstu tak jak w [TrackListItem], nie jeden tytuł.
 */
@Composable
fun AlbumArtCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAuroraTokens.current

    Column(
        modifier = modifier
            .width(108.dp)
            .clickable(onClick = onClick),
    ) {
        // Ikona zawsze rysowana jako spód, `AsyncImage` zawsze na wierzchu (nie if/else) — patrz
        // KDoc `ArtworkOverlayCard` dla pełnego wyjaśnienia: `albumArtUri` bywa niepuste, ale
        // nierozwiązywalne (album bez realnej okładki w MediaStore), więc fallback musi przeżyć
        // porażkę Coila, nie tylko `artworkUrl == null`.
        Box(
            modifier = Modifier
                .size(108.dp)
                .clip(HomeShelfCardShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Album,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(32.dp),
            )
            if (artworkUrl != null) {
                AsyncImage(model = artworkUrl, contentDescription = null, modifier = Modifier.size(108.dp))
            }
        }
        Text(
            text = title,
            style = AuroraTextStyles.Body,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = tokens.spacing.xs),
        )
        Text(
            text = subtitle,
            style = AuroraTextStyles.Label,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
