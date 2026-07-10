package com.raycc.nearness.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.raycc.nearness.data.AuthRepository
import com.raycc.nearness.data.LocalCacheRepository
import com.raycc.nearness.data.PairingSnapshot
import com.raycc.nearness.data.ProfileRepository
import io.github.jan.supabase.gotrue.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppState {
    data object Loading : AppState
    data object NeedsAuth : AppState
    data class Ready(
        val userId: String,
        val partnerId: String?,
        val partnershipId: String?,
    ) : AppState
}

/** Decides the top-level destination from session + pairing state. */
class RootViewModel(
    private val cache: LocalCacheRepository,
    private val auth: AuthRepository = AuthRepository(),
    private val profiles: ProfileRepository = ProfileRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            auth.sessionStatus.collect { status ->
                // Status-only: SessionStatus.Authenticated carries live tokens.
                Log.d("Nearness", "sessionStatus=${status::class.simpleName}")
                when (status) {
                    is SessionStatus.Authenticated -> resolvePairing()
                    is SessionStatus.NotAuthenticated -> {
                        // Only a deliberate sign-out should wipe cached couple data
                        // so a different/next account never renders the previous
                        // user's snapshot. A transient expired session keeps the
                        // cache; it's re-validated on the next successful auth.
                        if (status.isSignOut) cache.clear()
                        _state.value = AppState.NeedsAuth
                    }
                    else -> _state.value = AppState.Loading
                }
            }
        }
    }

    private suspend fun resolvePairing() {
        val userId = auth.currentUserId
        if (userId == null) {
            _state.value = AppState.NeedsAuth
            return
        }

        // Optimistic: go Ready immediately from cache, skipping a round-trip.
        cache.readPairing(userId)?.let { cached ->
            _state.value = AppState.Ready(
                userId = userId,
                partnerId = cached.partnerId,
                partnershipId = cached.partnershipId,
            )
        }

        // Verify against the server, then persist + emit the fresh result.
        profiles.getMyPartnership()
            .onSuccess { partnership ->
                val partnerId = partnership?.partnerOf(userId)
                val partnershipId = partnership?.id
                if (partnerId != null) {
                    registerFcmToken()
                }
                cache.writePairing(PairingSnapshot(userId, partnerId, partnershipId))
                _state.value = AppState.Ready(
                    userId = userId,
                    partnerId = partnerId,
                    partnershipId = partnershipId,
                )
            }
            .onFailure {
                // Transient failure: keep any optimistic state; never downgrade a
                // paired user to unpaired. If we had no cache, fall back to Ready.
                if (_state.value !is AppState.Ready) {
                    _state.value = AppState.Ready(userId = userId, partnerId = null, partnershipId = null)
                }
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
