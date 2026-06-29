package com.yourname.nearness.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.nearness.data.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PairingUiState(
    val myCode: String? = null,
    val isGenerating: Boolean = false,
    val enteredCode: String = "",
    val isRedeeming: Boolean = false,
    val paired: Boolean = false,
    val error: String? = null,
)

class PairingViewModel(
    private val profiles: ProfileRepository = ProfileRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    fun generateCode() {
        _uiState.update { it.copy(isGenerating = true, error = null) }
        viewModelScope.launch {
            profiles.generatePairingCode()
                .onSuccess { code -> _uiState.update { it.copy(isGenerating = false, myCode = code) } }
                .onFailure { e -> _uiState.update { it.copy(isGenerating = false, error = e.message) } }
        }
    }

    fun onEnteredCodeChange(value: String) =
        _uiState.update { it.copy(enteredCode = value.uppercase(), error = null) }

    fun redeemCode() {
        val code = _uiState.value.enteredCode.trim()
        if (code.length != 6) {
            _uiState.update { it.copy(error = "Codes are 6 characters") }
            return
        }
        _uiState.update { it.copy(isRedeeming = true, error = null) }
        viewModelScope.launch {
            profiles.redeemPairingCode(code)
                .onSuccess { _uiState.update { it.copy(isRedeeming = false, paired = true) } }
                .onFailure { e -> _uiState.update { it.copy(isRedeeming = false, error = e.message ?: "Invalid code") } }
        }
    }
}
