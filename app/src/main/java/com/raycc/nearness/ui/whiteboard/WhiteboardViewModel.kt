package com.raycc.nearness.ui.whiteboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycc.nearness.data.ProfileRepository
import com.raycc.nearness.data.WhiteboardRepository
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
    private val repo: WhiteboardRepository = WhiteboardRepository(),
    private val profilesRepo: ProfileRepository = ProfileRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(WhiteboardUiState())
    val uiState: StateFlow<WhiteboardUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null
    private val refreshMutex = Mutex()

    init {
        loadProfiles()
        loadFeed()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            val mine = profilesRepo.getMyProfile().getOrNull()
            val partner = profilesRepo.getProfile(partnerId).getOrNull()
            _uiState.update {
                it.copy(
                    myName = mine?.displayName ?: "You",
                    partnerName = partner?.displayName ?: "Partner",
                )
            }
        }
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
            repo.setArchived(itemId, archived = true).onFailure { e ->
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
            repo.getActiveFeed(partnershipId)
                .onSuccess { items ->
                    _uiState.update { it.copy(items = items) }
                    fetchSignedUrls(items)
                    _uiState.update { it.copy(isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    private suspend fun fetchSignedUrls(items: List<WhiteboardItem>) {
        val paths = items.mapNotNull { it.storagePath }.distinct()
        val currentUrls = _uiState.value.signedUrls.toMutableMap()
        val missingPaths = paths.filter { it !in currentUrls }
        if (missingPaths.isEmpty()) return

        coroutineScope {
            missingPaths.map { path ->
                launch {
                    repo.signedUrl(path).onSuccess { url ->
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
