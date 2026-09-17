package com.aurora.player.audiobook

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.AudiobookSearchResult
import com.aurora.player.library.LibraryViewModel
import androidx.compose.foundation.clickable

/** Wyszukiwanie w katalogu LibriVox po tytule — DESIGN.md Etap 37. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAudiobookSheet(
    viewModel: LibraryViewModel,
    onAdded: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalAuroraTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val searchResults by viewModel.audiobookSearchResults.collectAsState()
    val isSearching by viewModel.isSearchingAudiobooks.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.m)) {
            Text(
                text = "Dodaj audiobook",
                style = AuroraTextStyles.Title,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = tokens.spacing.s),
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = { Text("Tytuł książki") },
                leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { if (searchQuery.isNotBlank()) viewModel.searchAudiobooks(searchQuery) },
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = tokens.spacing.s),
            )
        }

        when {
            isSearching -> {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            searchResults.isNotEmpty() -> {
                LazyColumn(modifier = Modifier.height(360.dp).padding(bottom = tokens.spacing.l)) {
                    items(searchResults, key = { it.id }) { result ->
                        AudiobookSearchResultRow(
                            result = result,
                            onClick = {
                                viewModel.addAudiobookToLibrary(result.id) { audiobook ->
                                    if (audiobook != null) onAdded()
                                }
                            },
                        )
                    }
                }
            }

            else -> {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(tokens.spacing.l))
            }
        }
    }
}

@Composable
private fun AudiobookSearchResultRow(result: AudiobookSearchResult, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            if (result.coverUrl != null) {
                AsyncImage(model = result.coverUrl, contentDescription = null, modifier = Modifier.size(48.dp))
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
    }
}
