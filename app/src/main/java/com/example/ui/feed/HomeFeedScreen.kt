package com.example.ui.feed

import android.content.Intent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.VideoComment
import com.example.data.model.VideoPost
import com.example.media.VyoraPlayerPool
import com.example.media.formatCompactCount
import com.example.ui.UiState
import com.example.ui.VyoraAppViewModel
import com.example.ui.components.ReportDialog
import com.example.ui.components.VyoraAvatar
import com.example.ui.components.VyoraEmptyState
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PulseMagenta
import com.example.ui.theme.vString
import kotlinx.coroutines.delay

@Composable
fun HomeFeedScreen(
    viewModel: VyoraAppViewModel,
    isScreenActive: Boolean,
    onOpenSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenLiveLobby: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onOpenSoundDetail: (String, String) -> Unit,
    onOpenCreate: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val feedState by viewModel.feedVideos.collectAsStateWithLifecycle()
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
    val blockedIds by viewModel.blockedIds.collectAsStateWithLifecycle()
    val bookmarkedIds by viewModel.bookmarkedIds.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val activeUsers by viewModel.activeUsers.collectAsStateWithLifecycle()

    val unreadCount = remember(notifications) { notifications.count { !it.isRead } }
    var selectedTab by remember { mutableStateOf(0) } // 0 = For You, 1 = Following
    val playerPool = remember(context) { VyoraPlayerPool(context) }

    var commentTargetVideo by remember { mutableStateOf<VideoPost?>(null) }
    var reportTargetVideo by remember { mutableStateOf<VideoPost?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                playerPool.pauseAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerPool.releaseAll()
        }
    }

    LaunchedEffect(isScreenActive) {
        if (!isScreenActive) {
            playerPool.pauseAll()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = feedState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricViolet)
                }
            }
            is UiState.Error -> {
                VyoraEmptyState(
                    icon = Icons.Filled.Refresh,
                    title = "Connection Error",
                    subtitle = state.message,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is UiState.Success -> {
                val visibleVideos = remember(state.data, selectedTab, followingIds, blockedIds) {
                    val unblocked = state.data.filter { it.userId !in blockedIds }
                    if (selectedTab == 1) unblocked.filter { it.userId in followingIds } else unblocked
                }

                if (visibleVideos.isEmpty()) {
                    VyoraEmptyState(
                        icon = Icons.Filled.Videocam,
                        title = vString(R.string.empty_feed_title),
                        subtitle = vString(R.string.empty_feed_subtitle),
                        actionLabel = vString(R.string.studio_record_video),
                        onAction = onOpenCreate,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val pagerState = rememberPagerState(pageCount = { visibleVideos.size })

                    LaunchedEffect(pagerState.currentPage, visibleVideos, isScreenActive) {
                        val currentVideo = visibleVideos.getOrNull(pagerState.currentPage)
                        if (currentVideo != null && isScreenActive) {
                            playerPool.pauseAllExcept(currentVideo.id)
                            viewModel.recordView(currentVideo.id)
                            val nextVideo = visibleVideos.getOrNull(pagerState.currentPage + 1)
                            if (nextVideo != null) {
                                playerPool.preloadNext(nextVideo.id, nextVideo.videoUrl)
                            }
                        } else {
                            playerPool.pauseAll()
                        }
                    }

                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("video_vertical_pager"),
                        beyondViewportPageCount = 1
                    ) { pageIndex ->
                        val video = visibleVideos[pageIndex]
                        val isPageFocused = isScreenActive && pagerState.currentPage == pageIndex
                        val isBookmarked = video.id in bookmarkedIds
                        val isFollowing = video.userId in followingIds

                        VideoFeedItem(
                            video = video,
                            playerPool = playerPool,
                            isFocused = isPageFocused,
                            isOwnVideo = video.userId == viewModel.currentUserId,
                            isFollowing = isFollowing,
                            isBookmarked = isBookmarked,
                            checkInitiallyLiked = { viewModel.isVideoLiked(video.id) },
                            onToggleLike = { viewModel.toggleLike(video) },
                            onToggleBookmark = { viewModel.toggleBookmark(video.id) },
                            onOpenComments = { commentTargetVideo = video },
                            onShare = {
                                viewModel.recordShare(video)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${video.caption}\n${video.videoUrl}")
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share via VYORA"))
                            },
                            onFollowCreator = {
                                val target = activeUsers.firstOrNull { it.userId == video.userId }
                                if (target != null) {
                                    viewModel.followOrUnfollow(target)
                                }
                            },
                            onOpenProfile = { onOpenUserProfile(video.userId) },
                            onOpenSound = { onOpenSoundDetail(video.soundId, video.soundTitle) },
                            onReport = { reportTargetVideo = video }
                        )
                    }
                }
            }
        }

        // Top Floating Bar (LIVE | Following | For You + Notifications + Search)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onOpenLiveLobby,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("top_live_button")
            ) {
                Icon(Icons.Filled.LiveTv, contentDescription = vString(R.string.tab_live), tint = PulseMagenta)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FeedTopTab(
                    label = vString(R.string.tab_following),
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    tag = "tab_following"
                )
                FeedTopTab(
                    label = vString(R.string.tab_for_you),
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    tag = "tab_for_you"
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("top_notifications_button")
                ) {
                    BadgedBox(badge = {
                        if (unreadCount > 0) {
                            Badge(containerColor = PulseMagenta) { Text("$unreadCount", color = Color.White) }
                        }
                    }) {
                        Icon(Icons.Filled.Notifications, contentDescription = vString(R.string.notifications_title), tint = Color.White)
                    }
                }
                IconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("top_search_button")
                ) {
                    Icon(Icons.Filled.Search, contentDescription = vString(R.string.search_hint), tint = Color.White)
                }
            }
        }
    }

    if (commentTargetVideo != null) {
        CommentsBottomSheet(
            video = commentTargetVideo!!,
            viewModel = viewModel,
            onDismiss = { commentTargetVideo = null }
        )
    }

    if (reportTargetVideo != null) {
        val target = reportTargetVideo!!
        ReportDialog(
            onDismiss = { reportTargetVideo = null },
            onSubmit = { reason, details ->
                viewModel.submitReport("video", target.id, reason, details)
                reportTargetVideo = null
            }
        )
    }
}

@Composable
private fun FeedTopTab(label: String, selected: Boolean, onClick: () -> Unit, tag: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .testTag(tag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.65f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) NeonCyan else Color.Transparent)
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoFeedItem(
    video: VideoPost,
    playerPool: VyoraPlayerPool,
    isFocused: Boolean,
    isOwnVideo: Boolean,
    isFollowing: Boolean,
    isBookmarked: Boolean,
    checkInitiallyLiked: suspend () -> Boolean,
    onToggleLike: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onFollowCreator: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSound: () -> Unit,
    onReport: () -> Unit
) {
    val player = remember(video.id, video.videoUrl) {
        playerPool.getOrPreparePlayer(video.id, video.videoUrl)
    }
    var isManuallyPaused by remember(video.id) { mutableStateOf(false) }
    var isFirstFrameRendered by remember(video.id) { mutableStateOf(false) }
    var isLiked by remember(video.id) { mutableStateOf(false) }
    var showHeartBurst by remember { mutableStateOf(false) }

    LaunchedEffect(video.id) {
        isLiked = checkInitiallyLiked()
    }

    LaunchedEffect(isFocused, isManuallyPaused) {
        player.playWhenReady = isFocused && !isManuallyPaused
        if (!isFocused) {
            player.pause()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isFirstFrameRendered = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(showHeartBurst) {
        if (showHeartBurst) {
            delay(750)
            showHeartBurst = false
        }
    }

    val discRotation by rememberInfiniteTransition(label = "disc").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Restart),
        label = "disc_angle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(video.id) {
                detectTapGestures(
                    onTap = { isManuallyPaused = !isManuallyPaused },
                    onDoubleTap = {
                        showHeartBurst = true
                        if (!isLiked) {
                            isLiked = true
                            onToggleLike()
                        }
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.player = player
                }
            },
            update = { view ->
                if (view.player !== player) {
                    view.player = player
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Cover thumbnail shown until first video frame renders (prevents black screen!)
        if (!isFirstFrameRendered && video.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Bottom cinema gradient scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        // Play / Pause Indicator
        AnimatedVisibility(
            visible = isManuallyPaused,
            enter = fadeIn() + scaleIn(spring()),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Paused",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        // Double-Tap Heart Burst
        AnimatedVisibility(
            visible = showHeartBurst,
            enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = PulseMagenta,
                modifier = Modifier.size(104.dp)
            )
        }

        // Right/End Interaction Rail
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(contentAlignment = Alignment.BottomCenter) {
                VyoraAvatar(
                    avatarUrl = video.creatorAvatarUrl,
                    name = video.creatorUsername,
                    size = 50.dp,
                    onClick = onOpenProfile
                )
                if (!isOwnVideo && !isFollowing) {
                    Surface(
                        shape = CircleShape,
                        color = PulseMagenta,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { onFollowCreator() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, contentDescription = vString(R.string.follow), tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            ActionRailItem(
                icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                tint = if (isLiked) PulseMagenta else Color.White,
                count = formatCompactCount(video.likesCount),
                onClick = {
                    isLiked = !isLiked
                    onToggleLike()
                },
                tag = "like_button_${video.id}"
            )

            ActionRailItem(
                icon = Icons.AutoMirrored.Filled.Chat,
                tint = Color.White,
                count = formatCompactCount(video.commentsCount),
                onClick = onOpenComments,
                tag = "comment_button_${video.id}"
            )

            ActionRailItem(
                icon = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                tint = if (isBookmarked) NeonCyan else Color.White,
                count = formatCompactCount(video.bookmarksCount),
                onClick = onToggleBookmark,
                tag = "bookmark_button_${video.id}"
            )

            ActionRailItem(
                icon = Icons.Filled.Share,
                tint = Color.White,
                count = formatCompactCount(video.sharesCount),
                onClick = onShare,
                tag = "share_button_${video.id}"
            )

            IconButton(
                onClick = onReport,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = vString(R.string.report), tint = Color.White)
            }

            // Rotating Music Disc
            Surface(
                shape = CircleShape,
                color = Color(0xFF1E1E2E),
                modifier = Modifier
                    .size(44.dp)
                    .rotate(if (isFocused && !isManuallyPaused) discRotation else 0f)
                    .clickable { onOpenSound() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, contentDescription = video.soundTitle, tint = NeonCyan, modifier = Modifier.size(22.dp))
                }
            }
        }

        // Bottom Left/Start Metadata Overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 86.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "@${video.creatorUsername}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onOpenProfile() }
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(formatCompactCount(video.viewsCount), style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }

            if (video.caption.isNotBlank()) {
                Text(
                    text = video.caption,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.94f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onOpenSound() }
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = video.soundTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ActionRailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    count: String,
    onClick: () -> Unit,
    tag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = count, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsBottomSheet(
    video: VideoPost,
    viewModel: VyoraAppViewModel,
    onDismiss: () -> Unit
) {
    val comments by viewModel.observeComments(video.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var commentText by remember { mutableStateOf("") }
    var reportComment by remember { mutableStateOf<VideoComment?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = vString(R.string.comments_title, comments.size),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(comments, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        VyoraAvatar(avatarUrl = item.avatarUrl, name = item.username, size = 36.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("@${item.username}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.text, style = MaterialTheme.typography.bodyLarge)
                        }
                        IconButton(onClick = { reportComment = item }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = vString(R.string.report), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text(vString(R.string.comment_hint)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            viewModel.addComment(video, commentText)
                            commentText = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = vString(R.string.send), tint = Color.White)
                }
            }
        }
    }

    if (reportComment != null) {
        ReportDialog(
            onDismiss = { reportComment = null },
            onSubmit = { reason, details ->
                viewModel.submitReport("comment", reportComment!!.id, reason, details)
                reportComment = null
            }
        )
    }
}
