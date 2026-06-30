package com.raycc.nearness.data

import android.content.Intent
import android.util.Log
import com.raycc.nearness.supabase
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.handleDeeplinks
import io.github.jan.supabase.gotrue.providers.builtin.OTP
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

    /**
     * Resolves an incoming magic-link deep link. Returns an error message when the
     * link carried an error fragment (e.g. expired / already-used token) or the SDK
     * threw; null when the session was imported successfully.
     */
    fun handleDeeplink(intent: Intent): String? {
        val data = intent.data ?: return null
        val fragment = data.fragment
        if (fragment != null && fragment.contains("error=")) {
            return "Magic link expired or already used. Please resend."
        }
        return try {
            supabase.handleDeeplinks(intent)
            null
        } catch (e: Exception) {
            Log.e("Nearness", "handleDeeplinks threw", e)
            e.message ?: "Sign-in failed"
        }
    }
}
