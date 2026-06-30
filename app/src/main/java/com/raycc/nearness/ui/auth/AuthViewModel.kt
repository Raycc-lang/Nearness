package com.raycc.nearness.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycc.nearness.data.AuthRepository
import kotlinx.coroutines.delay
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
    val resendCooldown: Int = 0,
)

class AuthViewModel(
    private val auth: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, error = null) }

    fun sendMagicLink() {
        val s = _uiState.value
        if (s.email.isBlank()) {
            _uiState.update { it.copy(error = "Enter your email") }
            return
        }
        if (s.isSending || s.resendCooldown > 0) return
        _uiState.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            auth.sendMagicLink(s.email)
                .onSuccess {
                    _uiState.update {
                        it.copy(isSending = false, linkSent = true, resendCooldown = COOLDOWN_SECONDS)
                    }
                    startCooldown()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSending = false, error = friendlyError(e)) }
                }
        }
    }

    /** Called when an incoming magic-link deep link resolves to an error. */
    fun onDeepLinkError(message: String) {
        _uiState.update { it.copy(isSending = false, linkSent = false, error = message) }
    }

    private fun startCooldown() {
        viewModelScope.launch {
            while (_uiState.value.resendCooldown > 0) {
                delay(1000L)
                _uiState.update { it.copy(resendCooldown = it.resendCooldown - 1) }
            }
        }
    }

    private fun friendlyError(e: Throwable): String {
        val msg = e.message.orEmpty()
        return if (msg.contains("rate limit", ignoreCase = true)) {
            "Too many sign-in attempts. Please wait a few minutes, then retry."
        } else {
            msg.ifBlank { "Failed to send link" }
        }
    }

    private companion object {
        const val COOLDOWN_SECONDS = 30
    }
}
