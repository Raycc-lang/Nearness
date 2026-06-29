package com.yourname.nearness.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours

/**
 * Coarse presence formatting. Per SPEC §4.1 we NEVER show exact timestamps
 * or "active now". Only these buckets are allowed.
 */
object Presence {
    fun coarse(
        updatedAt: Instant,
        now: Instant,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): String {
        val age = now - updatedAt
        if (age < 1.hours) return "Updated just now"

        val updatedLocal = updatedAt.toLocalDateTime(zone)
        val nowLocal = now.toLocalDateTime(zone)

        return when {
            updatedLocal.date == nowLocal.date -> when (updatedLocal.hour) {
                in 0..11 -> "Updated this morning"
                in 12..16 -> "Updated this afternoon"
                else -> "Updated this evening"
            }
            updatedLocal.date == nowLocal.date.let { it.minusDays() } -> "Updated yesterday"
            else -> "Last seen a few days ago"
        }
    }

    private fun kotlinx.datetime.LocalDate.minusDays(): kotlinx.datetime.LocalDate =
        kotlinx.datetime.LocalDate.fromEpochDays(this.toEpochDays() - 1)
}
