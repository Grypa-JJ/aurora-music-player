package com.aurora.player.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens

/**
 * Nagłówek sekcji "art-first" shelfa (Home/Odkrywaj, Etap 39/41) — wyodrębniony z Home, żeby
 * Odkrywaj mógł użyć DOKŁADNIE tego samego wzorca zamiast duplikować layout (user: "Home i
 * Discover mają wyglądać jak część jednej rodziny wizualnej"). `Title` (18-20sp Medium), NIE
 * `Label`+accentColor jak stary `HomeSection` z Etapu 37 — dashboard nie jest już kolorowy per
 * sekcja, akcent żyje tylko w okładkach.
 */
@Composable
fun ShelfHeading(title: String, modifier: Modifier = Modifier) {
    val tokens = LocalAuroraTokens.current
    Text(
        text = title,
        style = AuroraTextStyles.Title,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s)
            .padding(top = tokens.spacing.l),
    )
}

/** Poziomy "shelf" (LazyRow) pod [ShelfHeading] — wyodrębniony z Home, patrz [ShelfHeading]. */
@Composable
fun HorizontalShelf(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    val tokens = LocalAuroraTokens.current
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = tokens.spacing.m),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
        content = content,
    )
}
