package com.aurora.player.nowplaying

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import com.aurora.player.designsystem.components.AuroraVisualizer
import com.aurora.player.designsystem.components.sharedElementOrSelf
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.eq.EqualizerSheet
import com.aurora.player.library.LibraryViewModel
import com.aurora.player.projectm.AmbientGlow
import com.aurora.player.projectm.ProjectMEngine
import com.aurora.player.projectm.ProjectMSurface
import com.aurora.player.projectm.ProjectMVisualizerMode
import com.aurora.player.projectm.ProjectMVisualizerSettings
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.concurrent.TimeUnit

private enum class VisualizerMode { AlbumArt, Inline, Fullscreen }

/**
 * Odtwarzacz pełnoekranowy — patrz DESIGN.md sekcja 3.2. Tło i akcent koloru są wyprowadzone
 * dynamicznie z okładki albumu (Palette API, DarkMuted/Vibrant swatch) i animowane przy zmianie
 * utworu — patrz DESIGN.md sekcja 2.1 ("dwuwarstwowy model akcentu").
 * [sharedTransitionScope]/[animatedVisibilityScope] (z `AuroraNavHost`) dają płynne przejście
 * okładki z mini-playera — patrz `sharedElementOrSelf`, DESIGN.md 2.4 pkt 7.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    albumArtSharedKey: Any = "album_art",
    onOpenLicenses: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val playbackState by viewModel.playbackState.collectAsState()
    val palette by viewModel.albumArtPalette.collectAsState()
    val tokens = LocalAuroraTokens.current
    val track = playbackState.currentTrack

    val fallbackAccent = MaterialTheme.colorScheme.primary
    val backgroundTop by animateColorAsState(
        targetValue = palette.darkMuted ?: palette.darkVibrant ?: MaterialTheme.colorScheme.background,
        animationSpec = tween(400),
        label = "nowPlayingBackgroundTop",
    )
    val accentColor by animateColorAsState(
        targetValue = palette.vibrant ?: fallbackAccent,
        animationSpec = tween(400),
        label = "nowPlayingAccent",
    )
    val backgroundBrush = Brush.verticalGradient(listOf(backgroundTop, Color(0xFF06060A)))
    var showEqSheet by remember { mutableStateOf(false) }
    var visualizerMode by remember { mutableStateOf(VisualizerMode.AlbumArt) }
    val hazeState = rememberHazeState()
    val visualizerFrame by viewModel.visualizerFrame.collectAsState()

    // GLES 3.1 to twardy wymóg projectM (patrz DESIGN.md Etap 9) — na słabszych/starszych
    // urządzeniach (minSdk appki to 26, nie wszystkie mają GLES 3.1) appka po cichu spada na
    // stary generatywny AuroraVisualizer zamiast crashować lub pokazywać czarny ekran.
    val isProjectMSupported = remember { ProjectMEngine.isDeviceSupported(context) }

    // Etap 16 (DESIGN.md) — LIFTED tutaj (nie wewnętrzny stan `ProjectMSurface`), bo ramka inline
    // i nakładka pełnoekranowa to dwie OSOBNE instancje silnika (patrz komentarz w ProjectMSurface)
    // i muszą dzielić dokładnie tę samą kategorię/preset, żeby przejście między nimi nie
    // "przeskakiwało" na inną wizualizację — to była zgłoszona regresja z Etapu 10.
    val defaultVisualizerMode = remember(track?.genre) {
        ProjectMVisualizerMode.defaultForGenre(track?.genre)
    }
    var projectMMode by remember(track?.genre) { mutableStateOf(defaultVisualizerMode) }
    var currentPresetPath by remember { mutableStateOf<String?>(null) }
    var visualizerSettings by remember(track?.genre) {
        mutableStateOf(
            ProjectMVisualizerSettings(
                presetDurationSeconds = ProjectMVisualizerSettings.defaultPresetDurationForGenre(track?.genre),
            ),
        )
    }

    // Zgłoszenie: "wizualizer powinien wracać do trybu okładki i zatrzymywać animację, gdy
    // muzyka przestaje grać". `AuroraVisualizer`/projectM i tak przestają dostawać nowe próbki
    // (cisza), ale sam WIDOK ma jawnie wrócić do okładki, nie zostać "zawieszony" na wizualizerze.
    LaunchedEffect(playbackState.isPlaying) {
        if (!playbackState.isPlaying && visualizerMode != VisualizerMode.AlbumArt) {
            visualizerMode = VisualizerMode.AlbumArt
        }
    }

    // Zgłoszenie: "chcemy by wizualizer był widoczny cały czas, bez ikonek/godziny — jak
    // fullscreen na YT" — immersywny tryb systemowy (paski wracają na przeciągnięcie od
    // krawędzi, nie znikają na stałe) + blokada wygaszania ekranu, dokładnie jak w każdym
    // odtwarzaczu wideo w pełnym ekranie. Aktywne TYLKO w Fullscreen — `onDispose` przywraca
    // oba ustawienia przy wyjściu (zmiana trybu lub opuszczenie ekranu), więc nigdy nie zostają
    // "przyklejone" poza wizualizerem.
    val view = LocalView.current
    DisposableEffect(visualizerMode) {
        val window = context.findActivity()?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        if (visualizerMode == VisualizerMode.Fullscreen) {
            insetsController?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = true
        }
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }

    @Composable
    fun VisualizerSurface(surfaceModifier: Modifier, compactControls: Boolean, onTapCyclesPreset: Boolean = true) {
        if (isProjectMSupported) {
            ProjectMSurface(
                modifier = surfaceModifier,
                mode = projectMMode,
                onModeChange = { projectMMode = it },
                currentPresetPath = currentPresetPath,
                onPresetPathChange = { currentPresetPath = it },
                settings = visualizerSettings,
                onSettingsChange = { visualizerSettings = it },
                // Etap 16, zgłoszenie: chipy trybu i tryb ustawień mają być dostępne też w małej
                // ramce, nie tylko na pełnym ekranie — nie ma już osobnego "trybu bez przełącznika".
                showModeSwitcher = true,
                showSettingsButton = true,
                onTapCyclesPreset = onTapCyclesPreset,
                // Etap 18, zgłoszenie: pełne chipy tekstowe "wyglądają na zbyt duże i
                // niedopasowane" w małej ramce — tam dostają kompaktowy przycisk+menu zamiast
                // rzędu 4 chipów (patrz komentarz przy `compactControls` w ProjectMSurface.kt).
                compactControls = compactControls,
            )
        } else {
            AuroraVisualizer(
                bandMagnitudes = visualizerFrame.bandMagnitudes,
                bassEnergy = visualizerFrame.bassEnergy,
                overallEnergy = visualizerFrame.overallEnergy,
                beatCount = visualizerFrame.beatCount,
                accentColor = accentColor,
                modifier = surfaceModifier,
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .hazeSource(state = hazeState)
            .padding(tokens.spacing.m),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Zwiń",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onBack),
            )
            Spacer(modifier = Modifier.weight(1f))
            if (onOpenLicenses != null) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Licencje open source",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onOpenLicenses),
                )
                Spacer(modifier = Modifier.width(tokens.spacing.m))
            }
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Equalizer",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { showEqSheet = true },
            )
        }

        if (showEqSheet) {
            EqualizerSheet(viewModel = viewModel, onDismiss = { showEqSheet = false }, hazeState = hazeState)
        }

        // AmbientGlow rysowany PIERWSZY (czyli niżej) — poświata pulsująca basem/głośnością,
        // podbarwiona kolorem okładki, widoczna tylko gdy ramka "ucieka" spod niej przy pulsie
        // (patrz komentarz w AmbientGlow.kt: identyczne wymiary co ramka poniżej, więc w spoczynku
        // jest całkowicie ukryta pod nieprzezroczystym tłem ramki). Etap 10 część 2.
        Box(contentAlignment = Alignment.Center) {
            if (isProjectMSupported) {
                AmbientGlow(
                    bassEnergy = visualizerFrame.bassEnergy,
                    overallEnergy = visualizerFrame.overallEnergy,
                    color = accentColor,
                    colorSource = visualizerSettings.colorSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = tokens.spacing.xl)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp)),
                )
            }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = tokens.spacing.xl)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .sharedElementOrSelf(sharedTransitionScope, animatedVisibilityScope, albumArtSharedKey)
                // Etap 19, zgłoszenie: "wciąż można uruchomić wizualizer po zatrzymaniu utworu
                // kliknięciem, że chodzi bez muzyki" — sam powrót do okładki na pauzie (efekt
                // niżej) nie wystarczał, bo NIC nie blokowało ponownego ręcznego otwarcia przez
                // tap, dopóki playback pozostawał zatrzymany. Gest pokazania wizualizera wymaga
                // więc teraz OBU warunków: trybu okładki I aktywnego odtwarzania.
                .clickable(enabled = visualizerMode == VisualizerMode.AlbumArt && playbackState.isPlaying) {
                    // Etap 16, zgłoszenie: tap na okładce pokazuje wizualizer w ramce; PONOWNY tap
                    // na samym wizualizerze (obsłużony wewnątrz `ProjectMSurface`, nie tutaj) zmienia
                    // preset zamiast wracać do okładki — powrót jest teraz TYLKO przez jawny X.
                    visualizerMode = VisualizerMode.Inline
                },
            contentAlignment = Alignment.Center,
        ) {
            // Etap 18, zgłoszenie: "silnik ma się uruchomić i wczytać już od początku piosenki,
            // a dopiero po kliknięciu się pokazać" — wizualizer jest teraz ZAWSZE zamontowany od
            // pojawienia się ekranu (także w trybie okładki, po prostu niewidoczny przez alpha=0),
            // zamiast tworzyć kontekst GL i kompilować preset od zera dopiero PO tapnięciu. To
            // była realna, kilkusekundowa przerwa (zweryfikowana na żywo: pusty czarny kwadrat
            // przez ~3s po każdym powrocie z okładki). `onTapCyclesPreset` wyłączone w trybie
            // okładki, żeby niewidoczny wizualizer nie podkradał tapów należących do gestu
            // "pokaż wizualizer" obsługiwanego przez klikalność tego zewnętrznego Box. Wywołanie
            // NIE jest warunkowane `isProjectMSupported` — `VisualizerSurface` samo przełącza się
            // na lekki fallback `AuroraVisualizer` na starszych urządzeniach (patrz jej definicja
            // wyżej), więc ten sam mechanizm pre-warmu obejmuje obie ścieżki.
            //
            // ŚWIADOMIE `size(1.dp)` zamiast samego `alpha(0f)` na pełnym rozmiarze: zgłoszone i
            // zweryfikowane na żywo (emulator + telefon usera równolegle) — pełnowymiarowy,
            // niewidoczny `AndroidView`/GLSurfaceView leżący DOKŁADNIE na obszarze klikalnym
            // okładki przechwytywał tapy, zanim dotarły do `.clickable` tego zewnętrznego Box
            // (interop AndroidView + nakładający się gest Compose to znany problem). Skurczenie do
            // 1dp fizycznie usuwa nakładanie się obszarów dotykowych — silnik i załadowany preset
            // zostają "ciepłe" (`engine.setWindowSize` przy odsłonięciu to tani resize viewportu,
            // NIE ponowne tworzenie kontekstu GL/kompilacja presetu).
            VisualizerSurface(
                if (visualizerMode == VisualizerMode.AlbumArt) {
                    Modifier.size(1.dp).alpha(0f)
                } else {
                    Modifier.fillMaxSize()
                },
                compactControls = true,
                onTapCyclesPreset = visualizerMode != VisualizerMode.AlbumArt,
            )
            when (visualizerMode) {
                VisualizerMode.AlbumArt -> {
                    if (track?.albumArtUri != null) {
                        AsyncImage(
                            model = track.albumArtUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Pokaż wizualizer",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(tokens.spacing.m)
                            .size(22.dp),
                    )
                }
                VisualizerMode.Inline -> {
                    CloseVisualizerButton(
                        modifier = Modifier.align(Alignment.TopStart).padding(tokens.spacing.s),
                        onClick = { visualizerMode = VisualizerMode.AlbumArt },
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(tokens.spacing.m)
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable { visualizerMode = VisualizerMode.Fullscreen },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fullscreen,
                            contentDescription = "Pełny ekran",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                VisualizerMode.Fullscreen -> {
                    // Treść wizualizera renderuje się w tym momencie we WŁASNEJ instancji w
                    // nakładce pełnoekranowej poniżej (świadomie bez movableContentOf, patrz jej
                    // komentarz) — ramka zostaje pusta, żeby nie renderować dwóch kopii naraz.
                }
            }
        }
        }

        Column(modifier = Modifier.padding(top = tokens.spacing.xl)) {
            Text(
                text = track?.title ?: "Nic nie gra",
                style = AuroraTextStyles.Display,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track?.artist.orEmpty(),
                style = AuroraTextStyles.Body,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = tokens.spacing.xs),
            )
        }

        // Dla utworów z chmury (Google Drive) długość nie jest znana z metadanych z góry —
        // playbackState.durationMs (z samego playera) ma pierwszeństwo, gdy już jest dostępna.
        val durationMs = playbackState.durationMs.takeIf { it > 0L } ?: track?.durationMs ?: 0L
        var isDragging by remember { mutableStateOf(false) }
        var dragPositionMs by remember { mutableStateOf(0f) }
        val sliderPosition = if (isDragging) dragPositionMs else playbackState.positionMs.toFloat()

        Column(modifier = Modifier.padding(top = tokens.spacing.l)) {
            Slider(
                value = sliderPosition.coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                onValueChange = {
                    isDragging = true
                    dragPositionMs = it
                },
                onValueChangeFinished = {
                    viewModel.onSeek(dragPositionMs.toLong())
                    isDragging = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTime(sliderPosition.toLong()),
                    style = AuroraTextStyles.TimeTabular,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
                Text(
                    text = formatTime(durationMs),
                    style = AuroraTextStyles.TimeTabular,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = tokens.spacing.l),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Poprzedni",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = viewModel::onSkipPrevious),
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.xl)
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(accentColor)
                    .clickable(onClick = viewModel::onTogglePlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pauza" else "Odtwórz",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }

            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Następny",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = viewModel::onSkipNext),
            )
        }
    }

        // Pełnoekranowa nakładka — WŁASNA instancja silnika (nie ta sama co w ramce inline, patrz
        // komentarz przy VisualizerSurface: świadomie bez movableContentOf po testach na
        // emulatorze). Etap 16, zgłoszenie: tap na wizualizerze zmienia preset (obsłużone
        // wewnątrz ProjectMSurface), powrót do okładki TYLKO przez jawny X w rogu — stary gest
        // "tap gdziekolwiek zwija" usunięty, bo kolidował z nowym "tap = następny preset".
        if (visualizerMode == VisualizerMode.Fullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                VisualizerSurface(Modifier.fillMaxSize(), compactControls = false)
                CloseVisualizerButton(
                    modifier = Modifier.align(Alignment.TopStart).padding(tokens.spacing.m),
                    onClick = { visualizerMode = VisualizerMode.AlbumArt },
                )
            }
        }
    }
}

/** `LocalContext` w Compose bywa opakowany w `ContextWrapper` (motyw, itp.) — trzeba odwinąć. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun formatTime(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0))
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** Jedyny sposób powrotu do okładki z wizualizera (ramka lub pełny ekran) — Etap 16, zgłoszenie. */
@Composable
private fun CloseVisualizerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Box 48x48dp (minimalny touch target Material) wokół 28dp ikony — sam .size(28.dp).clickable()
    // dawał obszar dotykowy poniżej zalecanego minimum.
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Zamknij wizualizer",
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(28.dp),
        )
    }
}
