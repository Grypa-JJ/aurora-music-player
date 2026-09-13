package com.aurora.player.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aurora.player.library.LibraryScreen
import com.aurora.player.library.LibraryViewModel
import com.aurora.player.nowplaying.NowPlayingScreen

private const val ROUTE_LIBRARY = "library"
private const val ROUTE_NOW_PLAYING = "now_playing"

/**
 * Jeden LibraryViewModel dzielony między ekranami (patrz DESIGN.md: single source of truth
 * dla stanu odtwarzania) — utworzony na poziomie hosta, nie wewnątrz composable() dla trasy,
 * żeby Library i Now Playing widziały ten sam stan playbacku.
 */
@Composable
fun AuroraNavHost(
    libraryViewModel: LibraryViewModel,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = ROUTE_LIBRARY) {
        composable(ROUTE_LIBRARY) {
            LibraryScreen(
                viewModel = libraryViewModel,
                onOpenNowPlaying = { navController.navigate(ROUTE_NOW_PLAYING) },
            )
        }
        composable(ROUTE_NOW_PLAYING) {
            NowPlayingScreen(
                viewModel = libraryViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
