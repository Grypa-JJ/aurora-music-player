package com.aurora.player.audiobook

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aurora.player.archive.ArchiveLibraryGroupRow
import com.aurora.player.designsystem.components.GridRows
import com.aurora.player.designsystem.components.ProposedGridTile
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.ContentDomain
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.Audiobook
import com.aurora.player.domain.model.ArchiveCategory
import com.aurora.player.library.LibraryViewModel
import java.util.Locale

/**
 * Audiobooki (LibriVox pod spodem, DESIGN.md Etap 37). Siatka 2-kolumnowa propozycji wg
 * języka+popularności na górze (styl "Made for you" ze Spotify Wrapped, referencja usera), pod
 * spodem lista tego, co user już dodał do biblioteki. Ekran ma sensowną, ograniczoną liczbę
 * elementów (propozycje capowane na ~30, biblioteka realistycznie dziesiątki) — zwykły
 * przewijany `Column`, nie `LazyColumn`, żeby uniknąć zagnieżdżania dwóch leniwych list.
 */
@Composable
fun AudiobooksScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onOpenAudiobook: (id: String) -> Unit,
    onOpenArchiveItem: (identifier: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val library by viewModel.audiobookLibrary.collectAsState()
    val recommendations by viewModel.audiobookSearchResults.collectAsState()
    val archiveGroups by viewModel.archiveLibraryByCategory.collectAsState()
    val archiveAudiobooks = archiveGroups[ArchiveCategory.AUDIOBOOKS].orEmpty()
    val tokens = LocalAuroraTokens.current
    var showAddSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadRecommendedAudiobooks(Locale.getDefault().isO3Language) }

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
                text = ContentDomain.Audiobooki.label,
                style = AuroraTextStyles.Headline,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(start = tokens.spacing.xs),
            )
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Dodaj audiobook",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(end = tokens.spacing.m).size(26.dp).clickable { showAddSheet = true },
            )
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Zgłoszenie: to, co user już ma, ma być widoczne od razu, bez przewijania przez
            // propozycje — "Twoja biblioteka" (+ Archiwum) najpierw, "Proponowane" niżej.
            Text(
                text = "Twoja biblioteka",
                style = AuroraTextStyles.Label,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
            )
            if (library.isEmpty() && archiveAudiobooks.isEmpty()) {
                Text(
                    text = "Brak audiobooków — dotknij propozycji poniżej albo + u góry, żeby wyszukać książkę z domeny publicznej (LibriVox).",
                    style = AuroraTextStyles.Body,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
                )
            } else if (library.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = tokens.spacing.m)) {
                    library.forEach { audiobook ->
                        AudiobookRow(
                            audiobook = audiobook,
                            onClick = { onOpenAudiobook(audiobook.id) },
                            modifier = Modifier.padding(bottom = tokens.spacing.m),
                        )
                    }
                }
            }

            // Etap 53, zgłoszenie: pobrane/streamowane audiobooki z Internet Archive (kategoria
            // "Audiobooki" w Archiwum) mają być widoczne TUTAJ, obok tych z LibriVox, nie tylko w
            // generycznym ekranie "Pobrane". Tap otwiera ten sam ArchiveItemDetailScreen co z
            // Archiwum — pełna lista ścieżek, bez duplikowania logiki pobierania/streamu.
            if (archiveAudiobooks.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = tokens.spacing.m)) {
                    archiveAudiobooks.forEach { group ->
                        ArchiveLibraryGroupRow(
                            group = group,
                            fallbackIcon = Icons.AutoMirrored.Filled.MenuBook,
                            onClick = { onOpenArchiveItem(group.identifier) },
                            modifier = Modifier.padding(bottom = tokens.spacing.m),
                        )
                    }
                }
            }

            if (recommendations.isNotEmpty()) {
                Text(
                    text = "Proponowane",
                    style = AuroraTextStyles.Label,
                    color = ContentDomain.Audiobooki.accentColor,
                    modifier = Modifier.padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
                )
                GridRows(items = recommendations, horizontalPadding = tokens.spacing.m, spacing = tokens.spacing.s) { result ->
                    ProposedGridTile(
                        title = result.title,
                        subtitle = result.author,
                        imageUrl = result.coverUrl,
                        fallbackIcon = Icons.AutoMirrored.Filled.MenuBook,
                        onClick = { viewModel.addAudiobookToLibrary(result.id) { onOpenAudiobook(result.id) } },
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddAudiobookSheet(
            viewModel = viewModel,
            onAdded = { showAddSheet = false },
            onDismiss = { showAddSheet = false },
        )
    }
}

@Composable
private fun AudiobookRow(audiobook: Audiobook, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            if (audiobook.coverUrl != null) {
                AsyncImage(model = audiobook.coverUrl, contentDescription = null, modifier = Modifier.size(48.dp))
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp).padding(12.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(start = tokens.spacing.m)) {
            Text(
                text = audiobook.title,
                style = AuroraTextStyles.Title,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
            Text(
                text = audiobook.author,
                style = AuroraTextStyles.Label,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
