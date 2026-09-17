package com.aurora.player.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aurora.player.sync.AuthRepository
import com.aurora.player.sync.SupabaseAuthRepository
import com.aurora.player.sync.SupabaseConfig
import com.aurora.player.sync.createAuroraSupabaseClient
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

/**
 * Etap 36: konto + sync na desktopie — ten sam [AuthRepository] (`:core:sync`) co na Androidzie,
 * tylko konfiguracja idzie ze zmiennych środowiskowych zamiast `local.properties`/`BuildConfig`
 * (na desktopie nie ma odpowiednika Android Gradle Plugin do tego). Świadomie NIE crashuje appki,
 * jeśli `SUPABASE_URL`/`SUPABASE_ANON_KEY` nie są ustawione — panel konta po prostu pokazuje o
 * tym komunikat zamiast martwej ciszy albo wyjątku sieciowego bez kontekstu.
 */
private val supabaseUrl: String = System.getenv("SUPABASE_URL").orEmpty()
private val supabaseAnonKey: String = System.getenv("SUPABASE_ANON_KEY").orEmpty()
private val isSupabaseConfigured: Boolean = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()

private val authRepository: AuthRepository? by lazy {
    if (!isSupabaseConfigured) {
        null
    } else {
        SupabaseAuthRepository(createAuroraSupabaseClient(SupabaseConfig(url = supabaseUrl, anonKey = supabaseAnonKey)))
    }
}

@Composable
fun AccountButton() {
    var showDialog by remember { mutableStateOf(false) }
    IconButton(onClick = { showDialog = true }) {
        Icon(Icons.Filled.AccountCircle, contentDescription = "Konto")
    }
    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(shape = MaterialTheme.shapes.large) {
                AccountDialogContent(modifier = Modifier.padding(24.dp))
            }
        }
    }
}

@Composable
private fun AccountDialogContent(modifier: Modifier = Modifier) {
    val repository = authRepository
    if (repository == null) {
        Column(modifier = modifier) {
            Text("Konto niedostępne", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Ustaw zmienne środowiskowe SUPABASE_URL i SUPABASE_ANON_KEY, żeby włączyć " +
                    "logowanie i synchronizację z telefonem.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        return
    }

    val sessionStatus by repository.sessionStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submit(action: suspend () -> Result<Unit>) {
        scope.launch {
            isSubmitting = true
            errorMessage = null
            action().onFailure { errorMessage = it.message ?: "Coś poszło nie tak" }
            isSubmitting = false
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        when (val status = sessionStatus) {
            is SessionStatus.Authenticated -> {
                Text("Zalogowano jako", style = MaterialTheme.typography.bodySmall)
                Text(status.session.user?.email ?: "(brak adresu e-mail)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { submit { repository.signOut() } },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Wyloguj się")
                }
            }
            else -> {
                Text("Zaloguj się", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Hasło") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                val canSubmit = !isSubmitting && email.isNotBlank() && password.length >= 6
                Button(
                    onClick = { submit { repository.signIn(email, password) } },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp))
                    } else {
                        Text("Zaloguj się")
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { submit { repository.signUp(email, password) } },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Nie masz konta? Zarejestruj się")
                }
                errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
