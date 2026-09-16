package com.aurora.player.eq

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aurora.player.designsystem.components.VerticalSlider
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.domain.model.EqDefaults
import com.aurora.player.domain.model.EqPresets
import com.aurora.player.library.LibraryViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Arkusz equalizera — patrz DESIGN.md sekcja 4.3. Etap 2: siatka suwaków + presety + on/off,
 * bez trybu "malowania" krzywej (Canvas+Bezier) i bez sekcji Bass Boost/Widener/Preamp osobno —
 * to zostawione na później, bo silnik DSP (na razie) realizuje tylko 10-pasmowy EQ.
 * [hazeState] opcjonalny (patrz DESIGN.md 2.4/4.3) — blur tła Now Playing pod spodem zamiast
 * płaskiego koloru; wzorzec `containerColor = Transparent` + `hazeEffect` na modifierze sheeta
 * zweryfikowany na oficjalnym przykładzie biblioteki Haze (ScaffoldSample, LargeTopAppBar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit,
    hazeState: HazeState? = null,
) {
    val eqState by viewModel.eqState.collectAsState()
    val tokens = LocalAuroraTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val surfaceColor = MaterialTheme.colorScheme.surface

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Zgłoszenie ze zrzutem ekranu: górne rogi sheeta wyglądały jak KOPUŁA, nie łagodne
        // zaokrąglenie — przyczyna: bez jawnego `shape`, ModalBottomSheet domyślnie bierze
        // `MaterialTheme.shapes.extraLarge`, a w AuroraShapes ten token to 999.dp (celowo, do
        // PIGUŁEK/przycisków — patrz Shape.kt) — przy szerokości sheeta dwa łuki 999dp po prostu
        // się zlewają w jedną półkolistą kopułę zamiast dwóch osobnych, subtelnych rogów. Jawny,
        // rozsądny promień tylko dla górnych rogów (ten sam co karty w reszcie appki) naprawia to
        // bez ruszania globalnego tokenu (który gdzie indziej jest poprawny dla pigułek).
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        // Etap 19 poprzednio dawał tu pełną Transparent + poleganie WYŁĄCZNIE na hazeEffect —
        // ale hazeEffect kryje tylko obszar POD samym paskiem uchwytu (drag handle), który
        // Material3 rysuje jako WŁASNY element sheeta nad tą treścią. Efekt: uchwyt miał za
        // słabe krycie i tło Now Playing prześwitywało dokładnie na styku z tytułem "Equalizer"
        // (zgłoszenie ze zrzutem ekranu). `containerColor` obejmuje CAŁY sheet (uchwyt + treść),
        // więc dając mu tu prawie pełną nieprzezroczystość zamiast zera, spójnie kryjemy wszystko
        // w jednym miejscu — hazeEffect zostaje na wierzchu jako sama faktura rozmycia, nie jedyna
        // warstwa krycia.
        containerColor = if (hazeState != null) surfaceColor.copy(alpha = 0.94f) else surfaceColor,
        // Zgłoszenie na żywo: nad sheetem widać pasek OSTREJ, nierozmytej okładki (np. napis
        // "GREATEST HITS" czytelny wprost) między poświatą Now Playing a samym sheetem —
        // `hazeEffect` rozmywa tylko WEWNĄTRZ sheeta, nie domyślny scrim Material3 (to zwykłe,
        // płaskie przyciemnienie malowane przez sam ModalBottomSheet, poza naszym Modifierem).
        // Nie da się podłączyć tam prawdziwego blura publicznym API — zamiast tego, ciemniejszy
        // scrim wystarczająco przyciemnia okładkę, żeby przejście do rozmytego sheeta nie rzucało
        // się w oczy jako "dziura" w efekcie.
        scrimColor = Color.Black.copy(alpha = 0.75f),
        modifier = if (hazeState != null) {
            Modifier.hazeEffect(state = hazeState, style = HazeMaterials.regular(surfaceColor))
        } else {
            Modifier
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Etap 19 wcześniej wymuszał tu fillMaxHeight(0.72f), próbując naprawić prześwit
                // nad sheetem — ale to leczyło objaw (słabe krycie uchwytu, już naprawione wyżej
                // przez containerColor), nie przyczynę, i przy okazji rozciągało sheet na sztywno
                // dużo powyżej realnej wysokości treści (10 suwaków + zakładki + Reset), zostawiając
                // pustą czarną przestrzeń pod "Reset" (kolejne zgłoszenie ze zrzutem). Naturalny
                // wrapContentHeight (domyślne zachowanie ModalBottomSheet) jest tu poprawny.
                .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Equalizer",
                    style = AuroraTextStyles.Title,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = eqState.enabled,
                    onCheckedChange = viewModel::onEqSetEnabled,
                )
            }

            LazyRow(
                modifier = Modifier.padding(top = tokens.spacing.m),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
            ) {
                items(EqPresets.GAINS.keys.toList()) { presetName ->
                    val isActive = eqState.activePresetName == presetName
                    Text(
                        text = presetName,
                        style = AuroraTextStyles.Label,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (isActive) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.background
                                },
                            )
                            .clickable { viewModel.onEqApplyPreset(presetName) }
                            .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = tokens.spacing.xl)
                    // Etap 19/20: powiększone z 180dp — bardziej namacalne, "premium" suwaki
                    // (zgodne z kierunkiem hi-fi z DESIGN.md), przy okazji naturalnie wypełniają
                    // więcej wysokości sheeta bez pustej, martwej przestrzeni.
                    .height(260.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                eqState.bands.forEachIndexed { index, band ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        VerticalSlider(
                            value = band.gainDb,
                            onValueChange = { viewModel.onEqSetBandGain(index, it) },
                            valueRange = EqDefaults.MIN_GAIN_DB..EqDefaults.MAX_GAIN_DB,
                            modifier = Modifier
                                .weight(1f)
                                .width(32.dp),
                        )
                        Spacer(modifier = Modifier.height(tokens.spacing.xs))
                        Text(
                            text = formatFrequency(band.frequencyHz),
                            style = AuroraTextStyles.Caption,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = tokens.spacing.m),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "Reset",
                    style = AuroraTextStyles.Label,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { viewModel.onEqReset() },
                )
            }
        }
    }
}

private fun formatFrequency(hz: Int): String =
    if (hz >= 1000) "${hz / 1000}k" else hz.toString()
