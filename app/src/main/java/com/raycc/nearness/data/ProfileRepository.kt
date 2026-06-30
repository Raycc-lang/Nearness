package com.raycc.nearness.data

import com.raycc.nearness.domain.Profile
import com.raycc.nearness.supabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

@Serializable
private data class FcmTokenPatch(@SerialName("fcm_token") val fcmToken: String)

@Serializable
private data class PairingCodePatch(
    @SerialName("pairing_code") val pairingCode: String,
    @SerialName("pairing_code_expires_at") val expiresAt: String,
)

@Serializable
private data class RedeemArgs(val code: String)

/** Profile + pairing data access. */
class ProfileRepository {

    private val currentUserId: String?
        get() = supabase.auth.currentUserOrNull()?.id

    suspend fun getMyProfile(): Result<Profile?> = runCatching {
        val id = currentUserId ?: return@runCatching null
        supabase.from("profiles")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull<ProfileDto>()
            ?.toDomain()
    }

    suspend fun getProfile(userId: String): Result<Profile?> = runCatching {
        supabase.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull<ProfileDto>()
            ?.toDomain()
    }

    /** Upsert the FCM token on launch (tokens rotate). */
    suspend fun updateFcmToken(token: String): Result<Unit> = runCatching {
        val id = currentUserId ?: error("Not signed in")
        supabase.from("profiles").update(FcmTokenPatch(token)) { filter { eq("id", id) } }
    }

    /** Generates and stores a 6-char code that expires in 7 days. */
    suspend fun generatePairingCode(): Result<String> = runCatching {
        val id = currentUserId ?: error("Not signed in")
        val code = randomCode()
        val expires = Clock.System.now().plus(7.days).toString()
        supabase.from("profiles")
            .update(PairingCodePatch(code, expires)) { filter { eq("id", id) } }
        code
    }

    /** Redeems a partner's code, linking both accounts (server-side RPC). */
    suspend fun redeemPairingCode(code: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc("redeem_pairing_code", RedeemArgs(code.trim().uppercase()))
    }

    private fun randomCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no ambiguous 0/O/1/I
        return (1..6).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }
}
