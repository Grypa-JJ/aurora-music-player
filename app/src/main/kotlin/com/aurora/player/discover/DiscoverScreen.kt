package com.aurora.player.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aurora.player.designsystem.components.ColorfulMosaicTile
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.ContentDomain
import com.aurora.player.designsystem.theme.LocalAuroraTokens

/**
 * "Odkrywaj" (Etap 37) — wyłącznie treści ZEWNĘTRZNE (nie Twoje): Radio/Podkasty-katalog/
 * Audiobooki/Muzyka niezależna/Archiwum. Podkasty-katalog to celowo ten sam `ROUTE_PODCASTS` co
 * "Subskrypcje" w Bibliotece — `PodcastsScreen` już zawiera wyszukiwarkę iTunes/Podcast Index
 * (Etap 32/33), nie duplikuję jej w osobnym ekranie.
 *
 * Siatka 2-kolumnowa z `ColorfulMosaicTile` — DOKŁADNIE ten sam komponent i struktura co na Home
 * (poprawka po feedbacku usera o spójności: "Home i Discover mają wyglądać jak część jednej
 * rodziny wizualnej, różniąc się tylko zawartością siatki"). `LazyColumn` istniał już wcześniej
 * (lista pionowa) — tu tylko grupujemy domeny w pary i renderujemy każdą parę jako wiersz zamiast
 * jednej domeny na `item`, więc nie ma konfliktu zagnieżdżonego scrolla (`LazyVerticalGrid` w
 * `LazyColumn`).
 */
@Composable
fun DiscoverScreen(onOpenDomain: (ContentDomain) -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalAuroraTokens.current
    val domains = listOf(
        ContentDomain.Radio,
        ContentDomain.Podkasty,
        ContentDomain.Audiobooki,
        ContentDomain.MuzykaNiezalezna,
        ContentDomain.Archiwum,
    )

    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Text(
            text = "Odkrywaj",
            style = AuroraTextStyles.Headline,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.l),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.s),
        ) {
            items(domains.chunked(2)) { rowDomains ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
                ) {
                    rowDomains.forEach { domain ->
                        ColorfulMosaicTile(
                            icon = domain.icon,
                            label = domain.label,
                            subtitle = if (domain.isAvailable) null else "Wkrótce",
                            backgroundColor = domain.vividColor,
                            onClick = { onOpenDomain(domain) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowDomains.size < 2) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
