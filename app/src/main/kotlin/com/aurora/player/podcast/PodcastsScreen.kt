package com.aurora.player.podcast

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.aurora.player.designsystem.components.GridRows
import com.aurora.player.designsystem.components.ProposedGridTile
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.ContentDomain
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.Podcast
import com.aurora.player.library.LibraryViewModel
import com.aurora.player.location.CountryPickerSheet
import com.aurora.player.location.resolveCountryCodeFromLastKnownLocation
import kotlinx.coroutines.launch

/**
 * Podcasty — DESIGN.md Etap 32, siatka propozycji dołożona w Etapie 37 (wcześniej ekran pokazywał
 * WYŁĄCZNIE subskrypcje, bez żadnych propozycji — jedyne z 6 źródeł treści bez domyślnej
 * zawartości). Ten sam wzorzec geolokalizacji "user zawsze kończy z jakąś listą" co
 * `RadioScreen`/`AddPodcastSheet`, świadomie reużyty, nie duplikowany.
 */
@Composable
fun PodcastsScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onOpenPodcast: (feedUrl: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val subscriptions by viewModel.podcastSubscriptions.collectAsState()
    val recommendations by viewModel.podcastSearchResults.collectAsState()
    val tokens = LocalAuroraTokens.current
    var showAddSheet by remember { mutableStateOf(false) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var hasResolvedInitialCountry by remember { mutableStateOf(false) }

    fun onCountryResolved(code: String) = viewModel.loadTopPodcasts(code)

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
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Wstecz",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(40.dp).clickable(onClick = onBack),
            )
            Text(
                text = ContentDomain.Podkasty.label,
                style = AuroraTextStyles.Headline,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(start = tokens.spacing.xs),
            )
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Dodaj podcast",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(end = tokens.spacing.m).size(26.dp).clickable { showAddSheet = true },
            )
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (recommendations.isNotEmpty()) {
                Text(
                    text = "Proponowane",
                    style = AuroraTextStyles.Label,
                    color = ContentDomain.Podkasty.accentColor,
                    modifier = Modifier.padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
                )
                GridRows(items = recommendations, horizontalPadding = tokens.spacing.m, spacing = tokens.spacing.s) { result ->
                    ProposedGridTile(
                        title = result.title,
                        subtitle = result.author,
                        imageUrl = result.artworkUrl,
                        fallbackIcon = Icons.Filled.Podcasts,
                        onClick = {
                            viewModel.subscribeToPodcast(result.feedUrl) { podcast ->
                                if (podcast != null) onOpenPodcast(podcast.feedUrl)
                            }
                        },
                    )
                }
            }

            Text(
                text = "Twoje subskrypcje",
                style = AuroraTextStyles.Label,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
            )
            if (subscriptions.isEmpty()) {
                Text(
                    text = "Brak subskrypcji — dotknij propozycji powyżej albo + u góry, żeby dodać podcast po adresie RSS.",
                    style = AuroraTextStyles.Body,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
                )
            } else {
                Column(modifier = Modifier.padding(horizontal = tokens.spacing.m)) {
                    subscriptions.forEach { podcast ->
                        PodcastRow(
                            podcast = podcast,
                            onClick = { onOpenPodcast(podcast.feedUrl) },
                            modifier = Modifier.padding(bottom = tokens.spacing.m),
                        )
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddPodcastSheet(
            viewModel = viewModel,
            onSubscribed = { showAddSheet = false },
            onDismiss = { showAddSheet = false },
        )
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

@Composable
private fun PodcastRow(podcast: Podcast, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalAuroraTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(tokens.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.background)) {
            if (podcast.artworkUrl != null) {
                AsyncImage(model = podcast.artworkUrl, contentDescription = null, modifier = Modifier.size(48.dp))
            } else {
                Icon(
                    imageVector = Icons.Filled.Podcasts,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp).padding(12.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(start = tokens.spacing.m)) {
            Text(
                text = podcast.title,
                style = AuroraTextStyles.Title,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = podcast.author,
                style = AuroraTextStyles.Label,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
