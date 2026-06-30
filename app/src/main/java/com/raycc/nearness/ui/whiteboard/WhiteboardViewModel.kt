package com.raycc.nearness.ui.whiteboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycc.nearness.data.WhiteboardRepository
import com.raycc.nearness.domain.WhiteboardItem
import com.raycc.nearness.domain.pairKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class WhiteboardUiState(
    val isLoading: Boolean = true,
    val items: List<WhiteboardItem> = emptyList(),
    val draft: String = "",
    val isPosting: Boolean = false,
    val error: String? = null,
)

class WhiteboardViewModel(
    private val userId: String,
    private val partnerId: String,
    private val repo: WhiteboardRepository = WhiteboardRepository(),
) : ViewModel() {

    private val pairKey = pairKey(userId, partnerId)

    private val _uiState = MutableStateFlow(WhiteboardUiState())
    val uiState: StateFlow<WhiteboardUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        loadFeed()
    }

    fun loadFeed() {
        viewModelScope.launch {
            refreshFeed(showLoading = true)
        }
    }

    fun onDraftChange(value: String) = _uiState.update { it.copy(draft = value.take(500)) }

    fun postText() {
        val text = _uiState.value.draft.trim()
        if (text.isEmpty()) return
        _uiState.update { it.copy(isPosting = true, draft = "") }
        viewModelScope.launch {
            repo.postText(pairKey, userId, text)
                .onSuccess { _uiState.update { it.copy(isPosting = false) }; loadFeed() }
                .onFailure { e -> _uiState.update { it.copy(isPosting = false, error = e.message, draft = text) } }
        }
    }

    fun archive(itemId: String) {
        // Optimistic removal from the active feed.
        val previous = _uiState.value.items
        _uiState.update { it.copy(items = it.items.filterNot { i -> i.id == itemId }) }
        viewModelScope.launch {
            repo.setArchived(itemId, archived = true).onFailure { e ->
                _uiState.update { it.copy(items = previous, error = e.message) }
            }
        }
    }

    /**
     * Polling replaces Supabase Realtime by design. FCM wakes the user for
     * partner posts; this loop keeps the visible feed fresh while the tab is open.
     */
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
        if (showLoading) _uiState.update { it.copy(isLoading = true) }
        repo.getActiveFeed(pairKey)
            .onSuccess { items ->
                _uiState.update { it.copy(isLoading = false, items = items) }
            }
            .onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private companion object {
        val POLL_INTERVAL = 30.seconds
    }
}
