package com.aurora.player

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
                    // enableEdgeToEdge() (poniżej) rysuje appkę POD paskami systemowymi celowo
                    // (nowoczesny, immersyjny wygląd) — ale bez tego jawnego odsunięcia treści
                    // przez WindowInsets, klikalne elementy chowają się pod paskiem statusu/
                    // zegarem albo pod belką nawigacji telefonu (zgłoszone: przyciski w Now
                    // Playing i mini-player pod belką Samsunga). To systemowa, jedna poprawka
                    // dla całej appki, nie łatanie pojedynczych ikonek per ekran.
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
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
