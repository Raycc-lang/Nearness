package com.raycc.nearness.data

import com.raycc.nearness.domain.Partnership
import com.raycc.nearness.domain.Profile
import com.raycc.nearness.supabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds


@Serializable
private data class FcmTokenPatch(@SerialName("fcm_token") val fcmToken: String)

@Serializable
private data class ProfilePatch(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_path") val avatarPath: String? = null,
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

    /** The caller's completed partnership, or null if unpaired / only pending. */
    suspend fun getMyPartnership(): Result<Partnership?> = runCatching {
        val id = currentUserId ?: return@runCatching null
        supabase.from("partnerships")
            .select {
                filter {
                    or {
                        eq("user_a_id", id)
                        eq("user_b_id", id)
                    }
                    filterNot(
                        "user_b_id",
                        io.github.jan.supabase.postgrest.query.filter.FilterOperator.IS,
                        null,
                    )
                }
                limit(1)
            }
            .decodeList<PartnershipDto>()
            .firstOrNull()
            ?.toDomain()
    }

    /** Upsert the FCM token on launch (tokens rotate). */
    suspend fun updateFcmToken(token: String): Result<Unit> = runCatching {
        val id = currentUserId ?: error("Not signed in")
        supabase.from("profiles").update(FcmTokenPatch(token)) { filter { eq("id", id) } }
    }

    /** Updates display name and/or avatar path on the caller's profile. */
    suspend fun updateProfile(displayName: String? = null, avatarPath: String? = null): Result<Unit> =
        runCatching {
            val id = currentUserId ?: error("Not signed in")
            supabase.from("profiles").update(ProfilePatch(displayName, avatarPath)) {
                filter { eq("id", id) }
            }
        }

    /**
     * Uploads an avatar to the private `avatars` bucket at `{uid}.jpg` (upsert,
     * so re-upload replaces) and patches the profile row. Returns the storage path.
     */
    suspend fun uploadAvatar(bytes: ByteArray): Result<String> = runCatching {
        val id = currentUserId ?: error("Not signed in")
        val path = "$id.jpg"
        supabase.storage.from("avatars").upload(path, bytes, upsert = true)
        supabase.from("profiles").update(ProfilePatch(avatarPath = path)) {
            filter { eq("id", id) }
        }
        path
    }

    /** Generates a pending partnership via server RPC; returns the 6-char code. */
    suspend fun generatePairingCode(): Result<String> = runCatching {
        supabase.postgrest.rpc("create_pending_partnership").decodeAs<String>()
    }

    /** Redeems a partner's code, completing the partnership (server-side RPC). */
    suspend fun redeemPairingCode(code: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc("redeem_pairing_code", RedeemArgs(code.trim().uppercase()))
    }

    /** Generates a signed URL for loading private avatar image. */
    suspend fun getAvatarUrl(path: String): Result<String> = runCatching {
        supabase.storage.from("avatars").createSignedUrl(path, 3600.seconds)
    }

}
