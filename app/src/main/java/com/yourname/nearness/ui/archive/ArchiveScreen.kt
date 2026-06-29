package com.yourname.nearness.ui.archive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Unarchive
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
fun ArchiveScreen(
    userId: String,
    partnerId: String,
    onBack: () -> Unit,
    viewModel: ArchiveViewModel = viewModel(
        factory = viewModelFactory { initializer { ArchiveViewModel(userId, partnerId) } },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archive") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search archive…") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                uiState.grouped.forEach { (month, items) ->
                    item(key = month) {
                        Text(
                            month,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    items(items.size, key = { items[it].id }) { idx ->
                        ArchiveRow(items[idx]) { viewModel.unarchive(items[idx].id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveRow(item: WhiteboardItem, onUnarchive: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                when (item.type) {
                    WhiteboardItemType.TEXT -> Text(item.content.orEmpty())
                    WhiteboardItemType.PHOTO -> Text("📷 Photo")
                    WhiteboardItemType.VOICE -> Text("🎙️ Voice memo")
                }
                item.caption?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            IconButton(onClick = onUnarchive) {
                Icon(Icons.Outlined.Unarchive, contentDescription = "Unarchive")
            }
        }
    }
}
