package com.example.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.VideoPost
import com.example.media.formatCompactCount
import com.example.ui.UiState
import com.example.ui.VyoraAppViewModel
import com.example.ui.components.VyoraAvatar
import com.example.ui.components.VyoraEmptyState
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.vString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverSearchScreen(
    viewModel: VyoraAppViewModel,
    onBack: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onOpenSoundDetail: (String, String) -> Unit
) {
    val feedState by viewModel.feedVideos.collectAsStateWithLifecycle()
    val users by viewModel.activeUsers.collectAsStateWithLifecycle()
    val hashtags by viewModel.hashtags.collectAsStateWithLifecycle()
    val sounds by viewModel.sounds.collectAsStateWithLifecycle()
    val profile by viewModel.currentUserProfile.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(0) } // 0 = Most Viewed, 1 = Newest, 2 = Interests

    val allVideos = (feedState as? UiState.Success)?.data ?: emptyList()
    val userInterests = profile?.interests ?: emptyList()

    val filteredVideos = remember(allVideos, query, selectedFilter, userInterests) {
        val q = query.trim().lowercase()
        val matching = if (q.isEmpty()) allVideos else {
            allVideos.filter {
                it.caption.lowercase().contains(q) ||
                    it.creatorUsername.lowercase().contains(q) ||
                    it.hashtags.any { tag -> tag.contains(q.removePrefix("#")) }
            }
        }
        when (selectedFilter) {
            0 -> matching.sortedByDescending { it.viewsCount }
            2 -> matching.filter { v ->
                userInterests.isEmpty() || v.hashtags.any { h -> userInterests.any { i -> h.contains(i, ignoreCase = true) } }
            }
            else -> matching
        }
    }

    val filteredUsers = remember(users, query) {
        val q = query.trim().lowercase().removePrefix("@")
        if (q.isEmpty()) users.take(10) else users.filter {
            it.username.lowercase().contains(q) || it.displayName.lowercase().contains(q)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vString(R.string.discover_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(vString(R.string.search_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discover_search_input")
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedFilter == 0,
                        onClick = { selectedFilter = 0 },
                        label = { Text(vString(R.string.trending_videos)) }
                    )
                    FilterChip(
                        selected = selectedFilter == 1,
                        onClick = { selectedFilter = 1 },
                        label = { Text(vString(R.string.latest_videos)) }
                    )
                    FilterChip(
                        selected = selectedFilter == 2,
                        onClick = { selectedFilter = 2 },
                        label = { Text("Interests") }
                    )
                }
            }

            if (hashtags.isNotEmpty()) {
                item {
                    Text(vString(R.string.trending_hashtags), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(hashtags, key = { it.id }) { tag ->
                            AssistChip(
                                onClick = { query = "#${tag.tag}" },
                                leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("${tag.tag} (${tag.videosCount})") }
                            )
                        }
                    }
                }
            }

            if (filteredUsers.isNotEmpty()) {
                item {
                    Text(vString(R.string.suggestions_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(filteredUsers, key = { it.userId }) { user ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp,
                                modifier = Modifier
                                    .width(120.dp)
                                    .clickable { onOpenUserProfile(user.userId) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    VyoraAvatar(avatarUrl = user.avatarUrl, name = user.displayName, size = 52.dp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(user.displayName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("@${user.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            if (sounds.isNotEmpty()) {
                item {
                    Text(vString(R.string.sounds_library), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(sounds, key = { it.id }) { sound ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                modifier = Modifier.clickable { onOpenSoundDetail(sound.id, sound.title) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = NeonCyan)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(sound.title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                        Text("${sound.usesCount} videos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (filteredVideos.isEmpty()) {
                item {
                    VyoraEmptyState(
                        icon = Icons.Filled.Search,
                        title = vString(R.string.empty_feed_title),
                        subtitle = vString(R.string.search_hint)
                    )
                }
            } else {
                val chunked = filteredVideos.chunked(2)
                items(chunked) { rowVideos ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowVideos.forEach { video ->
                            DiscoverVideoTile(
                                video = video,
                                onClick = { onOpenUserProfile(video.userId) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowVideos.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoverVideoTile(video: VideoPost, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(0.68f)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.linearGradient(listOf(ElectricViolet.copy(alpha = 0.3f), Color(0xFF141422)))
            )
            .clickable { onClick() }
    ) {
        if (video.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = video.caption.ifBlank { "@${video.creatorUsername}" },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(formatCompactCount(video.viewsCount), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundDetailScreen(
    soundId: String,
    soundTitle: String,
    viewModel: VyoraAppViewModel,
    onBack: () -> Unit,
    onUseSound: () -> Unit,
    onOpenUserProfile: (String) -> Unit
) {
    val sounds by viewModel.sounds.collectAsStateWithLifecycle()
    val feedState by viewModel.feedVideos.collectAsStateWithLifecycle()
    val sound = sounds.firstOrNull { it.id == soundId }
    val matchingVideos = remember(feedState, soundId, soundTitle) {
        val list = (feedState as? UiState.Success)?.data ?: emptyList()
        list.filter { (soundId.isNotBlank() && it.soundId == soundId) || it.soundTitle == soundTitle }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(soundTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Brush.linearGradient(listOf(ElectricViolet, NeonCyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sound?.title ?: soundTitle, style = MaterialTheme.typography.titleLarge)
                            Text(sound?.artist ?: "VYORA Creator Audio", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("${matchingVideos.size} videos", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                        }
                        Button(onClick = onUseSound) {
                            Text("Use")
                        }
                    }
                }
            }

            val chunked = matchingVideos.chunked(2)
            items(chunked) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { video ->
                        DiscoverVideoTile(
                            video = video,
                            onClick = { onOpenUserProfile(video.userId) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
