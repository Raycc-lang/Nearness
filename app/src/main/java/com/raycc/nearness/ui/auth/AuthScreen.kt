package com.raycc.nearness.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AuthScreen(viewModel: AuthViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nearness", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Sign in with your email. We'll send you a magic link.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        OutlinedTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        uiState.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (uiState.linkSent) {
            Text(
                "Check your inbox — tap the link to continue.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Button(
            onClick = viewModel::sendMagicLink,
            enabled = !uiState.isSending && uiState.resendCooldown == 0,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            if (uiState.isSending) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            val label = when {
                uiState.resendCooldown > 0 -> "Resend in ${uiState.resendCooldown}s"
                uiState.linkSent -> "Resend magic link"
                else -> "Send magic link"
            }
            Text(label)
        }
    }
}
