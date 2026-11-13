package com.example.ui.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.UiState
import com.example.ui.VyoraAppViewModel
import com.example.ui.components.VyoraAvatar
import com.example.ui.components.VyoraEmptyState
import com.example.ui.discover.DiscoverVideoTile
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.vString
import kotlinx.coroutines.launch

@Composable
fun FriendsScreen(
    viewModel: VyoraAppViewModel,
    onOpenUserProfile: (String) -> Unit,
    onOpenChat: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val users by viewModel.activeUsers.collectAsStateWithLifecycle()
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
    val blockedIds by viewModel.blockedIds.collectAsStateWithLifecycle()
    val followRequests by viewModel.followRequests.collectAsStateWithLifecycle()
    val feedState by viewModel.feedVideos.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }

    val filteredUsers = remember(users, searchQuery, blockedIds) {
        val q = searchQuery.trim().lowercase().removePrefix("@")
        users.filter { it.userId != viewModel.currentUserId && it.userId !in blockedIds }
            .filter { q.isEmpty() || it.username.lowercase().contains(q) || it.displayName.lowercase().contains(q) }
    }

    val friendsVideos = remember(feedState, followingIds, blockedIds) {
        val all = (feedState as? UiState.Success)?.data ?: emptyList()
        all.filter { it.userId in followingIds && it.userId !in blockedIds }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = vString(R.string.nav_friends),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(vString(R.string.search_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("friends_search_input")
            )
        }

        if (followRequests.isNotEmpty()) {
            item {
                Text(vString(R.string.follow_requests_title), style = MaterialTheme.typography.titleMedium, color = NeonCyan)
            }
            items(followRequests, key = { it.id }) { req ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VyoraAvatar(avatarUrl = req.requesterAvatarUrl, name = req.requesterUsername, size = 44.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("@${req.requesterUsername}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.respondToFollowRequest(req, true) }) {
                            Icon(Icons.Filled.Check, contentDescription = vString(R.string.accept), tint = NeonCyan)
                        }
                        IconButton(onClick = { viewModel.respondToFollowRequest(req, false) }) {
                            Icon(Icons.Filled.Close, contentDescription = vString(R.string.reject), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        item {
            Text(vString(R.string.suggestions_title), style = MaterialTheme.typography.titleMedium)
        }

        if (filteredUsers.isEmpty()) {
            item {
                VyoraEmptyState(
                    icon = Icons.Filled.Group,
                    title = vString(R.string.suggestions_title),
                    subtitle = "New creators joining VYORA will appear here."
                )
            }
        } else {
            items(filteredUsers, key = { it.userId }) { user ->
                val isFollowing = user.userId in followingIds
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenUserProfile(user.userId) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VyoraAvatar(avatarUrl = user.avatarUrl, name = user.displayName, size = 48.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "@${user.username} • ${user.followersCount} ${vString(R.string.followers_label)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val convId = viewModel.openConversationWith(user.userId)
                                if (convId != null) {
                                    onOpenChat(convId, user.userId)
                                }
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = vString(R.string.nav_messages), tint = ElectricViolet)
                        }
                        if (isFollowing) {
                            OutlinedButton(onClick = { viewModel.followOrUnfollow(user) }) {
                                Text(vString(R.string.following_label))
                            }
                        } else {
                            Button(onClick = { viewModel.followOrUnfollow(user) }) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(vString(R.string.follow))
                            }
                        }
                    }
                }
            }
        }

        if (friendsVideos.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(vString(R.string.friends_feed), style = MaterialTheme.typography.titleMedium)
            }
            val chunked = friendsVideos.chunked(2)
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
