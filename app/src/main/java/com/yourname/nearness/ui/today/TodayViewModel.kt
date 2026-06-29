package com.yourname.nearness.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.nearness.data.ScheduleRepository
import com.yourname.nearness.data.SignalsRepository
import com.yourname.nearness.data.StatusRepository
import com.yourname.nearness.domain.ActivityType
import com.yourname.nearness.domain.ScheduleBlock
import com.yourname.nearness.domain.Signal
import com.yourname.nearness.domain.StatusData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.seconds

data class TodayUiState(
    val isLoading: Boolean = true,
    val myStatus: StatusData? = null,
    val partnerStatus: StatusData? = null,
    val partnerSchedule: List<ScheduleBlock> = emptyList(),
    val recentSignal: Signal? = null,
    val error: String? = null,
)

class TodayViewModel(
    private val userId: String,
    private val partnerId: String,
    private val statusRepo: StatusRepository = StatusRepository(),
    private val scheduleRepo: ScheduleRepository = ScheduleRepository(),
    private val signalsRepo: SignalsRepository = SignalsRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val zone = TimeZone.currentSystemDefault()
            val today = Clock.System.now().toLocalDateTime(zone).date
            val dayStart = today.atStartOfDayIn(zone)
            val dayEnd = today.plus(1, kotlinx.datetime.DateTimeUnit.DAY).atStartOfDayIn(zone)

            val mine = statusRepo.getStatus(userId).getOrNull()
            val partner = statusRepo.getStatus(partnerId).getOrNull()
            val schedule = scheduleRepo.getBlocksBetween(partnerId, dayStart, dayEnd).getOrDefault(emptyList())
            val signal = signalsRepo.latestRecent(userId).getOrNull()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    myStatus = mine,
                    partnerStatus = partner,
                    partnerSchedule = schedule,
                    recentSignal = signal,
                )
            }
        }
    }

    /**
     * Polling replaces Supabase Realtime by design. FCM delivers partner changes
     * while the app is backgrounded; this loop keeps the open screen fresh.
     * Started/stopped from the Composable's lifecycle so it never polls in the
     * background.
     */
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

    /** Lightweight refresh of the things only the partner changes. */
    private suspend fun pollPartner() {
        val partner = statusRepo.getStatus(partnerId).getOrNull()
        val signal = signalsRepo.latestRecent(userId).getOrNull()
        _uiState.update { it.copy(partnerStatus = partner, recentSignal = signal) }
    }

    /** Optimistic status update: reflect immediately, then persist. */
    fun setMyStatus(activity: ActivityType, note: String?) {
        val previous = _uiState.value.myStatus
        _uiState.update {
            it.copy(myStatus = StatusData(userId, activity, note, Clock.System.now()))
        }
        viewModelScope.launch {
            statusRepo.upsertStatus(userId, activity, note).onFailure { e ->
                _uiState.update { it.copy(myStatus = previous, error = e.message) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private companion object {
        val POLL_INTERVAL = 30.seconds
    }
}
