package com.aurora.player.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aurora.player.album.AlbumDetailScreen
import com.aurora.player.artist.ArtistDetailScreen
import com.aurora.player.favorites.FavoritesScreen
import com.aurora.player.genius.GeniusMixPreviewScreen
import com.aurora.player.genius.GeniusMixesScreen
import com.aurora.player.library.LibraryScreen
import com.aurora.player.library.LibraryViewModel
import com.aurora.player.licenses.OpenSourceLicensesScreen
import com.aurora.player.nowplaying.NowPlayingScreen
import com.aurora.player.playlist.PlaylistDetailScreen
import com.aurora.player.playlist.PlaylistsScreen
import com.aurora.player.queue.QueueScreen
import java.net.URLDecoder
import java.net.URLEncoder

private const val ROUTE_LIBRARY = "library"
private const val ROUTE_NOW_PLAYING = "now_playing"
private const val ROUTE_GENIUS_MIXES = "genius_mixes"
private const val ROUTE_GENIUS_MIX_PREVIEW = "genius_mix_preview/{mixIndex}"
private const val ROUTE_LICENSES = "licenses"
private const val ROUTE_FAVORITES = "favorites"
private const val ROUTE_PLAYLISTS = "playlists"
private const val ROUTE_PLAYLIST_DETAIL = "playlist/{playlistId}"
private const val ROUTE_QUEUE = "queue"
private const val ROUTE_ALBUM_DETAIL = "album/{albumName}/{albumArtist}"
private const val ROUTE_ARTIST_DETAIL = "artist/{artistName}"

/** Nazwy albumów/wykonawców mogą zawierać "/", "&" itd. — trasa musi je kodować, nie brać wprost. */
private fun encodeRouteArg(value: String): String = URLEncoder.encode(value, "UTF-8")
private fun decodeRouteArg(value: String): String = URLDecoder.decode(value, "UTF-8")

/**
 * Jeden LibraryViewModel dzielony między ekranami (patrz DESIGN.md: single source of truth
 * dla stanu odtwarzania) — utworzony na poziomie hosta, nie wewnątrz composable() dla trasy,
 * żeby Library i Now Playing widziały ten sam stan playbacku.
 *
 * Całość owinięta w [SharedTransitionLayout], żeby okładka albumu mogła płynnie "lecieć"
 * z mini-playera do Now Playing (DESIGN.md sekcja 2.4 pkt 7) — `this@SharedTransitionLayout`
 * (SharedTransitionScope) i `this@composable` (AnimatedContentScope, który implementuje
 * AnimatedVisibilityScope) są przekazywane do obu ekranów.
 *
 * Etap 22: playlisty/ulubione/kolejka/podgląd Geniusa — nowe trasy dołożone płasko obok
 * istniejących (nie zagnieżdżone grafy), zgodnie z tym samym prostym wzorcem co reszta appki.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AuroraNavHost(
    libraryViewModel: LibraryViewModel,
    navController: NavHostController = rememberNavController(),
) {
    SharedTransitionLayout {
        NavHost(navController = navController, startDestination = ROUTE_LIBRARY) {
            composable(ROUTE_LIBRARY) {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    onOpenNowPlaying = { navController.navigate(ROUTE_NOW_PLAYING) },
                    onOpenGeniusMixes = { navController.navigate(ROUTE_GENIUS_MIXES) },
                    onOpenFavorites = { navController.navigate(ROUTE_FAVORITES) },
                    onOpenPlaylists = { navController.navigate(ROUTE_PLAYLISTS) },
                    onOpenAlbum = { name, artist ->
                        navController.navigate("album/${encodeRouteArg(name)}/${encodeRouteArg(artist)}")
                    },
                    onOpenArtist = { name -> navController.navigate("artist/${encodeRouteArg(name)}") },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
                )
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
        }
    }
}
