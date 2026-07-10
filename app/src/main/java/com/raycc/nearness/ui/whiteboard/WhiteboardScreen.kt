package com.raycc.nearness.ui.whiteboard

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.raycc.nearness.data.LocalCacheRepository
import coil.compose.AsyncImage
import com.raycc.nearness.domain.Presence
import com.raycc.nearness.domain.WhiteboardItem
import com.raycc.nearness.domain.WhiteboardItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhiteboardScreen(
    userId: String,
    partnerId: String,
    partnershipId: String,
    onBack: () -> Unit,
    onOpenArchive: () -> Unit,
    viewModel: WhiteboardViewModel = viewModel(
        key = "$userId-$partnerId-$partnershipId",
        factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY]!!
                WhiteboardViewModel(
                    userId = userId,
                    partnerId = partnerId,
                    partnershipId = partnershipId,
                    cache = LocalCacheRepository(app),
                )
            }
        },
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPostSheet by remember { mutableStateOf(false) }
    var postSheetType by remember { mutableStateOf<WhiteboardItemType?>(null) }
    val postSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Cache or state for reply fields per top-level item
    var activeReplyId by remember { mutableStateOf<String?>(null) }
    var replyText by remember { mutableStateOf("") }

    LifecycleStartEffect(viewModel) {
        viewModel.startPolling()
        onStopOrDispose { viewModel.stopPolling() }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Our Whiteboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenArchive) {
                        Icon(Icons.Filled.Inventory2, contentDescription = "Archive")
                    }
                },
            )
        },
        bottomBar = {
            // Sticky Footer Compose Bar
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        postSheetType = WhiteboardItemType.TEXT
                        showPostSheet = true
                    }) {
                        Icon(Icons.Filled.TextFields, contentDescription = "Write Note")
                    }

                    IconButton(onClick = {
                        postSheetType = WhiteboardItemType.PHOTO
                        showPostSheet = true
                    }) {
                        Icon(Icons.Filled.Image, contentDescription = "Share Photo")
                    }

                    IconButton(onClick = {
                        postSheetType = WhiteboardItemType.VOICE
                        showPostSheet = true
                    }) {
                        Icon(Icons.Filled.Mic, contentDescription = "Record Voice")
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            if (uiState.isLoading && uiState.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val topLevelPosts = remember(uiState.items) {
                    uiState.items.filter { it.parentId == null }
                }
                val repliesMap = remember(uiState.items) {
                    uiState.items.filter { it.parentId != null }.groupBy { it.parentId }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(topLevelPosts, key = { it.id }) { post ->
                        val postReplies = repliesMap[post.id] ?: emptyList()
                        val isReplying = activeReplyId == post.id

                        WhiteboardPostCard(
                            post = post,
                            replies = postReplies,
                            userId = userId,
                            partnerName = uiState.partnerName,
                            isReplying = isReplying,
                            replyText = replyText,
                            signedUrls = uiState.signedUrls,
                            onReplyClick = {
                                if (activeReplyId == post.id) {
                                    activeReplyId = null
                                    replyText = ""
                                } else {
                                    activeReplyId = post.id
                                    replyText = ""
                                }
                            },
                            onReplyTextChange = { replyText = it },
                            onSendReply = { text ->
                                viewModel.postText(text, parentId = post.id)
                                activeReplyId = null
                                replyText = ""
                            },
                            onArchive = { viewModel.archive(post.id) },
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }
            }
        }

        if (showPostSheet && postSheetType != null) {
            PostSheet(
                type = postSheetType!!,
                onDismiss = {
                    showPostSheet = false
                    postSheetType = null
                },
                sheetState = postSheetState,
                onPostText = { text ->
                    viewModel.postText(text)
                },
                onPostPhoto = { bytes, caption ->
                    viewModel.postPhoto(bytes, caption)
                },
                onPostVoice = { bytes, caption ->
                    viewModel.postVoice(bytes, caption)
                },
            )
        }
    }
}

@Composable
private fun WhiteboardPostCard(
    post: WhiteboardItem,
    replies: List<WhiteboardItem>,
    userId: String,
    partnerName: String,
    isReplying: Boolean,
    replyText: String,
    signedUrls: Map<String, String>,
    onReplyClick: () -> Unit,
    onReplyTextChange: (String) -> Unit,
    onSendReply: (String) -> Unit,
    onArchive: () -> Unit,
) {
    val authorName = if (post.authorId == userId) "You" else partnerName
    val coarseTime = Presence.coarse(post.createdAt, Clock.System.now())

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                RoundedCornerShape(24.dp),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$authorName • $coarseTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onArchive, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Archive,
                        contentDescription = "Archive Post",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content
            PostContent(post = post, signedUrls = signedUrls)

            // Inline Replies Area
            if (replies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                RepliesSection(
                    replies = replies,
                    userId = userId,
                    partnerName = partnerName,
                )
            }

            // Reply trigger / inline composer
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onReplyClick) {
                    Text(
                        text = if (isReplying) "Cancel" else "[ + Add Reply ]",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            AnimatedVisibility(visible = isReplying) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = onReplyTextChange,
                        placeholder = { Text("Write reply...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { onSendReply(replyText) },
                        enabled = replyText.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Reply")
                    }
                }
            }
        }
    }
}

@Composable
private fun PostContent(post: WhiteboardItem, signedUrls: Map<String, String>) {
    when (post.type) {
        WhiteboardItemType.TEXT -> {
            Text(
                text = post.textBody.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        WhiteboardItemType.PHOTO -> {
            val signedUrl = signedUrls[post.storagePath.orEmpty()]

            Column {
                if (signedUrl != null) {
                    AsyncImage(
                        model = signedUrl,
                        contentDescription = post.caption ?: "Photo post",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                if (!post.caption.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = post.caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        WhiteboardItemType.VOICE -> {
            VoicePlayerRow(post = post, signedUrls = signedUrls)
        }
    }
}

@Composable
private fun VoicePlayerRow(post: WhiteboardItem, signedUrls: Map<String, String>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isPlaying by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var durationText by remember { mutableStateOf("0:00") }

    val mediaPlayer = remember { MediaPlayer() }
    val cacheFile = remember(post.id) { File(context.cacheDir, "${post.id}.m4a") }

    var job: Job? by remember { mutableStateOf(null) }

    DisposableEffect(post.id) {
        onDispose {
            job?.cancel()
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.release()
        }
    }

    val signedUrl = signedUrls[post.storagePath.orEmpty()]

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) {
                        mediaPlayer.pause()
                        isPlaying = false
                        job?.cancel()
                    } else {
                        if (cacheFile.exists()) {
                            playAudio(mediaPlayer, cacheFile) {
                                isPlaying = true
                                job = scope.launch {
                                    while (mediaPlayer.isPlaying) {
                                        progress = mediaPlayer.currentPosition.toFloat() / mediaPlayer.duration
                                        val sec = mediaPlayer.currentPosition / 1000
                                        durationText = "%d:%02d".format(sec / 60, sec % 60)
                                        delay(100L)
                                    }
                                    isPlaying = false
                                    progress = 0f
                                }
                            }
                        } else if (signedUrl != null) {
                            isDownloading = true
                            scope.launch {
                                try {
                                    downloadFile(signedUrl, cacheFile)
                                    isDownloading = false
                                    playAudio(mediaPlayer, cacheFile) {
                                        isPlaying = true
                                        job = scope.launch {
                                            while (mediaPlayer.isPlaying) {
                                                progress = mediaPlayer.currentPosition.toFloat() / mediaPlayer.duration
                                                val sec = mediaPlayer.currentPosition / 1000
                                                durationText = "%d:%02d".format(sec / 60, sec % 60)
                                                delay(100L)
                                            }
                                            isPlaying = false
                                            progress = 0f
                                        }
                                    }
                                } catch (e: Exception) {
                                    isDownloading = false
                                    Toast.makeText(context, "Playback error", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Audio file not ready", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isDownloading,
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!post.caption.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = post.caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun playAudio(player: MediaPlayer, file: File, onStart: () -> Unit) {
    player.reset()
    player.setDataSource(file.absolutePath)
    player.prepare()
    player.start()
    onStart()
}

private suspend fun downloadFile(urlStr: String, file: File) {
    withContext(Dispatchers.IO) {
        val url = URL(urlStr)
        url.openStream().use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
    }
}

@Composable
private fun RepliesSection(
    replies: List<WhiteboardItem>,
    userId: String,
    partnerName: String,
) {
    var expanded by remember { mutableStateOf(false) }
    val showCount = if (expanded || replies.size <= 3) replies.size else 2
    val remaining = replies.size - showCount

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // Vertical Indentation Indicator
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(2.dp)),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            replies.take(showCount).forEach { reply ->
                val replyAuthorName = if (reply.authorId == userId) "You" else partnerName
                val timeStr = Presence.coarse(reply.createdAt, Clock.System.now())

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "$replyAuthorName • $timeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = reply.textBody.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (remaining > 0) {
                TextButton(onClick = { expanded = true }, modifier = Modifier.padding(0.dp)) {
                    Text(
                        text = "Show $remaining more replies",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------
// POST SHEET
// ------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostSheet(
    type: WhiteboardItemType,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    onPostText: (String) -> Unit,
    onPostPhoto: (ByteArray, String?) -> Unit,
    onPostVoice: (ByteArray, String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var textBody by remember { mutableStateOf("") }
    var captionText by remember { mutableStateOf("") }

    // Photo specific states
    var selectedPhotoBytes by remember { mutableStateOf<ByteArray?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                selectedPhotoBytes = bytes
            }
        },
    )

    // Voice specific states
    var isRecording by remember { mutableStateOf(false) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var recorder: MediaRecorder? by remember { mutableStateOf(null) }
    var recordingDurationSeconds by remember { mutableStateOf(0) }
    var voiceBytes by remember { mutableStateOf<ByteArray?>(null) }
    var recordTimerJob: Job? by remember { mutableStateOf(null) }

    val startRecordingAction = {
        val tempFile = File(context.cacheDir, "temp_record.m4a")
        recordedFile = tempFile
        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(tempFile.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            recordingDurationSeconds = 0
            recordTimerJob = scope.launch {
                while (isRecording) {
                    delay(1000L)
                    recordingDurationSeconds++
                    if (recordingDurationSeconds >= 180) {
                        recorder?.stop()
                        recorder?.release()
                        recorder = null
                        isRecording = false
                        voiceBytes = tempFile.readBytes()
                        return@launch
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error starting recorder", Toast.LENGTH_SHORT).show()
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                startRecordingAction()
            } else {
                Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
            }
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            recorder?.release()
        }
    }

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
            val title = when (type) {
                WhiteboardItemType.TEXT -> "Compose Note"
                WhiteboardItemType.PHOTO -> "Post Photo"
                WhiteboardItemType.VOICE -> "Record Voice Memo"
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            when (type) {
                WhiteboardItemType.TEXT -> {
                    OutlinedTextField(
                        value = textBody,
                        onValueChange = {
                            if (it.length <= 500) {
                                textBody = it
                            }
                        },
                        placeholder = { Text("What would you like to share?") },
                        minLines = 4,
                        suffix = {
                            Text(
                                text = "${textBody.length}/500",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Button(
                        onClick = {
                            onPostText(textBody)
                            onDismiss()
                        },
                        enabled = textBody.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Post")
                    }
                }

                WhiteboardItemType.PHOTO -> {
                    if (selectedPhotoBytes == null) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = "Select Photo")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Photo from Gallery")
                        }
                    } else {
                        Text(
                            text = "Photo Selected",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        OutlinedTextField(
                            value = captionText,
                            onValueChange = {
                                if (it.length <= 100) {
                                    captionText = it
                                }
                            },
                            placeholder = { Text("Optional caption...") },
                            singleLine = true,
                            suffix = {
                                Text(
                                    text = "${captionText.length}/100",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(
                                onClick = { selectedPhotoBytes = null },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Clear")
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Button(
                                onClick = {
                                    selectedPhotoBytes?.let { bytes ->
                                        onPostPhoto(bytes, captionText.trim().ifBlank { null })
                                    }
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Post")
                            }
                        }
                    }
                }

                WhiteboardItemType.VOICE -> {

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (isRecording) {
                                "%d:%02d".format(recordingDurationSeconds / 60, recordingDurationSeconds % 60)
                            } else if (voiceBytes != null) {
                                "Recorded Successfully"
                            } else {
                                "0:00"
                            },
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isRecording) {
                            WaveformVisualizer()
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        IconButton(
                            onClick = {
                                if (isRecording) {
                                    try {
                                        recorder?.stop()
                                        recorder?.release()
                                        recorder = null
                                        isRecording = false
                                        recordTimerJob?.cancel()
                                        recordedFile?.let { file ->
                                            voiceBytes = file.readBytes()
                                        }
                                    } catch (e: Exception) {
                                        isRecording = false
                                        recorder = null
                                    }
                                } else {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        startRecordingAction()
                                    } else {
                                        recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isRecording) "Stop" else "Record",
                                tint = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(36.dp),
                            )
                        }

                        if (voiceBytes != null && !isRecording) {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = captionText,
                                onValueChange = {
                                    if (it.length <= 100) {
                                        captionText = it
                                    }
                                },
                                placeholder = { Text("Optional caption...") },
                                singleLine = true,
                                suffix = {
                                    Text(
                                        text = "${captionText.length}/100",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                            ) {
                                TextButton(
                                    onClick = {
                                        voiceBytes = null
                                        recordedFile?.delete()
                                        recordedFile = null
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Discard")
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Button(
                                    onClick = {
                                        voiceBytes?.let { bytes ->
                                            onPostVoice(bytes, captionText.trim().ifBlank { null })
                                        }
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Post")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaveformVisualizer() {
    var phase by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            phase += 0.1f
            delay(30L)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 24.dp),
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val points = 60
        val step = width / points

        for (i in 0 until points) {
            val x = i * step
            val scale = 0.3f + 0.7f * kotlin.math.abs(kotlin.math.sin(i * 0.5f))
            val amp = (height * 0.4f) * kotlin.math.sin(i * 0.2f + phase) * scale
            drawLine(
                color = primaryColor,
                start = Offset(x, centerY - amp),
                end = Offset(x, centerY + amp),
                strokeWidth = 3.dp.toPx(),
            )
        }
    }
}
