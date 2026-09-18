package com.aurora.player.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.aurora.player.archive.DownloadsScreen
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
 *
 * Etap 40, zgłoszenie ze zrzutem ekranu ("pasek ma być transparentny z widocznym przewijaniem w
 * warstwie pod, cieniowanie jak w Spotify"): [Scaffold] zamieniony na zwykły [Box] — `Scaffold`
 * liczył `innerPadding` tak, żeby treść NIGDY nie zachodziła pod `bottomBar` (to sam sens
 * `innerPadding`), więc mimo poprawnie podłączonego `hazeSource`/`hazeEffect` wyżej, pod paskiem
 * faktycznie nic nie było narysowane — Haze rozmywał pustkę, stąd pasek wyglądał na płaski i
 * nieprzezroczysty niezależnie od tego, co user przewijał. Teraz `NavHost` jest pełnoekranowy
 * (`fillMaxSize`, BEZ odejmowania wysokości paska), a pasek pływa NAD nim jako overlay — treść
 * realnie przewija się pod nim i jest widoczna (rozmyta) przez `hazeEffect`. Żeby ostatni element
 * listy nie chował się na stałe pod paskiem, jego zmierzona wysokość idzie przez
 * [LocalBottomChromeInset] do ekranów root (Home/Biblioteka/Odkrywaj), które dodają ją do swojego
 * dolnego `contentPadding`/spacera — pozostałe ekrany (detale, Radio/Podkasty/Audiobooki itd.)
 * nie zostały jeszcze zaktualizowane, ich ostatnia pozycja może więc dotykać krawędzi paska.
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
    val density = LocalDensity.current
    var bottomChromeHeight by remember { mutableStateOf(0.dp) }

    // Etap 44, zgłoszenie: personalizacja Archiwum ("Dla Ciebie", filtrowanie po kategorii) czyta
    // lokalną bibliotekę (`uiState.allTracks`) w momencie wywołania, ale ten skan dotąd startował
    // TYLKO wewnątrz LibraryScreen (permission launcher tam, patrz jego `LaunchedEffect`) — user,
    // który wszedł w Archiwum z Home/Odkrywaj bez uprzedniego wejścia w Bibliotekę, dostawał
    // personalizację liczoną z pustej listy utworów (zawsze fallback na "popularne", nigdy
    // faktyczne dopasowanie). Tu, na poziomie hosta (uruchamiane raz, niezależnie od aktywnej
    // zakładki), gdy uprawnienie JEST już nadane (zwykły przypadek dla powracającego usera), skan
    // startuje od razu przy starcie appki — zanim user w ogóle zdąży dotrzeć do Archiwum. Gdy
    // uprawnienia jeszcze nie ma, świadomie NIC nie robimy tutaj (brak popupu z uprawnieniem od
    // razu na starcie) — prośba o zgodę zostaje tam, gdzie była, w LibraryScreen.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(context, audioLibraryPermission) ==
            PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) libraryViewModel.onPermissionResult(true)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val bottomInset = if (showBottomChrome) bottomChromeHeight.coerceAtLeast(0.dp) else 0.dp
        CompositionLocalProvider(LocalBottomChromeInset provides bottomInset) {
            SharedTransitionLayout {
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HOME,
                    modifier = Modifier.fillMaxSize().hazeSource(state = hazeState),
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
                        onOpenDownloads = { navController.navigate(ROUTE_DOWNLOADS) },
                    )
                }
                composable(ROUTE_DISCOVER) {
                    DiscoverScreen(
                        viewModel = libraryViewModel,
                        onOpenDomain = { domain -> navController.openDomain(domain) },
                        onOpenSearch = { navController.navigateToTab(ROUTE_SEARCH) },
                    )
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
                composable(ROUTE_DOWNLOADS) {
                    DownloadsScreen(
                        viewModel = libraryViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            }
        }

        if (showBottomChrome) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .onSizeChanged { size -> bottomChromeHeight = with(density) { size.height.toDp() } },
            ) {
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
    }
}

/** Ten sam wybór wg SDK co w `LibraryScreen.kt` (osobny plik, celowy mały duplikat — patrz `formatDuration`). */
private val audioLibraryPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
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
