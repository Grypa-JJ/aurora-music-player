package com.aurora.player.nowplaying

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
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
import androidx.compose.ui.graphics.lerp
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
import com.aurora.player.sleep.SleepTimerSheet
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
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
    onOpenQueue: (() -> Unit)? = null,
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
    val visualizerFrame by viewModel.visualizerFrame.collectAsState()
    // Zgłoszenie: "tło aplikacji reaguje na dźwięki i pulsuje w rytmie muzyki" — ten sam sygnał
    // basu co już napędza AmbientGlow za okładką, tu tylko delikatnie "oddycha" kolorem górnej
    // części gradientu w stronę koloru akcentu zamiast robić cokolwiek geometrycznie (skala/blur
    // całego ekranu przesuwałaby też tekst/przyciski, nie tylko tło — niepożądane). Spring, nie
    // tween: ten sam wzorzec wygładzania co AmbientGlow, żeby uderzenia basu nie migotały.
    val bassPulse by animateFloatAsState(
        targetValue = visualizerFrame.bassEnergy.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "nowPlayingBackgroundBassPulse",
    )
    val pulsedBackgroundTop = lerp(backgroundTop, accentColor, fraction = 0.22f * bassPulse)
    val backgroundBrush = Brush.verticalGradient(listOf(pulsedBackgroundTop, Color(0xFF06060A)))
    var showEqSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    val sleepTimerRemainingMs by viewModel.sleepTimerRemainingMs.collectAsState()
    val lyricsResult by viewModel.lyricsResult.collectAsState()
    val isLoadingLyrics by viewModel.isLoadingLyrics.collectAsState()
    var visualizerMode by remember { mutableStateOf(VisualizerMode.AlbumArt) }
    val hazeState = rememberHazeState()

    // GLES 3.1 to twardy wymóg projectM (patrz DESIGN.md Etap 9) — na słabszych/starszych
    // urządzeniach (minSdk appki to 26, nie wszystkie mają GLES 3.1) appka po cichu spada na
    // stary generatywny AuroraVisualizer zamiast crashować lub pokazywać czarny ekran.
    val isProjectMSupported = remember { ProjectMEngine.isDeviceSupported(context) }

    // Etap 16 (DESIGN.md) — LIFTED tutaj (nie wewnętrzny stan `ProjectMSurface`), bo ramka inline
    // i nakładka pełnoekranowa to dwie OSOBNE instancje silnika (patrz komentarz w ProjectMSurface)
    // i muszą dzielić dokładnie tę samą kategorię/preset, żeby przejście między nimi nie
    // "przeskakiwało" na inną wizualizację — to była zgłoszona regresja z Etapu 10.
    // Etap 20/21, zgłoszenie: domyślny tryb to zawsze ALL (patrz komentarz w
    // ProjectMVisualizerMode) — dawne dopasowanie do gatunku zawężało pulę losowania i zwiększało
    // szansę trafienia w ten sam, źle skurowany preset.
    var projectMMode by remember(track?.genre) { mutableStateOf(ProjectMVisualizerMode.ALL) }
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

    // Etap 22, zgłoszenie: "w pełnym ekranie wszystkie ikony mają zniknąć po 5s bez kontaktu,
    // jedno kliknięcie wybudza". `interactionTick` to celowy wzorzec debounce (jak auto-advance
    // presetu w ProjectMSurface) — każda interakcja go zwiększa, co RESTARTUJE poniższy efekt
    // (anuluje stare opóźnienie, zaczyna nowe 5s od zera), więc kontrolki nie znikają w trakcie
    // aktywnego oglądania, tylko po realnej ciszy.
    var fullscreenControlsVisible by remember { mutableStateOf(true) }
    var fullscreenInteractionTick by remember { mutableStateOf(0) }
    LaunchedEffect(visualizerMode, fullscreenInteractionTick) {
        if (visualizerMode != VisualizerMode.Fullscreen) return@LaunchedEffect
        fullscreenControlsVisible = true
        delay(5000)
        fullscreenControlsVisible = false
    }

    // Etap 22, zgłoszenie: "po 25s bez kliknięcia w menu odtwarzanych, bez możliwości edycji
    // i głupich przycisków, ekran robi się mleczny i widoczny jest tylko dobry, wyraźny widok
    // wizualizera" — ten sam wzorzec debounce co wyżej, ale dla GŁÓWNEGO ekranu (nie
    // pełnoekranowej nakładki, która ma własny, osobny 5s tryb uśpienia). Świadomie NIE liczone
    // gdy `visualizerMode == Fullscreen` (ten ekran wtedy w ogóle nie jest widoczny).
    var nowPlayingIdle by remember { mutableStateOf(false) }
    var nowPlayingInteractionTick by remember { mutableStateOf(0) }
    LaunchedEffect(visualizerMode, nowPlayingInteractionTick) {
        if (visualizerMode == VisualizerMode.Fullscreen) return@LaunchedEffect
        nowPlayingIdle = false
        delay(25000)
        // "widoczny jest tylko... widok wizualizera" — jeśli user zostawił appkę na samej
        // okładce (nie tapnął, żeby pokazać wizualizer), bezczynność sama odsłania wizualizer,
        // zamiast zatrzymać się na statycznej okładce. Wymaga isPlaying — ta sama zasada co
        // ręczny gest (Etap 19: wizualizer nie startuje bez odtwarzania).
        if (visualizerMode == VisualizerMode.AlbumArt && playbackState.isPlaying) {
            visualizerMode = VisualizerMode.Inline
        }
        nowPlayingIdle = true
    }
    val nowPlayingChromeAlpha by animateFloatAsState(
        targetValue = if (nowPlayingIdle) 0f else 1f,
        animationSpec = tween(600),
        label = "nowPlayingChromeAlpha",
    )

    @Composable
    fun VisualizerSurface(
        surfaceModifier: Modifier,
        compactControls: Boolean,
        onTapCyclesPreset: Boolean = true,
        controlsVisible: Boolean = true,
        onInteraction: () -> Unit = {},
    ) {
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
                controlsVisible = controlsVisible,
                onInteraction = onInteraction,
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
            // Etap 21/22: wcięcie systemowe TYLKO na tej treści, nie na wspólnym korzeniu wyżej —
            // nakładka pełnoekranowa wizualizera (na dole tego pliku) jest RODZEŃSTWEM tego
            // Column, nie jego potomkiem, więc świadomie NIE dostaje tego wcięcia i może się
            // wylewać pod paski systemowe (zgłoszenie: "wizualizer ma być na całym ekranie").
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(tokens.spacing.m),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(nowPlayingChromeAlpha),
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
            if (onOpenQueue != null) {
                Icon(
                    imageVector = Icons.Filled.QueueMusic,
                    contentDescription = "Kolejka",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onOpenQueue),
                )
                Spacer(modifier = Modifier.width(tokens.spacing.m))
            }
            Icon(
                imageVector = Icons.Filled.Bedtime,
                contentDescription = "Timer snu",
                tint = if (sleepTimerRemainingMs != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                modifier = Modifier
                    .size(24.dp)
                    .clickable { showSleepTimerSheet = true },
            )
            Spacer(modifier = Modifier.width(tokens.spacing.m))
            Icon(
                imageVector = Icons.Filled.Lyrics,
                contentDescription = "Tekst utworu",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { showLyricsSheet = true },
            )
            Spacer(modifier = Modifier.width(tokens.spacing.m))
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

        if (showSleepTimerSheet) {
            SleepTimerSheet(
                remainingMs = sleepTimerRemainingMs,
                onStart = viewModel::onStartSleepTimer,
                onCancel = viewModel::onCancelSleepTimer,
                onDismiss = { showSleepTimerSheet = false },
            )
        }

        if (showLyricsSheet) {
            LyricsSheet(
                result = lyricsResult,
                isLoading = isLoadingLyrics,
                positionMs = playbackState.positionMs,
                accentColor = accentColor,
                onDismiss = { showLyricsSheet = false },
            )
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

        Column(
            modifier = Modifier
                .padding(top = tokens.spacing.xl)
                .alpha(nowPlayingChromeAlpha),
        ) {
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

        Column(
            modifier = Modifier
                .padding(top = tokens.spacing.l)
                .alpha(nowPlayingChromeAlpha),
        ) {
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
                .padding(top = tokens.spacing.l)
                .alpha(nowPlayingChromeAlpha),
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

        // Tryb uśpienia głównego ekranu (Etap 22) — niewidoczna nakładka na wierzchu całej reszty,
        // żeby JEDEN tap gdziekolwiek (nie tylko na już-przygaszonych przyciskach) budził, i żeby
        // "bez możliwości edycji" było prawdziwe: przyciski pod spodem są wizualnie przezroczyste
        // (alpha powyżej), ale bez tej nakładki wciąż byłyby klikalne — to jedyne miejsce, które
        // faktycznie blokuje interakcję, nie tylko ją ukrywa.
        if (nowPlayingIdle) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { nowPlayingInteractionTick++ },
            )
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
                VisualizerSurface(
                    Modifier.fillMaxSize(),
                    compactControls = false,
                    controlsVisible = fullscreenControlsVisible,
                    onInteraction = { fullscreenInteractionTick++ },
                )
                if (fullscreenControlsVisible) {
                    CloseVisualizerButton(
                        modifier = Modifier.align(Alignment.TopStart).padding(tokens.spacing.m),
                        onClick = { visualizerMode = VisualizerMode.AlbumArt },
                    )
                } else {
                    // Kontrolki śpią — pełnoekranowa, niewidoczna nakładka na wierzchu, żeby
                    // JEDEN tap gdziekolwiek (nie tylko dokładnie na X/wizualizerze) budził, bez
                    // wykonywania żadnej innej akcji przy okazji (patrz komentarz przy
                    // `fullscreenInteractionTick` wyżej — to jedyne miejsce budzące, ProjectMSurface
                    // samo w sobie nie cykluje presetu dopóki `controlsVisible` nie wróci do true).
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { fullscreenInteractionTick++ },
                    )
                }
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
    // Etap 21/22, zgłoszenie: "X nie ma wchodzić pod pasek telefonu" — w ramce inline ten przycisk
    // siedzi już wewnątrz wcięcia rodzica (patrz Column z windowInsetsPadding wyżej), więc to tu
    // jest no-opem (insety już skonsumowane). W nakładce pełnoekranowej (świadomie BEZ wcięcia na
    // samym Boxie, żeby tło wizualizera mogło się wylewać pod paski) to jedyne miejsce, które
    // faktycznie odsuwa X od paska statusu/notcha — działa wszędzie, jeden kod, zero rozgałęzień.
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
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
