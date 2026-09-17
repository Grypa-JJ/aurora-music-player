package com.aurora.player.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.ContentDomain
import com.aurora.player.designsystem.theme.LocalAuroraTokens

/**
 * Placeholder dla domen treści bez własnego backendu jeszcze (Audiobooki/Muzyka niezależna/
 * Archiwum, Etap 37) — spełnia wprost wymóg "onboarding/empty state dla nowych domen" z
 * DESIGN.md, zamiast martwego linku albo pustego ekranu.
 */
@Composable
fun ComingSoonScreen(domain: ContentDomain, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalAuroraTokens.current

    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(modifier = Modifier.padding(tokens.spacing.s)) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(tokens.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(domain.accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = domain.icon,
                    contentDescription = null,
                    tint = domain.accentColor,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                text = domain.label,
                style = AuroraTextStyles.Headline,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = tokens.spacing.m),
            )
            Text(
                text = comingSoonDescription(domain),
                style = AuroraTextStyles.Body,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = tokens.spacing.s),
            )
            Text(
                text = "Wkrótce",
                style = AuroraTextStyles.Label,
                color = domain.accentColor,
                modifier = Modifier.padding(top = tokens.spacing.m),
            )
        }
    }
}

private fun comingSoonDescription(domain: ContentDomain): String = when (domain) {
    ContentDomain.Audiobooki ->
        "Darmowe audiobooki domeny publicznej (LibriVox) — tysiące książek czytanych przez wolontariuszy."
    ContentDomain.MuzykaNiezalezna ->
        "Muzyka na licencji Creative Commons od niezależnych artystów (Jamendo) — do przeglądania po gatunku i nastroju."
    ContentDomain.Archiwum ->
        "Koncerty na żywo, stare audycje radiowe i muzyka eksperymentalna z Internet Archive."
    else -> ""
}
