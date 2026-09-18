package com.aurora.player.library

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.player.archive.toTrack
import com.aurora.player.audiobook.toTrack
import com.aurora.player.cloud.GoogleDriveLibraryRepository
import com.aurora.player.cloud.WebDavLibraryRepository
import com.aurora.player.color.AlbumArtColorExtractor
import com.aurora.player.color.AlbumArtPalette
import com.aurora.player.domain.model.ArchiveCategory
import com.aurora.player.domain.model.ArchiveDownload
import com.aurora.player.domain.model.ArchiveItem
import com.aurora.player.domain.model.ArchiveTrack
import com.aurora.player.domain.model.Audiobook
import com.aurora.player.domain.model.AudiobookChapter
import com.aurora.player.domain.model.AudiobookSearchResult
import com.aurora.player.domain.model.IndependentTrack
import com.aurora.player.domain.model.ArtistInfo
import com.aurora.player.domain.repository.ArchiveRepository
import com.aurora.player.domain.repository.ArtistInfoRepository
import com.aurora.player.domain.repository.AudiobookRepository
import com.aurora.player.domain.repository.AudioMetadataRepository
import com.aurora.player.domain.repository.IndependentMusicRepository
import com.aurora.player.domain.model.EqState
import com.aurora.player.domain.model.GeniusMix
import com.aurora.player.domain.model.LyricsResult
import com.aurora.player.domain.model.PlaybackState
import com.aurora.player.domain.model.Playlist
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.repository.EqRepository
import com.aurora.player.domain.repository.FavoritesRepository
import com.aurora.player.domain.repository.GeniusRepository
import com.aurora.player.domain.repository.LyricsRepository
import com.aurora.player.domain.repository.MetadataEnrichmentRepository
import com.aurora.player.domain.model.Podcast
import com.aurora.player.domain.model.PodcastEpisode
import com.aurora.player.domain.model.PodcastSearchResult
import com.aurora.player.domain.model.RadioStation
import com.aurora.player.domain.model.TrackSource
import com.aurora.player.domain.repository.PlayerRepository
import com.aurora.player.domain.repository.PlaylistRepository
import com.aurora.player.domain.repository.PodcastCatalogRepository
import com.aurora.player.domain.repository.PodcastRepository
import com.aurora.player.domain.repository.RadioRepository
import com.aurora.player.domain.usecase.GetTracksUseCase
import com.aurora.player.independent.toTrack
import com.aurora.player.playback.SleepTimerController
import com.aurora.player.podcast.toTrack
import com.aurora.player.radio.toTrack
import com.aurora.player.visualizer.AudioVisualizerAnalyzer
import com.aurora.player.visualizer.VisualizerFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class LibraryUiState(
    val tracks: List<Track> = emptyList(),
    val cloudTracks: List<Track> = emptyList(),
    val webDavTracks: List<Track> = emptyList(),
    /** Ścieżki z Archiwum pobrane NA STAŁE do biblioteki (offline) — DESIGN.md Etap 40. */
    val archiveLibraryTracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingCloud: Boolean = false,
    val isLoadingWebDav: Boolean = false,
    val hasPermission: Boolean = false,
    /**
     * `true` po PIERWSZYM zakończonym skanie lokalnej biblioteki (patrz [LibraryViewModel.refresh])
     * — niezależnie od tego, czy skan znalazł jakiekolwiek utwory. Odróżnia "jeszcze nie skończyło
     * się skanować" od domyślnego `isLoading = false` sprzed startu skanu — bez tego pola
     * personalizacja Archiwum (patrz `LibraryViewModel.computeLocalTaste`) nie miała jak poczekać na
     * skan i zawsze widziała pustą listę, gdy Archiwum było otwierane od razu po starcie appki.
     */
    val localLibraryLoaded: Boolean = false,
) {
    /** Biblioteka z urządzenia + z chmury + z NAS/WebDAV + z Archiwum złączona w jedną listę do wyświetlenia. */
    val allTracks: List<Track> get() = tracks + cloudTracks + webDavTracks + archiveLibraryTracks
}

/** Ile razy mocniej ulubiony utwór liczy się w gustcie "Dla Ciebie" niż zwykła obecność w bibliotece. */
private const val FAVORITE_TASTE_WEIGHT = 5

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getTracksUseCase: GetTracksUseCase,
    private val colorExtractor: AlbumArtColorExtractor,
    val playerRepository: PlayerRepository,
    val eqRepository: EqRepository,
    private val geniusRepository: GeniusRepository,
    private val visualizerAnalyzer: AudioVisualizerAnalyzer,
    private val googleDriveLibraryRepository: GoogleDriveLibraryRepository,
    private val webDavLibraryRepository: WebDavLibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val favoritesRepository: FavoritesRepository,
    private val sleepTimerController: SleepTimerController,
    private val lyricsRepository: LyricsRepository,
    private val metadataEnrichmentRepository: MetadataEnrichmentRepository,
    private val audioMetadataRepository: AudioMetadataRepository,
    private val radioRepository: RadioRepository,
    private val podcastRepository: PodcastRepository,
    private val podcastCatalogRepository: PodcastCatalogRepository,
    private val audiobookRepository: AudiobookRepository,
    private val archiveRepository: ArchiveRepository,
    private val independentMusicRepository: IndependentMusicRepository,
    private val artistInfoRepository: ArtistInfoRepository,
) : ViewModel() {

    val isCloudSignedIn: StateFlow<Boolean> = googleDriveLibraryRepository.isSignedIn
    val cloudAccountEmail: StateFlow<String?> = googleDriveLibraryRepository.accountEmail
    val cloudLastError: StateFlow<String?> = googleDriveLibraryRepository.lastError
    fun clearCloudError() = googleDriveLibraryRepository.clearLastError()

    val isWebDavConnected: StateFlow<Boolean> = webDavLibraryRepository.isConnected
    val webDavServerLabel: StateFlow<String?> = webDavLibraryRepository.serverLabel
    val webDavLastError: StateFlow<String?> = webDavLibraryRepository.lastError
    fun clearWebDavError() = webDavLibraryRepository.clearLastError()

    /** Wizualizer widmowy Now Playing — patrz DESIGN.md, `AudioVisualizerAnalyzer`. */
    val visualizerFrame: StateFlow<VisualizerFrame> = visualizerAnalyzer.frame

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = playerRepository.playbackState
    val eqState: StateFlow<EqState> = eqRepository.eqState

    private val _albumArtPalette = MutableStateFlow(AlbumArtPalette())
    val albumArtPalette: StateFlow<AlbumArtPalette> = _albumArtPalette.asStateFlow()

    private val _isGeneratingMix = MutableStateFlow(false)
    val isGeneratingMix: StateFlow<Boolean> = _isGeneratingMix.asStateFlow()

    private val _geniusMixes = MutableStateFlow<List<GeniusMix>>(emptyList())
    val geniusMixes: StateFlow<List<GeniusMix>> = _geniusMixes.asStateFlow()

    private val _isLoadingMixes = MutableStateFlow(false)
    val isLoadingMixes: StateFlow<Boolean> = _isLoadingMixes.asStateFlow()

    private val _lyricsResult = MutableStateFlow<LyricsResult?>(null)
    val lyricsResult: StateFlow<LyricsResult?> = _lyricsResult.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    init {
        viewModelScope.launch {
            playbackState
                .map { it.currentTrack }
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .collect { track ->
                    _albumArtPalette.value = AlbumArtPalette()
                    if (track != null) {
                        _albumArtPalette.value = colorExtractor.extract(track.id, track.albumArtUri)
                    }
                }
        }
        // Napisy — DESIGN.md Etap 24. Ten sam wzorzec co paleta koloru wyżej: reaguje na zmianę
        // ID bieżącego utworu, nie na każdą emisję playbackState (ta zmienia się co ~300ms przy
        // odtwarzaniu — distinctUntilChanged po ID chroni przed odpytywaniem cache'u w kółko).
        viewModelScope.launch {
            playbackState
                .map { it.currentTrack }
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .collect { track ->
                    _lyricsResult.value = null
                    if (track != null) {
                        _isLoadingLyrics.value = true
                        _lyricsResult.value = lyricsRepository.getLyrics(track)
                        _isLoadingLyrics.value = false
                    }
                }
        }
        // Auto-zapis pozycji odcinka podkastu co ~5s odtwarzania — DESIGN.md Etap 25 ("gdzie
        // skończyłem"). Bucket po 5s (nie każdy tick 300ms z PlayerController), żeby nie walić w
        // Room przy każdej klatce pozycji.
        viewModelScope.launch {
            playbackState
                .distinctUntilChanged { old, new -> old.positionMs / 5000 == new.positionMs / 5000 }
                .collect { state ->
                    val track = state.currentTrack
                    if (track != null && track.source == TrackSource.PODCAST && state.positionMs > 0) {
                        podcastRepository.savePlaybackPosition(track.id, state.positionMs)
                    }
                }
        }
        // Ścieżki z Archiwum pobrane na stałe (Room, patrz ArchiveRepositoryImpl.library) — reaktywne,
        // bez ręcznego refresh() jak Cloud/WebDAV niżej: wpis w bazie po pobraniu sam dopisuje się
        // do allTracks, więc ulubione/playlisty/wyszukiwarka w Bibliotece widzą go natychmiast.
        viewModelScope.launch {
            archiveRepository.library.collect { tracks ->
                _uiState.update { it.copy(archiveLibraryTracks = tracks) }
            }
        }
        // Sesja Google potrafi przetrwać restart appki (GoogleSignIn.getLastSignedInAccount) —
        // jeśli tak, dociągnij bibliotekę z chmury bez czekania na akcję użytkownika.
        if (isCloudSignedIn.value) refreshCloud()
        // Dane logowania WebDAV są trwałe (SharedPreferences) — jeśli user już się kiedyś
        // połączył, dociągnij bibliotekę od razu, tak samo jak Google Drive powyżej.
        if (isWebDavConnected.value) refreshWebDav()
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasPermission = granted) }
        if (granted) refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val tracks = getTracksUseCase()
            _uiState.update { it.copy(tracks = tracks, isLoading = false, localLibraryLoaded = true) }
            extractAudioMetadata(tracks)
            enrichMetadata(tracks)
        }
    }

    /**
     * Naprawia błędne/brakujące tytuły, wykonawców i okładki w tle — DESIGN.md Etap 25/27/44.
     * Okładka lokalna ([LocalAlbumArtRepository]) i sieciowa (MusicBrainz/Cover Art Archive) są
     * teraz JEDNYM potokiem WEWNĄTRZ [MetadataEnrichmentRepository] (lokalnie per utwór przed
     * siecią dla tego samego utworu) — nie dwoma niezależnymi przebiegami tutaj. Świadomie:
     * dwie wcześniejsze próby tego rozdzielenia na poziomie ViewModelu (równolegle, potem
     * sekwencyjnie-całą-biblioteką) dawały odpowiednio wyścig o `albumArtUri` i zauważalne
     * opóźnienie całego wzbogacania — patrz komentarz w `MetadataEnrichmentRepositoryImpl`.
     * Osobna korutyna od pierwszego wyświetlenia biblioteki wyżej: poszczególne utwory
     * podmieniają się na liście w miarę jak dopasowania wracają.
     */
    private fun enrichMetadata(tracks: List<Track>) {
        viewModelScope.launch {
            metadataEnrichmentRepository.enrichLibrary(tracks).collect { enrichedTrack ->
                _uiState.update { state ->
                    state.copy(tracks = state.tracks.map { if (it.id == enrichedTrack.id) enrichedTrack else it })
                }
            }
        }
    }

    /**
     * Dociąga codec/bitrate/sample rate/bit depth w tle (lokalny odczyt pliku, prerekwizyt pod
     * przyszły Audio Lab) — DESIGN.md Etap 29. Ten sam wzorzec co [enrichMetadata] wyżej.
     */
    private fun extractAudioMetadata(tracks: List<Track>) {
        viewModelScope.launch {
            audioMetadataRepository.extractMissing(tracks).collect { updatedTrack ->
                _uiState.update { state ->
                    state.copy(tracks = state.tracks.map { if (it.id == updatedTrack.id) updatedTrack else it })
                }
            }
        }
    }

    fun onTrackClick(track: Track) {
        if (playbackState.value.currentTrack?.id == track.id) {
            playerRepository.togglePlayPause()
        } else {
            playerRepository.playQueue(listOf(track))
        }
    }

    fun onTogglePlayPause() {
        playerRepository.togglePlayPause()
    }

    fun onSeek(positionMs: Long) {
        playerRepository.seekTo(positionMs)
    }

    fun onSkipNext() {
        playerRepository.skipToNext()
    }

    fun onSkipPrevious() {
        playerRepository.skipToPrevious()
    }

    // --- Powtarzanie/losowa kolejność — DESIGN.md Etap 26 ---

    fun onCycleRepeatMode() = playerRepository.cycleRepeatMode()
    fun onToggleShuffle() = playerRepository.toggleShuffle()

    /** Odtwarza dowolną listę utworów od [startIndex] — playlisty, ulubione, podgląd Geniusa. */
    fun onPlayTracks(tracks: List<Track>, startIndex: Int = 0) {
        playerRepository.playQueue(tracks, startIndex)
    }

    /**
     * Instant Mix z utworu-ziarna — patrz DESIGN.md sekcja 5.3. Seed gra jako pierwszy.
     * `uiState.value.allTracks` (nie samo `TrackRepository.getAllTracks()`, patrz DESIGN.md
     * Etap 40) — inaczej Genius nie widziałby utworów z Drive/WebDAV/pobranych z Archiwum, w tym
     * SAMEGO seeda, gdyby akurat pochodził z jednego z tych źródeł.
     */
    fun onGeniusClick(seedTrack: Track) {
        viewModelScope.launch {
            _isGeneratingMix.value = true
            val mix = geniusRepository.generateInstantMix(seedTrack.id, _uiState.value.allTracks)
            _isGeneratingMix.value = false
            playerRepository.playQueue(listOf(seedTrack) + mix)
        }
    }

    fun onEqSetEnabled(enabled: Boolean) {
        eqRepository.setEnabled(enabled)
    }

    fun onEqSetBandGain(bandIndex: Int, gainDb: Float) {
        eqRepository.setBandGain(bandIndex, gainDb)
    }

    fun onEqApplyPreset(presetName: String) {
        eqRepository.applyPreset(presetName)
    }

    fun onEqReset() {
        eqRepository.reset()
    }

    /** Gotowe playlisty z klastrowania, bez wskazywania utworu-ziarna — DESIGN.md sekcja 5.5. */
    fun loadGeniusMixes() {
        viewModelScope.launch {
            _isLoadingMixes.value = true
            _geniusMixes.value = geniusRepository.generateGeniusMixes(_uiState.value.allTracks)
            _isLoadingMixes.value = false
        }
    }

    // --- Chmura (Google Drive) — patrz DESIGN.md, sekcja "Chmura" ---

    /**
     * Próbuje połączyć się z Google Drive. Jeśli Google wymaga ekranu zgody, [onNeedsConsent]
     * dostaje [android.content.IntentSender] do odpalenia przez `ActivityResultLauncher`
     * (patrz LibraryScreen) — inaczej (już autoryzowany/błąd) biblioteka po prostu się odświeża.
     */
    fun onCloudConnectClick(onNeedsConsent: (android.content.IntentSender) -> Unit) {
        viewModelScope.launch {
            val intentSender = googleDriveLibraryRepository.connect()
            if (intentSender != null) {
                onNeedsConsent(intentSender)
            } else if (googleDriveLibraryRepository.isSignedIn.value) {
                refreshCloud()
            }
        }
    }

    /** Wołane z `ActivityResultLauncher` w LibraryScreen po ekranie zgody Google. */
    fun onCloudConsentResult(data: Intent?) {
        if (googleDriveLibraryRepository.handleAuthorizationResult(data)) {
            refreshCloud()
        }
    }

    fun refreshCloud() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCloud = true) }
            val tracks = googleDriveLibraryRepository.refreshCloudTracks()
            _uiState.update { it.copy(cloudTracks = tracks, isLoadingCloud = false) }
        }
    }

    fun onCloudSignOut() {
        googleDriveLibraryRepository.signOut()
        _uiState.update { it.copy(cloudTracks = emptyList()) }
    }

    // --- NAS/WebDAV — DESIGN.md Etap 12/22 ---

    /** `true` = połączono i biblioteka odświeżona; `false` = błąd, patrz [webDavLastError]. */
    fun onWebDavConnectClick(serverUrl: String, username: String, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val connected = webDavLibraryRepository.connect(serverUrl, username, password)
            if (connected) refreshWebDav()
            onResult(connected)
        }
    }

    fun refreshWebDav() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingWebDav = true) }
            val tracks = webDavLibraryRepository.refreshTracks()
            _uiState.update { it.copy(webDavTracks = tracks, isLoadingWebDav = false) }
        }
    }

    fun onWebDavDisconnect() {
        webDavLibraryRepository.disconnect()
        _uiState.update { it.copy(webDavTracks = emptyList()) }
    }

    // --- Playlisty — DESIGN.md Etap 22 ---

    val playlists: StateFlow<List<Playlist>> = playlistRepository.playlists

    fun onCreatePlaylist(name: String, onCreated: (Long) -> Unit = {}) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = playlistRepository.createPlaylist(trimmed)
            onCreated(id)
        }
    }

    fun onRenamePlaylist(playlistId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { playlistRepository.renamePlaylist(playlistId, trimmed) }
    }

    fun onDeletePlaylist(playlistId: Long) {
        viewModelScope.launch { playlistRepository.deletePlaylist(playlistId) }
    }

    fun onAddTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch { playlistRepository.addTrack(playlistId, trackId) }
    }

    fun onRemoveTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch { playlistRepository.removeTrack(playlistId, trackId) }
    }

    fun onMoveTrackInPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch { playlistRepository.moveTrack(playlistId, fromIndex, toIndex) }
    }

    /** Podgląd Geniusa → "Zapisz jako playlistę" (patrz GeniusMixPreviewScreen). */
    fun onSaveMixAsPlaylist(mix: GeniusMix, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = playlistRepository.createPlaylist(mix.name)
            mix.tracks.forEach { playlistRepository.addTrack(id, it.id) }
            onSaved(id)
        }
    }

    // --- Ulubione — DESIGN.md Etap 22 ---

    val favoriteTrackIds: StateFlow<Set<Long>> = favoritesRepository.favoriteTrackIds

    fun onToggleFavorite(trackId: Long) {
        viewModelScope.launch { favoritesRepository.toggleFavorite(trackId) }
    }

    // --- Kolejka — DESIGN.md Etap 22 ---

    fun onAddToQueue(track: Track) = playerRepository.addToQueue(track)
    fun onRemoveFromQueue(index: Int) = playerRepository.removeFromQueue(index)
    fun onMoveQueueItem(fromIndex: Int, toIndex: Int) = playerRepository.moveQueueItem(fromIndex, toIndex)
    fun onPlayQueueIndex(index: Int) = playerRepository.playAt(index)

    // --- Timer snu — DESIGN.md Etap 22 ---

    val sleepTimerRemainingMs: StateFlow<Long?> = sleepTimerController.remainingMs
    fun onStartSleepTimer(durationMs: Long) = sleepTimerController.start(durationMs)
    fun onCancelSleepTimer() = sleepTimerController.cancel()

    // --- Radio — DESIGN.md Etap 24 ---

    private val _radioStations = MutableStateFlow<List<RadioStation>>(emptyList())
    val radioStations: StateFlow<List<RadioStation>> = _radioStations.asStateFlow()

    private val _isLoadingRadio = MutableStateFlow(false)
    val isLoadingRadio: StateFlow<Boolean> = _isLoadingRadio.asStateFlow()

    fun loadTopRadioStations(countryCode: String) {
        viewModelScope.launch {
            _isLoadingRadio.value = true
            _radioStations.value = radioRepository.topStationsByCountry(countryCode)
            _isLoadingRadio.value = false
        }
    }

    fun searchRadioStations(query: String) {
        viewModelScope.launch {
            _isLoadingRadio.value = true
            _radioStations.value = radioRepository.search(query)
            _isLoadingRadio.value = false
        }
    }

    fun onPlayRadioStation(station: RadioStation) {
        playerRepository.playQueue(listOf(station.toTrack()))
    }

    // --- Podcasty — DESIGN.md Etap 25 ---

    val podcastSubscriptions: StateFlow<List<Podcast>> = podcastRepository.subscriptions

    fun subscribeToPodcast(feedUrl: String, onResult: (Podcast?) -> Unit = {}) {
        viewModelScope.launch { onResult(podcastRepository.subscribeByFeedUrl(feedUrl)) }
    }

    fun unsubscribeFromPodcast(feedUrl: String) {
        viewModelScope.launch { podcastRepository.unsubscribe(feedUrl) }
    }

    private val _artistInfo = MutableStateFlow<ArtistInfo?>(null)
    val artistInfo: StateFlow<ArtistInfo?> = _artistInfo.asStateFlow()

    private val _isLoadingArtistInfo = MutableStateFlow(false)
    val isLoadingArtistInfo: StateFlow<Boolean> = _isLoadingArtistInfo.asStateFlow()

    /** Bio/gatunek/grafika wykonawcy z TheAudioDB — DESIGN.md Etap 43, ten sam wzorzec co [loadPodcastEpisodes]. */
    fun loadArtistInfo(artistName: String) {
        viewModelScope.launch {
            _artistInfo.value = null
            _isLoadingArtistInfo.value = true
            _artistInfo.value = artistInfoRepository.getArtistInfo(artistName)
            _isLoadingArtistInfo.value = false
        }
    }

    private val _podcastEpisodes = MutableStateFlow<List<PodcastEpisode>>(emptyList())
    val podcastEpisodes: StateFlow<List<PodcastEpisode>> = _podcastEpisodes.asStateFlow()

    private val _isLoadingPodcastEpisodes = MutableStateFlow(false)
    val isLoadingPodcastEpisodes: StateFlow<Boolean> = _isLoadingPodcastEpisodes.asStateFlow()

    fun loadPodcastEpisodes(feedUrl: String) {
        viewModelScope.launch {
            _isLoadingPodcastEpisodes.value = true
            _podcastEpisodes.value = podcastRepository.fetchEpisodes(feedUrl)
            _isLoadingPodcastEpisodes.value = false
        }
    }

    /** Odtwarza odcinek i od razu przeskakuje do zapisanej pozycji ("gdzie skończyłem"). */
    fun onPlayPodcastEpisode(episode: PodcastEpisode, podcast: Podcast) {
        viewModelScope.launch {
            val track = episode.toTrack(podcast)
            val savedPosition = podcastRepository.getPlaybackPosition(track.id)
            playerRepository.playQueue(listOf(track))
            if (savedPosition > 0L) playerRepository.seekTo(savedPosition)
        }
    }

    fun onSetPlaybackSpeed(speed: Float) = playerRepository.setPlaybackSpeed(speed)

    // --- Katalogi podkastów: iTunes (zawsze) + Podcast Index (opcjonalnie, własny klucz usera) ---

    private val _podcastSearchResults = MutableStateFlow<List<PodcastSearchResult>>(emptyList())
    val podcastSearchResults: StateFlow<List<PodcastSearchResult>> = _podcastSearchResults.asStateFlow()

    private val _isSearchingPodcasts = MutableStateFlow(false)
    val isSearchingPodcasts: StateFlow<Boolean> = _isSearchingPodcasts.asStateFlow()

    val isPodcastIndexConfigured: StateFlow<Boolean> = podcastCatalogRepository.isPodcastIndexConfigured

    fun setPodcastIndexCredentials(apiKey: String, apiSecret: String) =
        podcastCatalogRepository.setPodcastIndexCredentials(apiKey, apiSecret)

    fun clearPodcastIndexCredentials() = podcastCatalogRepository.clearPodcastIndexCredentials()

    fun searchPodcasts(query: String) {
        viewModelScope.launch {
            _isSearchingPodcasts.value = true
            val iTunesResults = podcastCatalogRepository.searchITunes(query)
            val podcastIndexResults = podcastCatalogRepository.searchPodcastIndex(query)
            // Ten sam podcast może wypaść z obu katalogów naraz — dedup po feedUrl, iTunes pierwsze.
            val seenFeedUrls = mutableSetOf<String>()
            _podcastSearchResults.value = (iTunesResults + podcastIndexResults).filter { seenFeedUrls.add(it.feedUrl) }
            _isSearchingPodcasts.value = false
        }
    }

    /**
     * Top podcasty danego kraju — DESIGN.md Etap 33, żeby "Dodaj podcast" miało od razu czym się
     * wypełnić (user: "chciałbym różne dla regionu, podobnie jak stacje dla radia"). Ta sama lista
     * co [searchPodcasts] ([podcastSearchResults]) — z punktu widzenia UI to po prostu inny sposób
     * jej wypełnienia, nie osobny stan.
     */
    fun loadTopPodcasts(countryCode: String) {
        viewModelScope.launch {
            _isSearchingPodcasts.value = true
            _podcastSearchResults.value = podcastCatalogRepository.topPodcastsByCountry(countryCode)
            _isSearchingPodcasts.value = false
        }
    }

    // --- Audiobooki (LibriVox) — DESIGN.md Etap 37 ---

    val audiobookLibrary: StateFlow<List<Audiobook>> = audiobookRepository.library

    private val _audiobookSearchResults = MutableStateFlow<List<AudiobookSearchResult>>(emptyList())
    val audiobookSearchResults: StateFlow<List<AudiobookSearchResult>> = _audiobookSearchResults.asStateFlow()

    private val _isSearchingAudiobooks = MutableStateFlow(false)
    val isSearchingAudiobooks: StateFlow<Boolean> = _isSearchingAudiobooks.asStateFlow()

    fun searchAudiobooks(query: String) {
        viewModelScope.launch {
            _isSearchingAudiobooks.value = true
            _audiobookSearchResults.value = audiobookRepository.search(query)
            _isSearchingAudiobooks.value = false
        }
    }

    /**
     * Propozycje wg języka + popularności (DESIGN.md Etap 37) — ta sama lista/state co wyszukiwanie
     * ([audiobookSearchResults]), wypełniana od razu przy wejściu na ekran, żeby user nigdy nie
     * widział pustego ekranu (ten sam wzorzec co [loadTopPodcasts]).
     */
    fun loadRecommendedAudiobooks(languageIso3: String) {
        viewModelScope.launch {
            _isSearchingAudiobooks.value = true
            _audiobookSearchResults.value = audiobookRepository.trending(languageIso3)
            _isSearchingAudiobooks.value = false
        }
    }

    fun addAudiobookToLibrary(id: String, onResult: (Audiobook?) -> Unit = {}) {
        viewModelScope.launch { onResult(audiobookRepository.addToLibrary(id)) }
    }

    fun removeAudiobookFromLibrary(id: String) {
        viewModelScope.launch { audiobookRepository.removeFromLibrary(id) }
    }

    private val _audiobookChapters = MutableStateFlow<List<AudiobookChapter>>(emptyList())
    val audiobookChapters: StateFlow<List<AudiobookChapter>> = _audiobookChapters.asStateFlow()

    private val _isLoadingAudiobookChapters = MutableStateFlow(false)
    val isLoadingAudiobookChapters: StateFlow<Boolean> = _isLoadingAudiobookChapters.asStateFlow()

    fun loadAudiobookChapters(id: String) {
        viewModelScope.launch {
            _isLoadingAudiobookChapters.value = true
            _audiobookChapters.value = audiobookRepository.fetchChapters(id)
            _isLoadingAudiobookChapters.value = false
        }
    }

    /** Odtwarza rozdział i od razu przeskakuje do zapisanej pozycji ("gdzie skończyłem"). */
    fun onPlayAudiobookChapter(chapter: AudiobookChapter, audiobook: Audiobook) {
        viewModelScope.launch {
            val track = chapter.toTrack(audiobook)
            val savedPosition = audiobookRepository.getPlaybackPosition(track.id)
            playerRepository.playQueue(listOf(track))
            if (savedPosition > 0L) playerRepository.seekTo(savedPosition)
        }
    }

    // --- Archiwum (Internet Archive) — DESIGN.md Etap 37 ---

    private val _archiveItems = MutableStateFlow<List<ArchiveItem>>(emptyList())
    val archiveItems: StateFlow<List<ArchiveItem>> = _archiveItems.asStateFlow()

    private val _isLoadingArchive = MutableStateFlow(false)
    val isLoadingArchive: StateFlow<Boolean> = _isLoadingArchive.asStateFlow()

    /**
     * Gust wyprowadzony z CAŁEJ lokalnej biblioteki (wykonawca + gatunek), z ulubionymi liczonymi
     * [FAVORITE_TASTE_WEIGHT] razy mocniej niż zwykła obecność w bibliotece — tak cała zawartość
     * urządzenia wpływa na dopasowanie, a ulubione wciąż dominują ranking. Współdzielone przez
     * [loadPersonalizedArchive] (karuzela "Dla Ciebie" na Home) i [browseArchiveCategory]
     * (przeglądanie konkretnej kategorii w Archiwum, DESIGN.md Etap 46).
     *
     * Tylko [TrackSource.LOCAL]: utwory z Google Drive/NAS-WebDAV mają w `Track.artist`/`Track.genre`
     * placeholdery (nazwa usługi/konta, nie realne metadane odczytane z pliku) — wliczanie ich
     * zaśmiecałoby gust, a w skrajnym przypadku wysłałoby e-mail konta Google jako `creator:` do
     * publicznego wyszukiwania archive.org.
     *
     * Zgłoszenie: Archiwum otwarte od razu po starcie appki (bez wcześniejszego wejścia w
     * Bibliotekę) dostawało zawsze "zahardcodowane" propozycje niezwiązane z muzyką na urządzeniu —
     * skan MediaStore (`refresh()`) startuje teraz eagerly (patrz `AuroraNavHost`), ale to wciąż
     * asynchroniczny odczyt dysku, a ten odczyt `_uiState.value` biegł RÓWNOLEGLE, nie PO nim.
     * Czekamy więc (maks. 3s, żeby nie zawiesić się na zawsze przy braku uprawnienia) na
     * [LibraryUiState.localLibraryLoaded], zanim w ogóle spojrzymy na `allTracks`.
     */
    private suspend fun computeLocalTaste(): Pair<List<String>, List<String>> {
        if (!_uiState.value.localLibraryLoaded) {
            withTimeoutOrNull(3_000) { uiState.first { it.localLibraryLoaded } }
        }
        val localTracks = _uiState.value.allTracks.filter { it.source == TrackSource.LOCAL }
        val favoriteIds = favoritesRepository.favoriteTrackIds.value
        val weighted = localTracks.flatMap { track ->
            List(if (track.id in favoriteIds) FAVORITE_TASTE_WEIGHT else 1) { track }
        }
        val topArtists = weighted.map { it.artist }
            .filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key }
        val topGenres = weighted.mapNotNull { it.genre?.lowercase()?.trim() }
            .filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key }
        return topArtists to topGenres
    }

    /**
     * Przeglądanie kategorii w Archiwum (zakładka Muzyka/Podkasty/Audiobooki/Radio) — Etap 46,
     * zgłoszenie: wcześniej zawsze wołało dumny [ArchiveRepository.browseCategory] (sortowanie po
     * dacie uploadu = głównie szum), teraz najpierw próbuje dopasowania do lokalnego gustu przez
     * [ArchiveRepository.personalizedForCategory] — ten sam sygnał co karuzela "Dla Ciebie", tylko
     * zawężony do tapniętej kategorii. Brak sygnału (świeża instalacja, pusta biblioteka lokalna) =
     * fallback na zwykłe [ArchiveRepository.browseCategory], teraz też posortowane po popularności.
     */
    fun browseArchiveCategory(category: ArchiveCategory) {
        viewModelScope.launch {
            _isLoadingArchive.value = true
            val (topArtists, topGenres) = computeLocalTaste()
            val personalized = if (topArtists.isNotEmpty() || topGenres.isNotEmpty()) {
                archiveRepository.personalizedForCategory(category, topArtists, topGenres)
            } else {
                emptyList()
            }
            _archiveItems.value = personalized.ifEmpty { archiveRepository.browseCategory(category) }
            _isLoadingArchive.value = false
        }
    }

    /**
     * "Dla Ciebie" na Home — patrz KDoc [ArchiveRepository.personalizedForYou] dla algorytmu.
     * Brak lokalnej biblioteki = brak sygnału, wtedy zamiast personalizacji pokazujemy top popularne
     * (patrz [ArchiveRepository.topPopular]).
     */
    fun loadPersonalizedArchive() {
        viewModelScope.launch {
            val (topArtists, topGenres) = computeLocalTaste()
            if (topArtists.isEmpty() && topGenres.isEmpty()) {
                _isLoadingArchive.value = true
                _archiveItems.value = archiveRepository.topPopular()
                _isLoadingArchive.value = false
                return@launch
            }
            _isLoadingArchive.value = true
            _archiveItems.value = archiveRepository.personalizedForYou(topArtists, topGenres)
            _isLoadingArchive.value = false
        }
    }

    fun searchArchive(query: String, category: ArchiveCategory? = null) {
        viewModelScope.launch {
            _isLoadingArchive.value = true
            _archiveItems.value = archiveRepository.search(query, category)
            _isLoadingArchive.value = false
        }
    }

    private val _archiveTracks = MutableStateFlow<List<ArchiveTrack>>(emptyList())
    val archiveTracks: StateFlow<List<ArchiveTrack>> = _archiveTracks.asStateFlow()

    private val _isLoadingArchiveTracks = MutableStateFlow(false)
    val isLoadingArchiveTracks: StateFlow<Boolean> = _isLoadingArchiveTracks.asStateFlow()

    fun loadArchiveTracks(identifier: String) {
        viewModelScope.launch {
            _isLoadingArchiveTracks.value = true
            _archiveTracks.value = archiveRepository.tracksForItem(identifier)
            _isLoadingArchiveTracks.value = false
        }
    }

    /** Odtwarza całą listę ścieżek itemu od [track], nie tylko tę jedną — jak album. */
    fun onPlayArchiveTrack(track: ArchiveTrack, item: ArchiveItem, queue: List<ArchiveTrack> = listOf(track)) {
        val startIndex = queue.indexOf(track).coerceAtLeast(0)
        playerRepository.playQueue(queue.map { it.toTrack(item) }, startIndex)
    }

    /** Ścieżki z Archiwum pobrane na stałe do biblioteki — do sprawdzenia stanu "już dodane" w UI. */
    val archiveLibraryTracks: StateFlow<List<Track>> = archiveRepository.library

    /** `Track.id` ścieżek aktualnie pobieranych z archive.org — do spinnera przy wierszu. */
    val archiveDownloadingTrackIds: StateFlow<Set<Long>> = archiveRepository.downloadingTrackIds

    /** Jak [archiveDownloadingTrackIds], ale z tytułem/okładką — do ekranu "Pobrane". */
    val archiveActiveDownloads: StateFlow<List<ArchiveDownload>> = archiveRepository.activeDownloads

    val archiveLastError: StateFlow<String?> = archiveRepository.lastError
    fun clearArchiveError() = archiveRepository.clearLastError()

    /**
     * Pobiera ścieżkę na stałe do biblioteki (archive.org → dysk urządzenia) — odtąd odtwarzalna
     * offline jak lokalny plik, a nie tylko streamowana. Patrz [ArchiveRepository.addTrackToLibrary].
     */
    fun onAddArchiveTrackToLibrary(track: ArchiveTrack, item: ArchiveItem) {
        viewModelScope.launch { archiveRepository.addTrackToLibrary(track, item) }
    }

    fun onRemoveArchiveTrackFromLibrary(trackId: Long) {
        viewModelScope.launch { archiveRepository.removeTrackFromLibrary(trackId) }
    }

    // --- Muzyka niezależna (Jamendo) — DESIGN.md Etap 37 ---

    private val _independentTracks = MutableStateFlow<List<IndependentTrack>>(emptyList())
    val independentTracks: StateFlow<List<IndependentTrack>> = _independentTracks.asStateFlow()

    private val _isLoadingIndependentMusic = MutableStateFlow(false)
    val isLoadingIndependentMusic: StateFlow<Boolean> = _isLoadingIndependentMusic.asStateFlow()

    fun loadTrendingIndependentMusic() {
        viewModelScope.launch {
            _isLoadingIndependentMusic.value = true
            _independentTracks.value = independentMusicRepository.trending()
            _isLoadingIndependentMusic.value = false
        }
    }

    fun searchIndependentMusic(query: String) {
        viewModelScope.launch {
            _isLoadingIndependentMusic.value = true
            _independentTracks.value = independentMusicRepository.search(query)
            _isLoadingIndependentMusic.value = false
        }
    }

    fun browseIndependentMusicByTag(tag: String) {
        viewModelScope.launch {
            _isLoadingIndependentMusic.value = true
            _independentTracks.value = independentMusicRepository.tracksByTag(tag)
            _isLoadingIndependentMusic.value = false
        }
    }

    /** Odtwarza całą przeglądaną listę od [track], nie tylko ten jeden utwór. */
    fun onPlayIndependentTrack(track: IndependentTrack, queue: List<IndependentTrack> = listOf(track)) {
        val startIndex = queue.indexOf(track).coerceAtLeast(0)
        playerRepository.playQueue(queue.map { it.toTrack() }, startIndex)
    }
}
