package com.aurora.player.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aurora.player.designsystem.components.AlbumArtCard
import com.aurora.player.designsystem.components.ArtworkOverlayCard
import com.aurora.player.designsystem.components.HorizontalShelf
import com.aurora.player.designsystem.components.ShelfHeading
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.ContentDomain
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.GeniusMix
import com.aurora.player.domain.model.Playlist
import com.aurora.player.domain.model.Track
import com.aurora.player.library.LibraryViewModel
import com.aurora.player.library.groupTracksByAlbum
import com.aurora.player.location.resolveCountryCodeFromLastKnownLocation
import com.aurora.player.navigation.LocalBottomChromeInset
import java.util.Locale

/**
 * Ekran startowy (Etap 39, druga runda po feedbacku "kafelki Szybkiego dostępu powielają
 * przycisk Odkrywaj z dołu, nie musi ich być w ogóle") — dashboard wyłącznie z shelfów
 * "art-first" (bez odrębnej siatki miniaturowych kafli-skrótów): pod paskiem szukaj od razu
 * "Biblioteka" + albumy → "Playlisty" → "Miksy Geniusa" → "Podkasty" → "Archiwum" → "Audiobooki"
 * → "Radio". Podkasty/Archiwum/Audiobooki/Radio NIGDY nie są puste — brak subskrypcji/historii
 * pokazuje realne PROPOZYCJE (`loadTopPodcasts`/`loadPersonalizedArchive`/
 * `loadRecommendedAudiobooks`/`loadTopRadioStations`, już istniejące w [LibraryViewModel]), żeby
 * ekran był "żywy" nawet przy zerowej bibliotece (user: "trzeba ten ekran uzupełnić o treści
 * którymi dysponujemy... wszystko co jest na froncie ma mieć okładkę").
 */
@Composable
fun HomeScreen(
    viewModel: LibraryViewModel,
    onOpenSearch: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenGeniusMixPreview: (Int) -> Unit,
    onOpenPlaylistDetail: (Long) -> Unit,
    onOpenPodcastDetail: (String) -> Unit,
    onOpenAlbum: (name: String, artist: String) -> Unit,
    onOpenArchiveItem: (String) -> Unit,
    onOpenAudiobook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAuroraTokens.current
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val geniusMixes by viewModel.geniusMixes.collectAsState()
    val podcastSubscriptions by viewModel.podcastSubscriptions.collectAsState()
    val recommendedPodcasts by viewModel.podcastSearchResults.collectAsState()
    val archiveItems by viewModel.archiveItems.collectAsState()
    val audiobookLibrary by viewModel.audiobookLibrary.collectAsState()
    val recommendedAudiobooks by viewModel.audiobookSearchResults.collectAsState()
    val radioStations by viewModel.radioStations.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadGeniusMixes() }
    LaunchedEffect(Unit) { viewModel.loadPersonalizedArchive() }
    // Podkasty/Audiobooki na Home NIGDY nie są puste — bez subskrypcji/biblioteki user i tak
    // dostaje realną propozycję, nie martwą sekcję (ten sam "zawsze jakaś lista" wzorzec co
    // Radio/AddPodcastSheet, DESIGN.md Etap 31/33/37).
    LaunchedEffect(audiobookLibrary) {
        if (audiobookLibrary.isEmpty()) viewModel.loadRecommendedAudiobooks(Locale.getDefault().isO3Language)
    }
    // Kraj rozwiązywany RAZ i współdzielony między Podkastami i Radiem (nie dwa niezależne
    // wywołania Geocodera dla tego samego wyniku).
    var resolvedCountryCode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { resolvedCountryCode = resolveCountryCodeFromLastKnownLocation(context) ?: "US" }
    LaunchedEffect(podcastSubscriptions, resolvedCountryCode) {
        val countryCode = resolvedCountryCode
        if (podcastSubscriptions.isEmpty() && countryCode != null) viewModel.loadTopPodcasts(countryCode)
    }
    LaunchedEffect(resolvedCountryCode) {
        resolvedCountryCode?.let { viewModel.loadTopRadioStations(it) }
    }

    val trackById = remember(uiState.allTracks) { uiState.allTracks.associateBy { it.id } }
    // Najnowsze album przede wszystkim — jedyna "recency" dostępna bez nowej pracy w warstwie
    // danych (Track ma dateAddedMs, nie ma play count'u), patrz DESIGN.md Etap 39.
    val recentAlbums = remember(uiState.allTracks) {
        groupTracksByAlbum(uiState.allTracks)
            .sortedByDescending { group -> group.tracks.maxOfOrNull { it.dateAddedMs } ?: 0L }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.l),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Aurora", style = AuroraTextStyles.Headline, color = MaterialTheme.colorScheme.onBackground)
            Icon(
                imageVector = Icons.Filled.AccountCircle,
                contentDescription = "Konto",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp).clickable(onClick = onOpenAccount),
            )
        }

        // Pole wyszukiwania na Home nawiguje do zakładki Szukaj (globalne, cross-domain) — to
        // jedyny wjazd do wyszukiwania w appce po usunięciu osobnej zakładki dolnej (Etap 39,
        // user: "usuwamy tą lupę z dołu bo nie chcemy powtórzeń").
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

        if (recentAlbums.isNotEmpty()) {
            ShelfHeading(title = "Biblioteka")
            HorizontalShelf {
                items(recentAlbums) { group ->
                    AlbumArtCard(
                        title = group.name,
                        subtitle = group.artist,
                        artworkUrl = group.albumArtUri,
                        onClick = { onOpenAlbum(group.name, group.artist) },
                    )
                }
            }
        }

        if (playlists.isNotEmpty()) {
            ShelfHeading(title = "Playlisty")
            HorizontalShelf {
                items(playlists) { playlist ->
                    ArtworkOverlayCard(
                        title = playlist.name,
                        subtitle = "${playlist.trackIds.size} utworów",
                        artworkUrl = playlist.firstArtworkUrl(trackById),
                        fallbackGradient = listOf(ContentDomain.Playlisty.vividColor, ContentDomain.Genius.vividColor),
                        fallbackIcon = Icons.Filled.QueueMusic,
                        onClick = { onOpenPlaylistDetail(playlist.id) },
                    )
                }
            }
        }

        if (geniusMixes.isNotEmpty()) {
            ShelfHeading(title = "Miksy Geniusa")
            HorizontalShelf {
                itemsIndexed(geniusMixes) { index, mix ->
                    ArtworkOverlayCard(
                        title = mix.name,
                        subtitle = "${mix.tracks.size} utworów",
                        artworkUrl = mix.firstArtworkUrl(),
                        fallbackGradient = listOf(ContentDomain.Genius.vividColor, ContentDomain.Radio.vividColor),
                        fallbackIcon = Icons.Filled.AutoAwesome,
                        onClick = { onOpenGeniusMixPreview(index) },
                    )
                }
            }
        }

        // Subskrypcje jeśli są, inaczej propozycje dla regionu — sekcja NIGDY nie zostaje
        // ukryta jak w Etapie 37 (`if (podcastSubscriptions.isNotEmpty())` chowało całą sekcję).
        val podcastsToShow = podcastSubscriptions.ifEmpty { null }
        if (podcastsToShow != null) {
            ShelfHeading(title = "Podkasty")
            HorizontalShelf {
                items(podcastsToShow) { podcast ->
                    ArtworkOverlayCard(
                        title = podcast.title,
                        subtitle = podcast.author,
                        artworkUrl = podcast.artworkUrl,
                        fallbackGradient = listOf(ContentDomain.Podkasty.vividColor, ContentDomain.Radio.vividColor),
                        fallbackIcon = Icons.Filled.Podcasts,
                        onClick = { onOpenPodcastDetail(podcast.feedUrl) },
                    )
                }
            }
        } else if (recommendedPodcasts.isNotEmpty()) {
            ShelfHeading(title = "Podkasty dla Ciebie")
            HorizontalShelf {
                items(recommendedPodcasts) { result ->
                    ArtworkOverlayCard(
                        title = result.title,
                        subtitle = result.author,
                        artworkUrl = result.artworkUrl,
                        fallbackGradient = listOf(ContentDomain.Podkasty.vividColor, ContentDomain.Radio.vividColor),
                        fallbackIcon = Icons.Filled.Podcasts,
                        onClick = { onOpenPodcastDetail(result.feedUrl) },
                    )
                }
            }
        }

        if (archiveItems.isNotEmpty()) {
            ShelfHeading(title = "Archiwum")
            HorizontalShelf {
                items(archiveItems) { item ->
                    ArtworkOverlayCard(
                        title = item.title,
                        subtitle = item.creator.ifBlank { item.year?.toString() },
                        artworkUrl = item.coverUrl,
                        fallbackGradient = listOf(ContentDomain.Archiwum.vividColor, ContentDomain.Podkasty.vividColor),
                        fallbackIcon = Icons.Filled.Museum,
                        onClick = { onOpenArchiveItem(item.identifier) },
                    )
                }
            }
        }

        // Biblioteka jeśli user coś dodał, inaczej propozycje wg języka urządzenia — ten sam
        // wzorzec co Podkasty wyżej.
        val audiobooksToShow = audiobookLibrary.ifEmpty { null }
        if (audiobooksToShow != null) {
            ShelfHeading(title = "Audiobooki")
            HorizontalShelf {
                items(audiobooksToShow) { book ->
                    ArtworkOverlayCard(
                        title = book.title,
                        subtitle = book.author,
                        artworkUrl = book.coverUrl,
                        fallbackGradient = listOf(ContentDomain.Audiobooki.vividColor, ContentDomain.Biblioteka.vividColor),
                        fallbackIcon = Icons.Filled.MenuBook,
                        onClick = { onOpenAudiobook(book.id) },
                    )
                }
            }
        } else if (recommendedAudiobooks.isNotEmpty()) {
            ShelfHeading(title = "Audiobooki dla Ciebie")
            HorizontalShelf {
                items(recommendedAudiobooks) { result ->
                    ArtworkOverlayCard(
                        title = result.title,
                        subtitle = result.author,
                        artworkUrl = result.coverUrl,
                        fallbackGradient = listOf(ContentDomain.Audiobooki.vividColor, ContentDomain.Biblioteka.vividColor),
                        fallbackIcon = Icons.Filled.MenuBook,
                        onClick = { onOpenAudiobook(result.id) },
                    )
                }
            }
        }

        // Radio nie ma koncepcji "biblioteki" — zawsze najpopularniejsze stacje dla regionu,
        // tap = odtwórz od razu (tak jak w RadioScreen), bez ekranu szczegółów.
        if (radioStations.isNotEmpty()) {
            ShelfHeading(title = "Radio")
            HorizontalShelf {
                items(radioStations) { station ->
                    ArtworkOverlayCard(
                        title = station.name,
                        subtitle = station.tags.ifBlank { null },
                        artworkUrl = station.faviconUrl,
                        fallbackGradient = listOf(ContentDomain.Radio.vividColor, ContentDomain.Genius.vividColor),
                        fallbackIcon = Icons.Filled.Radio,
                        onClick = { viewModel.onPlayRadioStation(station) },
                    )
                }
            }
        }

        // Pływający mini-player + dolna nawigacja (patrz AuroraNavHost/LocalBottomChromeInset) —
        // NavHost jest teraz pełnoekranowy (żeby treść realnie przewijała się pod paskiem i Haze
        // miał co rozmywać), więc ostatni shelf potrzebuje realnej wysokości paska jako spacera,
        // nie sztywnego `xl`, inaczej "Radio" chowałoby się na stałe pod nim.
        Box(modifier = Modifier.height(LocalBottomChromeInset.current + tokens.spacing.xl))
    }
}

private fun Playlist.firstArtworkUrl(trackById: Map<Long, Track>): String? =
    trackIds.firstNotNullOfOrNull { trackById[it]?.albumArtUri }

private fun GeniusMix.firstArtworkUrl(): String? =
    tracks.firstNotNullOfOrNull { it.albumArtUri }
