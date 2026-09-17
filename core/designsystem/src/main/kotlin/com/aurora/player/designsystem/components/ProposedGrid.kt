package com.aurora.player.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens

/**
 * Siatka 2-kolumnowa propozycji (styl "Made for you" ze Spotify Wrapped, referencja usera przy
 * Etapie 37) — dzielona przez Audiobooki i Podkasty, bo obie potrzebują dokładnie tego samego
 * kształtu (kafelek: okładka + tytuł + podtytuł). Zwykły `Column`+`Row` z `chunked(2)`, NIE
 * `LazyVerticalGrid` — listy propozycji są capowane (rzędu dziesiątek), a to pozwala osadzić
 * siatkę wewnątrz zwykłego przewijanego ekranu bez konfliktu dwóch zagnieżdżonych leniwych list.
 */
@Composable
fun <T> GridRows(
    items: List<T>,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp,
    spacing: Dp = 8.dp,
    tile: @Composable (T) -> Unit,
) {
    Column(modifier = modifier.padding(horizontal = horizontalPadding)) {
        items.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = spacing)) {
                Box(modifier = Modifier.weight(1f)) { tile(pair[0]) }
                Box(modifier = Modifier.weight(1f).padding(start = spacing)) {
                    if (pair.size > 1) tile(pair[1])
                }
            }
        }
    }
}

@Composable
fun ProposedGridTile(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    fallbackIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAuroraTokens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        Box(
            // `onSurface @ 8%` (NIE `colorScheme.background`, identyczny z tłem ekranu — przez to
            // kwadrat wyglądał jak pusta dziura zamiast widocznego placeholdera, zgłoszenie usera)
            // — zawsze widoczny kwadrat, nawet zanim/gdyby okładka się nie wczytała.
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(24.dp),
            )
            // Rysowana NAD ikoną (kolejność = z-order) — po wczytaniu przykrywa placeholder;
            // `matchParentSize()` zamiast własnego `fillMaxWidth().aspectRatio(1f)`, żeby dokładnie
            // dopasować się do rodzica bez podwójnego, potencjalnie niespójnego wymuszania proporcji.
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Column(modifier = Modifier.padding(tokens.spacing.s)) {
            Text(
                text = title,
                style = AuroraTextStyles.Label,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AuroraTextStyles.Caption,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
