package com.raycc.nearness.data

import com.raycc.nearness.domain.ScheduleBlock
import com.raycc.nearness.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Instant

class ScheduleRepository {

    /** Today's blocks for a user, ordered by start time. */
    suspend fun getBlocksBetween(
        userId: String,
        dayStart: Instant,
        dayEnd: Instant,
    ): Result<List<ScheduleBlock>> = runCatching {
        supabase.from("schedule_blocks")
            .select {
                filter {
                    eq("user_id", userId)
                    gte("starts_at", dayStart)
                    lt("starts_at", dayEnd)
                }
                order("starts_at", Order.ASCENDING)
            }
            .decodeList<ScheduleBlockDto>()
            .map { it.toDomain() }
    }

    suspend fun addBlock(
        userId: String,
        label: String,
        startsAt: Instant,
        endsAt: Instant,
    ): Result<Unit> = runCatching {
        supabase.from("schedule_blocks").insert(
            ScheduleBlockInsert(
                userId = userId,
                label = label,
                startsAt = startsAt,
                endsAt = endsAt,
            ),
        )
    }

    suspend fun deleteBlock(id: String): Result<Unit> = runCatching {
        supabase.from("schedule_blocks").delete { filter { eq("id", id) } }
    }
}
