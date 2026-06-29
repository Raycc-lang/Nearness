package com.yourname.nearness.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.nearness.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val isSending: Boolean = false,
    val linkSent: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(
    private val auth: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, error = null) }

    fun sendMagicLink() {
        val email = _uiState.value.email
        if (email.isBlank()) {
            _uiState.update { it.copy(error = "Enter your email") }
            return
        }
        _uiState.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            auth.sendMagicLink(email)
                .onSuccess { _uiState.update { s -> s.copy(isSending = false, linkSent = true) } }
                .onFailure { e ->
                    _uiState.update { s -> s.copy(isSending = false, error = e.message ?: "Failed to send link") }
                }
        }
    }
}
