package com.aurora.player.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aurora.player.account.AccountScreen
import com.aurora.player.album.AlbumDetailScreen
import com.aurora.player.archive.ArchiveItemDetailScreen
import com.aurora.player.archive.ArchiveScreen
import com.aurora.player.artist.ArtistDetailScreen
import com.aurora.player.audiobook.AudiobookDetailScreen
import com.aurora.player.audiobook.AudiobooksScreen
import com.aurora.player.common.ComingSoonScreen
import com.aurora.player.designsystem.components.MiniPlayerBar
import com.aurora.player.designsystem.theme.ContentDomain
import com.aurora.player.discover.DiscoverScreen
import com.aurora.player.favorites.FavoritesScreen
import com.aurora.player.genius.GeniusMixPreviewScreen
import com.aurora.player.genius.GeniusMixesScreen
import com.aurora.player.home.HomeScreen
import com.aurora.player.independent.IndependentMusicScreen
import com.aurora.player.library.LibraryScreen
import com.aurora.player.library.LibraryViewModel
import com.aurora.player.licenses.OpenSourceLicensesScreen
import com.aurora.player.nowplaying.NowPlayingScreen
import com.aurora.player.playlist.PlaylistDetailScreen
import com.aurora.player.playlist.PlaylistsScreen
import com.aurora.player.podcast.PodcastDetailScreen
import com.aurora.player.podcast.PodcastsScreen
import com.aurora.player.queue.QueueScreen
import com.aurora.player.radio.RadioScreen
import com.aurora.player.search.SearchScreen
import java.net.URLDecoder
import java.net.URLEncoder

/** feedUrl to pełny URL, więc trafia w trasę zakodowany tak samo jak nazwy albumów/wykonawców. */

/** Nazwy albumów/wykonawców mogą zawierać "/", "&" itd. — trasa musi je kodować, nie brać wprost. */
private fun encodeRouteArg(value: String): String = URLEncoder.encode(value, "UTF-8")
private fun decodeRouteArg(value: String): String = URLDecoder.decode(value, "UTF-8")

/**
 * Jeden LibraryViewModel dzielony między ekranami (patrz DESIGN.md: single source of truth
 * dla stanu odtwarzania) — utworzony na poziomie hosta, nie wewnątrz composable() dla trasy,
 * żeby wszystkie zakładki widziały ten sam stan playbacku.
 *
 * Etap 37: `Scaffold` z `bottomBar` (mini-player + `AuroraBottomNav`) dołożony na tym poziomie —
 * widoczny na 3 zakładkach root (Home/Biblioteka/Odkrywaj — Etap 39 usunął "Szukaj" jako
 * zakładkę), ukryty na ekranach push/detail
 * (Now Playing, Album/Artist/Playlist Detail itd.), tak jak dawniej mini-player pokazywał się
 * tylko w obrębie LibraryScreen. Reszta tras zostaje płasko obok siebie (nie zagnieżdżone grafy),
 * zgodnie z ustaloną wcześniej konwencją appki.
 *
 * Znana uproszczona granica tej rundy: mini-player hostowany tutaj NIE dostaje już
 * `sharedTransitionScope`/`animatedVisibilityScope` (te scope'y istnieją tylko wewnątrz
 * pojedynczego `composable {}` bloku aktywnej trasy, nie na poziomie hosta) — traci się przez to
 * animację "okładka leci do Now Playing" konkretnie z mini-playera (NowPlayingScreen otwarty
 * bezpośrednio nadal ją ma). `MiniPlayerBar` degraduje się do zwykłego przejścia, bez crasha.
 *
 * Etap 39: [rememberHazeState] utworzony TU, na poziomie hosta (jeden, dzielony) — `NavHost`
 * sam oznaczony `Modifier.hazeSource`, więc cokolwiek scrolluje się pod mini-playerem/dolną
 * nawigacją na KAŻDEJ trasie, nie tylko na ekranach, które wcześniej ręcznie wołały
 * `rememberHazeState()` lokalnie (NowPlaying, Library) — te lokalne wywołania zostają bez zmian,
 * są niezależne (osobne okna blur, np. pod EQ sheet). Naprawia też realny gap: `MiniPlayerBar`
 * miał parametr `hazeState` od Etapu 37, ale to wywołanie nigdy go nie przekazywało — więc mini-
 * player nigdy nie miał prawdziwego blura, tylko płaski `surfaceColor` fallback.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AuroraNavHost(
    libraryViewModel: LibraryViewModel,
    navController: NavHostController = rememberNavController(),
) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val playbackState by libraryViewModel.playbackState.collectAsState()
    val hazeState = rememberHazeState()
    // Etap 37, poprawka po referencji Spotify: dolna nawigacja zostaje widoczna PRAWIE wszędzie
    // (Radio/Podkasty/Playlisty/Audiobooki/Archiwum/Muzyka niezależna/szczegóły itd.), tak jak w
    // Spotify — znika TYLKO na pełnoekranowym Now Playing. Poprzednia wersja pokazywała ją
    // wyłącznie na 4 zakładkach root, co dawało wrażenie "appka bez dołu" na każdym innym ekranie.
    val showBottomChrome = currentRoute != ROUTE_NOW_PLAYING

    Scaffold(
        bottomBar = {
            if (showBottomChrome) {
                Column {
                    playbackState.currentTrack?.let { track ->
                        MiniPlayerBar(
                            title = track.title,
                            artist = track.artist,
                            albumArtUrl = track.albumArtUri,
                            isPlaying = playbackState.isPlaying,
                            onTogglePlayPause = libraryViewModel::onTogglePlayPause,
                            onClick = { navController.navigate(ROUTE_NOW_PLAYING) },
                            hazeState = hazeState,
                        )
                    }
                    AuroraBottomNav(navController, hazeState)
                }
            }
        },
    ) { innerPadding ->
        SharedTransitionLayout {
            NavHost(
                navController = navController,
                startDestination = ROUTE_HOME,
                modifier = Modifier.padding(innerPadding).hazeSource(state = hazeState),
            ) {
                composable(ROUTE_HOME) {
                    HomeScreen(
                        viewModel = libraryViewModel,
                        onOpenSearch = { navController.navigateToTab(ROUTE_SEARCH) },
                        onOpenAccount = { navController.navigate(ROUTE_ACCOUNT) },
                        onOpenNowPlaying = { navController.navigate(ROUTE_NOW_PLAYING) },
                        onOpenGeniusMixPreview = { index -> navController.navigate("genius_mix_preview/$index") },
                        onOpenPlaylistDetail = { id -> navController.navigate("playlist/$id") },
                        onOpenPodcastDetail = { feedUrl ->
                            navController.navigate("podcast_detail/${encodeRouteArg(feedUrl)}")
                        },
                        onOpenAlbum = { name, artist ->
                            navController.navigate("album/${encodeRouteArg(name)}/${encodeRouteArg(artist)}")
                        },
                        onOpenArchiveItem = { identifier ->
                            navController.navigate("archive_item_detail/${encodeRouteArg(identifier)}")
                        },
                        onOpenAudiobook = { id -> navController.navigate("audiobook_detail/${encodeRouteArg(id)}") },
                    )
                }
                composable(ROUTE_LIBRARY) {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        onOpenFavorites = { navController.navigate(ROUTE_FAVORITES) },
                        onOpenPlaylists = { navController.navigate(ROUTE_PLAYLISTS) },
                        onOpenAlbum = { name, artist ->
                            navController.navigate("album/${encodeRouteArg(name)}/${encodeRouteArg(artist)}")
                        },
                        onOpenArtist = { name -> navController.navigate("artist/${encodeRouteArg(name)}") },
                        onOpenPodcasts = { navController.navigate(ROUTE_PODCASTS) },
                        onOpenAccount = { navController.navigate(ROUTE_ACCOUNT) },
                    )
                }
                composable(ROUTE_DISCOVER) {
                    DiscoverScreen(onOpenDomain = { domain -> navController.openDomain(domain) })
                }
                composable(ROUTE_SEARCH) {
                    SearchScreen(
                        viewModel = libraryViewModel,
                        onTrackClick = { track -> libraryViewModel.onTrackClick(track) },
                    )
                }
                composable(
                    ROUTE_COMING_SOON,
                    arguments = listOf(navArgument("domain") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val domainName = backStackEntry.arguments?.getString("domain").orEmpty()
                    val domain = ContentDomain.entries.find { it.name == domainName } ?: ContentDomain.Audiobooki
                    ComingSoonScreen(domain = domain, onBack = { navController.popBackStack() })
                }
                composable(ROUTE_NOW_PLAYING) {
                    NowPlayingScreen(
                        viewModel = libraryViewModel,
                        onBack = { navController.popBackStack() },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable,
                        onOpenLicenses = { navController.navigate(ROUTE_LICENSES) },
                        onOpenQueue = { navController.navigate(ROUTE_QUEUE) },
                    )
                }
                composable(ROUTE_GENIUS_MIXES) {
                    GeniusMixesScreen(
                        viewModel = libraryViewModel,
                        onOpenMixPreview = { index -> navController.navigate("genius_mix_preview/$index") },
                    )
                }
                composable(
                    ROUTE_GENIUS_MIX_PREVIEW,
                    arguments = listOf(navArgument("mixIndex") { type = NavType.IntType }),
                ) { backStackEntry ->
                    val mixIndex = backStackEntry.arguments?.getInt("mixIndex") ?: 0
                    GeniusMixPreviewScreen(
                        viewModel = libraryViewModel,
                        mixIndex = mixIndex,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(ROUTE_LICENSES) {
                    OpenSourceLicensesScreen(onBack = { navController.popBackStack() })
                }
                composable(ROUTE_FAVORITES) {
                    FavoritesScreen(
                        viewModel = libraryViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(ROUTE_PLAYLISTS) {
                    PlaylistsScreen(
                        viewModel = libraryViewModel,
                        onBack = { navController.popBackStack() },
                        onOpenPlaylist = { id -> navController.navigate("playlist/$id") },
                    )
                }
                composable(
                    ROUTE_PLAYLIST_DETAIL,
                    arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
                ) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
                    PlaylistDetailScreen(
                        viewModel = libraryViewModel,
                        playlistId = playlistId,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(ROUTE_QUEUE) {
                    QueueScreen(
                        viewModel = libraryViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    ROUTE_ALBUM_DETAIL,
                    arguments = listOf(
                        navArgument("albumName") { type = NavType.StringType },
                        navArgument("albumArtist") { type = NavType.StringType },
                    ),
                ) { backStackEntry ->
                    val albumName = decodeRouteArg(backStackEntry.arguments?.getString("albumName").orEmpty())
                    val albumArtist = decodeRouteArg(backStackEntry.arguments?.getString("albumArtist").orEmpty())
                    AlbumDetailScreen(
                        viewModel = libraryViewModel,
                        albumName = albumName,
                        albumArtist = albumArtist,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    ROUTE_ARTIST_DETAIL,
                    arguments = listOf(navArgument("artistName") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val artistName = decodeRouteArg(backStackEntry.arguments?.getString("artistName").orEmpty())
                    ArtistDetailScreen(
                        viewModel = libraryViewModel,
                        artistName = artistName,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(ROUTE_RADIO) {
                    RadioScreen(
                        viewModel = libraryViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(ROUTE_PODCASTS) {
                    PodcastsScreen(
                        viewModel = libraryViewModel,
                        onBack = { navController.popBackStack() },
                        onOpenPodcast = { feedUrl -> navController.navigate("podcast_detail/${encodeRouteArg(feedUrl)}") },
                    )
                }
                composable(
                    ROUTE_PODCAST_DETAIL,
                    arguments = listOf(navArgument("feedUrl") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val feedUrl = decodeRouteArg(backStackEntry.arguments?.getString("feedUrl").orEmpty())
                    PodcastDetailScreen(
                        viewModel = libraryViewModel,
                        feedUrl = feedUrl,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(ROUTE_ACCOUNT) {
                    AccountScreen(onBack = { navController.popBackStack() })
                }
                composable(ROUTE_AUDIOBOOKS) {
                    AudiobooksScreen(
                        viewModel = libraryViewModel,
                        onBack = { navController.popBackStack() },
                        onOpenAudiobook = { id -> navController.navigate("audiobook_detail/${encodeRouteArg(id)}") },
                    )
                }
                composable(
                    ROUTE_AUDIOBOOK_DETAIL,
                    arguments = listOf(navArgument("audiobookId") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val audiobookId = decodeRouteArg(backStackEntry.arguments?.getString("audiobookId").orEmpty())
                    AudiobookDetailScreen(
                        viewModel = libraryViewModel,
                        id = audiobookId,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(ROUTE_ARCHIVE) {
                    ArchiveScreen(
                        viewModel = libraryViewModel,
                        onBack = { navController.popBackStack() },
                        onOpenItem = { identifier ->
                            navController.navigate("archive_item_detail/${encodeRouteArg(identifier)}")
                        },
                    )
                }
                composable(
                    ROUTE_ARCHIVE_ITEM_DETAIL,
                    arguments = listOf(navArgument("identifier") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val identifier = decodeRouteArg(backStackEntry.arguments?.getString("identifier").orEmpty())
                    ArchiveItemDetailScreen(
                        viewModel = libraryViewModel,
                        identifier = identifier,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(ROUTE_INDEPENDENT_MUSIC) {
                    IndependentMusicScreen(
                        viewModel = libraryViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private fun routeForDomain(domain: ContentDomain): String = when (domain) {
    ContentDomain.Biblioteka -> ROUTE_LIBRARY
    ContentDomain.Playlisty -> ROUTE_PLAYLISTS
    ContentDomain.Genius -> ROUTE_GENIUS_MIXES
    ContentDomain.Radio -> ROUTE_RADIO
    ContentDomain.Podkasty -> ROUTE_PODCASTS
    ContentDomain.Audiobooki -> ROUTE_AUDIOBOOKS
    ContentDomain.MuzykaNiezalezna -> ROUTE_INDEPENDENT_MUSIC
    ContentDomain.Archiwum -> ROUTE_ARCHIVE
}

/** Biblioteka to zakładka root (przełączenie stanu), reszta to zwykły push na stos. */
private fun NavHostController.openDomain(domain: ContentDomain) {
    val route = routeForDomain(domain)
    if (route in BOTTOM_NAV_ROUTES) navigateToTab(route) else navigate(route)
}
