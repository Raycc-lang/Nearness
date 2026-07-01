package com.raycc.nearness.data

import com.raycc.nearness.domain.ActivityType
import com.raycc.nearness.domain.Partnership
import com.raycc.nearness.domain.Profile
import com.raycc.nearness.domain.ScheduleBlock
import com.raycc.nearness.domain.Signal
import com.raycc.nearness.domain.SignalType
import com.raycc.nearness.domain.StatusData
import com.raycc.nearness.domain.WhiteboardItem
import com.raycc.nearness.domain.WhiteboardItemType
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase wire types. These stay inside the data/ layer; repositories map
 * them to domain models before returning (per AGENTS.md).
 */

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("fcm_token") val fcmToken: String? = null,
) {
    fun toDomain() = Profile(id, displayName, avatarPath)
}

@Serializable
data class PartnershipDto(
    val id: String,
    @SerialName("user_a_id") val userAId: String,
    @SerialName("user_b_id") val userBId: String? = null,
    @SerialName("pairing_code") val pairingCode: String? = null,
    @SerialName("pairing_code_expires_at") val pairingCodeExpiresAt: Instant? = null,
    @SerialName("created_at") val createdAt: Instant,
) {
    fun toDomain() = Partnership(id, userAId, userBId, pairingCode, pairingCodeExpiresAt, createdAt)
}

@Serializable
data class StatusDto(
    @SerialName("user_id") val userId: String,
    val activity: String,
    val note: String? = null,
    @SerialName("updated_at") val updatedAt: Instant,
) {
    fun toDomain() = StatusData(
        userId = userId,
        activity = ActivityType.fromWire(activity),
        note = note,
        updatedAt = updatedAt,
    )
}

@Serializable
data class StatusUpsert(
    @SerialName("user_id") val userId: String,
    val activity: String,
    val note: String?,
)

@Serializable
data class ScheduleBlockDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val label: String,
    @SerialName("starts_at") val startsAt: Instant,
    @SerialName("ends_at") val endsAt: Instant,
) {
    fun toDomain() = ScheduleBlock(id, userId, label, startsAt, endsAt)
}

@Serializable
data class ScheduleBlockInsert(
    @SerialName("user_id") val userId: String,
    val label: String,
    @SerialName("starts_at") val startsAt: Instant,
    @SerialName("ends_at") val endsAt: Instant,
)

@Serializable
data class WhiteboardItemDto(
    val id: String,
    @SerialName("partnership_id") val partnershipId: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("parent_id") val parentId: String? = null,
    val type: String,
    @SerialName("text_body") val textBody: String? = null,
    @SerialName("storage_path") val storagePath: String? = null,
    val caption: String? = null,
    @SerialName("archived_at") val archivedAt: Instant? = null,
    @SerialName("created_at") val createdAt: Instant,
) {
    fun toDomain() = WhiteboardItem(
        id = id,
        partnershipId = partnershipId,
        authorId = authorId,
        parentId = parentId,
        type = WhiteboardItemType.valueOf(type.uppercase()),
        textBody = textBody,
        storagePath = storagePath,
        caption = caption,
        archivedAt = archivedAt,
        createdAt = createdAt,
    )
}

@Serializable
data class SignalDto(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("receiver_id") val receiverId: String,
    val type: String,
    @SerialName("custom_text") val customText: String? = null,
    @SerialName("sent_at") val sentAt: Instant,
) {
    fun toDomain() = Signal(
        id = id,
        senderId = senderId,
        receiverId = receiverId,
        type = SignalType.valueOf(type.uppercase()),
        customText = customText,
        sentAt = sentAt,
    )
}

@Serializable
data class SignalInsert(
    @SerialName("sender_id") val senderId: String,
    @SerialName("receiver_id") val receiverId: String,
    val type: String,
    @SerialName("custom_text") val customText: String?,
)
