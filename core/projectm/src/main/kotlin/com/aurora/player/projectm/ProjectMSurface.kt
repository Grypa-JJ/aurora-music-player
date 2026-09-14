package com.aurora.player.projectm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
 *
 * @param initialMode tryb (Etap 10) na start — np. dobrany wg gatunku bieżącego utworu przez
 *   [ProjectMVisualizerMode.defaultForGenre]. Przełącznik chipów pod spodem pozwala go zmienić
 *   ręcznie w dowolnym momencie.
 * @param showModeSwitcher czy w ogóle pokazywać chipy — w małej ramce inline (Etap 9) nie ma na
 *   nie miejsca, mają sens dopiero na pełnym ekranie.
 */
@Composable
fun ProjectMSurface(
    modifier: Modifier = Modifier,
    initialMode: ProjectMVisualizerMode = ProjectMVisualizerMode.ALL,
    showModeSwitcher: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var presetsDir by remember { mutableStateOf<String?>(null) }
    var currentMode by remember(initialMode) { mutableStateOf(initialMode) }
    val viewRef = remember { mutableStateOf<ProjectMSurfaceView?>(null) }

    LaunchedEffect(Unit) {
        presetsDir = withContext(Dispatchers.IO) {
            PresetInstaller.ensureInstalled(context).absolutePath
        }
    }

    val dir = presetsDir
    if (dir != null) {
        Box(modifier = modifier) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    ProjectMSurfaceView(ctx).apply {
                        installedAssetsDir = dir
                        initialVisualizerMode = currentMode
                    }.also { viewRef.value = it }
                },
            )

            if (showModeSwitcher) {
                ModeSwitcher(
                    selected = currentMode,
                    onSelect = { mode ->
                        currentMode = mode
                        viewRef.value?.setVisualizerMode(mode)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                )
            }
        }

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

@Composable
private fun ModeSwitcher(
    selected: ProjectMVisualizerMode,
    onSelect: (ProjectMVisualizerMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        ProjectMVisualizerMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Text(
                text = mode.displayName,
                color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) Color.White else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
