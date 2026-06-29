package com.yourname.nearness.data

import com.yourname.nearness.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow

/** Magic-link authentication. */
class AuthRepository {

    val sessionStatus: Flow<SessionStatus> = supabase.auth.sessionStatus

    val currentUserId: String?
        get() = supabase.auth.currentUserOrNull()?.id

    /** Sends a magic-link email. Redirect handled by the nearness:// deep link. */
    suspend fun sendMagicLink(email: String): Result<Unit> = runCatching {
        supabase.auth.signInWith(OTP) {
            this.email = email.trim()
        }
    }

    suspend fun signOut(): Result<Unit> = runCatching { supabase.auth.signOut() }
}
