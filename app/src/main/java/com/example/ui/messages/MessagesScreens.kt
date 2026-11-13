package com.example.ui.messages

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ChatMessage
import com.example.ui.VyoraAppViewModel
import com.example.ui.components.VyoraAvatar
import com.example.ui.components.VyoraEmptyState
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PulseMagenta
import com.example.ui.theme.vString
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsListScreen(
    viewModel: VyoraAppViewModel,
    onOpenChat: (conversationId: String, peerId: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val users by viewModel.activeUsers.collectAsStateWithLifecycle()
    val blockedIds by viewModel.blockedIds.collectAsStateWithLifecycle()
    var showNewChatSheet by remember { mutableStateOf(false) }

    val visibleConversations = remember(conversations, blockedIds) {
        conversations.filter { conv ->
            val peerId = conv.participantIds.firstOrNull { it != viewModel.currentUserId }
            peerId != null && peerId !in blockedIds
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vString(R.string.messages_title), fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewChatSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("new_chat_fab")
            ) {
                Icon(Icons.Filled.AddComment, contentDescription = vString(R.string.new_chat), tint = Color.White)
            }
        }
    ) { padding ->
        if (visibleConversations.isEmpty()) {
            VyoraEmptyState(
                icon = Icons.AutoMirrored.Filled.Chat,
                title = vString(R.string.messages_title),
                subtitle = vString(R.string.no_messages_yet),
                actionLabel = vString(R.string.new_chat),
                onAction = { showNewChatSheet = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visibleConversations, key = { it.id }) { conv ->
                    val peerId = conv.participantIds.firstOrNull { it != viewModel.currentUserId }.orEmpty()
                    val peer = users.firstOrNull { it.userId == peerId }
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(conv.id, peerId) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VyoraAvatar(
                                avatarUrl = peer?.avatarUrl.orEmpty(),
                                name = peer?.displayName ?: peerId.take(2),
                                size = 48.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = peer?.displayName ?: "@${peerId.take(8)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = conv.lastMessage.ifBlank { "Tap to chat" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { viewModel.deleteConversation(conv.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = vString(R.string.delete_chat), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewChatSheet) {
        ModalBottomSheet(onDismissRequest = { showNewChatSheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(vString(R.string.new_chat), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val candidates = users.filter { it.userId != viewModel.currentUserId && it.userId !in blockedIds }
                    items(candidates, key = { it.userId }) { user ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showNewChatSheet = false
                                    scope.launch {
                                        val convId = viewModel.openConversationWith(user.userId)
                                        if (convId != null) {
                                            onOpenChat(convId, user.userId)
                                        }
                                    }
                                }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                VyoraAvatar(avatarUrl = user.avatarUrl, name = user.displayName, size = 42.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text("@${user.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    conversationId: String,
    peerId: String,
    viewModel: VyoraAppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val users by viewModel.activeUsers.collectAsStateWithLifecycle()
    val blockedIds by viewModel.blockedIds.collectAsStateWithLifecycle()
    val messages by viewModel.observeMessages(conversationId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val peer = users.firstOrNull { it.userId == peerId }
    val participantIds = remember(viewModel.currentUserId, peerId) {
        listOf(viewModel.currentUserId, peerId).sorted()
    }
    var messageText by remember { mutableStateOf("") }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var audioStartMs by remember { mutableLongStateOf(0L) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.sendChatMessage(conversationId, participantIds, "image", mediaUri = uri)
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.sendChatMessage(conversationId, participantIds, "video", mediaUri = uri)
        }
    }
    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            viewModel.showToast("Microphone permission required for voice notes")
        }
    }

    LaunchedEffect(messages) {
        messages.forEach { msg ->
            if (msg.senderId != viewModel.currentUserId && viewModel.currentUserId !in msg.readBy) {
                viewModel.markMessageRead(conversationId, msg)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaRecorder?.release()
            } catch (_: Exception) {
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VyoraAvatar(avatarUrl = peer?.avatarUrl.orEmpty(), name = peer?.displayName ?: "U", size = 36.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(peer?.displayName ?: "@${peerId.take(8)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.toggleBlockUser(peerId)
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Block,
                            contentDescription = if (peerId in blockedIds) vString(R.string.unblock_user) else vString(R.string.block_user),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == viewModel.currentUserId
                    val isRead = msg.readBy.size >= 2
                    ChatBubble(
                        message = msg,
                        isMine = isMine,
                        isRead = isRead
                    )
                }
            }

            if (isRecordingAudio) {
                Surface(color = PulseMagenta.copy(alpha = 0.2f), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(vString(R.string.recording_voice), color = PulseMagenta, style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = {
                            try {
                                mediaRecorder?.stop()
                                mediaRecorder?.release()
                            } catch (_: Exception) {
                            }
                            mediaRecorder = null
                            isRecordingAudio = false
                            val f = audioFile
                            if (f != null && f.exists()) {
                                val duration = (System.currentTimeMillis() - audioStartMs).coerceAtLeast(1000L)
                                viewModel.sendChatMessage(
                                    conversationId = conversationId,
                                    participantIds = participantIds,
                                    type = "audio",
                                    mediaUri = Uri.fromFile(f),
                                    audioDurationMs = duration
                                )
                            }
                        }) {
                            Icon(Icons.Filled.Stop, contentDescription = "Send Voice", tint = PulseMagenta)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Filled.Image, contentDescription = "Photo", tint = NeonCyan)
                }
                IconButton(onClick = {
                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }) {
                    Icon(Icons.Filled.Videocam, contentDescription = "Video", tint = ElectricViolet)
                }
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text(vString(R.string.type_message)) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (messageText.isBlank()) {
                    IconButton(
                        onClick = {
                            val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (!hasMic) {
                                micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                val outFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                                val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    MediaRecorder(context)
                                } else {
                                    @Suppress("DEPRECATION")
                                    MediaRecorder()
                                }
                                try {
                                    rec.setAudioSource(MediaRecorder.AudioSource.MIC)
                                    rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                    rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                    rec.setOutputFile(outFile.absolutePath)
                                    rec.prepare()
                                    rec.start()
                                    mediaRecorder = rec
                                    audioFile = outFile
                                    audioStartMs = System.currentTimeMillis()
                                    isRecordingAudio = true
                                } catch (_: Exception) {
                                    rec.release()
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(PulseMagenta)
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = vString(R.string.voice_note_hold), tint = Color.White)
                    }
                } else {
                    IconButton(
                        onClick = {
                            viewModel.sendChatMessage(conversationId, participantIds, "text", text = messageText.trim())
                            messageText = ""
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("send_message_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = vString(R.string.send), tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    isMine: Boolean,
    isRead: Boolean
) {
    val context = LocalContext.current
    var isPlayingVoice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (message.type) {
                    "image" -> {
                        if (message.mediaUrl.isNotBlank()) {
                            AsyncImage(
                                model = message.mediaUrl,
                                contentDescription = "Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }
                    "video" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Videocam, contentDescription = null, tint = if (isMine) Color.White else NeonCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Video attachment", color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    "audio" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                if (!isPlayingVoice && message.mediaUrl.isNotBlank()) {
                                    isPlayingVoice = true
                                    try {
                                        MediaPlayer().apply {
                                            setDataSource(context, Uri.parse(message.mediaUrl))
                                            setOnPreparedListener { start() }
                                            setOnCompletionListener {
                                                isPlayingVoice = false
                                                release()
                                            }
                                            prepareAsync()
                                        }
                                    } catch (_: Exception) {
                                        isPlayingVoice = false
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isPlayingVoice) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = "Voice note",
                                tint = if (isMine) Color.White else NeonCyan
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Voice ${(message.audioDurationMs / 1000L).coerceAtLeast(1)}s",
                                color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (isMine) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isRead) Icons.Filled.DoneAll else Icons.Filled.Done,
                            contentDescription = if (isRead) "Read" else "Sent",
                            tint = if (isRead) NeonCyan else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
