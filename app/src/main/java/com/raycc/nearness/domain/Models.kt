package com.raycc.nearness.domain

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
    HUG("🫂", "Hug"),
    KISS("💋", "Kiss"),
    GOOD_MORNING("☀️", "Good morning"),
    GOOD_NIGHT("🌙", "Good night"),
    THINKING_OF_YOU("💭", "Just thinking of you"),
    CUSTOM("", "Custom");
}

data class Profile(
    val id: String,
    val displayName: String?,
    val avatarPath: String?,
)

/** Canonical pair relationship. Pending while [userBId] is null. */
data class Partnership(
    val id: String,
    val userAId: String,
    val userBId: String?,
    val pairingCode: String?,
    val pairingCodeExpiresAt: Instant?,
    val createdAt: Instant,
) {
    /** The other member of this partnership, or null if still pending. */
    fun partnerOf(userId: String): String? = when (userId) {
        userAId -> userBId
        userBId -> userAId
        else -> null
    }
}

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
    val partnershipId: String,
    val authorId: String,
    val parentId: String?,
    val type: WhiteboardItemType,
    val textBody: String?,
    val storagePath: String?,
    val caption: String?,
    val archivedAt: Instant?,
    val createdAt: Instant,
) {
    val isReply: Boolean get() = parentId != null
}

data class Signal(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val type: SignalType,
    val customText: String?,
    val sentAt: Instant,
)
