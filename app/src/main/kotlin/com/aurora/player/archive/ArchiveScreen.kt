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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.aurora.player.domain.model.ArchiveCollections
import com.aurora.player.domain.model.ArchiveItem
import com.aurora.player.library.LibraryViewModel

private data class CollectionTile(val label: String, val collection: String)

/** Sentinel odróżniający "Dla Ciebie" (personalizacja) od realnej kolekcji IA — patrz LaunchedEffect niżej. */
private const val PERSONALIZED_TILE = "personalized"

private val COLLECTION_TILES = listOf(
    CollectionTile("Dla Ciebie", PERSONALIZED_TILE),
    CollectionTile("Koncerty na żywo", ArchiveCollections.LIVE_MUSIC),
    CollectionTile("Netlabele — muzyka współczesna", ArchiveCollections.NETLABELS),
    CollectionTile("Stare radio", ArchiveCollections.OLD_TIME_RADIO),
)

/**
 * "Archiwum" (Internet Archive pod spodem, DESIGN.md Etap 37) — koncerty na żywo, netlabele
 * (współczesna muzyka niezależna, nie tylko starocie), stare audycje radiowe. Kafle kolekcji +
 * wyszukiwarka globalna po `mediatype:audio`.
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
    val tokens = LocalAuroraTokens.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedCollection by remember { mutableStateOf<String?>(PERSONALIZED_TILE) }

    LaunchedEffect(Unit) { viewModel.loadPersonalizedArchive() }

    fun selectCollection(collection: String) {
        searchQuery = ""
        selectedCollection = collection
        if (collection == PERSONALIZED_TILE) viewModel.loadPersonalizedArchive() else viewModel.browseArchiveCollection(collection)
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
            onValueChange = { searchQuery = it },
            singleLine = true,
            placeholder = { Text("Szukaj w archiwum") },
            leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (searchQuery.isBlank()) {
                        selectedCollection?.let { selectCollection(it) }
                    } else {
                        selectedCollection = null
                        viewModel.searchArchive(searchQuery)
                    }
                },
            ),
            shape = RoundedCornerShape(999.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = tokens.spacing.m),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
        ) {
            items(COLLECTION_TILES) { tile ->
                CollectionChip(
                    label = tile.label,
                    selected = selectedCollection == tile.collection,
                    onClick = { selectCollection(tile.collection) },
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
                LazyColumn(contentPadding = PaddingValues(horizontal = tokens.spacing.m, vertical = tokens.spacing.s)) {
                    items(items, key = { it.identifier }) { item ->
                        ArchiveItemRow(
                            item = item,
                            onClick = { onOpenItem(item.identifier) },
                            modifier = Modifier.padding(bottom = tokens.spacing.m),
                        )
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
