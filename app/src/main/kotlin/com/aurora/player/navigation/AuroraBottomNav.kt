package com.aurora.player.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * 3 zakładki dolne (Etap 39, DESIGN.md — było 4 w Etapie 37): Home | Biblioteka | Odkrywaj.
 * "Szukaj" usunięta jako osobna zakładka — user: "usuwamy tą lupę z dołu bo nie chcemy
 * powtórzeń", wyszukiwanie zostaje dostępne wyłącznie przez pole na Home. Świadomie NIE ma tu
 * "Teraz odtwarzane" ani "Ustawienia" — Now Playing wjeżdża z mini-playera (swipe/tap), a nie
 * z równorzędnej zakładki (tak jak stary plan Etap 20b zakładał); Ustawienia to ikona na Home.
 *
 * Pasek jest teraz przeźroczysty/pływający (Etap 39, user: "pasek ma być przeźroczysty jak na
 * tym screenie") — `containerColor = Color.Transparent` na [NavigationBar] + prawdziwy blur
 * (Haze `thin`) na wrapującym [Column], ten sam wzorzec co
 * [com.aurora.player.designsystem.components.MiniPlayerBar]. Bez [hazeState] (np. wywołujący
 * ekran jeszcze nie oznaczył treści `Modifier.hazeSource`) pasek dostaje płaski, półprzezroczysty
 * scrim, żeby treść pod nim wciąż była czytelna — nigdy w pełni niewidzialny.
 */
private enum class BottomNavTab(val route: String, val label: String, val icon: ImageVector) {
    Home(ROUTE_HOME, "Home", Icons.Filled.Home),
    Biblioteka(ROUTE_LIBRARY, "Biblioteka", Icons.Filled.LibraryMusic),
    Odkrywaj(ROUTE_DISCOVER, "Odkrywaj", Icons.Filled.Explore),
}

@Composable
fun AuroraBottomNav(navController: NavHostController, hazeState: HazeState? = null) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState, style = HazeMaterials.thin(surfaceColor))
                } else {
                    Modifier.background(surfaceColor.copy(alpha = 0.92f))
                },
            ),
    ) {
        // Hairline "krawędź szkła" (DESIGN.md 2.1) zamiast twardego Material elevation-shadow.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color.White.copy(alpha = 0.08f)),
        )
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
