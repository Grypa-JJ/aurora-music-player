package com.aurora.player.podcast

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Własny (darmowy) klucz Podcast Index usera — NIGDY jeden wspólny klucz appki, patrz
 * [PodcastIndexCredentialStore]. Link "Załóż darmowy klucz" celowo NIE otwiera przeglądarki sam
 * (appka nie ma jeszcze wzorca in-app browser/CustomTabs) — instrukcja tekstowa wystarcza,
 * podcastindex.org/register to jedna, prosta strona.
 */
@Composable
fun PodcastIndexSetupDialog(
    onConfirm: (apiKey: String, apiSecret: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var apiKey by remember { mutableStateOf("") }
    var apiSecret by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Podcast Index (opcjonalnie)") },
        text = {
            Column {
                Text("Darmowy, własny klucz z podcastindex.org/register — appka nigdy nie dzieli jednego klucza między userów.")
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    singleLine = true,
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = apiSecret,
                    onValueChange = { apiSecret = it },
                    singleLine = true,
                    label = { Text("API Secret") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = apiKey.isNotBlank() && apiSecret.isNotBlank(),
                onClick = { onConfirm(apiKey.trim(), apiSecret.trim()) },
            ) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
