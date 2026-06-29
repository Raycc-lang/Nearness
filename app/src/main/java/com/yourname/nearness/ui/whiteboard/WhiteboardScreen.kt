package com.yourname.nearness.ui.whiteboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yourname.nearness.domain.WhiteboardItem
import com.yourname.nearness.domain.WhiteboardItemType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteboardScreen(
    userId: String,
    partnerId: String,
    onOpenArchive: () -> Unit,
    viewModel: WhiteboardViewModel = viewModel(
        factory = viewModelFactory { initializer { WhiteboardViewModel(userId, partnerId) } },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(viewModel) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Whiteboard") },
                actions = {
                    IconButton(onClick = onOpenArchive) {
                        Icon(Icons.Outlined.Inventory2, contentDescription = "Archive")
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.draft,
                    onValueChange = viewModel::onDraftChange,
                    placeholder = { Text("Leave a note…") },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::postText, enabled = !uiState.isPosting) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Post")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.items, key = { it.id }) { item ->
                WhiteboardCard(item = item, onArchive = { viewModel.archive(item.id) })
            }
        }
    }
}

@Composable
private fun WhiteboardCard(item: WhiteboardItem, onArchive: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                when (item.type) {
                    WhiteboardItemType.TEXT -> Text(item.content.orEmpty())
                    WhiteboardItemType.PHOTO -> Text("📷 Photo${captionSuffix(item.caption)}")
                    WhiteboardItemType.VOICE -> Text("🎙️ Voice memo${captionSuffix(item.caption)}")
                }
            }
            IconButton(onClick = onArchive) {
                Icon(Icons.Outlined.Archive, contentDescription = "Archive")
            }
        }
    }
}

private fun captionSuffix(caption: String?): String =
    if (caption.isNullOrBlank()) "" else " — $caption"
