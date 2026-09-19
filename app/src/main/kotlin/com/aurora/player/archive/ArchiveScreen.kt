package com.aurora.player.archive

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Museum
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.ContentDomain
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.ArchiveCategory
import com.aurora.player.domain.model.ArchiveItem
import com.aurora.player.library.LibraryViewModel

/** Etykiety kategorii w UI — kolejność determinuje kolejność chipów. */
private val CATEGORY_LABELS = linkedMapOf(
    ArchiveCategory.MUSIC to "Muzyka",
    ArchiveCategory.PODCASTS to "Podcasty",
    ArchiveCategory.AUDIOBOOKS to "Audiobooki",
    ArchiveCategory.RADIO to "Radio",
)

/**
 * "Archiwum" (Internet Archive pod spodem, DESIGN.md Etap 37) — koncerty na żywo, netlabele
 * (współczesna muzyka niezależna, nie tylko starocie), podcasty, audiobooki, stare audycje
 * radiowe. Chipy kategorii ([ArchiveCategory]) filtrują zarówno przeglądanie, jak i wyszukiwarkę
 * tekstową; brak wybranej kategorii = "Dla Ciebie" (personalizacja) albo wyszukiwanie globalne.
 *
 * Etap 51 (zgłoszenie): wyszukiwanie/kategoria/wyniki żyją teraz w `LibraryViewModel`, nie lokalnie
 * — powrót z `ArchiveItemDetailScreen` (push/pop w Compose Navigation kasuje i odtwarza tę
 * kompozycję) nie zeruje już wpisanego zapytania. Doszły też: podpowiedzi wykonawców z lokalnej
 * biblioteki pod wpisywanym tekstem, i doładowywanie kolejnej strony przy przewinięciu do końca
 * listy (`loadMoreArchiveItems`) zamiast sztywnego limitu.
 */
@Composable
fun ArchiveScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onOpenItem: (identifier: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items by viewModel.archiveItems.collectAsState()
    val isLoading by viewModel.isLoadingArchive.collectAsState()
    val isLoadingMore by viewModel.isLoadingMoreArchive.collectAsState()
    val canLoadMore by viewModel.archiveCanLoadMore.collectAsState()
    val searchQuery by viewModel.archiveSearchQuery.collectAsState()
    val selectedCategory by viewModel.archiveSelectedCategory.collectAsState()
    val tokens = LocalAuroraTokens.current

    LaunchedEffect(Unit) { viewModel.ensureArchiveLoaded() }

    val suggestions = remember(searchQuery) { viewModel.archiveArtistSuggestions(searchQuery) }

    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalCount = listState.layoutInfo.totalItemsCount
            totalCount > 0 && lastVisible >= totalCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, canLoadMore, isLoading) {
        if (shouldLoadMore && canLoadMore && !isLoading) viewModel.loadMoreArchiveItems()
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
                text = ContentDomain.Archiwum.label,
                style = AuroraTextStyles.Headline,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(start = tokens.spacing.xs),
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onArchiveSearchQueryChange(it) },
            singleLine = true,
            placeholder = { Text("Szukaj w archiwum (np. #pl #rap)") },
            leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.onArchiveSearchSubmit() }),
            shape = RoundedCornerShape(999.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
        )

        // Podpowiedzi wykonawców z lokalnej biblioteki (patrz KDoc ekranu) — tap wypełnia pole i od
        // razu szuka, bo to jedyny sensowny następny krok po wybraniu konkretnego wykonawcy.
        if (suggestions.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = tokens.spacing.m),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
                modifier = Modifier.padding(bottom = tokens.spacing.s),
            ) {
                items(suggestions) { artist ->
                    CollectionChip(
                        label = artist,
                        selected = false,
                        onClick = {
                            viewModel.onArchiveSearchQueryChange(artist)
                            viewModel.onArchiveSearchSubmit()
                        },
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = tokens.spacing.m),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
        ) {
            items(CATEGORY_LABELS.entries.toList()) { (category, label) ->
                CollectionChip(
                    label = label,
                    selected = selectedCategory == category,
                    onClick = { viewModel.onToggleArchiveCategory(category) },
                )
            }
        }

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Brak wyników.",
                        style = AuroraTextStyles.Body,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(tokens.spacing.l),
                    )
                }
            }

            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
                ) {
                    items(items, key = { it.identifier }) { item ->
                        ArchiveItemRow(
                            item = item,
                            onClick = { onOpenItem(item.identifier) },
                            modifier = Modifier.padding(bottom = tokens.spacing.m),
                        )
                    }
                    if (isLoadingMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(tokens.spacing.m), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = LocalAuroraTokens.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
    ) {
        Text(
            text = label,
            style = AuroraTextStyles.Label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ArchiveItemRow(item: ArchiveItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            if (item.coverUrl != null) {
                AsyncImage(model = item.coverUrl, contentDescription = null, modifier = Modifier.size(48.dp))
            } else {
                Icon(
                    imageVector = Icons.Filled.Museum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp).padding(12.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(start = tokens.spacing.m)) {
            Text(
                text = item.title,
                style = AuroraTextStyles.Title,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(item.creator.ifBlank { null }, item.year?.toString()).joinToString(" • "),
                style = AuroraTextStyles.Label,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
