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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
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
import com.aurora.player.domain.model.LyricsLine
import com.aurora.player.domain.model.LyricsResult
import com.aurora.player.domain.model.RepeatMode
import com.aurora.player.domain.model.TrackSource
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

/**
 * "Kanały" w kwadraciku Now Playing (jak przełączanie wejść telewizora) — DESIGN.md Etap 26.
 * Okładka i Napisy są pomijane w cyklu, gdy nie ma czego pokazać (brak `albumArtUri`/tekstu) —
 * Wizualizer zawsze istnieje, więc cykl nigdy nie jest pusty.
 */
private enum class NowPlayingChannel { AlbumArt, Visualizer, Lyrics }

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
    val lyricsResult by viewModel.lyricsResult.collectAsState()
    val isLoadingLyrics by viewModel.isLoadingLyrics.collectAsState()
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsState()
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
    val sleepTimerRemainingMs by viewModel.sleepTimerRemainingMs.collectAsState()
    val hazeState = rememberHazeState()

    // Etap 26, zgłoszenie: "co jeśli nie ma okładki albumu, ani tekstu — wtedy otwierany jest od
    // razu wizualizer". Wizualizer zawsze istnieje, więc jest jedynym bezpiecznym fallbackiem —
    // Napisy NIE wchodzą do tej reguły, bo w momencie otwarcia ekranu appka jeszcze nie wie, czy
    // dla utworu w ogóle istnieje tekst (ładuje się asynchronicznie), więc i tak nie mogłyby być
    // kandydatem na kanał startowy. Liczone RAZ przy pierwszym wejściu na ekran (jak dawne
    // `visualizerMode`), nie przy każdej zmianie utworu — swipe/tap między kanałami ma zostać
    // tam, gdzie user go zostawił, gdy playback po prostu przechodzi do kolejnej piosenki.
    val hasAlbumArt = track?.albumArtUri != null
    var channel by remember { mutableStateOf(if (hasAlbumArt) NowPlayingChannel.AlbumArt else NowPlayingChannel.Visualizer) }
    var isFullscreen by remember { mutableStateOf(false) }

    val hasLyrics = lyricsResult is LyricsResult.Synced || lyricsResult is LyricsResult.Plain
    // Kolejność kanałów w cyklu — DESIGN.md Etap 26: okładka → wizualizer → napisy. Pomija kanały
    // bez treści (patrz enum wyżej); Wizualizer zawsze zostaje, więc lista nigdy nie jest pusta.
    val availableChannels = buildList {
        if (hasAlbumArt) add(NowPlayingChannel.AlbumArt)
        add(NowPlayingChannel.Visualizer)
        if (hasLyrics) add(NowPlayingChannel.Lyrics)
    }
    val defaultChannel = if (hasAlbumArt) NowPlayingChannel.AlbumArt else NowPlayingChannel.Visualizer

    fun cycleChannel(forward: Boolean) {
        if (availableChannels.isEmpty()) return
        val currentIndex = availableChannels.indexOf(channel).takeIf { it >= 0 } ?: 0
        val delta = if (forward) 1 else -1
        channel = availableChannels[(currentIndex + delta + availableChannels.size) % availableChannels.size]
    }

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
    // muzyka przestaje grać" — uogólnione na `defaultChannel` (Etap 26): jeśli okładki nie ma,
    // "spoczynkiem" jest wizualizer, nie pusty kwadrat.
    LaunchedEffect(playbackState.isPlaying) {
        if (!playbackState.isPlaying && channel != defaultChannel) {
            channel = defaultChannel
            isFullscreen = false
        }
    }

    // Zgłoszenie: "chcemy by wizualizer/napisy były widoczne cały czas, bez ikonek/godziny — jak
    // fullscreen na YT" — immersywny tryb systemowy (paski wracają na przeciągnięcie od
    // krawędzi, nie znikają na stałe) + blokada wygaszania ekranu, dokładnie jak w każdym
    // odtwarzaczu wideo w pełnym ekranie. Aktywne dla OBU pełnoekranowych kanałów (Wizualizer i
    // Napisy — Etap 26 rozszerza to z samego wizualizera). `onDispose` przywraca oba ustawienia
    // przy wyjściu, więc nigdy nie zostają "przyklejone".
    val view = LocalView.current
    DisposableEffect(isFullscreen) {
        val window = context.findActivity()?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        if (isFullscreen) {
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
    // aktywnego oglądania, tylko po realnej ciszy. Etap 26: ten sam mechanizm dla Napisów.
    var fullscreenControlsVisible by remember { mutableStateOf(true) }
    var fullscreenInteractionTick by remember { mutableStateOf(0) }
    LaunchedEffect(isFullscreen, fullscreenInteractionTick) {
        if (!isFullscreen) return@LaunchedEffect
        fullscreenControlsVisible = true
        delay(5000)
        fullscreenControlsVisible = false
    }

    // Etap 22, zgłoszenie: "po 25s bez kliknięcia w menu odtwarzanych, bez możliwości edycji
    // i głupich przycisków, ekran robi się mleczny i widoczny jest tylko dobry, wyraźny widok
    // wizualizera" — ten sam wzorzec debounce co wyżej, ale dla GŁÓWNEGO ekranu (nie
    // pełnoekranowej nakładki, która ma własny, osobny 5s tryb uśpienia). Świadomie NIE liczone
    // gdy appka jest już `isFullscreen` (ten ekran wtedy w ogóle nie jest widoczny).
    var nowPlayingIdle by remember { mutableStateOf(false) }
    var nowPlayingInteractionTick by remember { mutableStateOf(0) }
    LaunchedEffect(channel, isFullscreen, nowPlayingInteractionTick) {
        if (isFullscreen) return@LaunchedEffect
        nowPlayingIdle = false
        delay(25000)
        // "widoczny jest tylko... widok wizualizera" — jeśli user zostawił appkę na samej
        // okładce (nie tapnął/nie swipnął), bezczynność sama odsłania wizualizer, zamiast
        // zatrzymać się na statycznej okładce. Wymaga isPlaying — ta sama zasada co ręczny gest
        // (Etap 19: wizualizer nie startuje bez odtwarzania).
        if (channel == NowPlayingChannel.AlbumArt && playbackState.isPlaying) {
            channel = NowPlayingChannel.Visualizer
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

    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 56.dp.toPx() } }

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .hazeSource(state = hazeState)
            // Etap 21/22: wcięcie systemowe TYLKO na tej treści, nie na wspólnym korzeniu wyżej —
            // nakładka pełnoekranowa (na dole tego pliku) jest RODZEŃSTWEM tego Column, nie jego
            // potomkiem, więc świadomie NIE dostaje tego wcięcia i może się wylewać pod paski
            // systemowe (zgłoszenie: "wizualizer ma być na całym ekranie").
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
                // Etap 26: kanał Napisy dostaje własne, jednolite tło w kolorze okładki ("nasze
                // okno na świat" ma kopiować kolor albumu, zgłoszenie usera) zamiast domyślnego
                // `surface` — ten sam `backgroundTop` co reszta ekranu, żeby paleta była spójna.
                .background(if (channel == NowPlayingChannel.Lyrics) backgroundTop else MaterialTheme.colorScheme.surface)
                .sharedElementOrSelf(sharedTransitionScope, animatedVisibilityScope, albumArtSharedKey)
                // Etap 26: swipe pozioma zmienia kanał (okładka→wizualizer→napisy, cyklicznie) —
                // tap na samym wizualizerze zostaje zajęty przez zmianę presetu ProjectM (patrz
                // `onTapCyclesPreset` niżej), więc to jedyny gest, który mógł objąć wszystkie 3
                // kanały bez kolizji z istniejącym gestem. NIEZWERYFIKOWANE NA ŻYWO — potrzebuje
                // realnego telefonu, żeby potwierdzić że Compose poprawnie odróżnia ten swipe od
                // taps należących do `.clickable` niżej i do wewnętrznego gestu ProjectMSurface.
                .pointerInput(availableChannels) {
                    var dragTotal = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onDragEnd = {
                            when {
                                dragTotal <= -swipeThresholdPx -> cycleChannel(forward = true)
                                dragTotal >= swipeThresholdPx -> cycleChannel(forward = false)
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        dragTotal += dragAmount
                    }
                }
                // Etap 19, zgłoszenie: "wciąż można uruchomić wizualizer po zatrzymaniu utworu
                // kliknięciem, że chodzi bez muzyki" — sam powrót do spoczynku na pauzie (efekt
                // wyżej) nie wystarczał, bo NIC nie blokowało ponownego ręcznego otwarcia przez
                // tap, dopóki playback pozostawał zatrzymany. Gest pokazania wizualizera wymaga
                // więc teraz OBU warunków: trybu okładki I aktywnego odtwarzania.
                .clickable(enabled = channel == NowPlayingChannel.AlbumArt && playbackState.isPlaying) {
                    // Etap 16, zgłoszenie: tap na okładce pokazuje wizualizer w ramce; PONOWNY tap
                    // na samym wizualizerze (obsłużony wewnątrz `ProjectMSurface`, nie tutaj) zmienia
                    // preset zamiast wracać do okładki — powrót jest teraz TYLKO przez jawny X.
                    channel = NowPlayingChannel.Visualizer
                },
            contentAlignment = Alignment.Center,
        ) {
            // Etap 18, zgłoszenie: "silnik ma się uruchomić i wczytać już od początku piosenki,
            // a dopiero po kliknięciu się pokazać" — wizualizer jest teraz ZAWSZE zamontowany od
            // pojawienia się ekranu (niewidoczny przy Okładce/Napisach przez alpha=0), zamiast
            // tworzyć kontekst GL i kompilować preset od zera dopiero PO tapnięciu. To była realna,
            // kilkusekundowa przerwa (zweryfikowana na żywo: pusty czarny kwadrat przez ~3s po
            // każdym powrocie z okładki). `onTapCyclesPreset` wyłączone poza kanałem Wizualizer,
            // żeby niewidoczny wizualizer nie podkradał tapów należących do innych kanałów.
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
                if (channel == NowPlayingChannel.Visualizer) Modifier.fillMaxSize() else Modifier.size(1.dp).alpha(0f),
                compactControls = true,
                onTapCyclesPreset = channel == NowPlayingChannel.Visualizer,
            )
            when (channel) {
                NowPlayingChannel.AlbumArt -> {
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
                NowPlayingChannel.Visualizer -> {
                    CloseVisualizerButton(
                        modifier = Modifier.align(Alignment.TopStart).padding(tokens.spacing.s),
                        onClick = { channel = defaultChannel },
                    )
                    FullscreenExpandButton(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(tokens.spacing.m),
                        onClick = { isFullscreen = true },
                    )
                }
                NowPlayingChannel.Lyrics -> {
                    LyricsChannelContent(
                        result = lyricsResult,
                        isLoading = isLoadingLyrics,
                        positionMs = playbackState.positionMs,
                        accentColor = accentColor,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                    )
                    CloseVisualizerButton(
                        modifier = Modifier.align(Alignment.TopStart).padding(tokens.spacing.s),
                        onClick = { channel = defaultChannel },
                    )
                    FullscreenExpandButton(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(tokens.spacing.m),
                        onClick = { isFullscreen = true },
                    )
                }
            }
        }
        }

        Row(
            modifier = Modifier
                .padding(top = tokens.spacing.xl)
                .alpha(nowPlayingChromeAlpha),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
            if (track != null) {
                val isFavorite = favoriteTrackIds.contains(track.id)
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "Usuń z ulubionych" else "Dodaj do ulubionych",
                    tint = if (isFavorite) accentColor else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .padding(start = tokens.spacing.m)
                        .size(28.dp)
                        .clickable { viewModel.onToggleFavorite(track.id) },
                )
            }
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

        // Prędkość odtwarzania — DESIGN.md Etap 25, tylko dla podcastów (muzyka prawie nigdy jej
        // nie potrzebuje, a stały rząd chipów zaśmiecałby ekran odtwarzania muzyki).
        if (track?.source == TrackSource.PODCAST) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = tokens.spacing.s)
                    .alpha(nowPlayingChromeAlpha),
                horizontalArrangement = Arrangement.Center,
            ) {
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                    val isActive = playbackState.playbackSpeed == speed
                    Text(
                        text = "${if (speed % 1f == 0f) speed.toInt() else speed}x",
                        style = AuroraTextStyles.Label,
                        color = if (isActive) accentColor else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier
                            .padding(horizontal = tokens.spacing.xs)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isActive) accentColor.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { viewModel.onSetPlaybackSpeed(speed) }
                            .padding(horizontal = tokens.spacing.s, vertical = tokens.spacing.xs),
                    )
                }
            }
        }

        // Etap 26, zgłoszenie: "ubogi panel sterowania — gdzie jest serduszko/zapętlij/losowe
        // odtwarzanie". Serduszko przeniosło się obok tytułu wyżej (wzorzec Spotify); tu zostają
        // Losowo/Poprzedni/Play/Następny/Powtarzaj w jednym rzędzie transportu.
        val canShuffle = playbackState.queue.size > 1
        val repeatIcon = if (playbackState.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat
        val repeatActive = playbackState.repeatMode != RepeatMode.OFF
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = tokens.spacing.l)
                .alpha(nowPlayingChromeAlpha),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = "Losowa kolejność",
                tint = if (playbackState.isShuffleEnabled) accentColor else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .alpha(if (canShuffle) 1f else 0.3f)
                    .size(22.dp)
                    .clickable(enabled = canShuffle, onClick = viewModel::onToggleShuffle),
            )

            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Poprzedni",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(start = tokens.spacing.l)
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

            Icon(
                imageVector = repeatIcon,
                contentDescription = "Powtarzaj",
                tint = if (repeatActive) accentColor else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(start = tokens.spacing.l)
                    .size(22.dp)
                    .clickable(onClick = viewModel::onCycleRepeatMode),
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

        // Pełnoekranowa nakładka — WŁASNA instancja silnika wizualizera (nie ta sama co w ramce
        // inline, patrz komentarz przy VisualizerSurface: świadomie bez movableContentOf po
        // testach na emulatorze). Etap 16, zgłoszenie: tap na wizualizerze zmienia preset
        // (obsłużone wewnątrz ProjectMSurface), powrót TYLKO przez jawny X w rogu — stary gest
        // "tap gdziekolwiek zwija" usunięty, bo kolidował z nowym "tap = następny preset". Etap 26
        // rozszerza tę samą nakładkę na kanał Napisy.
        if (isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (channel == NowPlayingChannel.Lyrics) backgroundTop else Color.Black),
            ) {
                when (channel) {
                    NowPlayingChannel.Visualizer -> VisualizerSurface(
                        Modifier.fillMaxSize(),
                        compactControls = false,
                        controlsVisible = fullscreenControlsVisible,
                        onInteraction = { fullscreenInteractionTick++ },
                    )
                    NowPlayingChannel.Lyrics -> LyricsChannelContent(
                        result = lyricsResult,
                        isLoading = isLoadingLyrics,
                        positionMs = playbackState.positionMs,
                        accentColor = accentColor,
                        modifier = Modifier.fillMaxSize(),
                    )
                    NowPlayingChannel.AlbumArt -> Unit // fullscreen niedostępny z tego kanału
                }
                if (fullscreenControlsVisible) {
                    CloseVisualizerButton(
                        modifier = Modifier.align(Alignment.TopStart).padding(tokens.spacing.m),
                        onClick = { isFullscreen = false },
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

/** Jedyny sposób powrotu do kanału domyślnego z Wizualizera/Napisów (ramka lub pełny ekran). */
@Composable
private fun CloseVisualizerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Box 48x48dp (minimalny touch target Material) wokół 28dp ikony — sam .size(28.dp).clickable()
    // dawał obszar dotykowy poniżej zalecanego minimum.
    // Etap 21/22, zgłoszenie: "X nie ma wchodzić pod pasek telefonu" — w ramce inline ten przycisk
    // siedzi już wewnątrz wcięcia rodzica (patrz Column z windowInsetsPadding wyżej), więc to tu
    // jest no-opem (insety już skonsumowane). W nakładce pełnoekranowej (świadomie BEZ wcięcia na
    // samym Boxie, żeby tło mogło się wylewać pod paski) to jedyne miejsce, które faktycznie
    // odsuwa X od paska statusu/notcha — działa wszędzie, jeden kod, zero rozgałęzień.
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Zamknij",
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(28.dp),
        )
    }
}

/** Rozwiń Wizualizer/Napisy na pełny ekran — Etap 26 (dawniej tylko wizualizer, Etap 16). */
@Composable
private fun FullscreenExpandButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick),
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

/**
 * Treść kanału Napisy — DESIGN.md Etap 26. [LyricsResult.Synced] przewija się jak karaoke
 * ([positionMs] → podświetlona bieżąca linia); [LyricsResult.Plain] to statyczny blok (LRCLIB nie
 * zawsze ma zsynchronizowaną wersję). Współdzielone między ramką w kwadraciku i pełnym ekranem —
 * jeden kod, dwa rozmiary przez [modifier].
 */
@Composable
private fun LyricsChannelContent(
    result: LyricsResult?,
    isLoading: Boolean,
    positionMs: Long,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            isLoading -> CircularProgressIndicator(color = accentColor)
            result == null || result is LyricsResult.NotFound -> Text(
                text = "Brak tekstu dla tego utworu",
                style = AuroraTextStyles.Body,
                color = Color.White.copy(alpha = 0.6f),
            )
            result is LyricsResult.Plain -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            ) {
                item {
                    Text(
                        text = result.text,
                        style = AuroraTextStyles.Body,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            result is LyricsResult.Synced -> SyncedLyricsKaraoke(
                lines = result.lines,
                positionMs = positionMs,
                accentColor = accentColor,
            )
        }
    }
}

@Composable
private fun SyncedLyricsKaraoke(lines: List<LyricsLine>, positionMs: Long, accentColor: Color) {
    val listState = rememberLazyListState()
    val currentIndex by remember(lines) {
        derivedStateOf { lines.indexOfLast { it.timestampMs <= positionMs }.coerceAtLeast(0) }
    }

    LaunchedEffect(currentIndex) {
        // Ujemny offset zamiast samego scrollToItem — bieżąca linia ląduje bliżej środka
        // widocznego obszaru zamiast przyklejać się do samej góry przy każdej zmianie.
        listState.animateScrollToItem(index = currentIndex, scrollOffset = -200)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 48.dp, horizontal = 20.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            Text(
                text = line.text,
                style = if (index == currentIndex) AuroraTextStyles.Title else AuroraTextStyles.Body,
                color = if (index == currentIndex) accentColor else Color.White.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
    }
}
