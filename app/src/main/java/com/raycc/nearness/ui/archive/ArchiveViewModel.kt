package com.raycc.nearness.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raycc.nearness.data.ProfileRepository
import com.raycc.nearness.data.WhiteboardRepository
import com.raycc.nearness.domain.WhiteboardItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class ArchiveUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val all: List<WhiteboardItem> = emptyList(),
    val myName: String = "You",
    val partnerName: String = "Partner",
    val signedUrls: Map<String, String> = emptyMap(),
    val error: String? = null,
) {
    /** Items filtered by the local search query, grouped by "Month Year". */
    val grouped: Map<String, List<WhiteboardItem>>
        get() {
            val zone = TimeZone.currentSystemDefault()
            val filtered = if (query.isBlank()) all else all.filter {
                (it.textBody?.contains(query, ignoreCase = true) == true) ||
                    (it.caption?.contains(query, ignoreCase = true) == true)
            }
            return filtered.groupBy {
                val d = it.createdAt.toLocalDateTime(zone).date
                val monthName = d.month.name.lowercase().replaceFirstChar { char -> char.uppercase() }
                "$monthName ${d.year}"
            }
        }
}

class ArchiveViewModel(
    private val userId: String,
    private val partnerId: String,
    private val partnershipId: String,
    private val repo: WhiteboardRepository = WhiteboardRepository(),
    private val profilesRepo: ProfileRepository = ProfileRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArchiveUiState())
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    init {
        loadProfiles()
        load()
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

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repo.getArchive(partnershipId)
                .onSuccess { items ->
                    _uiState.update { it.copy(all = items) }
                    fetchSignedUrls(items)
                    _uiState.update { it.copy(isLoading = false) }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
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

    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }

    fun unarchive(itemId: String) {
        val previous = _uiState.value.all
        _uiState.update { it.copy(all = it.all.filterNot { i -> i.id == itemId }) }
        viewModelScope.launch {
            repo.setArchived(itemId, archived = false).onFailure { e ->
                _uiState.update { it.copy(all = previous, error = e.message) }
            }
        }
    }
}
