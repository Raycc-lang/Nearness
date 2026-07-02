package com.raycc.nearness.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.raycc.nearness.domain.ActivityType
import com.raycc.nearness.domain.ScheduleBlock
import com.raycc.nearness.domain.StatusData
import com.raycc.nearness.domain.WhiteboardItem
import com.raycc.nearness.domain.WhiteboardItemType
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local, on-disk cache backed by a single Preferences DataStore. Its only
 * purpose is faster cold starts: the last-known Today state is written after
 * each successful refresh and read back on launch so the UI can render
 * immediately, then refresh in the background.
 *
 * This is the only place that touches DataStore (per AGENTS.md, persistence
 * lives in the data/ layer). ViewModels call it; Composables never do.
 *
 * Deliberately NOT cached:
 *  - active signals (ephemeral 20-min window; a stale banner would be wrong)
 *  - schedule blocks from a previous day (dropped on read via [TodaySnapshot.cachedDate])
 *  - signed URLs past their expiry (re-signed on refresh)
 */
private val Context.cacheDataStore by preferencesDataStore(name = "nearness_cache")

/** A cacheable status. Presence strings are derived from [updatedAt] at render
 *  time, so a cached timestamp ages into "yesterday"/"a few days ago" correctly. */
@Serializable
data class CachedStatus(
    val userId: String,
    val activity: String,
    val note: String?,
    val updatedAt: Instant,
)

@Serializable
data class CachedScheduleBlock(
    val id: String,
    val userId: String,
    val label: String,
    val startsAt: Instant,
    val endsAt: Instant,
)

/** The subset of Today state that is safe to cache. */
@Serializable
data class TodaySnapshot(
    val ownerUserId: String,
    /** ISO date the schedule blocks refer to; schedule is dropped if this != today. */
    val cachedDate: String,
    val yourName: String,
    val yourAvatarPath: String?,
    val yourAvatarUrl: String?,
    val yourAvatarUrlExpiresAt: Instant?,
    val yourStatus: CachedStatus?,
    val yourSchedule: List<CachedScheduleBlock>,
    val partnerId: String?,
    val partnerName: String,
    val partnerAvatarPath: String?,
    val partnerAvatarUrl: String?,
    val partnerAvatarUrlExpiresAt: Instant?,
    val partnerStatus: CachedStatus?,
    val partnerSchedule: List<CachedScheduleBlock>,
    val whiteboardPreview: String,
)

/** Cached pairing resolution, so RootViewModel can go Ready without a round-trip. */
@Serializable
data class PairingSnapshot(
    val userId: String,
    val partnerId: String?,
    val partnershipId: String?,
)

/** A cacheable whiteboard item. Signed media URLs are NOT cached (they expire);
 *  only [storagePath] is kept and re-signed on refresh. */
@Serializable
data class CachedWhiteboardItem(
    val id: String,
    val partnershipId: String,
    val authorId: String,
    val parentId: String?,
    val type: String,
    val textBody: String?,
    val storagePath: String?,
    val caption: String?,
    val archivedAt: Instant?,
    val createdAt: Instant,
)

/** Cached whiteboard feed or archive for a partnership, plus display names. */
@Serializable
data class WhiteboardSnapshot(
    val partnershipId: String,
    val myName: String,
    val partnerName: String,
    val items: List<CachedWhiteboardItem>,
)

fun StatusData.toCached() = CachedStatus(userId, activity.name, note, updatedAt)

fun CachedStatus.toDomain() = StatusData(
    userId = userId,
    activity = ActivityType.entries.firstOrNull { it.name == activity } ?: ActivityType.FREE,
    note = note,
    updatedAt = updatedAt,
)

fun ScheduleBlock.toCached() = CachedScheduleBlock(id, userId, label, startsAt, endsAt)

fun CachedScheduleBlock.toDomain() = ScheduleBlock(id, userId, label, startsAt, endsAt)

fun WhiteboardItem.toCached() = CachedWhiteboardItem(
    id = id,
    partnershipId = partnershipId,
    authorId = authorId,
    parentId = parentId,
    type = type.name,
    textBody = textBody,
    storagePath = storagePath,
    caption = caption,
    archivedAt = archivedAt,
    createdAt = createdAt,
)

fun CachedWhiteboardItem.toDomain() = WhiteboardItem(
    id = id,
    partnershipId = partnershipId,
    authorId = authorId,
    parentId = parentId,
    type = WhiteboardItemType.entries.firstOrNull { it.name == type } ?: WhiteboardItemType.TEXT,
    textBody = textBody,
    storagePath = storagePath,
    caption = caption,
    archivedAt = archivedAt,
    createdAt = createdAt,
)

class LocalCacheRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun readToday(userId: String): TodaySnapshot? {
        val raw = appContext.cacheDataStore.data.first()[KEY_TODAY] ?: return null
        return runCatching { json.decodeFromString<TodaySnapshot>(raw) }
            .getOrNull()
            ?.takeIf { it.ownerUserId == userId }
    }

    suspend fun writeToday(snapshot: TodaySnapshot) {
        appContext.cacheDataStore.edit { it[KEY_TODAY] = json.encodeToString(snapshot) }
    }

    suspend fun readPairing(userId: String): PairingSnapshot? {
        val raw = appContext.cacheDataStore.data.first()[KEY_PAIRING] ?: return null
        return runCatching { json.decodeFromString<PairingSnapshot>(raw) }
            .getOrNull()
            ?.takeIf { it.userId == userId }
    }

    suspend fun writePairing(snapshot: PairingSnapshot) {
        appContext.cacheDataStore.edit { it[KEY_PAIRING] = json.encodeToString(snapshot) }
    }

    suspend fun readWhiteboard(partnershipId: String): WhiteboardSnapshot? =
        readWhiteboardSnapshot(KEY_WHITEBOARD, partnershipId)

    suspend fun writeWhiteboard(snapshot: WhiteboardSnapshot) {
        appContext.cacheDataStore.edit { it[KEY_WHITEBOARD] = json.encodeToString(snapshot) }
    }

    suspend fun readArchive(partnershipId: String): WhiteboardSnapshot? =
        readWhiteboardSnapshot(KEY_ARCHIVE, partnershipId)

    suspend fun writeArchive(snapshot: WhiteboardSnapshot) {
        appContext.cacheDataStore.edit { it[KEY_ARCHIVE] = json.encodeToString(snapshot) }
    }

    private suspend fun readWhiteboardSnapshot(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        partnershipId: String,
    ): WhiteboardSnapshot? {
        val raw = appContext.cacheDataStore.data.first()[key] ?: return null
        return runCatching { json.decodeFromString<WhiteboardSnapshot>(raw) }
            .getOrNull()
            ?.takeIf { it.partnershipId == partnershipId }
    }

    /** Clears all cached state (e.g. on sign-out). */
    suspend fun clear() {
        appContext.cacheDataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_TODAY = stringPreferencesKey("today_snapshot")
        val KEY_PAIRING = stringPreferencesKey("pairing_snapshot")
        val KEY_WHITEBOARD = stringPreferencesKey("whiteboard_snapshot")
        val KEY_ARCHIVE = stringPreferencesKey("archive_snapshot")
    }
}
