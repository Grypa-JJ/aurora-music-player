package com.aurora.player.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
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
 * Karta "queue" na Home (playlisty/miksy Geniusa/podkasty, Etap 39, DESIGN.md) — zamiast
 * statycznego koloru tła, tekst leży NA okładce ze scrimem na dole (wzorem dużych kart Spotify
 * Home), a nie pod nią jak w [AlbumArtCard]. Brak okładki → gradient z palety domeny + ikona,
 * nigdy płaska jednolita plama — to jest dokładnie mechanizm z feedbacku usera ("zamiast
 * statycznego koloru tła tam gdzie jest to możliwe odtwarzamy okładkę albumu bądź logo
 * podcastu"). 160dp — szerzej niż [AlbumArtCard] (108dp), żeby w viewporcie telefonu było
 * widać tylko 2–3 karty naraz + skrawek następnej ("kolejka", nie pełna siatka).
 *
 * Gradient+ikona rysowane są ZAWSZE jako spód, `AsyncImage` zawsze na wierzchu (gdy [artworkUrl]
 * nie jest `null`) — NIE `if/else`. Zweryfikowane na żywym emulatorze (Etap 39): `Track.albumArtUri`
 * z `MediaStoreScanner` jest zbudowane przez `ContentUris.withAppendedId` i jest NIEPUSTE nawet
 * gdy album faktycznie nie ma okładki (MediaProvider wtedy loguje `IOException: No album art
 * found`, a Coil po cichu nie renderuje nic) — `artworkUrl != null` nie znaczy "da się wczytać".
 * Rysując fallback pod obrazkiem zamiast w gałęzi `else`, porażka Coila ujawnia fallback, a nie
 * czarną dziurę.
 */
@Composable
fun ArtworkOverlayCard(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fallbackGradient: List<Color> = listOf(Color(0xFF7B2FF7), Color(0xFFFF2D87)),
    fallbackIcon: ImageVector = Icons.Filled.QueueMusic,
) {
    val tokens = LocalAuroraTokens.current

    Box(
        modifier = modifier
            .size(160.dp)
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

        // Scrim owinięty wokół samego tekstu (wysokość = treść + padding, nie sztywny %), żeby
        // biały tekst miał gwarantowany kontrast niezależnie od jasności okładki — patrz
        // DESIGN.md 2.4 pkt 3 (duotone overlay).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f)),
                    ),
                )
                .padding(tokens.spacing.s),
        ) {
            Column {
                Text(
                    text = title,
                    style = AuroraTextStyles.Body,
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
