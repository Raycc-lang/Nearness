package com.raycc.nearness.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
        Text(
            text = "Nearness",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "A quiet space for the two of you.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )

        if (uiState.linkSent) {
            Text(
                text = "Check your email. We've sent you a sign-in link.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email Address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.Start).padding(top = 8.dp),
                )
            }

            Button(
                onClick = viewModel::sendMagicLink,
                enabled = !uiState.isSending && uiState.resendCooldown == 0,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) {
                if (uiState.isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                }
                val label = when {
                    uiState.resendCooldown > 0 -> "Resend in ${uiState.resendCooldown}s"
                    else -> "Send Magic Link"
                }
                Text(label)
            }
        }
    }
}
