package com.aurora.player.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurora.player.designsystem.components.DomainArtworkTile
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.ContentDomain
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.library.LibraryViewModel
import com.aurora.player.navigation.LocalBottomChromeInset

/**
 * Aspect ratio ostatniego, samotnego kafla w nieparzystym rzędzie: normalny kafel to `1.3`
 * (szerokość:wysokość) przy szerokości kolumny ~połowy rzędu, więc jego wysokość ~= kolWidth/1.3.
 * Kafel na pełną szerokość (~2×kolWidth) o POŁOWIE tej wysokości potrzebuje aspect ratio
 * `2×kolWidth / (kolWidth/1.3/2) = 5.2`, żeby wyjść "długi, ale o połowę cieńszy" (zgłoszenie usera).
 */
private const val WIDE_TILE_ASPECT_RATIO = 5.2f

/**
 * "Odkrywaj" (Etap 37) — wyłącznie treści ZEWNĘTRZNE (nie Twoje): Radio/Podkasty-katalog/
 * Audiobooki/Muzyka niezależna/Archiwum. Podkasty-katalog to celowo ten sam `ROUTE_PODCASTS` co
 * "Subskrypcje" w Bibliotece — `PodcastsScreen` już zawiera wyszukiwarkę iTunes/Podcast Index
 * (Etap 32/33), nie duplikuję jej w osobnym ekranie.
 *
 * Siatka 2-kolumnowa z [DomainArtworkTile] (Etap 41, user: "wygląd Odkrywaj ma się zgrywać z
 * Biblioteką") — zastępuje płaski `ColorfulMosaicTile` z Etapu 37/39 (świadomie zostawiony bez
 * zmian przy przebudowie Home w Etapie 39, teraz dogoniony). Okładka per kafel = pierwsza dostępna
 * z tego samego [LibraryViewModel] stanu, który Home ładuje przy starcie appki (subskrypcje/
 * propozycje podkastów, biblioteka/propozycje audiobooków, Archiwum, stacje radiowe) — CELOWO bez
 * własnych nowych zapytań sieciowych tutaj: skoro Home i Odkrywaj współdzielą jeden `viewModel`,
 * a Home jest ekranem startowym appki, te dane są już ciepłe zanim user w ogóle otworzy Odkrywaj.
 * Brak okładki (np. Muzyka niezależna, bo Home jej nie ładuje) = zwykły fallback gradient+ikona,
 * nie pustka — ta sama zasada odporności na porażkę Coila co w `ArtworkOverlayCard`.
 *
 * Etap 42, zgłoszenie po porównaniu z Home: brakowało paska "Szukaj" — Home ma go bezpośrednio
 * pod nagłówkiem jako jedyny wjazd do globalnej wyszukiwarki (Etap 39 usunął osobną zakładkę),
 * a Odkrywaj w ogóle go nie miał. Dokładnie ten sam (readonly, klik → `onOpenSearch`) wzorzec co
 * na Home, żeby nie było dwóch różnych sposobów dotarcia do tej samej wyszukiwarki.
 *
 * Etap 43 (próba: chipy kategorii + półki z konkretnymi pozycjami zamiast kafli) COFNIĘTA —
 * user po teście: tap na pozycję w półce leciał od razu w szczegóły JEDNEJ pozycji, więc zniknęła
 * jasna, jednoznaczna droga do pełnego, funkcjonalnego ekranu domeny (wyszukiwarka + filtry
 * kategorii w Archiwum itd.) — trzeba było "wejść" (dalej nawigować), żeby dostać się do czegoś
 * użytecznego. Duży kafel = jeden oczywisty cel (pełny ekran domeny), zostaje.
 */
@Composable
fun DiscoverScreen(
    viewModel: LibraryViewModel,
    onOpenDomain: (ContentDomain) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAuroraTokens.current
    val domains = listOf(
        ContentDomain.Radio,
        ContentDomain.Podkasty,
        ContentDomain.Audiobooki,
        ContentDomain.MuzykaNiezalezna,
        ContentDomain.Archiwum,
    )

    val podcastSubscriptions by viewModel.podcastSubscriptions.collectAsState()
    val recommendedPodcasts by viewModel.podcastSearchResults.collectAsState()
    val audiobookLibrary by viewModel.audiobookLibrary.collectAsState()
    val recommendedAudiobooks by viewModel.audiobookSearchResults.collectAsState()
    val radioStations by viewModel.radioStations.collectAsState()
    val archiveItems by viewModel.archiveItems.collectAsState()
    val independentTracks by viewModel.independentTracks.collectAsState()

    val artworkByDomain = remember(
        podcastSubscriptions, recommendedPodcasts, audiobookLibrary, recommendedAudiobooks,
        radioStations, archiveItems, independentTracks,
    ) {
        mapOf(
            ContentDomain.Radio to radioStations.firstNotNullOfOrNull { it.faviconUrl },
            ContentDomain.Podkasty to (podcastSubscriptions.firstOrNull()?.artworkUrl
                ?: recommendedPodcasts.firstOrNull()?.artworkUrl),
            ContentDomain.Audiobooki to (audiobookLibrary.firstOrNull()?.coverUrl
                ?: recommendedAudiobooks.firstOrNull()?.coverUrl),
            ContentDomain.MuzykaNiezalezna to independentTracks.firstNotNullOfOrNull { it.imageUrl },
            ContentDomain.Archiwum to archiveItems.firstNotNullOfOrNull { it.coverUrl },
        )
    }

    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Text(
            text = "Odkrywaj",
            style = AuroraTextStyles.Headline,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.l),
        )

        // Dokładnie ten sam (readonly, klik → onOpenSearch) pasek co na Home — patrz KDoc wyżej.
        OutlinedTextField(
            value = "",
            onValueChange = {},
            readOnly = true,
            enabled = false,
            singleLine = true,
            placeholder = { Text("Szukaj") },
            leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
            shape = RoundedCornerShape(999.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                disabledBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                disabledTextColor = MaterialTheme.colorScheme.onBackground,
                disabledLeadingIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                disabledPlaceholderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.m)
                .clickable(onClick = onOpenSearch),
        )

        LazyColumn(
            // Dolny padding = realna wysokość pływającego mini-playera/nawigacji (patrz
            // AuroraNavHost/LocalBottomChromeInset), nie tylko `spacing.s` — NavHost jest
            // pełnoekranowy, więc bez tego ostatni wiersz kafli chowałby się na stałe pod paskiem.
            contentPadding = PaddingValues(
                start = tokens.spacing.m,
                end = tokens.spacing.m,
                top = tokens.spacing.s,
                bottom = LocalBottomChromeInset.current + tokens.spacing.s,
            ),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.s),
        ) {
            items(domains.chunked(2)) { rowDomains ->
                if (rowDomains.size == 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
                    ) {
                        rowDomains.forEach { domain ->
                            DomainArtworkTile(
                                title = domain.label,
                                subtitle = if (domain.isAvailable) null else "Wkrótce",
                                artworkUrl = artworkByDomain[domain],
                                fallbackGradient = listOf(domain.vividColor, gradientPartnerFor(domain).vividColor),
                                fallbackIcon = domain.icon,
                                onClick = { onOpenDomain(domain) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else {
                    // Nieparzysta liczba domen (5) — ostatni kafel dostaje pełną szerokość zamiast
                    // kwadratu obok pustego miejsca (zgłoszenie: "nierówna ilość słabo wygląda"),
                    // za to o połowę cieńszy niż zwykły kafel (patrz WIDE_TILE_ASPECT_RATIO).
                    val domain = rowDomains.first()
                    DomainArtworkTile(
                        title = domain.label,
                        subtitle = if (domain.isAvailable) null else "Wkrótce",
                        artworkUrl = artworkByDomain[domain],
                        fallbackGradient = listOf(domain.vividColor, gradientPartnerFor(domain).vividColor),
                        fallbackIcon = domain.icon,
                        onClick = { onOpenDomain(domain) },
                        modifier = Modifier.fillMaxWidth(),
                        aspectRatio = WIDE_TILE_ASPECT_RATIO,
                    )
                }
            }
        }
    }
}

/** Drugi kolor gradientu fallbacku per domena — ten sam wzorzec parowania co `ArtworkOverlayCard` na Home. */
private fun gradientPartnerFor(domain: ContentDomain): ContentDomain = when (domain) {
    ContentDomain.Radio -> ContentDomain.Genius
    ContentDomain.Podkasty -> ContentDomain.Radio
    ContentDomain.Audiobooki -> ContentDomain.Biblioteka
    ContentDomain.MuzykaNiezalezna -> ContentDomain.Genius
    ContentDomain.Archiwum -> ContentDomain.Podkasty
    else -> ContentDomain.Genius
}
