package com.aurora.player.projectm

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Punkt wejścia do wizualizera projectM w Compose. Celowo NIE montuje `AndroidView`/GLSurfaceView
 * dopóki [PresetInstaller] nie skończy jednorazowej ekstrakcji presetów z assets — inaczej
 * `onSurfaceCreated` (który czyta ścieżkę presetów tylko raz, przy tworzeniu) mógłby wystartować
 * zanim ścieżka jest gotowa, i wizualizer zostałby bez żadnego załadowanego presetu na starcie.
 *
 * Wołający (np. `NowPlayingScreen` przez `movableContentOf`) odpowiada za umieszczenie tego
 * composable w drzewie tylko wtedy, gdy [ProjectMEngine.isDeviceSupported] zwróciło true —
 * w przeciwnym razie GLSurfaceView z GLES < 3.1 może się nie stworzyć poprawnie.
 */
@Composable
fun ProjectMSurface(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var presetsDir by remember { mutableStateOf<String?>(null) }
    val viewRef = remember { mutableStateOf<ProjectMSurfaceView?>(null) }

    LaunchedEffect(Unit) {
        presetsDir = withContext(Dispatchers.IO) {
            PresetInstaller.ensureInstalled(context).absolutePath
        }
    }

    val dir = presetsDir
    if (dir != null) {
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { ctx ->
                ProjectMSurfaceView(ctx).apply {
                    installedAssetsDir = dir
                }.also { viewRef.value = it }
            },
        )

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                val view = viewRef.value ?: return@LifecycleEventObserver
                when (event) {
                    Lifecycle.Event.ON_RESUME -> view.onResume()
                    Lifecycle.Event.ON_PAUSE -> view.onPause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                viewRef.value?.release()
                viewRef.value = null
            }
        }
    }
}
