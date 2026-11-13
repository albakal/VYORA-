package com.example.ui.studio

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.SoundTrack
import com.example.data.remote.UploadStatus
import com.example.ui.VyoraAppViewModel
import com.example.ui.components.SoundPickerBottomSheet
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PulseMagenta
import com.example.ui.theme.vString
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateStudioScreen(
    viewModel: VyoraAppViewModel,
    onClose: () -> Unit,
    onGoLive: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sounds by viewModel.sounds.collectAsStateWithLifecycle()
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermissions = result[Manifest.permission.CAMERA] == true &&
            result[Manifest.permission.RECORD_AUDIO] == true
    }

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedCoverUri by remember { mutableStateOf<Uri?>(null) }
    var selectedSound by remember { mutableStateOf<SoundTrack?>(null) }
    var showSoundPicker by remember { mutableStateOf(false) }

    var maxDurationSec by remember { mutableLongStateOf(15L) }
    var elapsedSec by remember { mutableLongStateOf(0L) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    val galleryVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
        }
    }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedCoverUri = uri
        }
    }

    // Countdown timer while recording
    LaunchedEffect(isRecording, isPaused, maxDurationSec) {
        while (isRecording && !isPaused) {
            delay(1000L)
            elapsedSec += 1L
            if (elapsedSec >= maxDurationSec) {
                activeRecording?.stop()
                isRecording = false
                isPaused = false
            }
        }
    }

    if (selectedVideoUri != null) {
        VideoPublishEditor(
            videoUri = selectedVideoUri!!,
            coverUri = selectedCoverUri,
            selectedSound = selectedSound,
            durationSec = elapsedSec.coerceAtLeast(5L),
            uploadState = uploadState,
            onPickCover = {
                coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onOpenSounds = { showSoundPicker = true },
            onDiscard = {
                viewModel.clearUploadState()
                selectedVideoUri = null
                selectedCoverUri = null
                elapsedSec = 0L
            },
            onPublish = { caption ->
                viewModel.uploadAndPublishVideo(
                    context = context,
                    videoUri = selectedVideoUri!!,
                    customCoverUri = selectedCoverUri,
                    caption = caption,
                    soundId = selectedSound?.id.orEmpty(),
                    soundTitle = selectedSound?.title ?: context.getString(R.string.studio_original_sound),
                    durationSec = elapsedSec.coerceAtLeast(5L),
                    onPublished = {
                        viewModel.clearUploadState()
                        onClose()
                    }
                )
            }
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            if (!hasPermissions) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Filled.Videocam, contentDescription = null, tint = ElectricViolet, modifier = Modifier.size(64.dp))
                    Text(
                        text = vString(R.string.permission_camera_audio),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Button(
                        onClick = {
                            permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                        },
                        modifier = Modifier.testTag("grant_camera_permissions_btn")
                    ) {
                        Text(vString(R.string.grant_permissions))
                    }
                    OutlinedButton(onClick = {
                        galleryVideoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(vString(R.string.studio_pick_gallery))
                    }
                }
            } else {
                val previewView = remember { PreviewView(context) }

                LaunchedEffect(isFrontCamera) {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val recorder = Recorder.Builder()
                                .setQualitySelector(QualitySelector.from(Quality.HD))
                                .build()
                            val vc = VideoCapture.withOutput(recorder)
                            videoCapture = vc
                            val selector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, vc)
                        } catch (_: Exception) {
                            // Camera unavailable in virtualized container; gallery picker still available
                        }
                    }, ContextCompat.getMainExecutor(context))
                }

                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // Top Bar: Close, Sound Pill, Flip Camera
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.clickable { showSoundPicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedSound?.title ?: vString(R.string.studio_select_sound),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }

                    IconButton(onClick = { isFrontCamera = !isFrontCamera }) {
                        Icon(Icons.Filled.Cameraswitch, contentDescription = vString(R.string.studio_flip_camera), tint = Color.White)
                    }
                }

                // Recording Timer Bar
                if (isRecording) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 68.dp, start = 24.dp, end = 24.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { (elapsedSec.toFloat() / maxDurationSec.toFloat()).coerceIn(0f, 1f) },
                            color = PulseMagenta,
                            trackColor = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${elapsedSec}s / ${maxDurationSec}s",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                // Bottom Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isRecording) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15L to R.string.studio_duration_15, 60L to R.string.studio_duration_60, 180L to R.string.studio_duration_180).forEach { (sec, labelRes) ->
                                FilterChip(
                                    selected = maxDurationSec == sec,
                                    onClick = { maxDurationSec = sec },
                                    label = { Text(vString(labelRes)) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gallery Upload
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    galleryVideoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                }
                                .testTag("pick_gallery_video_btn")
                        ) {
                            Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                            Text(vString(R.string.studio_pick_gallery), style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }

                        // Record / Pause / Stop
                        if (!isRecording) {
                            Surface(
                                shape = CircleShape,
                                color = PulseMagenta,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clickable {
                                        val vc = videoCapture ?: return@clickable
                                        val name = "VYORA_${System.currentTimeMillis()}.mp4"
                                        val cv = ContentValues().apply {
                                            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                                            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VYORA")
                                            }
                                        }
                                        val outputOptions = MediaStoreOutputOptions.Builder(
                                            context.contentResolver,
                                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                        ).setContentValues(cv).build()

                                        @SuppressLint("MissingPermission")
                                        val rec = vc.output.prepareRecording(context, outputOptions)
                                            .withAudioEnabled()
                                            .start(ContextCompat.getMainExecutor(context)) { event ->
                                                when (event) {
                                                    is VideoRecordEvent.Start -> {
                                                        isRecording = true
                                                        isPaused = false
                                                        elapsedSec = 0L
                                                    }
                                                    is VideoRecordEvent.Finalize -> {
                                                        isRecording = false
                                                        isPaused = false
                                                        if (!event.hasError()) {
                                                            selectedVideoUri = event.outputResults.outputUri
                                                        }
                                                    }
                                                }
                                            }
                                        activeRecording = rec
                                    }
                                    .testTag("start_record_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.FiberManualRecord, contentDescription = "Record", tint = Color.White, modifier = Modifier.size(36.dp))
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (isPaused) {
                                            activeRecording?.resume()
                                            isPaused = false
                                        } else {
                                            activeRecording?.pause()
                                            isPaused = true
                                        }
                                    },
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(ElectricViolet)
                                ) {
                                    Icon(
                                        imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                                IconButton(
                                    onClick = { activeRecording?.stop() },
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(PulseMagenta)
                                ) {
                                    Icon(Icons.Filled.Stop, contentDescription = vString(R.string.studio_stop_rec), tint = Color.White, modifier = Modifier.size(32.dp))
                                }
                            }
                        }

                        // Go LIVE
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { onGoLive() }
                                .testTag("studio_go_live_btn")
                        ) {
                            Icon(Icons.Filled.LiveTv, contentDescription = null, tint = PulseMagenta, modifier = Modifier.size(28.dp))
                            Text(vString(R.string.tab_live), style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                }
            }

            // Always show Close button when permissions are denied
            if (!hasPermissions) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    if (showSoundPicker) {
        SoundPickerBottomSheet(
            sounds = sounds,
            onSelectSound = { selectedSound = it },
            onCreateSound = { title, artist, dur -> viewModel.createSoundTrack(title, artist, null, dur) },
            onDismiss = { showSoundPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPublishEditor(
    videoUri: Uri,
    coverUri: Uri?,
    selectedSound: SoundTrack?,
    durationSec: Long,
    uploadState: UploadStatus?,
    onPickCover: () -> Unit,
    onOpenSounds: () -> Unit,
    onDiscard: () -> Unit,
    onPublish: (String) -> Unit
) {
    val context = LocalContext.current
    var caption by remember { mutableStateOf("") }
    var videoVolume by remember { mutableFloatStateOf(1.0f) }

    val previewPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(videoVolume) {
        previewPlayer.volume = videoVolume
    }

    DisposableEffect(previewPlayer) {
        onDispose { previewPlayer.release() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vString(R.string.studio_publish)) },
                navigationIcon = {
                    IconButton(onClick = onDiscard) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = true
                            player = previewPlayer
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text(vString(R.string.studio_caption_hint)) },
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("publish_caption_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onPickCover,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(vString(R.string.studio_pick_cover))
                }
                OutlinedButton(
                    onClick = onOpenSounds,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(selectedSound?.title ?: vString(R.string.studio_select_sound), maxLines = 1)
                }
            }

            if (coverUri != null) {
                AsyncImage(
                    model = coverUri,
                    contentDescription = "Selected Cover",
                    modifier = Modifier
                        .size(84.dp)
                        .clip(MaterialTheme.shapes.small)
                )
            }

            Column {
                Text("Audio Volume (${(videoVolume * 100).toInt()}%)", style = MaterialTheme.typography.bodyMedium)
                Slider(value = videoVolume, onValueChange = { videoVolume = it })
            }

            when (uploadState) {
                is UploadStatus.Progress -> {
                    LinearProgressIndicator(
                        progress = { uploadState.percent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(vString(R.string.studio_uploading, uploadState.percent), style = MaterialTheme.typography.labelLarge)
                }
                is UploadStatus.Failed -> {
                    Text(uploadState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                else -> {}
            }

            Button(
                onClick = { onPublish(caption) },
                enabled = uploadState !is UploadStatus.Progress,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("publish_video_button")
            ) {
                Text(vString(R.string.studio_publish), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
