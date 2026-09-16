package com.aurora.player.radio

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aurora.player.designsystem.components.TrackListItem
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.library.LibraryViewModel
import com.aurora.player.location.CountryPickerSheet
import com.aurora.player.location.resolveCountryCodeFromLastKnownLocation
import kotlinx.coroutines.launch

/**
 * Radio internetowe (Radio-Browser) — DESIGN.md Etap 31. Przy pierwszym wejściu próbuje
 * zaproponować stacje z kraju usera (geolokalizacja → Geocoder), a gdy to się nie powiedzie z
 * JAKIEGOKOLWIEK powodu (odmowa zgody, brak lokalizacji, brak działającego Geocodera) pokazuje
 * ręczny wybór kraju — user zawsze kończy z jakąś listą stacji, nigdy z pustym ekranem.
 */
@Composable
fun RadioScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stations by viewModel.radioStations.collectAsState()
    val isLoading by viewModel.isLoadingRadio.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val tokens = LocalAuroraTokens.current

    var searchQuery by remember { mutableStateOf("") }
    var showCountryPicker by remember { mutableStateOf(false) }
    var selectedCountryCode by remember { mutableStateOf<String?>(null) }
    var hasResolvedInitialCountry by remember { mutableStateOf(false) }

    fun onCountryResolved(code: String) {
        selectedCountryCode = code
        viewModel.loadTopRadioStations(code)
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

    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.s, vertical = tokens.spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = "Wstecz",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(40.dp).clickable(onClick = onBack),
            )
            Text(
                text = "Radio",
                style = AuroraTextStyles.Headline,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(start = tokens.spacing.xs),
            )
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = "Zmień kraj",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(end = tokens.spacing.m)
                    .size(24.dp)
                    .clickable { showCountryPicker = true },
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            singleLine = true,
            placeholder = { Text("Szukaj stacji") },
            leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (searchQuery.isBlank()) {
                        selectedCountryCode?.let { viewModel.loadTopRadioStations(it) }
                    } else {
                        viewModel.searchRadioStations(searchQuery)
                    }
                },
            ),
            shape = RoundedCornerShape(999.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
        )

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            stations.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Brak stacji. Spróbuj wyszukać po nazwie albo zmień kraj.",
                        style = AuroraTextStyles.Body,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(tokens.spacing.l),
                    )
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = tokens.spacing.s, vertical = tokens.spacing.s),
                ) {
                    items(stations, key = { it.stationUuid }) { station ->
                        val track = remember(station) { station.toTrack() }
                        TrackListItem(
                            title = track.title,
                            artist = listOfNotNull(
                                station.tags.substringBefore(',').ifBlank { null },
                                station.countryCode.ifBlank { null },
                            ).joinToString(" • ").ifBlank { "Radio" },
                            albumArtUrl = track.albumArtUri,
                            durationLabel = "NA ŻYWO",
                            isCurrentlyPlaying = playbackState.currentTrack?.id == track.id,
                            onClick = { viewModel.onPlayRadioStation(station) },
                        )
                    }
                }
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
}
