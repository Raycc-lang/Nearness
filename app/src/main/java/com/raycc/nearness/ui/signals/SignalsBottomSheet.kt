package com.raycc.nearness.ui.signals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.raycc.nearness.domain.SignalType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SignalSheet(
    userId: String,
    partnerId: String,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    viewModel: SignalsViewModel = viewModel(
        key = "signals-$userId-$partnerId",
        factory = viewModelFactory { initializer { SignalsViewModel(userId, partnerId) } },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.sentLabel) {
        if (uiState.sentLabel != null) {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Send a Signal",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Let them know you are thinking of them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )

            // Presets
            Text(
                text = "Presets",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            val presets = SignalType.entries.filter { it != SignalType.CUSTOM }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                presets.forEach { preset ->
                    OutlinedButton(
                        onClick = { viewModel.sendPreset(preset) },
                        enabled = !uiState.isSending,
                    ) {
                        Text("${preset.emoji} ${preset.label}")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Custom Signal Input
            Text(
                text = "Or write a custom signal",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            OutlinedTextField(
                value = uiState.customText,
                onValueChange = viewModel::onCustomChange,
                placeholder = { Text("Custom message...") },
                singleLine = true,
                suffix = {
                    Text(
                        text = "${uiState.customText.length}/30",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = viewModel::sendCustom,
                enabled = !uiState.isSending && uiState.customText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                }
                Text("Send")
            }

            uiState.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
