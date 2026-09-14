package com.aurora.player.projectm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
 * @param compactControls gdy true (mała ramka inline — zgłoszenie: "ambient/spectrum/particle
 *   w tym małym okienku wyglądają na zbyt duże i niedopasowane"), przełącznik trybu to jeden
 *   mały okrągły przycisk z ikoną + menu rozwijane zamiast rzędu 4 pełnych chipów tekstowych,
 *   które w wąskiej ramce nie mieszczą się bez zawijania tekstu. Pełny ekran ma dość miejsca,
 *   więc tam zostaje pełny rząd (false).
 *
 * UKŁAD ROGÓW (świadoma zasada, nie przypadek — każdy róg ma DOKŁADNIE jednego właściciela,
 * żeby żadne dwa elementy nigdy się nie nakładały, niezależnie od tego jak szeroki jest rząd
 * chipów): ten composable rysuje TYLKO przycisk ustawień (zawsze top-end) i przełącznik trybu
 * (zawsze dolny — bottom-start jako kompakt, bottom-center jako pełny rząd). X-zamknięcia
 * (top-start) i ikonę pełnego ekranu (bottom-end, tylko w ramce inline) rysuje WOŁAJĄCY
 * (`NowPlayingScreen`) — Etap 16/18, zgłoszenie o nachodzących na siebie przyciskach: wcześniej
 * oba poziomy (ten composable + wołający) używały tego samego rogu bottom-end niezależnie od
 * siebie, co przy szerokich chipach dawało realne nakładanie się obszarów dotykowych
 * (zweryfikowane na żywo przez `uiautomator dump`: ikona pełnego ekranu miała hitbox
 * dosłownie pokrywający się z chipem "Particle" i przyciskiem ustawień).
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
    compactControls: Boolean = false,
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

            // Przycisk ustawień: ZAWSZE top-end, w obu rozmiarach — patrz komentarz przy
            // sygnaturze funkcji. Osobny róg niż przełącznik trybu (dół), więc nie mogą się
            // zderzyć niezależnie od szerokości rzędu chipów.
            if (showSettingsButton) {
                SettingsButton(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    onClick = { showSettingsPanel = !showSettingsPanel },
                )
            }

            // Przełącznik trybu: chowany, gdy panel ustawień jest otwarty (panel go i tak
            // zastępuje merytorycznie — pokazywanie obu naraz to zbędny bałagan, nie oszczędność
            // miejsca), więc nie ma szans na kolizję z panelem.
            if (showModeSwitcher && !showSettingsPanel) {
                if (compactControls) {
                    CompactModeButton(
                        selected = mode,
                        onSelect = onModeChange,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                    )
                } else {
                    ModeSwitcher(
                        selected = mode,
                        onSelect = onModeChange,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp),
                    )
                }
            }

            if (showSettingsPanel) {
                // Scrim pod panelem: przechwytuje tapy POZA panelem i zamyka go (standardowy
                // wzorzec "tap outside to dismiss"), zamiast zostawiać martwe miejsce, w które
                // tap nic nie robi.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { showSettingsPanel = false },
                )
                VisualizerSettingsPanel(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        // Pochłania własne tapy, żeby scrim pod spodem nie zamykał panelu przy
                        // kliknięciu wewnątrz niego (np. w pustą przestrzeń między kontrolkami).
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {},
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
            // Box zamiast samego Text: Material zaleca min. 48x48dp obszaru dotykowego (zgłoszenie
            // usera o "dobrych zasadach i praktykach") — sam napis + stara padding(8dp) dawał
            // realnie ~36dp wysokości, poniżej minimum. Wizualnie pigułka zostaje kompaktowa,
            // touch target rośnie niewidocznie wokół niej.
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) Color.White else Color.Transparent)
                    .clickable { onSelect(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.displayName,
                    color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * Kompaktowa wersja przełącznika trybu dla małej ramki inline (Etap 16/18, zgłoszenie: pełne
 * chipy tekstowe "wyglądają na zbyt duże i niedopasowane" w wąskim oknie) — jeden okrągły
 * przycisk z ikoną BIEŻĄCEGO trybu, tap otwiera standardowe Material3 `DropdownMenu` z listą
 * wszystkich trybów. Stały rozmiar niezależnie od liczby/długości nazw trybów, więc nie ma ryzyka
 * zawijania tekstu jak przy pełnym rzędzie chipów.
 */
@Composable
private fun CompactModeButton(
    selected: ProjectMVisualizerMode,
    onSelect: (ProjectMVisualizerMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = selected.icon,
                contentDescription = "Tryb wizualizera: ${selected.displayName}, zmień",
                tint = Color.White,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ProjectMVisualizerMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayName) },
                    leadingIcon = { Icon(imageVector = mode.icon, contentDescription = null) },
                    trailingIcon = {
                        if (mode == selected) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = "Ustawienia wizualizera",
            tint = Color.White,
        )
    }
}
