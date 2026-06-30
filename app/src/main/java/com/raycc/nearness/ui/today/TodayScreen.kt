package com.raycc.nearness.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.raycc.nearness.domain.ActivityType
import com.raycc.nearness.domain.Presence
import com.raycc.nearness.domain.ScheduleBlock
import com.raycc.nearness.domain.SignalType
import com.raycc.nearness.domain.StatusData
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun TodayScreen(
    userId: String,
    partnerId: String,
    viewModel: TodayViewModel = viewModel(
        factory = viewModelFactory { initializer { TodayViewModel(userId, partnerId) } },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(viewModel) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        uiState.recentSignal?.let { signal ->
            item { SignalBanner(label = signalText(signal.type, signal.customText)) }
        }

        item {
            Text("Them", style = MaterialTheme.typography.labelLarge)
            PartnerStatusCard(uiState.partnerStatus)
        }

        if (uiState.partnerSchedule.isNotEmpty()) {
            item { Text("Today's schedule", style = MaterialTheme.typography.labelLarge) }
            items(uiState.partnerSchedule) { block -> ScheduleRow(block) }
        }

        item {
            Text("You", style = MaterialTheme.typography.labelLarge)
            MyStatusPicker(
                current = uiState.myStatus?.activity,
                onPick = { viewModel.setMyStatus(it, uiState.myStatus?.note) },
            )
        }
    }
}

@Composable
private fun SignalBanner(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PartnerStatusCard(status: StatusData?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (status == null) {
                Text("No status yet")
            } else {
                Text(
                    "${status.activity.emoji}  ${status.activity.label}",
                    style = MaterialTheme.typography.headlineSmall,
                )
                status.note?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) }
                Text(
                    Presence.coarse(status.updatedAt, Clock.System.now()),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MyStatusPicker(current: ActivityType?, onPick: (ActivityType) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ActivityType.entries.forEach { activity ->
            val selected = activity == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    )
                    .clickable { onPick(activity) }
                    .padding(12.dp),
            ) {
                Text(activity.emoji, style = MaterialTheme.typography.headlineSmall)
                Text(
                    activity.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun ScheduleRow(block: ScheduleBlock) {
    val zone = TimeZone.currentSystemDefault()
    val start = block.startsAt.toLocalDateTime(zone).time
    val end = block.endsAt.toLocalDateTime(zone).time
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("%02d:%02d–%02d:%02d".format(start.hour, start.minute, end.hour, end.minute))
        Text(block.label)
    }
}

private fun signalText(type: SignalType, custom: String?): String =
    if (type == SignalType.CUSTOM) custom.orEmpty() else "${type.emoji}  ${type.label}"
