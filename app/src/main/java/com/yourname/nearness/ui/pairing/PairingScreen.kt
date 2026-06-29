package com.yourname.nearness.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PairingScreen(
    onPaired: () -> Unit,
    viewModel: PairingViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.paired) {
        if (uiState.paired) onPaired()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Pair with your partner", style = MaterialTheme.typography.headlineSmall)

        // Share my code
        Text("Share your code", modifier = Modifier.padding(top = 24.dp))
        if (uiState.myCode != null) {
            Text(
                uiState.myCode!!,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(8.dp),
            )
            Text("Expires in 7 days", style = MaterialTheme.typography.bodySmall)
        } else {
            OutlinedButton(
                onClick = viewModel::generateCode,
                enabled = !uiState.isGenerating,
            ) { Text("Generate code") }
        }

        Divider(modifier = Modifier.padding(vertical = 24.dp))

        // Enter partner's code
        Text("Or enter their code")
        OutlinedTextField(
            value = uiState.enteredCode,
            onValueChange = viewModel::onEnteredCodeChange,
            label = { Text("6-character code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        uiState.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        Button(
            onClick = viewModel::redeemCode,
            enabled = !uiState.isRedeeming,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) { Text("Pair") }
    }
}
