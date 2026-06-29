package com.yourname.nearness.data

import com.yourname.nearness.domain.ActivityType
import com.yourname.nearness.domain.ActivityType.Companion.toWire
import com.yourname.nearness.domain.StatusData
import com.yourname.nearness.supabase
import io.github.jan.supabase.postgrest.from

/** The only layer that calls Supabase for status. Returns domain models. */
class StatusRepository {

    suspend fun getStatus(userId: String): Result<StatusData?> = runCatching {
        supabase.from("status")
            .select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<StatusDto>()
            ?.toDomain()
    }

    suspend fun upsertStatus(userId: String, activity: ActivityType, note: String?): Result<Unit> =
        runCatching {
            supabase.from("status").upsert(
                StatusUpsert(userId = userId, activity = activity.toWire(), note = note),
                onConflict = "user_id",
            )
        }
}
