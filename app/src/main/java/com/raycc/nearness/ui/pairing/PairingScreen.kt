package com.raycc.nearness.ui.pairing

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    onBack: () -> Unit,
    onPaired: () -> Unit,
    viewModel: PairingViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }

    LaunchedEffect(uiState.paired) {
        if (uiState.paired) {
            onPaired()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pairing", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("My Code") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Enter Code") },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (selectedTab == 0) {
                    MyCodeTab(
                        uiState = uiState,
                        onRegenerate = viewModel::generateCode,
                    )
                } else {
                    EnterCodeTab(
                        uiState = uiState,
                        onCodeChange = viewModel::onEnteredCodeChange,
                        onPairClick = viewModel::redeemCode,
                    )
                }
            }
        }
    }
}

@Composable
private fun MyCodeTab(
    uiState: PairingUiState,
    onRegenerate: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    ) {
        Text(
            "Share this code with your partner. Once they enter it, you'll be paired.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (uiState.isGenerating) {
            CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        } else if (uiState.myCode != null) {
            val code = uiState.myCode
            Text(
                text = code,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(16.dp)
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
            Text(
                "Expires in 7 days",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Tap to copy · Long-press to share",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRegenerate,
            enabled = !uiState.isGenerating,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
        ) {
            Text("Regenerate Code")
        }

        uiState.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun EnterCodeTab(
    uiState: PairingUiState,
    onCodeChange: (String) -> Unit,
    onPairClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    ) {
        Text(
            "Enter the 6-character code generated by your partner.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = uiState.enteredCode,
            onValueChange = {
                if (it.length <= 6) {
                    onCodeChange(it.uppercase())
                }
            },
            label = { Text("Code") },
            placeholder = { Text("ABCDEF") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        uiState.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.Start),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onPairClick,
            enabled = !uiState.isRedeeming && uiState.enteredCode.length == 6,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isRedeeming) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).padding(end = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            }
            Text("Pair")
        }
    }
}
