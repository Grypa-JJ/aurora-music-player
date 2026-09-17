package com.aurora.player.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.player.sync.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Etap 36: konto + sesja. Osobny ViewModel (nie dopisany do [com.aurora.player.library.LibraryViewModel],
 * który już jest duży) — konto nie potrzebuje żadnego stanu odtwarzacza, więc dzielenie go z
 * resztą appki tylko rozdymałoby ten jeden, centralny ViewModel bez korzyści.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val accountState: StateFlow<AccountUiState> = authRepository.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated -> AccountUiState.SignedIn(email = status.session.user?.email)
                SessionStatus.Initializing -> AccountUiState.Loading
                is SessionStatus.NotAuthenticated, is SessionStatus.RefreshFailure -> AccountUiState.SignedOut
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountUiState.Loading)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

    fun signUp(email: String, password: String) = submit {
        authRepository.signUp(email, password)
    }

    fun signIn(email: String, password: String) = submit {
        authRepository.signIn(email, password)
    }

    fun signOut() = submit {
        authRepository.signOut()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun submit(action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _isSubmitting.value = true
            action()
                .onFailure { _errorMessage.value = it.message ?: "Coś poszło nie tak" }
            _isSubmitting.value = false
        }
    }
}

sealed interface AccountUiState {
    data object Loading : AccountUiState
    data object SignedOut : AccountUiState
    data class SignedIn(val email: String?) : AccountUiState
}
