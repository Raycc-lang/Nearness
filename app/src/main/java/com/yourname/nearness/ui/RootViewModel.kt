package com.yourname.nearness.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.yourname.nearness.data.AuthRepository
import com.yourname.nearness.data.ProfileRepository
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppState {
    data object Loading : AppState
    data object NeedsAuth : AppState
    data object NeedsPairing : AppState
    data class Ready(val userId: String, val partnerId: String) : AppState
}

/** Decides the top-level destination from session + pairing state. */
class RootViewModel(
    private val auth: AuthRepository = AuthRepository(),
    private val profiles: ProfileRepository = ProfileRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> resolvePairing()
                    is SessionStatus.NotAuthenticated -> _state.value = AppState.NeedsAuth
                    else -> _state.value = AppState.Loading
                }
            }
        }
    }

    private suspend fun resolvePairing() {
        val profile = profiles.getMyProfile().getOrNull()
        val userId = auth.currentUserId
        val partnerId = profile?.partnerId
        _state.value = if (userId != null && partnerId != null) {
            registerFcmToken()
            AppState.Ready(userId = userId, partnerId = partnerId)
        } else {
            AppState.NeedsPairing
        }
    }

    /** Upsert the FCM token on launch (tokens rotate). */
    private fun registerFcmToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            viewModelScope.launch { profiles.updateFcmToken(token) }
        }
    }

    /** Called after a successful pairing to advance the app state. */
    fun onPaired() {
        viewModelScope.launch { resolvePairing() }
    }
}
