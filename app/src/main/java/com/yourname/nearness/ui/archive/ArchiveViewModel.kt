package com.yourname.nearness.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.nearness.data.WhiteboardRepository
import com.yourname.nearness.domain.WhiteboardItem
import com.yourname.nearness.domain.pairKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class ArchiveUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val all: List<WhiteboardItem> = emptyList(),
    val error: String? = null,
) {
    /** Items filtered by the local search query, grouped by "Month Year". */
    val grouped: Map<String, List<WhiteboardItem>>
        get() {
            val zone = TimeZone.currentSystemDefault()
            val filtered = if (query.isBlank()) all else all.filter {
                (it.content?.contains(query, ignoreCase = true) == true) ||
                    (it.caption?.contains(query, ignoreCase = true) == true)
            }
            return filtered.groupBy {
                val d = it.createdAt.toLocalDateTime(zone).date
                "${d.month.name.lowercase().replaceFirstChar(Char::uppercase)} ${d.year}"
            }
        }
}

class ArchiveViewModel(
    userId: String,
    partnerId: String,
    private val repo: WhiteboardRepository = WhiteboardRepository(),
) : ViewModel() {

    private val pairKey = pairKey(userId, partnerId)

    private val _uiState = MutableStateFlow(ArchiveUiState())
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repo.getArchive(pairKey)
                .onSuccess { items -> _uiState.update { it.copy(isLoading = false, all = items) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
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
