package com.aurora.player.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddToQueue
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aurora.player.album.AlbumsScreen
import com.aurora.player.artist.ArtistsScreen
import com.aurora.player.designsystem.components.DomainShortcutCard
import com.aurora.player.designsystem.components.TrackAction
import com.aurora.player.designsystem.components.TrackActionsSheet
import com.aurora.player.designsystem.components.TrackListItem
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TrackSource
import com.aurora.player.navigation.LocalBottomChromeInset
import com.aurora.player.playlist.AddToPlaylistSheet
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import java.util.concurrent.TimeUnit

private val audioPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

/** Taby "Utwory/Albumy/Wykonawcy" z pierwotnej wizji appki (DESIGN.md sekcja 3.1, Etap 22/23). */
private enum class LibraryTab(val label: String) {
    Tracks("Utwory"),
    Albums("Albumy"),
    Artists("Wykonawcy"),
}

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenFavorites: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenAlbum: (name: String, artist: String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPodcasts: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isCloudSignedIn by viewModel.isCloudSignedIn.collectAsState()
    val cloudAccountEmail by viewModel.cloudAccountEmail.collectAsState()
    val isWebDavConnected by viewModel.isWebDavConnected.collectAsState()
    val webDavServerLabel by viewModel.webDavServerLabel.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val favoriteTrackIds by viewModel.favoriteTrackIds.collectAsState()
    val archiveLibraryTracks by viewModel.archiveLibraryTracks.collectAsState()
    val tokens = LocalAuroraTokens.current
    val hazeState = rememberHazeState()

    var permissionRequested by remember { mutableStateOf(false) }
    var trackForMenu by remember { mutableStateOf<Track?>(null) }
    var trackForPlaylistSheet by remember { mutableStateOf<Track?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSourcesSheet by remember { mutableStateOf(false) }
    var showWebDavDialog by remember { mutableStateOf(false) }
    var isConnectingWebDav by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(LibraryTab.Tracks) }

    // Etap 22, user: ekran startowy ma mieć użyteczność Spotify — wyszukiwarka była największym
    // brakiem (biblioteka bez niej jest przeszukiwalna tylko przez ręczne scrollowanie). Filtr
    // czysto lokalny (nie przez VM/repozytorium) — to prosty substring na już wczytanej liście,
    // bez potrzeby osobnego stanu ani zapytania.
    val filteredTracks = remember(uiState.allTracks, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            uiState.allTracks
        } else {
            uiState.allTracks.filter { track ->
                track.title.contains(query, ignoreCase = true) ||
                    track.artist.contains(query, ignoreCase = true) ||
                    track.album.contains(query, ignoreCase = true)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onPermissionResult(granted) }

    val cloudConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onCloudConsentResult(result.data) }

    // Zgłoszenie: "kliknięcie w konto w pickerze nic nie robi" — flow logowania Google dotąd
    // połykał każdy błąd w ciszy (patrz GoogleDriveLibraryRepository). Teraz każdy błąd trafia
    // tutaj i user WIDZI, że coś poszło nie tak (i co dokładnie), zamiast martwej ciszy.
    val cloudError by viewModel.cloudLastError.collectAsState()
    val webDavError by viewModel.webDavLastError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(cloudError) {
        cloudError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearCloudError()
        }
    }
    LaunchedEffect(webDavError) {
        webDavError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearWebDavError()
        }
    }

    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(context, audioPermission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            viewModel.onPermissionResult(true)
        } else if (!permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(audioPermission)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Etap 21/22: wcięcie systemowe przeniesione tu z globalnego korzenia w MainActivity —
        // patrz komentarz tam. Ten ekran nie ma nakładki, która miałaby wylewać się pod paski,
        // więc wcięcie na samym korzeniu treści jest poprawne i wystarczające.
        // Etap 33, zgłoszenie ze zrzutem ekranu: `hazeSource` był dotąd TYLKO na samej
        // LazyColumn listy utworów, nie na nagłówku/wyszukiwarce/zakładkach nad nią — sheet
        // wystarczająco wysoki (np. MusicSourcesSheet), żeby sięgnąć POWYŻEJ górnej krawędzi
        // listy, widział tam "nic do rozmycia" i renderował się jako szew między rozmytym a
        // nierozmytym pasem. Przeniesione na korzeń całego ekranu (ten sam wzorzec co
        // NowPlayingScreen) — jeden hazeSource obejmujący wszystko, zero szwów niezależnie od
        // wysokości sheeta.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .hazeSource(state = hazeState),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.l),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Biblioteka",
                    style = AuroraTextStyles.Headline,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.m)) {
                    // Etap 36: konto (login/sync między telefonem a desktopem) — osobna ikona od
                    // "Źródeł muzyki" celowo, to dwa różne pojęcia (skąd appka BIERZE utwory vs.
                    // czyje konto SYNCHRONIZUJE Ulubione/playlisty/EQ).
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = "Konto",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = onOpenAccount),
                    )
                    // Etap 12/22, zgłoszenie: osobna ikona per dostawca (chmura + NAS + docelowo
                    // OneDrive/Dropbox) nie skaluje się — JEDNA ikona "Źródła muzyki" otwiera listę
                    // wszystkich źródeł (DESIGN.md Etap 12 to zresztą od początku tak zakładał: ekran
                    // "Źródła muzyki" ze statusem per źródło, nie osobne przyciski w nagłówku).
                    val anySourceConnected = isCloudSignedIn || isWebDavConnected
                    Icon(
                        imageVector = if (anySourceConnected) Icons.Filled.Cloud else Icons.Filled.CloudOff,
                        contentDescription = "Źródła muzyki",
                        tint = if (anySourceConnected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        },
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { showSourcesSheet = true },
                    )
                }
            }

            // Zgłoszenie: wyszukiwarka/skróty/przełącznik tabów były NA STAŁE nad listą — tyle co
            // ekran albumu z okładką i "Odtwórz" (patrz AlbumDetailScreen), ten sam problem na
            // mniejszych ekranach. Wydzielone do lambdy, wstrzykiwanej jako pierwszy element
            // odpowiedniej listy/siatki niżej — przewija się razem z treścią, chowa do góry.
            // Pozostaje TYLKO cienki pasek "Biblioteka" nad tym, tak jak strzałka "Wstecz" w
            // ekranach szczegółów.
            val libraryHeader: @Composable () -> Unit = {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        placeholder = { Text("Szukaj w bibliotece") },
                        leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Filled.Clear, contentDescription = "Wyczyść")
                                }
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = tokens.spacing.m),
                    )

                    // Etap 37: Genius/Radio przeniesione na Home (ekran startowy, kuratorski dashboard
                    // ze skrótami do wszystkich 8 domen treści) — zostają tu tylko skróty do rzeczy
                    // ściśle "Twoich" w obrębie samej Biblioteki (Ulubione/Playlisty/Subskrypcje), żeby
                    // nie trzeba było wracać na Home po prostą, częstą czynność w obrębie tego taba.
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = tokens.spacing.m),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
                        modifier = Modifier.padding(top = tokens.spacing.m),
                    ) {
                        item {
                            DomainShortcutCard(
                                icon = Icons.Filled.FavoriteBorder,
                                title = "Ulubione",
                                subtitle = "${favoriteTrackIds.size} utworów",
                                onClick = onOpenFavorites,
                            )
                        }
                        item {
                            DomainShortcutCard(
                                icon = Icons.Filled.QueueMusic,
                                title = "Playlisty",
                                subtitle = "${playlists.size} playlist",
                                onClick = onOpenPlaylists,
                            )
                        }
                        item {
                            DomainShortcutCard(
                                icon = Icons.Filled.Podcasts,
                                title = "Subskrypcje",
                                subtitle = "Podkasty i audiobooki",
                                onClick = onOpenPodcasts,
                            )
                        }
                        item {
                            DomainShortcutCard(
                                icon = Icons.Filled.Download,
                                title = "Pobrane",
                                subtitle = "${archiveLibraryTracks.size} utworów",
                                onClick = onOpenDownloads,
                            )
                        }
                    }

                    // Etap 22/23, user: "wszystkie wymienione" (Albumy/Wykonawcy z pierwotnej wizji,
                    // sekcja 3.1). Prosty segmentowany pasek, nie Material3 TabRow — spójniejszy wizualnie
                    // z resztą appki (pigułki, karty), tak jak presety EQ w EqualizerSheet.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.m),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
                    ) {
                        LibraryTab.entries.forEach { tab ->
                            val isSelected = tab == selectedTab
                            Text(
                                text = tab.label,
                                style = AuroraTextStyles.Label,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                    )
                                    .clickable { selectedTab = tab }
                                    .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
                            )
                        }
                    }
                }
            }

            when {
                // Nie blokuj widoku permission-promptem, jeśli mamy już czym wypełnić listę
                // (np. same utwory z chmury bez zgody na lokalny storage). Header zostaje widoczny —
                // to jedyny sposób dotarcia do tabów/wyszukiwarki, zanim cokolwiek się załaduje.
                !uiState.hasPermission && uiState.allTracks.isEmpty() -> {
                    Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        libraryHeader()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(tokens.spacing.l),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "Aurora potrzebuje dostępu do muzyki na urządzeniu, żeby zbudować Twoją bibliotekę.",
                                style = AuroraTextStyles.Body,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                            Button(
                                onClick = { permissionLauncher.launch(audioPermission) },
                                modifier = Modifier.padding(top = tokens.spacing.m),
                            ) {
                                Text("Zezwól na dostęp")
                            }
                        }
                    }
                }

                uiState.allTracks.isEmpty() && !uiState.isLoading && !uiState.isLoadingCloud -> {
                    Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        libraryHeader()
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Nie znaleziono muzyki na urządzeniu.",
                                style = AuroraTextStyles.Body,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                selectedTab == LibraryTab.Albums -> {
                    AlbumsScreen(
                        viewModel = viewModel,
                        searchQuery = searchQuery,
                        onOpenAlbum = onOpenAlbum,
                        modifier = Modifier.weight(1f),
                        header = libraryHeader,
                    )
                }

                selectedTab == LibraryTab.Artists -> {
                    ArtistsScreen(
                        viewModel = viewModel,
                        searchQuery = searchQuery,
                        onOpenArtist = onOpenArtist,
                        modifier = Modifier.weight(1f),
                        header = libraryHeader,
                    )
                }

                filteredTracks.isEmpty() -> {
                    Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        libraryHeader()
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Brak wyników dla „$searchQuery”",
                                style = AuroraTextStyles.Body,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        // Dolny padding = realna wysokość pływającego mini-playera/nawigacji
                        // (AuroraNavHost/LocalBottomChromeInset) — NavHost jest pełnoekranowy, więc
                        // bez tego ostatni utwór chowałby się na stałe pod paskiem.
                        contentPadding = PaddingValues(
                            start = tokens.spacing.s,
                            end = tokens.spacing.s,
                            top = tokens.spacing.s,
                            bottom = LocalBottomChromeInset.current + tokens.spacing.s,
                        ),
                    ) {
                        item { libraryHeader() }
                        items(filteredTracks, key = { it.id }) { track ->
                            TrackListItem(
                                title = track.title,
                                artist = track.artist,
                                albumArtUrl = track.albumArtUri,
                                durationLabel = formatDuration(track.durationMs),
                                isCurrentlyPlaying = playbackState.currentTrack?.id == track.id,
                                onClick = { viewModel.onTrackClick(track) },
                                onGeniusClick = { viewModel.onGeniusClick(track) },
                                onMoreClick = { trackForMenu = track },
                                isCloudTrack = track.source == TrackSource.CLOUD,
                                isSavedOffline = track.source == TrackSource.ARCHIVE,
                            )
                        }
                    }
                }
            }
        }

        // Etap 37: MiniPlayerBar przeniesiony na poziom AuroraNavHost (jeden wspólny pasek nad
        // AuroraBottomNav, widoczny na wszystkich zakładkach, nie tylko tutaj) — patrz
        // AuroraNavHost.kt. Etap 40, zgłoszenie: NavHost tam jest pełnoekranowy (nie przycięty
        // przez Scaffold), więc Snackbar BEZ `LocalBottomChromeInset` faktycznie chował się pod
        // pływającym paskiem (float leży NAD treścią ekranu, nie za nią) — poprzedni komentarz o
        // "akceptowalności" tego był błędny.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = LocalBottomChromeInset.current + tokens.spacing.m),
        ) { data ->
            Snackbar(snackbarData = data)
        }
    }

    trackForMenu?.let { track ->
        val isFavorite = track.id in favoriteTrackIds
        TrackActionsSheet(
            title = track.title,
            subtitle = track.artist,
            albumArtUrl = track.albumArtUri,
            actions = listOf(
                TrackAction(
                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    label = if (isFavorite) "Usuń z ulubionych" else "Dodaj do ulubionych",
                    onClick = { viewModel.onToggleFavorite(track.id); trackForMenu = null },
                ),
                TrackAction(
                    icon = Icons.Filled.AddToQueue,
                    label = "Dodaj do kolejki",
                    onClick = { viewModel.onAddToQueue(track); trackForMenu = null },
                ),
                TrackAction(
                    icon = Icons.Filled.PlaylistAdd,
                    label = "Dodaj do playlisty",
                    onClick = { trackForPlaylistSheet = track; trackForMenu = null },
                ),
            ),
            onDismiss = { trackForMenu = null },
        )
    }

    if (showSourcesSheet) {
        MusicSourcesSheet(
            isCloudSignedIn = isCloudSignedIn,
            cloudAccountEmail = cloudAccountEmail,
            isWebDavConnected = isWebDavConnected,
            webDavServerLabel = webDavServerLabel,
            hazeState = hazeState,
            onCloudClick = {
                if (isCloudSignedIn) {
                    viewModel.onCloudSignOut()
                } else {
                    viewModel.onCloudConnectClick { intentSender ->
                        cloudConsentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    }
                }
            },
            onWebDavClick = {
                if (isWebDavConnected) {
                    viewModel.onWebDavDisconnect()
                } else {
                    showSourcesSheet = false
                    showWebDavDialog = true
                }
            },
            onDismiss = { showSourcesSheet = false },
        )
    }

    if (showWebDavDialog) {
        WebDavConnectDialog(
            isConnecting = isConnectingWebDav,
            onConnect = { serverUrl, username, password ->
                isConnectingWebDav = true
                viewModel.onWebDavConnectClick(serverUrl, username, password) { success ->
                    isConnectingWebDav = false
                    if (success) showWebDavDialog = false
                }
            },
            onDismiss = { showWebDavDialog = false },
        )
    }

    trackForPlaylistSheet?.let { track ->
        AddToPlaylistSheet(
            trackId = track.id,
            playlists = playlists,
            onAddToPlaylist = { playlistId ->
                viewModel.onAddTrackToPlaylist(playlistId, track.id)
                trackForPlaylistSheet = null
            },
            onCreatePlaylist = { name ->
                viewModel.onCreatePlaylist(name) { newId -> viewModel.onAddTrackToPlaylist(newId, track.id) }
                trackForPlaylistSheet = null
            },
            onDismiss = { trackForPlaylistSheet = null },
        )
    }
}

/**
 * Lista wszystkich źródeł muzyki (Etap 12/22, DESIGN.md) — jedna, skalowalna lista zamiast
 * osobnej ikony w nagłówku per dostawca (zgłoszenie: "500 ikonek do dysków zewnętrznych").
 * Kolejne źródła (OneDrive/Dropbox — Etap 12, wymagają rejestracji aplikacji deweloperskiej u
 * dostawcy) dokładają się jako kolejny wiersz tutaj, nie kolejna ikona w LibraryScreen.
 */
/**
 * Etap 33, zgłoszenie ze zrzutem ekranu: sheet nie miał jawnego `shape`, więc dziedziczył
 * `MaterialTheme.shapes.extraLarge` (999dp, myślany do pigułek — patrz Shape.kt) i renderował
 * się jako kopuła nachodząca na listę pod spodem, zamiast zwykłych zaokrąglonych rogów — ten sam
 * bug i ta sama poprawka co przy EqualizerSheet (Etap 19/20). Rzędy źródeł dostały też styl karty
 * (zaokrąglone tło, odstępy) zamiast płaskiej listy, żeby wyglądać spójnie z resztą appki
 * (PlaylistCard/PodcastRow) zamiast "średnio".
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MusicSourcesSheet(
    isCloudSignedIn: Boolean,
    cloudAccountEmail: String?,
    isWebDavConnected: Boolean,
    webDavServerLabel: String?,
    hazeState: HazeState? = null,
    onCloudClick: () -> Unit,
    onWebDavClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalAuroraTokens.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = if (hazeState != null) surfaceColor.copy(alpha = 0.94f) else surfaceColor,
        scrimColor = Color.Black.copy(alpha = 0.75f),
        modifier = if (hazeState != null) {
            Modifier.hazeEffect(state = hazeState, style = HazeMaterials.regular(surfaceColor))
        } else {
            Modifier
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.m)
                .padding(bottom = tokens.spacing.l),
        ) {
            Text(
                text = "Źródła muzyki",
                style = AuroraTextStyles.Title,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = tokens.spacing.s),
            )
            MusicSourceRow(
                icon = if (isCloudSignedIn) Icons.Filled.Cloud else Icons.Filled.CloudOff,
                title = "Google Drive",
                subtitle = cloudAccountEmail ?: "Nie połączono",
                isConnected = isCloudSignedIn,
                onClick = onCloudClick,
                modifier = Modifier.padding(bottom = tokens.spacing.s),
            )
            MusicSourceRow(
                icon = Icons.Filled.Storage,
                title = "NAS / WebDAV",
                subtitle = webDavServerLabel ?: "Nie połączono",
                isConnected = isWebDavConnected,
                onClick = onWebDavClick,
                modifier = Modifier.padding(bottom = tokens.spacing.s),
            )
            // OneDrive/Dropbox (DESIGN.md Etap 12) — wymagają rejestracji aplikacji deweloperskiej
            // u dostawcy, więc na razie wyszarzone "wkrótce" zamiast udawania że działają.
            MusicSourceRow(
                icon = Icons.Filled.CloudOff,
                title = "OneDrive",
                subtitle = "Wkrótce",
                isConnected = false,
                enabled = false,
                onClick = {},
                modifier = Modifier.padding(bottom = tokens.spacing.s),
            )
            MusicSourceRow(
                icon = Icons.Filled.CloudOff,
                title = "Dropbox",
                subtitle = "Wkrótce",
                isConnected = false,
                enabled = false,
                onClick = {},
            )
        }
    }
}

@Composable
private fun MusicSourceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tokens = LocalAuroraTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s + 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isConnected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.6f else 0.3f)
            },
            modifier = Modifier.size(28.dp),
        )
        Column(modifier = Modifier.padding(start = tokens.spacing.m).weight(1f)) {
            Text(
                text = title,
                style = AuroraTextStyles.Body,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
            )
            Text(
                text = subtitle,
                style = AuroraTextStyles.Caption,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.6f else 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isConnected) {
            Text(
                text = "Rozłącz",
                style = AuroraTextStyles.Caption,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Dialog logowania do serwera NAS/WebDAV — bez OAuth, więc zwykłe pola tekstowe wystarczą. */
@Composable
private fun WebDavConnectDialog(
    isConnecting: Boolean,
    onConnect: (serverUrl: String, username: String, password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var serverUrl by remember { mutableStateOf("https://") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Połącz z NAS/WebDAV") },
        text = {
            Column {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    singleLine = true,
                    label = { Text("Adres serwera") },
                    placeholder = { Text("https://moj-nas.local/muzyka") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    singleLine = true,
                    label = { Text("Login") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text("Hasło") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isConnecting && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                onClick = { onConnect(serverUrl.trim(), username.trim(), password) },
            ) {
                Text("Połącz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        },
    )
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
