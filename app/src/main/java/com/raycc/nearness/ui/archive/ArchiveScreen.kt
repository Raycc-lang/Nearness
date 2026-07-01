package com.raycc.nearness.ui.archive

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.raycc.nearness.domain.Presence
import com.raycc.nearness.domain.WhiteboardItem
import com.raycc.nearness.domain.WhiteboardItemType
import kotlinx.datetime.Clock

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArchiveScreen(
    userId: String,
    partnerId: String,
    partnershipId: String,
    onBack: () -> Unit,
    viewModel: ArchiveViewModel = viewModel(
        key = "$userId-$partnerId-$partnershipId",
        factory = viewModelFactory {
            initializer {
                ArchiveViewModel(
                    userId = userId,
                    partnerId = partnerId,
                    partnershipId = partnershipId,
                )
            }
        },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archive", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search text and captions...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )

            if (uiState.isLoading && uiState.all.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    uiState.grouped.forEach { (monthYear, items) ->
                        stickyHeader {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(
                                    text = monthYear.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        items(items, key = { it.id }) { item ->
                            ArchiveRow(
                                item = item,
                                userId = userId,
                                partnerName = uiState.partnerName,
                                signedUrls = uiState.signedUrls,
                                onUnarchive = { viewModel.unarchive(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveRow(
    item: WhiteboardItem,
    userId: String,
    partnerName: String,
    signedUrls: Map<String, String>,
    onUnarchive: () -> Unit,
) {
    val authorName = if (item.authorId == userId) "You" else partnerName
    val dateStr = Presence.coarse(item.createdAt, Clock.System.now())

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                RoundedCornerShape(16.dp),
            ),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$authorName • $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                when (item.type) {
                    WhiteboardItemType.TEXT -> {
                        Text(
                            text = item.textBody.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    WhiteboardItemType.PHOTO -> {
                        val signedUrl = signedUrls[item.storagePath.orEmpty()]

                        Column {
                            if (signedUrl != null) {
                                AsyncImage(
                                    model = signedUrl,
                                    contentDescription = item.caption ?: "Archived photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                            }
                            if (!item.caption.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.caption,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    WhiteboardItemType.VOICE -> {
                        Text(
                            text = if (item.caption.isNullOrBlank()) "Voice memo" else "Voice memo: ${item.caption}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            IconButton(onClick = onUnarchive) {
                Icon(
                    imageVector = Icons.Outlined.Unarchive,
                    contentDescription = "Unarchive",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
