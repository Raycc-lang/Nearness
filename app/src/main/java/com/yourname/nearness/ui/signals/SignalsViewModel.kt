package com.yourname.nearness.ui.signals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.nearness.data.SignalsRepository
import com.yourname.nearness.domain.SignalType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignalsUiState(
    val customText: String = "",
    val isSending: Boolean = false,
    val sentLabel: String? = null,
    val error: String? = null,
)

class SignalsViewModel(
    private val userId: String,
    private val partnerId: String,
    private val repo: SignalsRepository = SignalsRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignalsUiState())
    val uiState: StateFlow<SignalsUiState> = _uiState.asStateFlow()

    fun onCustomChange(value: String) = _uiState.update { it.copy(customText = value.take(30)) }

    fun sendPreset(type: SignalType) = send(type, null)

    fun sendCustom() {
        val text = _uiState.value.customText.trim()
        if (text.isEmpty()) return
        send(SignalType.CUSTOM, text)
    }

    private fun send(type: SignalType, custom: String?) {
        _uiState.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            repo.sendSignal(userId, partnerId, type, custom)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            customText = "",
                            sentLabel = custom ?: type.label,
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isSending = false, error = e.message) } }
        }
    }

    fun clearSent() = _uiState.update { it.copy(sentLabel = null) }
}
