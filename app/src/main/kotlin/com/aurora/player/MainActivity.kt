package com.aurora.player

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aurora.player.designsystem.theme.AuroraTheme
import com.aurora.player.library.LibraryViewModel
import com.aurora.player.navigation.AuroraNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AuroraTheme {
                Surface(
                    // Etap 17 dawał tu globalne `windowInsetsPadding(WindowInsets.safeDrawing)` —
                    // jedna poprawka dla całej appki, ale kosztem tego, że ŻADEN ekran nie mógł
                    // wylać tła pod paski systemowe, nawet tam gdzie to pożądane (pełnoekranowy
                    // wizualizer, zgłoszenie: "X nie ma wchodzić pod pasek, ale wizualizer ma być
                    // na całym ekranie"). Compose nie pozwala dziecku cofnąć wcięcia nałożonego
                    // przez przodka (próba z ujemnym paddingiem rzuca wyjątkiem w runtime — patrz
                    // DESIGN.md), więc jedyny poprawny sposób to nakładać wcięcie PUNKTOWO, per
                    // ekran, tylko tam gdzie faktycznie potrzebne — patrz `LibraryScreen`,
                    // `GeniusMixesScreen`, `OpenSourceLicensesScreen`, `NowPlayingScreen` (tam
                    // tylko na treść ekranu, świadomie NIE na nakładkę pełnoekranową wizualizera).
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        RequestNotificationPermission()
                    }
                    val libraryViewModel: LibraryViewModel = viewModel()
                    AuroraNavHost(libraryViewModel = libraryViewModel)
                }
            }
        }
    }
}

/** Bez tego powiadomienie MediaSession (kontrolki w tle) nie pokaże się na Androidzie 13+. */
@androidx.compose.runtime.Composable
private fun RequestNotificationPermission() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
