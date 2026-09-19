package com.aurora.player.podcast

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.PodcastSearchResult
import com.aurora.player.domain.model.PodcastSearchSource
import com.aurora.player.library.LibraryViewModel
import com.aurora.player.location.CountryPickerSheet
import com.aurora.player.location.resolveCountryCodeFromLastKnownLocation
import kotlinx.coroutines.launch

/**
 * Dodawanie podcastu — DESIGN.md Etap 32/33. Trzy równoległe ścieżki: wklejony adres RSS (działa
 * zawsze, zero API), wyszukiwanie w katalogu (iTunes zawsze + Podcast Index gdy skonfigurowany) i
 * domyślnie wypełniona lista top podcastów kraju usera — ten sam wzorzec geolokalizacji/fallbacku
 * co `RadioScreen` (DESIGN.md Etap 31), świadomie reużyty wprost zamiast duplikowany
 * ([resolveCountryCodeFromLastKnownLocation]/[CountryPickerSheet] to ogólne, bezstanowe narzędzia
 * bez niczego specyficznego dla radia).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPodcastSheet(
    viewModel: LibraryViewModel,
    onSubscribed: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokens = LocalAuroraTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val searchResults by viewModel.podcastSearchResults.collectAsState()
    val isSearching by viewModel.isSearchingPodcasts.collectAsState()
    val isPodcastIndexConfigured by viewModel.isPodcastIndexConfigured.collectAsState()
    val transcriptAvailability by viewModel.podcastTranscriptAvailability.collectAsState()

    var feedUrl by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showPodcastIndexDialog by remember { mutableStateOf(false) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var selectedCountryCode by remember { mutableStateOf<String?>(null) }
    var hasResolvedInitialCountry by remember { mutableStateOf(false) }
    // Zgłoszenie: "przycisk szukaj tylko z tłumaczeniem" — filtr do listy wyników, gdy user
    // konkretnie szuka anglojęzycznego podcastu z dostępną transkrypcją/tłumaczeniem. Lokalny stan
    // UI (nie ViewModel) — dotyczy wyłącznie tego, co pokazujemy w TYM arkuszu, nie samych danych.
    var onlyWithTranscript by remember { mutableStateOf(false) }
    // Promowanie "ma transkrypcję" na górę listy — świadomie liczone TU, lokalnie, a nie w
    // ViewModelu: `podcastSearchResults` zasila też siatkę "Proponowane" w PodcastsScreen, gdzie
    // przesortowanie byłoby niepożądane (zgłoszenie: transkrypcja jest rzadka, więc kuratorskie
    // propozycje miałyby się zrobić losowe/zdominowane przez 2-3 podcasty). Tu, w wynikach
    // wyszukiwania, promowanie ma sens i było wprost proszone.
    val filteredResults = remember(searchResults, transcriptAvailability, onlyWithTranscript) {
        val base = if (onlyWithTranscript) searchResults.filter { transcriptAvailability[it.feedUrl] == true } else searchResults
        base.sortedByDescending { transcriptAvailability[it.feedUrl] == true }
    }

    fun onCountryResolved(code: String) {
        selectedCountryCode = code
        viewModel.loadTopPodcasts(code)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scope.launch {
                val code = resolveCountryCodeFromLastKnownLocation(context)
                if (code != null) onCountryResolved(code) else showCountryPicker = true
            }
        } else {
            showCountryPicker = true
        }
    }

    // Ten sam wzorzec "user zawsze kończy z jakąś listą, nigdy z pustym ekranem" co RadioScreen —
    // odpalone RAZ przy otwarciu arkusza (nie przy każdej rekompozycji).
    LaunchedEffect(Unit) {
        if (hasResolvedInitialCountry) return@LaunchedEffect
        hasResolvedInitialCountry = true
        val alreadyGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            val code = resolveCountryCodeFromLastKnownLocation(context)
            if (code != null) onCountryResolved(code) else showCountryPicker = true
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.m)) {
            Text(
                text = "Dodaj podcast",
                style = AuroraTextStyles.Title,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = tokens.spacing.s),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = feedUrl,
                    onValueChange = { feedUrl = it },
                    singleLine = true,
                    placeholder = { Text("Wklej adres RSS") },
                    leadingIcon = { Icon(imageVector = Icons.Filled.Language, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    enabled = feedUrl.isNotBlank(),
                    onClick = {
                        viewModel.subscribeToPodcast(feedUrl.trim()) { podcast ->
                            if (podcast != null) onSubscribed()
                        }
                    },
                    modifier = Modifier.padding(start = tokens.spacing.s),
                ) { Text("Dodaj") }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = tokens.spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "lub przeszukaj katalog / top w Twoim kraju",
                    style = AuroraTextStyles.Label,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.Public,
                    contentDescription = "Zmień kraj",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(end = tokens.spacing.s)
                        .size(20.dp)
                        .clickable { showCountryPicker = true },
                )
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Ustawienia Podcast Index",
                    tint = if (isPodcastIndexConfigured) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    },
                    modifier = Modifier.size(20.dp).clickable { showPodcastIndexDialog = true },
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = { Text("Szukaj podcastu") },
                leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (searchQuery.isBlank()) {
                            selectedCountryCode?.let { viewModel.loadTopPodcasts(it) }
                        } else {
                            viewModel.searchPodcasts(searchQuery)
                        }
                    },
                ),
                modifier = Modifier.fillMaxWidth().padding(top = tokens.spacing.s),
            )

            // Zgłoszenie: filtr "tylko z tłumaczeniem" — do przeglądania anglojęzycznych podcastów
            // z dostępną transkrypcją (→ tłumaczenie EN→PL w Now Playing, patrz Etap 54).
            Row(
                modifier = Modifier
                    .padding(top = tokens.spacing.s, bottom = tokens.spacing.s)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (onlyWithTranscript) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.background
                        },
                    )
                    .clickable { onlyWithTranscript = !onlyWithTranscript }
                    .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Translate,
                    contentDescription = null,
                    tint = if (onlyWithTranscript) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Tylko z tłumaczeniem",
                    style = AuroraTextStyles.Label,
                    color = if (onlyWithTranscript) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = tokens.spacing.xs),
                )
            }
        }

        when {
            isSearching -> {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            filteredResults.isNotEmpty() -> {
                LazyColumn(modifier = Modifier.height(360.dp).padding(bottom = tokens.spacing.l)) {
                    items(filteredResults, key = { it.feedUrl }) { result ->
                        PodcastSearchResultRow(
                            result = result,
                            hasTranscript = transcriptAvailability[result.feedUrl] == true,
                            onClick = {
                                viewModel.subscribeToPodcast(result.feedUrl) { podcast ->
                                    if (podcast != null) onSubscribed()
                                }
                            },
                        )
                    }
                }
            }

            onlyWithTranscript -> {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Żaden z wyników (jeszcze) nie ma potwierdzonej transkrypcji.",
                        style = AuroraTextStyles.Body,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = tokens.spacing.l),
                    )
                }
            }

            else -> {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(tokens.spacing.l))
            }
        }
    }

    if (showCountryPicker) {
        CountryPickerSheet(
            onSelect = { code ->
                showCountryPicker = false
                onCountryResolved(code)
            },
            onDismiss = { showCountryPicker = false },
        )
    }

    if (showPodcastIndexDialog) {
        if (isPodcastIndexConfigured) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showPodcastIndexDialog = false },
                title = { Text("Podcast Index") },
                text = { Text("Klucz jest skonfigurowany i aktywny.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.clearPodcastIndexCredentials()
                        showPodcastIndexDialog = false
                    }) { Text("Usuń klucz") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showPodcastIndexDialog = false }) { Text("Zamknij") }
                },
            )
        } else {
            PodcastIndexSetupDialog(
                onConfirm = { apiKey, apiSecret ->
                    viewModel.setPodcastIndexCredentials(apiKey, apiSecret)
                    showPodcastIndexDialog = false
                },
                onDismiss = { showPodcastIndexDialog = false },
            )
        }
    }
}

@Composable
private fun PodcastSearchResultRow(
    result: PodcastSearchResult,
    hasTranscript: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAuroraTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s + 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.background),
        ) {
            if (result.artworkUrl != null) {
                AsyncImage(model = result.artworkUrl, contentDescription = null, modifier = Modifier.size(48.dp))
            }
        }
        Column(modifier = Modifier.padding(horizontal = tokens.spacing.m).weight(1f)) {
            Text(
                text = result.title,
                style = AuroraTextStyles.Body,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = result.author,
                style = AuroraTextStyles.Label,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Etap 54, zgłoszenie: "te ikonki napisów już w wyszukiwaniu" — patrz
        // `LibraryViewModel.checkTranscriptAvailability` (sprawdzane w tle, mapa dochodzi
        // stopniowo, więc ikonka może "domalować się" chwilę po pokazaniu wyników).
        if (hasTranscript) {
            Icon(
                imageVector = Icons.Filled.Translate,
                contentDescription = "Dostępna transkrypcja/tłumaczenie",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.padding(end = tokens.spacing.xs).size(16.dp),
            )
        }
        if (result.source == PodcastSearchSource.PODCAST_INDEX) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "Podcast Index",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
