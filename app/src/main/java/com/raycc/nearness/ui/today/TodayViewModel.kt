package com.raycc.nearness.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycc.nearness.data.LocalCacheRepository
import com.raycc.nearness.data.ProfileRepository
import com.raycc.nearness.data.ScheduleRepository
import com.raycc.nearness.data.SignalsRepository
import com.raycc.nearness.data.StatusRepository
import com.raycc.nearness.data.TodaySnapshot
import com.raycc.nearness.data.WhiteboardRepository
import com.raycc.nearness.data.toCached
import com.raycc.nearness.data.toDomain
import com.raycc.nearness.domain.ActivityType
import com.raycc.nearness.domain.ScheduleBlock
import com.raycc.nearness.domain.Signal
import com.raycc.nearness.domain.StatusData
import com.raycc.nearness.domain.WhiteboardItemType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class TodayUiState(
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isPaired: Boolean = false,
    val partnerId: String? = null,
    val partnerName: String = "Partner",
    val partnerAvatarUrl: String? = null,
    val partnerStatus: StatusData? = null,
    val partnerSchedule: List<ScheduleBlock> = emptyList(),
    val yourName: String = "User",
    val yourAvatarUrl: String? = null,
    val yourStatus: StatusData? = null,
    val yourSchedule: List<ScheduleBlock> = emptyList(),
    val activeSignal: Signal? = null,
    val whiteboardPreview: String = "",
    val errorMessage: String? = null,
)

class TodayViewModel(
    private val userId: String,
    private val initialPartnerId: String?,
    private val initialPartnershipId: String?,
    private val cache: LocalCacheRepository,
    private val statusRepo: StatusRepository = StatusRepository(),
    private val scheduleRepo: ScheduleRepository = ScheduleRepository(),
    private val signalsRepo: SignalsRepository = SignalsRepository(),
    private val profilesRepo: ProfileRepository = ProfileRepository(),
    private val whiteboardRepo: WhiteboardRepository = WhiteboardRepository(),
) : ViewModel() {

    private var partnerId = initialPartnerId
    private var partnershipId = initialPartnershipId

    private val _uiState = MutableStateFlow(TodayUiState(partnerId = initialPartnerId))
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null
    private val refreshMutex = Mutex()

    private var cachedYourAvatarPath: String? = null
    private var cachedYourAvatarUrl: String? = null
    private var cachedPartnerAvatarPath: String? = null
    private var cachedPartnerAvatarUrl: String? = null

    init {
        viewModelScope.launch {
            // Render last-known state instantly (if any), then refresh in background.
            cache.readToday(userId)?.let { snapshot ->
                _uiState.update { applyCachedSnapshot(it, snapshot) }
            }
            refresh()
        }
    }

    /**
     * Seeds UI state from a cached snapshot for a fast cold start. Sets
     * [TodayUiState.isInitialLoading] to false so the screen renders immediately;
     * the subsequent refresh flips [TodayUiState.isRefreshing] instead.
     *
     * Intentionally excludes the active signal (ephemeral) and drops schedule
     * blocks cached for a previous day.
     */
    private fun applyCachedSnapshot(current: TodayUiState, snap: TodaySnapshot): TodayUiState {
        val now = Clock.System.now()
        val zone = TimeZone.currentSystemDefault()
        val todayStr = now.toLocalDateTime(zone).date.toString()
        val sameDay = snap.cachedDate == todayStr

        // Reuse cached signed URLs only while still valid, and seed the in-memory
        // avatar caches so the upcoming refresh can skip re-signing them.
        val yourUrlValid = snap.yourAvatarUrl != null &&
            (snap.yourAvatarUrlExpiresAt?.let { it > now } == true)
        if (yourUrlValid) {
            cachedYourAvatarPath = snap.yourAvatarPath
            cachedYourAvatarUrl = snap.yourAvatarUrl
        }
        val partnerUrlValid = snap.partnerAvatarUrl != null &&
            (snap.partnerAvatarUrlExpiresAt?.let { it > now } == true)
        if (partnerUrlValid) {
            cachedPartnerAvatarPath = snap.partnerAvatarPath
            cachedPartnerAvatarUrl = snap.partnerAvatarUrl
        }

        return current.copy(
            isInitialLoading = false,
            isPaired = snap.partnerId != null,
            partnerId = snap.partnerId,
            partnerName = snap.partnerName,
            partnerAvatarUrl = if (partnerUrlValid) snap.partnerAvatarUrl else null,
            partnerStatus = snap.partnerStatus?.toDomain(),
            partnerSchedule = if (sameDay) snap.partnerSchedule.map { it.toDomain() } else emptyList(),
            yourName = snap.yourName,
            yourAvatarUrl = if (yourUrlValid) snap.yourAvatarUrl else null,
            yourStatus = snap.yourStatus?.toDomain(),
            yourSchedule = if (sameDay) snap.yourSchedule.map { it.toDomain() } else emptyList(),
            whiteboardPreview = snap.whiteboardPreview,
        )
    }

    /** Persists the cacheable subset of the current UI state for the next cold start. */
    private suspend fun persistSnapshot() {
        val s = _uiState.value
        val now = Clock.System.now()
        val zone = TimeZone.currentSystemDefault()
        val todayStr = now.toLocalDateTime(zone).date.toString()
        val urlExpiry = now + AVATAR_URL_CACHE_TTL
        cache.writeToday(
            TodaySnapshot(
                ownerUserId = userId,
                cachedDate = todayStr,
                yourName = s.yourName,
                yourAvatarPath = cachedYourAvatarPath,
                yourAvatarUrl = s.yourAvatarUrl,
                yourAvatarUrlExpiresAt = s.yourAvatarUrl?.let { urlExpiry },
                yourStatus = s.yourStatus?.toCached(),
                yourSchedule = s.yourSchedule.map { it.toCached() },
                partnerId = s.partnerId,
                partnerName = s.partnerName,
                partnerAvatarPath = cachedPartnerAvatarPath,
                partnerAvatarUrl = s.partnerAvatarUrl,
                partnerAvatarUrlExpiresAt = s.partnerAvatarUrl?.let { urlExpiry },
                partnerStatus = s.partnerStatus?.toCached(),
                partnerSchedule = s.partnerSchedule.map { it.toCached() },
                whiteboardPreview = s.whiteboardPreview,
            )
        )
    }

    fun refresh() {
        viewModelScope.launch {
            refreshMutex.withLock {
                refreshInternal()
            }
        }
    }

    private suspend fun refreshInternal() {
        if (_uiState.value.isInitialLoading) {
            // Keep isInitialLoading true
        } else {
            _uiState.update { it.copy(isRefreshing = true) }
        }

        // If partnerId was null, check if they paired now
        if (partnerId == null) {
            profilesRepo.getMyPartnership().onSuccess { p ->
                if (p != null) {
                    partnerId = p.partnerOf(userId)
                    partnershipId = p.id
                }
            }
        }

        val zone = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(zone).date
        val dayStart = today.atStartOfDayIn(zone)
        val dayEnd = dayStart + 1.days

        // Load user data
        val myProfile = profilesRepo.getMyProfile().getOrNull()
        val yourName = myProfile?.displayName ?: "User"
        val yourAvatarUrl = myProfile?.avatarPath?.let { path ->
            if (path == cachedYourAvatarPath && cachedYourAvatarUrl != null) {
                cachedYourAvatarUrl
            } else {
                val url = profilesRepo.getAvatarUrl(path).getOrNull()
                cachedYourAvatarPath = path
                cachedYourAvatarUrl = url
                url
            }
        }
        val yourStatus = statusRepo.getStatus(userId).getOrNull()
        val yourSchedule = scheduleRepo.getBlocksBetween(userId, dayStart, dayEnd).getOrDefault(emptyList())

        var partnerName = "Partner"
        var partnerAvatarUrl: String? = null
        var partnerStatus: StatusData? = null
        var partnerSchedule = emptyList<ScheduleBlock>()
        var activeSignal: Signal? = null
        var whiteboardPreview = ""

        val currentPartnerId = partnerId
        val currentPartnershipId = partnershipId

        if (currentPartnerId != null && currentPartnershipId != null) {
            val partnerProfile = profilesRepo.getProfile(currentPartnerId).getOrNull()
            partnerName = partnerProfile?.displayName ?: "Partner"
            partnerAvatarUrl = partnerProfile?.avatarPath?.let { path ->
                if (path == cachedPartnerAvatarPath && cachedPartnerAvatarUrl != null) {
                    cachedPartnerAvatarUrl
                } else {
                    val url = profilesRepo.getAvatarUrl(path).getOrNull()
                    cachedPartnerAvatarPath = path
                    cachedPartnerAvatarUrl = url
                    url
                }
            }
            partnerStatus = statusRepo.getStatus(currentPartnerId).getOrNull()
            partnerSchedule = scheduleRepo.getBlocksBetween(currentPartnerId, dayStart, dayEnd).getOrDefault(emptyList())

            // Most recent signal in the last 20 minutes (repo enforces the window). SPEC §4.3.
            activeSignal = signalsRepo.latestRecent(userId).getOrNull()

            // Whiteboard preview
            whiteboardRepo.getActiveFeed(currentPartnershipId).onSuccess { feed ->
                val latest = feed.firstOrNull()
                whiteboardPreview = when {
                    latest == null -> ""
                    latest.type == WhiteboardItemType.TEXT -> latest.textBody.orEmpty()
                    latest.type == WhiteboardItemType.PHOTO -> {
                        "📷 Photo" + if (latest.caption.isNullOrBlank()) "" else " — ${latest.caption}"
                    }
                    latest.type == WhiteboardItemType.VOICE -> {
                        "🎙️ Voice memo" + if (latest.caption.isNullOrBlank()) "" else " — ${latest.caption}"
                    }
                    else -> ""
                }
            }
        }

        _uiState.update {
            it.copy(
                isInitialLoading = false,
                isRefreshing = false,
                isPaired = currentPartnerId != null,
                partnerId = currentPartnerId,
                partnerName = partnerName,
                partnerAvatarUrl = partnerAvatarUrl,
                partnerStatus = partnerStatus,
                partnerSchedule = partnerSchedule,
                yourName = yourName,
                yourAvatarUrl = yourAvatarUrl,
                yourStatus = yourStatus,
                yourSchedule = yourSchedule,
                activeSignal = activeSignal,
                whiteboardPreview = whiteboardPreview,
                errorMessage = null,
            )
        }

        persistSnapshot()
    }

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL)
                pollPartner()
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun pollPartner() {
        refreshMutex.withLock {
            if (partnerId == null) {
                profilesRepo.getMyPartnership().onSuccess { p ->
                    if (p != null) {
                        partnerId = p.partnerOf(userId)
                        partnershipId = p.id
                        refreshInternal()
                    }
                }
                return
            }

            val currentPartnerId = partnerId ?: return
            val currentPartnershipId = partnershipId ?: return

            val zone = TimeZone.currentSystemDefault()
            val today = Clock.System.now().toLocalDateTime(zone).date
            val dayStart = today.atStartOfDayIn(zone)
            val dayEnd = dayStart + 1.days

            val partnerStatus = statusRepo.getStatus(currentPartnerId).getOrNull()
            val partnerSchedule = scheduleRepo.getBlocksBetween(currentPartnerId, dayStart, dayEnd).getOrDefault(emptyList())

            val activeSignal = signalsRepo.latestRecent(userId).getOrNull()

            var whiteboardPreview = _uiState.value.whiteboardPreview
            whiteboardRepo.getActiveFeed(currentPartnershipId).onSuccess { feed ->
                val latest = feed.firstOrNull()
                whiteboardPreview = when {
                    latest == null -> ""
                    latest.type == WhiteboardItemType.TEXT -> latest.textBody.orEmpty()
                    latest.type == WhiteboardItemType.PHOTO -> {
                        "📷 Photo" + if (latest.caption.isNullOrBlank()) "" else " — ${latest.caption}"
                    }
                    latest.type == WhiteboardItemType.VOICE -> {
                        "🎙️ Voice memo" + if (latest.caption.isNullOrBlank()) "" else " — ${latest.caption}"
                    }
                    else -> ""
                }
            }

            _uiState.update {
                it.copy(
                    isPaired = true,
                    partnerId = currentPartnerId,
                    partnerStatus = partnerStatus,
                    partnerSchedule = partnerSchedule,
                    activeSignal = activeSignal,
                    whiteboardPreview = whiteboardPreview,
                )
            }

            persistSnapshot()
        }
    }

    fun updateYourStatusAndNote(activity: ActivityType, note: String?) {
        val previous = _uiState.value.yourStatus
        _uiState.update {
            it.copy(yourStatus = StatusData(userId, activity, note, Clock.System.now()))
        }
        viewModelScope.launch {
            statusRepo.upsertStatus(userId, activity, note)
                .onSuccess { persistSnapshot() }
                .onFailure { e ->
                    _uiState.update { it.copy(yourStatus = previous, errorMessage = e.message) }
                }
        }
    }

    fun deleteScheduleBlock(blockId: String) {
        val previous = _uiState.value.yourSchedule
        _uiState.update {
            it.copy(yourSchedule = it.yourSchedule.filterNot { b -> b.id == blockId })
        }
        viewModelScope.launch {
            scheduleRepo.deleteBlock(blockId)
                .onSuccess { persistSnapshot() }
                .onFailure { e ->
                    _uiState.update { it.copy(yourSchedule = previous, errorMessage = e.message) }
                }
        }
    }

    fun addScheduleBlock(label: String, startsAt: Instant, endsAt: Instant) {
        viewModelScope.launch {
            scheduleRepo.addBlock(userId, label, startsAt, endsAt)
                .onSuccess { refresh() }
                .onFailure { e -> _uiState.update { it.copy(errorMessage = e.message) } }
        }
    }

    fun updateProfile(displayName: String) {
        viewModelScope.launch {
            profilesRepo.updateProfile(displayName = displayName)
                .onSuccess { refresh() }
                .onFailure { e -> _uiState.update { it.copy(errorMessage = e.message) } }
    }
    }

    fun uploadAvatar(bytes: ByteArray, onComplete: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val res = profilesRepo.uploadAvatar(bytes)
            res.onSuccess { refresh() }
            onComplete(res)
        }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    private companion object {
        val POLL_INTERVAL = 30.seconds

        /** Signed avatar URLs live ~1h; cache slightly below that so a reused URL
         *  from cache is still valid when the next cold start reads it. */
        val AVATAR_URL_CACHE_TTL = 50.minutes
    }
}
