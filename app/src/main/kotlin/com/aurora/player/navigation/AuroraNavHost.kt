package com.aurora.player.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aurora.player.genius.GeniusMixesScreen
import com.aurora.player.library.LibraryScreen
import com.aurora.player.library.LibraryViewModel
import com.aurora.player.nowplaying.NowPlayingScreen

private const val ROUTE_LIBRARY = "library"
private const val ROUTE_NOW_PLAYING = "now_playing"
private const val ROUTE_GENIUS_MIXES = "genius_mixes"

/**
 * Jeden LibraryViewModel dzielony między ekranami (patrz DESIGN.md: single source of truth
 * dla stanu odtwarzania) — utworzony na poziomie hosta, nie wewnątrz composable() dla trasy,
 * żeby Library i Now Playing widziały ten sam stan playbacku.
 *
 * Całość owinięta w [SharedTransitionLayout], żeby okładka albumu mogła płynnie "lecieć"
 * z mini-playera do Now Playing (DESIGN.md sekcja 2.4 pkt 7) — `this@SharedTransitionLayout`
 * (SharedTransitionScope) i `this@composable` (AnimatedContentScope, który implementuje
 * AnimatedVisibilityScope) są przekazywane do obu ekranów.
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
                )
            }
            composable(ROUTE_GENIUS_MIXES) {
                GeniusMixesScreen(viewModel = libraryViewModel)
            }
        }
    }
}
