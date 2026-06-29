package com.yourname.nearness.domain

import kotlinx.datetime.Instant

/**
 * Pure-Kotlin domain models. No Android, no Supabase types.
 * Repositories map raw Supabase responses to these before returning.
 */

enum class ActivityType(val emoji: String, val label: String) {
    RESTING("🌙", "Resting"),
    WORKING("💼", "Working"),
    FREE("🌿", "Free"),
    OUT("🚶", "Out");

    companion object {
        /** Maps the snake_case DB value to the enum. */
        fun fromWire(value: String): ActivityType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FREE

        fun ActivityType.toWire(): String = name.lowercase()
    }
}

enum class WhiteboardItemType { TEXT, PHOTO, VOICE }

enum class SignalType(val emoji: String, val label: String) {
    THINKING_OF_YOU("🤍", "Thinking of you"),
    GOOD_MORNING("☀️", "Good morning"),
    GOOD_NIGHT("🌙", "Good night"),
    MISS_YOU("👀", "Miss you"),
    CUSTOM("", "Custom");
}

data class Profile(
    val id: String,
    val displayName: String?,
    val partnerId: String?,
    val pairingCode: String?,
)

data class StatusData(
    val userId: String,
    val activity: ActivityType,
    val note: String?,
    val updatedAt: Instant,
)

data class ScheduleBlock(
    val id: String,
    val userId: String,
    val label: String,
    val startsAt: Instant,
    val endsAt: Instant,
)

data class WhiteboardItem(
    val id: String,
    val pairKey: String,
    val authorId: String,
    val type: WhiteboardItemType,
    val content: String?,
    val caption: String?,
    val archivedAt: Instant?,
    val createdAt: Instant,
)

data class Signal(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val type: SignalType,
    val customText: String?,
    val sentAt: Instant,
)

/**
 * Stable key identifying a pair, independent of who computes it.
 * Sort both UUIDs and join with "_".
 */
fun pairKey(userId: String, partnerId: String): String =
    listOf(userId, partnerId).sorted().joinToString("_")
