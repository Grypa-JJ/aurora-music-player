package com.aurora.player.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aurora.player.designsystem.theme.AuroraTextStyles
import com.aurora.player.designsystem.theme.LocalAuroraTokens
import com.aurora.player.navigation.LocalBottomChromeInset

/**
 * Konto + logowanie — fundament pod synchronizację między telefonem a desktopem (DESIGN.md
 * Etap 36). Świadomie NIE synchronizuje jeszcze żadnych danych (Ulubione/Playlisty/EQ) — to
 * dopiero pozwala się zalogować na obu urządzeniach tym samym kontem, reszta to kolejna runda.
 */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val accountState by viewModel.accountState.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val tokens = LocalAuroraTokens.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Column(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.s, vertical = tokens.spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = "Wstecz",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(40.dp).clickable(onClick = onBack),
            )
            Text(
                text = "Konto",
                style = AuroraTextStyles.Headline,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = tokens.spacing.xs),
            )
        }

        when (val state = accountState) {
            AccountUiState.Loading -> Unit
            is AccountUiState.SignedIn -> SignedInContent(
                email = state.email,
                isSubmitting = isSubmitting,
                onSignOut = viewModel::signOut,
                modifier = Modifier.padding(horizontal = tokens.spacing.m),
            )
            AccountUiState.SignedOut -> SignedOutContent(
                isSubmitting = isSubmitting,
                onSignIn = viewModel::signIn,
                onSignUp = viewModel::signUp,
                modifier = Modifier.padding(horizontal = tokens.spacing.m),
            )
        }

        // Etap 40, zgłoszenie: bez tego Snackbar mógł wylądować pod pływającym mini-playerem/
        // nawigacją na dole (NavHost jest pełnoekranowy, patrz AuroraNavHost/LocalBottomChromeInset).
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(bottom = LocalBottomChromeInset.current),
        )
    }
}

@Composable
private fun SignedInContent(
    email: String?,
    isSubmitting: Boolean,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAuroraTokens.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Zalogowano jako",
            style = AuroraTextStyles.Body,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Text(
            text = email ?: "(brak adresu e-mail)",
            style = AuroraTextStyles.Headline,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(tokens.spacing.l))
        OutlinedButton(onClick = onSignOut, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
            Text("Wyloguj się")
        }
    }
}

@Composable
private fun SignedOutContent(
    isSubmitting: Boolean,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalAuroraTokens.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Zaloguj się, żeby zsynchronizować Ulubione, playlisty i ustawienia między " +
                "telefonem a komputerem.",
            style = AuroraTextStyles.Body,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Spacer(modifier = Modifier.height(tokens.spacing.l))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            singleLine = true,
            label = { Text("E-mail") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(tokens.spacing.s))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            singleLine = true,
            label = { Text("Hasło") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(tokens.spacing.m))

        val canSubmit = !isSubmitting && email.isNotBlank() && password.length >= 6

        Button(
            onClick = { onSignIn(email, password) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Zaloguj się")
            }
        }
        Spacer(modifier = Modifier.height(tokens.spacing.s))
        TextButton(
            onClick = { onSignUp(email, password) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Nie masz konta? Zarejestruj się")
        }
    }
}
