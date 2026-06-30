package com.raycc.nearness.ui.pairing

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PairingScreen(
    onPaired: () -> Unit,
    viewModel: PairingViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

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
            val code = uiState.myCode!!
            Text(
                code,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier
                    .padding(8.dp)
                    .pointerInput(code) {
                        detectTapGestures(
                            onTap = {
                                clipboard.setText(AnnotatedString(code))
                                Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                            },
                            onLongPress = {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, code)
                                }
                                context.startActivity(Intent.createChooser(share, "Share code"))
                            },
                        )
                    },
            )
            Text("Expires in 7 days", style = MaterialTheme.typography.bodySmall)
            Text(
                "Tap to copy · long-press to share",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            OutlinedButton(
                onClick = viewModel::generateCode,
                enabled = !uiState.isGenerating,
            ) { Text("Generate code") }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

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
