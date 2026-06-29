package com.yourname.nearness.ui.signals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yourname.nearness.domain.SignalType

@Composable
fun SignalsScreen(
    userId: String,
    partnerId: String,
    viewModel: SignalsViewModel = viewModel(
        factory = viewModelFactory { initializer { SignalsViewModel(userId, partnerId) } },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(uiState.sentLabel) {
        uiState.sentLabel?.let {
            snackbar.showSnackbar("Sent: $it")
            viewModel.clearSent()
        }
    }

    val presets = SignalType.entries.filter { it != SignalType.CUSTOM }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        ) {
            Text("Send a signal", style = MaterialTheme.typography.headlineSmall)
            Text(
                "A gentle tap on the shoulder. No reply needed.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items(presets) { type ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !uiState.isSending) { viewModel.sendPreset(type) },
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(type.emoji, style = MaterialTheme.typography.displaySmall)
                            Text(type.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = uiState.customText,
                onValueChange = viewModel::onCustomChange,
                label = { Text("Custom (max 30)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
            Button(
                onClick = viewModel::sendCustom,
                enabled = !uiState.isSending && uiState.customText.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Send custom signal") }
        }
    }
}
