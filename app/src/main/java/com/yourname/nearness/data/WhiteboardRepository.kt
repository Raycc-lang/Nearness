package com.yourname.nearness.data

import com.yourname.nearness.domain.WhiteboardItem
import com.yourname.nearness.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val BUCKET = "whiteboard-media"

@Serializable
private data class WhiteboardInsert(
    @SerialName("partner_pair_key") val pairKey: String,
    @SerialName("author_id") val authorId: String,
    val type: String,
    val content: String?,
    val caption: String?,
)

@Serializable
private data class ArchivePatch(@SerialName("archived_at") val archivedAt: String?)

/** Only layer that calls Supabase / Storage for whiteboard data. */
class WhiteboardRepository {

    /** Active (non-archived) feed for a pair, newest first. */
    suspend fun getActiveFeed(pairKey: String): Result<List<WhiteboardItem>> = runCatching {
        supabase.from("whiteboard_items")
            .select {
                filter {
                    eq("partner_pair_key", pairKey)
                    exact("archived_at", null)
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<WhiteboardItemDto>()
            .map { it.toDomain() }
    }

    /** All archived items for a pair, newest first (grouped by month in UI). */
    suspend fun getArchive(pairKey: String): Result<List<WhiteboardItem>> = runCatching {
        supabase.from("whiteboard_items")
            .select {
                filter {
                    eq("partner_pair_key", pairKey)
                    isNot("archived_at", null)
                }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<WhiteboardItemDto>()
            .map { it.toDomain() }
    }

    suspend fun postText(pairKey: String, authorId: String, text: String): Result<Unit> =
        runCatching {
            supabase.from("whiteboard_items").insert(
                WhiteboardInsert(pairKey, authorId, "text", text, null),
            )
        }

    /**
     * Uploads media bytes to the private bucket, then creates the feed row.
     * Path convention (AGENTS.md): {pair_key}/{item_id}/{file}.
     * We pre-generate the item id so the path is unique and deletable.
     */
    suspend fun postMedia(
        pairKey: String,
        authorId: String,
        type: String, // "photo" or "voice"
        itemId: String,
        fileName: String,
        bytes: ByteArray,
        caption: String?,
    ): Result<Unit> = runCatching {
        val path = "$pairKey/$itemId/$fileName"
        supabase.storage.from(BUCKET).upload(path, bytes) { upsert = false }
        supabase.from("whiteboard_items").insert(
            WhiteboardInsert(pairKey, authorId, type, path, caption),
        )
    }

    suspend fun signedUrl(path: String): Result<String> = runCatching {
        supabase.storage.from(BUCKET).createSignedUrl(path, 60 * 60)
    }

    suspend fun setArchived(id: String, archived: Boolean): Result<Unit> = runCatching {
        val patch = ArchivePatch(if (archived) Clock.System.now().toString() else null)
        supabase.from("whiteboard_items").update(patch) { filter { eq("id", id) } }
    }
}
