package com.aurora.player.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aurora.player.designsystem.theme.AuroraTextPrimary
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens

/**
 * Kafel domeny na siatce "Odkrywaj" (Etap 41) — ten sam wizualny język "art-first" co
 * [ArtworkOverlayCard] na Home (okładka pod tekstem ze scrimem na dole, gradient+ikona jako
 * fallback, NIGDY płaska jednolita plama koloru jak stary `ColorfulMosaicTile`), ale elastycznej
 * szerokości (`fillMaxWidth().aspectRatio(1.3f)`, promień [HomeShelfCardShape]) zamiast sztywnych
 * 160dp — bo Odkrywaj to pełnoszerokościowa siatka 2 kolumn, nie poziomy "shelf z peekiem".
 *
 * Gradient+ikona ZAWSZE rysowane jako spód, `AsyncImage` zawsze na wierzchu (nie if/else) — ta
 * sama zasada co [ArtworkOverlayCard]/[AlbumArtCard]: niepusty URL nie gwarantuje wczytania się
 * obrazka, więc porażka Coila ma ujawniać fallback, nie czarną dziurę.
 *
 * [aspectRatio] konfigurowalny (domyślnie 1.3, jak dawniej) — Odkrywaj używa węższej wartości dla
 * ostatniego, samotnego kafla w nieparzystym rzędzie (pełna szerokość, połowa wysokości zamiast
 * kwadratu obok pustego miejsca), patrz `DiscoverScreen`.
 */
@Composable
fun DomainArtworkTile(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    fallbackGradient: List<Color>,
    fallbackIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1.3f,
) {
    val tokens = LocalAuroraTokens.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(HomeShelfCardShape)
            .background(Brush.linearGradient(fallbackGradient))
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp).align(Alignment.Center),
        )
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f)),
                    ),
                )
                .padding(tokens.spacing.m),
        ) {
            Column {
                Text(
                    text = title,
                    style = AuroraTextStyles.Title,
                    color = AuroraTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = AuroraTextStyles.Caption,
                        color = AuroraTextPrimary.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
