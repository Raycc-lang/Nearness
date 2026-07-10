package com.raycc.nearness.ui.today

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.SheetState
import androidx.compose.ui.graphics.Color
import kotlinx.datetime.toInstant
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.raycc.nearness.data.LocalCacheRepository
import coil.compose.AsyncImage
import com.raycc.nearness.domain.ActivityType
import com.raycc.nearness.domain.Presence
import com.raycc.nearness.domain.ScheduleBlock
import com.raycc.nearness.domain.Signal
import com.raycc.nearness.domain.SignalType
import com.raycc.nearness.domain.StatusData
import com.raycc.nearness.ui.signals.SignalSheet
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    userId: String,
    initialPartnerId: String?,
    initialPartnershipId: String?,
    onPairClicked: () -> Unit,
    onWhiteboardClicked: () -> Unit,
    onPaired: () -> Unit,
    viewModel: TodayViewModel = viewModel(
        key = "$userId-$initialPartnerId-$initialPartnershipId",
        factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                TodayViewModel(
                    userId = userId,
                    initialPartnerId = initialPartnerId,
                    initialPartnershipId = initialPartnershipId,
                    cache = LocalCacheRepository(app),
                )
            }
        },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSignalSheet by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }

    val signalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val profileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LifecycleStartEffect(viewModel) {
        viewModel.startPolling()
        onStopOrDispose { viewModel.stopPolling() }
    }

    LaunchedEffect(uiState.isPaired) {
        if (uiState.isPaired && initialPartnerId == null) {
            onPaired()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues),
        ) {
            if (uiState.isInitialLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // 1. Fixed Banner Container (does not shift cards)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.activeSignal != null) {
                            SignalBanner(
                                signal = uiState.activeSignal!!,
                                partnerName = uiState.partnerName,
                            )
                        } else {
                            // Subtle title or quiet header that occupies the exact same space
                            Text(
                                text = "Nearness",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Partner Card / Unpaired State (occupies roughly 50% space)
                    if (uiState.isPaired) {
                        PartnerCard(
                            name = uiState.partnerName,
                            avatarUrl = uiState.partnerAvatarUrl,
                            status = uiState.partnerStatus,
                            schedule = uiState.partnerSchedule,
                            onSendSignalClick = { showSignalSheet = true },
                            modifier = Modifier.weight(0.5f)
                        )
                    } else {
                        PairPromptCard(
                            onPairClicked = onPairClicked,
                            modifier = Modifier.weight(0.5f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Your Card (occupies ~32% space)
                    YourCard(
                        name = uiState.yourName,
                        avatarUrl = uiState.yourAvatarUrl,
                        status = uiState.yourStatus,
                        schedule = uiState.yourSchedule,
                        onAvatarClick = { showProfileSheet = true },
                        onCardClick = { showEditSheet = true },
                        modifier = Modifier.weight(0.32f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. Whiteboard Entry Card (only when paired)
                    if (uiState.isPaired) {
                        WhiteboardEntryCard(
                            preview = uiState.whiteboardPreview,
                            onClick = onWhiteboardClicked,
                        )
                    }

                    // Extra space at bottom
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Bottom Sheets
        if (showSignalSheet) {
            SignalSheet(
                userId = userId,
                partnerId = uiState.partnerId.orEmpty(),
                onDismiss = { showSignalSheet = false },
                sheetState = signalSheetState,
            )
        }

        if (showEditSheet) {
            EditSheet(
                userId = userId,
                uiState = uiState,
                onDismiss = { showEditSheet = false },
                sheetState = editSheetState,
                onSaveStatus = { activity, note ->
                    viewModel.updateYourStatusAndNote(activity, note)
                },
                onDeleteBlock = { blockId ->
                    viewModel.deleteScheduleBlock(blockId)
                },
                onAddBlock = { label, startsAt, endsAt ->
                    viewModel.addScheduleBlock(label, startsAt, endsAt)
                },
            )
        }

        if (showProfileSheet) {
            ProfileSheet(
                uiState = uiState,
                onDismiss = { showProfileSheet = false },
                sheetState = profileSheetState,
                onSaveProfile = { name ->
                    viewModel.updateProfile(name)
                },
                onUploadAvatar = { bytes, onComplete ->
                    viewModel.uploadAvatar(bytes, onComplete)
                },
            )
        }
    }
}

@Composable
private fun SignalBanner(signal: Signal, partnerName: String) {
    val displayName = formatDisplayName(partnerName)
    val message = if (signal.type == SignalType.CUSTOM) {
        "$displayName: ${signal.customText}"
    } else {
        "$displayName sent you a ${signal.type.label} ${signal.type.emoji}"
    }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                RoundedCornerShape(24.dp),
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PartnerCard(
    name: String,
    avatarUrl: String?,
    status: StatusData?,
    schedule: List<ScheduleBlock>,
    onSendSignalClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formattedPresence = status?.let {
        Presence.coarse(it.updatedAt, Clock.System.now())
    } ?: "Updated a while ago"

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                RoundedCornerShape(24.dp),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(url = avatarUrl, name = name, size = 60.dp)
                Spacer(modifier = Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDisplayName(name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onSendSignalClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Send Signal",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = status?.let { "${it.activity.emoji} ${it.activity.label}" } ?: "Offline",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = formattedPresence,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Note
            if (status != null && !status.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "\"${status.note}\"",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Schedule Timeline
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Schedule",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (schedule.isEmpty()) {
                    Text(
                        text = "No blocks remaining today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        schedule.forEach { block ->
                            ScheduleTimelineRow(block = block)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YourCard(
    name: String,
    avatarUrl: String?,
    status: StatusData?,
    schedule: List<ScheduleBlock>,
    onAvatarClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formattedPresence = status?.let {
        Presence.coarse(it.updatedAt, Clock.System.now())
    } ?: "Updated a while ago"

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                RoundedCornerShape(24.dp),
            )
            .clickable { onCardClick() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(url = avatarUrl, name = name, onClick = onAvatarClick)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatDisplayName(name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = status?.let { "${it.activity.emoji} ${it.activity.label}" } ?: "Free",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formattedPresence,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Note
            if (status != null && !status.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "\"${status.note}\"",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Schedule Timeline
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Schedule",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (schedule.isEmpty()) {
                    Text(
                        text = "No blocks remaining today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        schedule.forEach { block ->
                            ScheduleTimelineRow(block = block)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PairPromptCard(
    onPairClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
        modifier = modifier
            .border(
                1.dp,
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                RoundedCornerShape(24.dp),
            )
            .clickable { onPairClicked() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Pair with your partner",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Connect with your partner to share signals, whiteboard posts, and daily updates.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
            Button(
                onClick = onPairClicked,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) {
                Text("Get Started")
            }
        }
    }
}

@Composable
private fun WhiteboardEntryCard(
    preview: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                RoundedCornerShape(24.dp),
            )
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Our Whiteboard",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = preview.ifBlank { "Nothing shared yet. Leave a note or a photo." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Open Whiteboard")
        }
    }
}

@Composable
private fun ScheduleTimelineRow(block: ScheduleBlock) {
    val zone = TimeZone.currentSystemDefault()
    val start = block.startsAt.toLocalDateTime(zone).time
    val end = block.endsAt.toLocalDateTime(zone).time
    val isExpired = block.endsAt < Clock.System.now()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(if (isExpired) 0.5f else 1f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = block.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textDecoration = if (isExpired) TextDecoration.LineThrough else TextDecoration.None,
        )
        Text(
            text = "%02d:%02d - %02d:%02d".format(start.hour, start.minute, end.hour, end.minute),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun Avatar(
    url: String?,
    name: String,
    size: Dp = 48.dp,
    onClick: (() -> Unit)? = null,
) {
    val modifier = Modifier
        .size(size)
        .clip(CircleShape)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)

    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = "$name's avatar",
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Text(
                text = name.firstOrNull()?.uppercase().orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

// ------------------------------------------------------------------------
// EDIT SHEET
// ------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(
    userId: String,
    uiState: TodayUiState,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    onSaveStatus: (ActivityType, String?) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onAddBlock: (String, Instant, Instant) -> Unit,
) {
    var selectedActivity by remember { mutableStateOf(uiState.yourStatus?.activity ?: ActivityType.FREE) }
    var noteText by remember { mutableStateOf(uiState.yourStatus?.note.orEmpty()) }

    var isAddingBlock by remember { mutableStateOf(false) }
    var blockLabel by remember { mutableStateOf("") }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    var startHour by remember { mutableStateOf(12) }
    var startMinute by remember { mutableStateOf(0) }
    var endHour by remember { mutableStateOf(13) }
    var endMinute by remember { mutableStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Edit Your Day",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            // Section 1: Activity Status Picker
            Column {
                Text(
                    text = "Current Status",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActivityType.entries.forEach { activity ->
                        val isSelected = activity == selectedActivity
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(68.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable { selectedActivity = activity }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(activity.emoji, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = activity.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }

            // Section 2: Momentary Note Field
            Column {
                Text(
                    text = "Momentary Note",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text = "Disappears with next status update",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = {
                        if (it.length <= 80) {
                            noteText = it
                        }
                    },
                    placeholder = { Text("Write what you're up to...") },
                    singleLine = true,
                    suffix = {
                        Text(
                            text = "${noteText.length}/80",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Section 3: Schedule Blocks Manager
            Column {
                Text(
                    text = "Your Schedule Blocks",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                if (uiState.yourSchedule.isEmpty()) {
                    Text(
                        text = "No blocks today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                } else {
                    uiState.yourSchedule.forEach { block ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val zone = TimeZone.currentSystemDefault()
                            val start = block.startsAt.toLocalDateTime(zone).time
                            val end = block.endsAt.toLocalDateTime(zone).time
                            Text(
                                text = "${block.label} (%02d:%02d - %02d:%02d)".format(
                                    start.hour,
                                    start.minute,
                                    end.hour,
                                    end.minute,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            IconButton(
                                onClick = { onDeleteBlock(block.id) },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Block",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                // Add Schedule Block Inline Form
                if (isAddingBlock) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(12.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    ) {
                        // Templates Row
                        Text(
                            text = "Shift Templates",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val templates = listOf(
                                ShiftTemplate("Morning Shift", 7, 0, 11, 0),
                                ShiftTemplate("Morning Shift", 9, 0, 13, 0),
                                ShiftTemplate("Afternoon Shift", 15, 0, 19, 0),
                                ShiftTemplate("Afternoon Shift", 17, 0, 21, 0)
                            )
                            templates.forEach { template ->
                                val displayLabel = when {
                                    template.startHour == 7 -> "Morning (7-11)"
                                    template.startHour == 9 -> "Morning (9-1)"
                                    template.startHour == 15 -> "Afternoon (3-7)"
                                    template.startHour == 17 -> "Afternoon (5-9)"
                                    else -> "${template.label} (${template.startHour}-${template.endHour})"
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .clickable {
                                            blockLabel = template.label
                                            startHour = template.startHour
                                            startMinute = template.startMinute
                                            endHour = template.endHour
                                            endMinute = template.endMinute
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = displayLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = blockLabel,
                            onValueChange = { blockLabel = it },
                            placeholder = { Text("Block label (e.g. Focus time)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { showStartTimePicker = true }) {
                                Text("Start: %02d:%02d".format(startHour, startMinute))
                            }
                            TextButton(onClick = { showEndTimePicker = true }) {
                                Text("End: %02d:%02d".format(endHour, endMinute))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { isAddingBlock = false }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (blockLabel.isNotBlank()) {
                                        val start = timeToInstant(startHour, startMinute)
                                        val end = timeToInstant(endHour, endMinute)
                                        if (end > start) {
                                            onAddBlock(blockLabel, start, end)
                                            blockLabel = ""
                                            isAddingBlock = false
                                        }
                                    }
                                },
                            ) {
                                Text("Add")
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { isAddingBlock = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Block")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Schedule Block")
                    }
                }
            }

            // Section 4: Save & Apply Status changes
            Button(
                onClick = {
                    onSaveStatus(selectedActivity, noteText.trim().ifBlank { null })
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }

    // Time Pickers Dialogs
    if (showStartTimePicker) {
        TimePickerDialog(
            title = "Select Start Time",
            initialHour = startHour,
            initialMinute = startMinute,
            onDismiss = { showStartTimePicker = false },
            onConfirm = { h, m ->
                startHour = h
                startMinute = m
                showStartTimePicker = false
            },
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            title = "Select End Time",
            initialHour = endHour,
            initialMinute = endMinute,
            onDismiss = { showEndTimePicker = false },
            onConfirm = { h, m ->
                endHour = h
                endMinute = m
                showEndTimePicker = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text(title) },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
    )
}

private fun timeToInstant(hour: Int, minute: Int): Instant {
    val zone = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(zone).date
    val localDateTime = kotlinx.datetime.LocalDateTime(today.year, today.month, today.dayOfMonth, hour, minute)
    return localDateTime.toInstant(zone)
}

// ------------------------------------------------------------------------
// PROFILE SHEET
// ------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSheet(
    uiState: TodayUiState,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    onSaveProfile: (String) -> Unit,
    onUploadAvatar: (ByteArray, (Result<String>) -> Unit) -> Unit,
) {
    var nameText by remember { mutableStateOf(uiState.yourName) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var avatarError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                val bytes = compressAvatar(context, uri)
                if (bytes != null) {
                    isUploadingAvatar = true
                    avatarError = null
                    onUploadAvatar(bytes) { res ->
                        isUploadingAvatar = false
                        if (res.isFailure) {
                            avatarError = res.exceptionOrNull()?.message
                        } else {
                            Toast.makeText(context, "Avatar uploaded", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        },
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Edit Profile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start),
            )

            // Avatar Tapping Area
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable {
                        launcher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
            ) {
                Avatar(url = uiState.yourAvatarUrl, name = uiState.yourName, size = 96.dp)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isUploadingAvatar) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Avatar",
                            tint = MaterialTheme.colorScheme.onSecondary,
                        )
                    }
                }
            }

            avatarError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Display Name Input
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Display Name",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                OutlinedTextField(
                    value = nameText,
                    onValueChange = {
                        if (it.length <= 30) {
                            nameText = it
                        }
                    },
                    placeholder = { Text("Your name") },
                    singleLine = true,
                    suffix = {
                        Text(
                            text = "${nameText.length}/30",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Save
            Button(
                onClick = {
                    onSaveProfile(nameText.trim().ifBlank { "User" })
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}

private fun compressAvatar(context: Context, uri: android.net.Uri): ByteArray? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeStream(inputStream, null, options)

            val targetSize = 256
            var inSampleSize = 1
            if (options.outHeight > targetSize || options.outWidth > targetSize) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= targetSize && halfWidth / inSampleSize >= targetSize) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = inSampleSize
            }
            context.contentResolver.openInputStream(uri)?.use { stream2 ->
                val bitmap = android.graphics.BitmapFactory.decodeStream(stream2, null, decodeOptions)
                if (bitmap != null) {
                    val scaled = if (bitmap.width > targetSize || bitmap.height > targetSize) {
                        val scale = targetSize.toFloat() / Math.max(bitmap.width, bitmap.height)
                        val w = (bitmap.width * scale).toInt()
                        val h = (bitmap.height * scale).toInt()
                        android.graphics.Bitmap.createScaledBitmap(bitmap, w, h, true)
                    } else {
                        bitmap
                    }
                    val out = java.io.ByteArrayOutputStream()
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                    out.toByteArray()
                } else {
                    null
                }
            }
        }
    } catch (e: Exception) {
        null
    }
}

data class ShiftTemplate(
    val label: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
)

/**
 * Display name with the first letter auto-capitalized.
 * Only applies to Latin letters; CJK and other scripts are left as-is
 * (uppercasing a CJK char is a no-op anyway, but we skip explicitly).
 */
private fun formatDisplayName(name: String): String {
    if (name.isEmpty()) return name
    val first = name.first()
    return if (first in 'a'..'z') name.replaceFirstChar { it.uppercaseChar() } else name
}
