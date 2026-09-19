package com.aurora.player.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.chrisbanes.haze.HazeState

/**
 * 3 zakładki dolne (Etap 39, DESIGN.md — było 4 w Etapie 37): Home | Biblioteka | Odkrywaj.
 * "Szukaj" usunięta jako osobna zakładka — user: "usuwamy tą lupę z dołu bo nie chcemy
 * powtórzeń", wyszukiwanie zostaje dostępne wyłącznie przez pole na Home. Świadomie NIE ma tu
 * "Teraz odtwarzane" ani "Ustawienia" — Now Playing wjeżdża z mini-playera (swipe/tap), a nie
 * z równorzędnej zakładki (tak jak stary plan Etap 20b zakładał); Ustawienia to ikona na Home.
 *
 * Zgłoszenie: dotychczasowy frosted-glass blur (Haze `thin`) zastąpiony w pełni przezroczystym
 * tłem — pasek ma odsłaniać maksimum ekranu pod spodem, nie wyglądać jak osobna szyba. Gradient
 * scrim (przezroczysty → czarny) żyje jeden poziom wyżej, na całej grupie MiniPlayerBar+ten pasek
 * (patrz `AuroraNavHost`) — TU osobny gradient dawałby podwójne przyciemnienie w miejscu zakładki.
 * [hazeState] zostaje w sygnaturze (wołający ekran wciąż może go przekazywać), ale nie jest już
 * używany — blur celowo usunięty.
 */
private enum class BottomNavTab(val route: String, val label: String, val icon: ImageVector) {
    Home(ROUTE_HOME, "Home", Icons.Filled.Home),
    Biblioteka(ROUTE_LIBRARY, "Biblioteka", Icons.Filled.LibraryMusic),
    Odkrywaj(ROUTE_DISCOVER, "Odkrywaj", Icons.Filled.Explore),
}

@Composable
fun AuroraBottomNav(navController: NavHostController, hazeState: HazeState? = null) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Column(modifier = Modifier.fillMaxWidth()) {
        NavigationBar(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            BottomNavTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = currentRoute == tab.route,
                    onClick = { navController.navigateToTab(tab.route) },
                    icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                    label = { Text(tab.label) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ),
                )
            }
        }
    }
}
