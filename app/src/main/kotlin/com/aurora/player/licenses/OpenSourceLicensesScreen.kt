package com.aurora.player.licenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wymagane przez LGPL-2.1 (biblioteka projectM, patrz DESIGN.md Etap 9) — notka o użyciu
 * biblioteki, jej copyright, pełny tekst licencji i wskazanie dokładnego kodu źródłowego wersji,
 * której appka używa (nie link do "żywego" repo — to za mało wg wiki projektu, patrz research).
 * Osobno: atrybucja paczki presetów (status prawny słabszy niż sama biblioteka — patrz niżej).
 */
@Composable
fun OpenSourceLicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tokens = LocalAuroraTokens.current
    var lgplText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        lgplText = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("licenses/LGPL-2.1-projectM.txt").bufferedReader().use { it.readText() }
            }.getOrDefault("")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(tokens.spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Zamknij",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onBack),
            )
            Text(
                text = "Licencje open source",
                style = AuroraTextStyles.Headline,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = tokens.spacing.m),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = tokens.spacing.l),
        ) {
            SectionTitle("projectM (wizualizer)")
            BodyText(
                "Wizualizer widoczny na ekranie Teraz odtwarzane korzysta z biblioteki " +
                    "projectM — Milkdrop-esque visualisation SDK, wersja 4.1.7.\n\n" +
                    "Copyright (C) 2003-2024 projectM Team.\n" +
                    "Licencja: GNU Lesser General Public License, wersja 2.1 (LGPL-2.1-or-later).\n\n" +
                    "Biblioteka jest linkowana jako osobna, dynamicznie ładowana biblioteka " +
                    "(libprojectM-4.so), zgodnie z wymogami LGPL dla aplikacji zamkniętoźródłowych " +
                    "— nie jest wtopiona w kod tej appki.\n\n" +
                    "Dokładny kod źródłowy tej wersji: " +
                    "github.com/projectM-visualizer/projectm/releases/tag/v4.1.7 " +
                    "(konkretne wydanie, nie zmieniający się branch). Jeśli ten link kiedykolwiek " +
                    "przestanie działać, napisz na adres kontaktowy dewelopera podany w opisie " +
                    "aplikacji — prześlemy archiwum źródeł (oferta ważna min. 3 lata od wydania tej " +
                    "wersji aplikacji, zgodnie z LGPL §6c).",
            )

            SectionTitle("Presety wizualizera — \"Cream of the Crop\"")
            BodyText(
                "Kuratorstwo: Jason Fletcher / ISOSCELES (projectM-visualizer/presets-cream-of-the-crop). " +
                    "Presety w formacie Milkdrop (.milk) w zdecydowanej większości nigdy nie miały " +
                    "formalnej licencji od swoich autorów — zespół projectM zakłada status public " +
                    "domain (bez formalnej gwarancji) i usuwa konkretny preset z przyszłych wydań na " +
                    "życzenie autora. Pełny tekst: " +
                    "github.com/projectM-visualizer/presets-cream-of-the-crop/blob/master/LICENSE.md\n\n" +
                    "Paczka tekstur: Milkdrop Texture Pack (projectM-visualizer/presets-milkdrop-texture-pack) " +
                    "— bez jakiejkolwiek deklaracji licencyjnej ze strony projektu.",
            )

            if (lgplText.isNotBlank()) {
                SectionTitle("Pełny tekst licencji LGPL-2.1")
                Text(
                    text = lgplText,
                    style = AuroraTextStyles.Caption,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = tokens.spacing.xxl),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val tokens = LocalAuroraTokens.current
    Text(
        text = text,
        style = AuroraTextStyles.Title,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = tokens.spacing.l, bottom = tokens.spacing.s),
    )
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = AuroraTextStyles.Label,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
    )
}
