package com.aurora.player.projectm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Panel ustawień wizualizera (Etap 10 część 2) — patrz [ProjectMVisualizerSettings] dla wyjaśnienia
 * dlaczego akurat te kontrolki (i nie np. "rozmycie", które nie ma odpowiednika w API projectM).
 */
@Composable
fun VisualizerSettingsPanel(
    settings: ProjectMVisualizerSettings,
    onSettingsChange: (ProjectMVisualizerSettings) -> Unit,
    modifier: Modifier = Modifier,
    // Etap 19/20 — null gdy nie ma jeszcze bieżącego presetu do zablokowania (np. lista pusta);
    // patrz [PresetBlocklistStore] i wywołanie w ProjectMSurface.
    onBlockCurrentPreset: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            // Zgłoszenie na żywo: w małej ramce inline (kwadrat wielkości okładki) panel miał
            // WIĘCEJ kontrolek niż mieściło się w wysokości — zwykły Column bez przewijania po
            // prostu UCINAŁ dół (zniknęło "Kolor poświaty" i przycisk blokady presetu) zamiast
            // pokazać komunikat o błędzie, więc łatwo było to przeoczyć. `verticalScroll` czyni
            // to niewrażliwe na przyszłość — kolejna dołożona kontrolka (np. FPS z Etapu 18)
            // przewinie się zamiast znowu ucinać dół w małym kontenerze.
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            text = "Ustawienia wizualizera",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.height(12.dp))

        LabeledSlider(
            label = "Szybkość zmiany presetów",
            valueText = "${settings.presetDurationSeconds.roundToInt()}s",
            value = settings.presetDurationSeconds.toFloat(),
            valueRange = ProjectMVisualizerSettings.MIN_PRESET_DURATION_SECONDS.toFloat()..
                ProjectMVisualizerSettings.MAX_PRESET_DURATION_SECONDS.toFloat(),
            onValueChange = { onSettingsChange(settings.copy(presetDurationSeconds = it.toDouble())) },
        )

        LabeledSlider(
            label = "Czułość na beat",
            valueText = "%.1f".format(settings.beatSensitivity),
            value = settings.beatSensitivity,
            valueRange = ProjectMVisualizerSettings.MIN_SENSITIVITY..ProjectMVisualizerSettings.MAX_SENSITIVITY,
            onValueChange = { onSettingsChange(settings.copy(beatSensitivity = it)) },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Mocny bas = natychmiastowa zmiana presetu",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = settings.hardCutEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(hardCutEnabled = it)) },
                // Pełne, świadome przypisanie kolorów zamiast dziedziczenia domyślnego
                // Material3 wyglądu (zgłoszenie: Switch "nie wygląda profesjonalnie") —
                // przezroczyste obwódki usuwają domyślny konturowy pierścień, kolory spójne
                // z resztą panelu (biały kciuk, fioletowy tor zgodny z akcentem appki).
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedBorderColor = Color.Transparent,
                    checkedIconColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.8f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.16f),
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }

        if (settings.hardCutEnabled) {
            LabeledSlider(
                label = "Czułość natychmiastowej zmiany",
                valueText = "%.1f".format(settings.hardCutSensitivity),
                value = settings.hardCutSensitivity,
                valueRange = ProjectMVisualizerSettings.MIN_SENSITIVITY..ProjectMVisualizerSettings.MAX_SENSITIVITY,
                onValueChange = { onSettingsChange(settings.copy(hardCutSensitivity = it)) },
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Kolor poświaty",
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(modifier = Modifier.padding(top = 8.dp)) {
            GlowColorSource.entries.forEach { source ->
                val isSelected = source == settings.colorSource
                Text(
                    text = source.displayName,
                    color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.12f))
                        .clickable { onSettingsChange(settings.copy(colorSource = source)) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

        if (onBlockCurrentPreset != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onBlockCurrentPreset)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.VisibilityOff,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Nie pokazuj więcej tego wyglądu",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
            Text(valueText, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            valueRange = valueRange,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
        )
    }
}
