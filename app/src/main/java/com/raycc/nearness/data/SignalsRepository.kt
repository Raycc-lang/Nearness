package com.raycc.nearness.data

import com.raycc.nearness.domain.Signal
import com.raycc.nearness.domain.SignalType
import com.raycc.nearness.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes

class SignalsRepository {

    suspend fun sendSignal(
        senderId: String,
        receiverId: String,
        type: SignalType,
        customText: String?,
    ): Result<Unit> = runCatching {
        supabase.from("signals").insert(
            SignalInsert(
                senderId = senderId,
                receiverId = receiverId,
                type = type.name.lowercase(),
                customText = customText,
            ),
        )
    }

    /** Most recent signal received in the last 20 minutes, else null (ephemeral). */
    suspend fun latestRecent(userId: String): Result<Signal?> = runCatching {
        val since = Clock.System.now().minus(20.minutes)
        supabase.from("signals")
            .select {
                filter {
                    eq("receiver_id", userId)
                    gte("sent_at", since)
                }
                order("sent_at", Order.DESCENDING)
                limit(1)
            }
            .decodeList<SignalDto>()
            .firstOrNull()
            ?.toDomain()
    }
}
