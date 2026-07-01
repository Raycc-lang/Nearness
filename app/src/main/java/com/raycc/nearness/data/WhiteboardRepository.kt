package com.raycc.nearness.data

import com.raycc.nearness.domain.WhiteboardItem
import com.raycc.nearness.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val BUCKET = "whiteboard-media"

@Serializable
private data class WhiteboardInsert(
    @SerialName("partnership_id") val partnershipId: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("parent_id") val parentId: String? = null,
    val type: String,
    @SerialName("text_body") val textBody: String? = null,
    @SerialName("storage_path") val storagePath: String? = null,
    val caption: String? = null,
)

@Serializable
private data class ArchivePatch(@SerialName("archived_at") val archivedAt: String?)

/** Only layer that calls Supabase / Storage for whiteboard data. */
class WhiteboardRepository {

    /** Active (non-archived) feed for a partnership, newest first. */
    suspend fun getActiveFeed(partnershipId: String): Result<List<WhiteboardItem>> = runCatching {
        supabase.from("whiteboard_items")
            .select {
                filter {
                    eq("partnership_id", partnershipId)
                    exact("archived_at", null)
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<WhiteboardItemDto>()
            .map { it.toDomain() }
    }

    /** All archived items for a partnership, newest first. */
    suspend fun getArchive(partnershipId: String): Result<List<WhiteboardItem>> = runCatching {
        supabase.from("whiteboard_items")
            .select {
                filter {
                    eq("partnership_id", partnershipId)
                    filterNot(
                        "archived_at",
                        io.github.jan.supabase.postgrest.query.filter.FilterOperator.IS,
                        null,
                    )
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<WhiteboardItemDto>()
            .map { it.toDomain() }
    }

    /** Posts a text item. [parentId] non-null makes it a reply to that top-level post. */
    suspend fun postText(
        partnershipId: String,
        authorId: String,
        text: String,
        parentId: String? = null,
    ): Result<Unit> = runCatching {
        supabase.from("whiteboard_items").insert(
            WhiteboardInsert(
                partnershipId = partnershipId,
                authorId = authorId,
                parentId = parentId,
                type = "text",
                textBody = text,
            ),
        )
    }

    /**
     * Uploads media bytes to the private bucket, then creates the feed row.
     * Path convention: {partnership_id}/{item_id}/{file}. Pre-generate the item
     * id so the path is unique and deletable. [parentId] non-null makes it a reply.
     */
    suspend fun postMedia(
        partnershipId: String,
        authorId: String,
        type: String, // "photo" or "voice"
        itemId: String,
        fileName: String,
        bytes: ByteArray,
        caption: String?,
        parentId: String? = null,
    ): Result<Unit> = runCatching {
        val path = "$partnershipId/$itemId/$fileName"
        supabase.storage.from(BUCKET).upload(path, bytes, upsert = false)
        supabase.from("whiteboard_items").insert(
            WhiteboardInsert(
                partnershipId = partnershipId,
                authorId = authorId,
                parentId = parentId,
                type = type,
                storagePath = path,
                caption = caption,
            ),
        )
    }

    suspend fun signedUrl(path: String): Result<String> = runCatching {
        supabase.storage.from(BUCKET).createSignedUrl(path, 3600.seconds)
    }

    suspend fun setArchived(id: String, archived: Boolean): Result<Unit> = runCatching {
        val patch = ArchivePatch(if (archived) Clock.System.now().toString() else null)
        supabase.from("whiteboard_items").update(patch) { filter { eq("id", id) } }
    }
}
