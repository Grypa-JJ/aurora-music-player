package com.aurora.player.genius

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.GeniusMix
import com.aurora.player.library.LibraryViewModel

/**
 * "Genius Mixes" — gotowe playlisty z klastrowania, bez wskazywania utworu-ziarna.
 * Patrz DESIGN.md sekcja 3.4/5.5. Etap 4: uproszczona wersja — jedna lista kart zamiast
 * wielu tematycznych "półek" ("Bo słuchałeś X", "Odkryj ponownie" itd. to etap 5, wymagają
 * dłuższej realnej historii odsłuchań niż to co zbiera się w pierwszych dniach używania appki).
 */
@Composable
fun GeniusMixesScreen(
    viewModel: LibraryViewModel,
    onOpenMixPreview: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mixes by viewModel.geniusMixes.collectAsState()
    val isLoading by viewModel.isLoadingMixes.collectAsState()
    val tokens = LocalAuroraTokens.current

    LaunchedEffect(Unit) { viewModel.loadGeniusMixes() }

    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Text(
            text = "Genius",
            style = AuroraTextStyles.Headline,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(
                horizontal = tokens.spacing.m,
                vertical = tokens.spacing.l,
            ),
        )

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            mixes.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(tokens.spacing.l),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Za mało utworów w bibliotece, żeby zbudować miksy. Posłuchaj trochę i wróć tutaj — im więcej historii, tym trafniejsze rekomendacje.",
                        style = AuroraTextStyles.Body,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
                ) {
                    // Klucz po indeksie, NIE po nazwie — patrz komentarz w
                    // GeniusRepositoryImpl.generateGeniusMixes: nawet po naprawie deduplikacji
                    // nazw u źródła, klucz LazyColumn nie powinien nigdy zależeć od pola, którego
                    // unikalności nie gwarantuje typ (GeniusMix.name to zwykły String).
                    itemsIndexed(mixes) { index, mix ->
                        // Etap 22, user: "brak podglądu playlist" — tap otwiera teraz podgląd
                        // tracklisty (GeniusMixPreviewScreen) zamiast odtwarzać od razu; "Odtwórz"
                        // jest tam jednym tapnięciem dalej, więc nic nie zostało utracone.
                        GeniusMixCard(
                            mix = mix,
                            onClick = { onOpenMixPreview(index) },
                            modifier = Modifier.padding(bottom = tokens.spacing.m),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeniusMixCard(
    mix: GeniusMix,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAuroraTokens.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(tokens.spacing.m),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.padding(start = tokens.spacing.m)) {
                Text(
                    text = mix.name,
                    style = AuroraTextStyles.Title,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${mix.tracks.size} utworów",
                    style = AuroraTextStyles.Label,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Odtwórz miks",
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
