package com.aurora.player.projectm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Punkt wejścia do wizualizera projectM w Compose. Celowo NIE montuje `AndroidView`/GLSurfaceView
 * dopóki [PresetInstaller] nie skończy jednorazowej ekstrakcji presetów z assets.
 *
 * Etap 16 (DESIGN.md) — zgłoszony bug "przejście ramka->pełny ekran losuje inny preset": [mode]
 * i [currentPresetPath] są teraz w pełni STEROWANE Z ZEWNĄTRZ (nie wewnętrzny stan jak wcześniej),
 * bo dwie osobne instancje tego composable (ramka inline i nakładka pełnoekranowa) muszą dzielić
 * dokładnie tę samą wartość, żeby użytkownik widział ten sam preset po zmianie rozmiaru — to
 * wołający (`NowPlayingScreen`) trzyma ten stan i przekazuje go obu instancjom.
 *
 * @param mode/[onModeChange] tryb (Etap 10) — sterowany, nie wewnętrzny.
 * @param currentPresetPath/[onPresetPathChange] który plik `.milk` jest aktualnie pokazywany;
 *   `null` = "jeszcze nie wybrano", ten composable sam dobierze wtedy losowy z [mode] i zgłosi
 *   przez [onPresetPathChange] (patrz [ensureValidPreset]).
 * @param onTapCyclesPreset gdy true (Etap 16, zgłoszenie: "kolejne kliknięcie zmienia
 *   wizualizację"), tap na powierzchni wizualizera (poza chipami/przyciskami) przechodzi do
 *   kolejnego presetu z tej samej kategorii zamiast robić cokolwiek innego — nawigację między
 *   trybami ekranu (ramka/pełny ekran/okładka) obsługuje wołający przez osobne przyciski.
 */
@Composable
fun ProjectMSurface(
    modifier: Modifier = Modifier,
    mode: ProjectMVisualizerMode,
    onModeChange: (ProjectMVisualizerMode) -> Unit,
    currentPresetPath: String?,
    onPresetPathChange: (String) -> Unit,
    settings: ProjectMVisualizerSettings = ProjectMVisualizerSettings(),
    onSettingsChange: (ProjectMVisualizerSettings) -> Unit = {},
    showModeSwitcher: Boolean = false,
    showSettingsButton: Boolean = false,
    onTapCyclesPreset: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var presetsDir by remember { mutableStateOf<String?>(null) }
    var showSettingsPanel by remember { mutableStateOf(false) }
    val viewRef = remember { mutableStateOf<ProjectMSurfaceView?>(null) }

    LaunchedEffect(Unit) {
        presetsDir = withContext(Dispatchers.IO) {
            PresetInstaller.ensureInstalled(context).absolutePath
        }
    }

    val dir = presetsDir
    val presetList = remember(dir, mode) {
        dir?.let { PresetLibrary.listPresets(it, mode) }.orEmpty()
    }

    // Jeśli jeszcze nic nie wybrano, ALBO poprzedni wybór nie należy już do bieżącej kategorii
    // (user zmienił tryb) — dobierz nowy, losowy preset z aktualnej listy i zgłoś w górę.
    LaunchedEffect(presetList, currentPresetPath) {
        if (presetList.isEmpty()) return@LaunchedEffect
        if (currentPresetPath == null || currentPresetPath !in presetList) {
            onPresetPathChange(presetList[Random.nextInt(presetList.size)])
        }
    }

    // Ładuje bieżący preset do JUŻ zamontowanego widoku, gdy zmieni się z zewnątrz (ręczny tap,
    // automatyczne przejście czasowe poniżej, albo świeży wybór z efektu wyżej).
    LaunchedEffect(currentPresetPath) {
        currentPresetPath?.let { viewRef.value?.jumpToPreset(it, smoothTransition = true) }
    }

    // Automatyczne przejście do kolejnego presetu po czasie z ustawień (Etap 10 część 2) —
    // celowo liczone w Kotlinie, nie przez wewnętrzny timer projectM (patrz PresetLibrary.kt:
    // playlist/timer projectM usunięty, bo nie dawał się zsynchronizować między dwiema
    // instancjami). Klucz na currentPresetPath: każda zmiana (ręczna czy automatyczna) resetuje
    // odliczanie, więc ręczny tap nie ginie zaraz potem pod automatycznym przejściem.
    LaunchedEffect(currentPresetPath, settings.presetDurationSeconds, presetList) {
        if (presetList.size <= 1 || currentPresetPath == null) return@LaunchedEffect
        delay((settings.presetDurationSeconds * 1000).toLong())
        val nextIndex = (presetList.indexOf(currentPresetPath) + 1).mod(presetList.size)
        onPresetPathChange(presetList[nextIndex])
    }

    LaunchedEffect(settings) {
        viewRef.value?.applySettings(settings)
    }

    if (dir != null) {
        Box(modifier = modifier) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        enabled = onTapCyclesPreset,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        if (presetList.size > 1 && currentPresetPath != null) {
                            val nextIndex = (presetList.indexOf(currentPresetPath) + 1).mod(presetList.size)
                            onPresetPathChange(presetList[nextIndex])
                        }
                    },
                factory = { ctx ->
                    ProjectMSurfaceView(ctx).apply {
                        installedAssetsDir = dir
                        initialSettings = settings
                        initialPresetPath = currentPresetPath
                    }.also { viewRef.value = it }
                },
            )

            if (showModeSwitcher || showSettingsButton) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showModeSwitcher) {
                        ModeSwitcher(selected = mode, onSelect = onModeChange)
                    }
                    if (showSettingsButton) {
                        SettingsButton(
                            modifier = Modifier.padding(start = 8.dp),
                            onClick = { showSettingsPanel = !showSettingsPanel },
                        )
                    }
                }
            }

            if (showSettingsPanel) {
                VisualizerSettingsPanel(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
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

@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = "Ustawienia wizualizera",
            tint = Color.White,
        )
    }
}
