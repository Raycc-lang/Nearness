package com.raycc.nearness.ui.whiteboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycc.nearness.data.LocalCacheRepository
import com.raycc.nearness.data.ProfileRepository
import com.raycc.nearness.data.WhiteboardRepository
import com.raycc.nearness.data.WhiteboardSnapshot
import com.raycc.nearness.data.toCached
import com.raycc.nearness.data.toDomain
import com.raycc.nearness.domain.WhiteboardItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class WhiteboardUiState(
    val isLoading: Boolean = true,
    val items: List<WhiteboardItem> = emptyList(),
    val myName: String = "You",
    val partnerName: String = "Partner",
    val signedUrls: Map<String, String> = emptyMap(),
    val isPosting: Boolean = false,
    val error: String? = null,
)

class WhiteboardViewModel(
    private val userId: String,
    private val partnerId: String,
    private val partnershipId: String,
    private val cache: LocalCacheRepository,
    private val repo: WhiteboardRepository = WhiteboardRepository(),
    private val profilesRepo: ProfileRepository = ProfileRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(WhiteboardUiState())
    val uiState: StateFlow<WhiteboardUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null
    private val refreshMutex = Mutex()

    // Tracks when each signed URL expires so fetchSignedUrls knows to re-sign it,
    // rather than treating "already in signedUrls" as valid forever.
    private val signedUrlExpiry = mutableMapOf<String, Instant>()

    init {
        viewModelScope.launch {
            // Render the last-known feed instantly, then refresh in the background.
            cache.readWhiteboard(partnershipId)?.let { snap ->
                val items = snap.items.map { it.toDomain() }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        items = items,
                        myName = snap.myName,
                        partnerName = snap.partnerName,
                    )
                }
                fetchSignedUrls(items)
            }
            loadFeed()
        }
    }

    /** Fetches both display names. Called inside [refreshFeed] so names and items
     *  are persisted as one consistent snapshot (no interleaving persist). */
    private suspend fun loadProfiles() {
        val mine = profilesRepo.getMyProfile().getOrNull()
        val partner = profilesRepo.getProfile(partnerId).getOrNull()
        _uiState.update {
            it.copy(
                myName = mine?.displayName ?: "You",
                partnerName = partner?.displayName ?: "Partner",
            )
        }
    }

    private suspend fun persistSnapshot() {
        val s = _uiState.value
        cache.writeWhiteboard(
            WhiteboardSnapshot(
                partnershipId = partnershipId,
                myName = s.myName,
                partnerName = s.partnerName,
                items = s.items.map { it.toCached() },
            )
        )
    }

    fun loadFeed() {
        viewModelScope.launch {
            refreshFeed(showLoading = true)
        }
    }

    fun postText(text: String, parentId: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { it.copy(isPosting = true) }
        viewModelScope.launch {
            repo.postText(partnershipId, userId, trimmed, parentId)
                .onSuccess {
                    _uiState.update { it.copy(isPosting = false) }
                    loadFeed()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isPosting = false, error = e.message) }
                }
        }
    }

    fun postPhoto(bytes: ByteArray, caption: String?, parentId: String? = null) {
        _uiState.update { it.copy(isPosting = true) }
        viewModelScope.launch {
            val itemId = java.util.UUID.randomUUID().toString()
            repo.postMedia(
                partnershipId = partnershipId,
                authorId = userId,
                type = "photo",
                itemId = itemId,
                fileName = "photo.jpg",
                bytes = bytes,
                caption = caption,
                parentId = parentId,
            ).onSuccess {
                _uiState.update { it.copy(isPosting = false) }
                loadFeed()
            }.onFailure { e ->
                _uiState.update { it.copy(isPosting = false, error = e.message) }
            }
        }
    }

    fun postVoice(bytes: ByteArray, caption: String?, parentId: String? = null) {
        _uiState.update { it.copy(isPosting = true) }
        viewModelScope.launch {
            val itemId = java.util.UUID.randomUUID().toString()
            repo.postMedia(
                partnershipId = partnershipId,
                authorId = userId,
                type = "voice",
                itemId = itemId,
                fileName = "voice.m4a",
                bytes = bytes,
                caption = caption,
                parentId = parentId,
            ).onSuccess {
                _uiState.update { it.copy(isPosting = false) }
                loadFeed()
            }.onFailure { e ->
                _uiState.update { it.copy(isPosting = false, error = e.message) }
            }
        }
    }

    fun archive(itemId: String) {
        val previous = _uiState.value.items
        _uiState.update { it.copy(items = it.items.filterNot { i -> i.id == itemId || i.parentId == itemId }) }
        viewModelScope.launch {
            repo.setArchived(itemId, archived = true)
                .onSuccess { persistSnapshot() }
                .onFailure { e ->
                    _uiState.update { it.copy(items = previous, error = e.message) }
                }
        }
    }

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL)
                refreshFeed(showLoading = false)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun refreshFeed(showLoading: Boolean) {
        refreshMutex.withLock {
            if (showLoading) _uiState.update { it.copy(isLoading = true) }
            loadProfiles()
            repo.getActiveFeed(partnershipId)
                .onSuccess { items ->
                    _uiState.update { it.copy(items = items) }
                    fetchSignedUrls(items)
                    _uiState.update { it.copy(isLoading = false) }
                    persistSnapshot()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    private suspend fun fetchSignedUrls(items: List<WhiteboardItem>) {
        val paths = items.mapNotNull { it.storagePath }.distinct()
        val currentUrls = _uiState.value.signedUrls
        val now = Clock.System.now()
        // Re-sign anything we've never signed, plus anything whose signed URL is
        // about to expire — otherwise a URL signed >1h ago but not yet loaded by
        // Coil would 403 with no recovery.
        val pathsNeedingSign = paths.filter { path ->
            val expiry = signedUrlExpiry[path]
            path !in currentUrls || expiry == null || expiry - now <= 5.minutes
        }
        if (pathsNeedingSign.isEmpty()) return

        coroutineScope {
            pathsNeedingSign.map { path ->
                launch {
                    repo.signedUrl(path).onSuccess { url ->
                        signedUrlExpiry[path] = now + 55.minutes
                        _uiState.update { state ->
                            state.copy(signedUrls = state.signedUrls + (path to url))
                        }
                    }
                }
            }.forEach { it.join() }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private companion object {
        val POLL_INTERVAL = 600.seconds
    }
}
