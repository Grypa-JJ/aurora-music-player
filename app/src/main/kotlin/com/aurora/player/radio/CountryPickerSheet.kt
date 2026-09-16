package com.aurora.player.radio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import java.util.Locale

/**
 * Ręczny wybór kraju dla Radia — fallback gdy user odmówi geolokalizacji ALBO gdy się ona nie
 * powiedzie (Geocoder bez wyniku, częste na emulatorach) — DESIGN.md Etap 31.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(
    onSelect: (countryCode: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalAuroraTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val countries = remember {
        Locale.getISOCountries()
            .map { code -> code to Locale("", code).displayCountry }
            .filter { (_, name) -> name.isNotBlank() }
            .sortedBy { it.second }
    }
    val filtered = remember(countries, query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) countries else countries.filter { it.second.contains(trimmed, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.m)) {
            Text(
                text = "Wybierz kraj",
                style = AuroraTextStyles.Title,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = tokens.spacing.s),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Szukaj kraju") },
                modifier = Modifier.fillMaxWidth().padding(bottom = tokens.spacing.s),
            )
        }
        LazyColumn(modifier = Modifier.height(400.dp).padding(bottom = tokens.spacing.l)) {
            items(filtered, key = { it.first }) { (code, name) ->
                Text(
                    text = name,
                    style = AuroraTextStyles.Body,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(code) }
                        .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s + 4.dp),
                )
            }
        }
    }
}
