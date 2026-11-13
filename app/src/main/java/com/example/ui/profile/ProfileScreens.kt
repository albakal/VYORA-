package com.example.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.VideoPost
import com.example.media.formatCompactCount
import com.example.ui.UiState
import com.example.ui.VyoraAppViewModel
import com.example.ui.auth.signOutVyora
import com.example.ui.components.ApkExportControls
import com.example.ui.components.VyoraAvatar
import com.example.ui.components.VyoraEmptyState
import com.example.ui.discover.DiscoverVideoTile
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.vString
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    targetUserId: String,
    viewModel: VyoraAppViewModel,
    currentLanguage: String,
    currentThemeMode: String,
    onChangeLanguage: (String) -> Unit,
    onChangeTheme: (String) -> Unit,
    onOpenWallet: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenChat: (String, String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val isOwnProfile = targetUserId == viewModel.currentUserId
    val myProfile by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val activeUsers by viewModel.activeUsers.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
    val bookmarkedIds by viewModel.bookmarkedIds.collectAsStateWithLifecycle()
    val feedState by viewModel.feedVideos.collectAsStateWithLifecycle()

    val profile = if (isOwnProfile) myProfile else activeUsers.firstOrNull { it.userId == targetUserId }
    val allVideos = (feedState as? UiState.Success)?.data ?: emptyList()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Videos, 1 = Saved
    var showEditProfile by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var editCaptionVideo by remember { mutableStateOf<VideoPost?>(null) }
    var selectedVideoDetail by remember { mutableStateOf<VideoPost?>(null) }

    val displayedVideos = remember(allVideos, selectedTab, targetUserId, bookmarkedIds) {
        if (selectedTab == 0) {
            allVideos.filter { it.userId == targetUserId }
        } else {
            allVideos.filter { it.id in bookmarkedIds }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("@${profile?.username ?: "creator"}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isOwnProfile) {
                        IconButton(onClick = onOpenNotifications) {
                            Icon(Icons.Filled.Notifications, contentDescription = vString(R.string.notifications_title))
                        }
                        IconButton(
                            onClick = { showSettings = true },
                            modifier = Modifier.testTag("profile_settings_button")
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = vString(R.string.settings_title))
                        }
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    VyoraAvatar(
                        avatarUrl = profile?.avatarUrl.orEmpty(),
                        name = profile?.displayName ?: "VYORA",
                        size = 88.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = profile?.displayName ?: "Creator",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (!profile?.bio.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = profile?.bio.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProfileStat(formatCompactCount(profile?.followingCount ?: 0L), vString(R.string.following_label))
                        ProfileStat(formatCompactCount(profile?.followersCount ?: 0L), vString(R.string.followers_label))
                        ProfileStat(formatCompactCount(profile?.likesCount ?: 0L), vString(R.string.likes_label))
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isOwnProfile) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showEditProfile = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("edit_profile_button")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(vString(R.string.edit_profile))
                            }
                            Button(
                                onClick = onOpenWallet,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_wallet_button")
                            ) {
                                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(vString(R.string.wallet_earnings))
                            }
                        }
                        if (isAdmin) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onOpenAdmin,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_admin_dashboard_btn")
                            ) {
                                Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(vString(R.string.admin_dashboard))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ApkExportControls(
                            onStatusMessage = { msg -> viewModel.showToast(msg) },
                            saveButtonTag = "profile_save_apk_button",
                            shareButtonTag = "profile_share_apk_button"
                        )
                    } else if (profile != null) {
                        val isFollowing = profile.userId in followingIds
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { viewModel.followOrUnfollow(profile) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isFollowing) vString(R.string.following_label) else vString(R.string.follow))
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        val convId = viewModel.openConversationWith(profile.userId)
                                        if (convId != null) onOpenChat(convId, profile.userId)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(vString(R.string.nav_messages))
                            }
                        }
                    }
                }
            }

            item {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(vString(R.string.tab_videos)) }
                    )
                    if (isOwnProfile) {
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text(vString(R.string.tab_saved)) }
                        )
                    }
                }
            }

            if (displayedVideos.isEmpty()) {
                item {
                    VyoraEmptyState(
                        icon = Icons.Filled.VideoLibrary,
                        title = vString(R.string.empty_feed_title),
                        subtitle = vString(R.string.empty_feed_subtitle)
                    )
                }
            } else {
                val chunked = displayedVideos.chunked(2)
                items(chunked) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { video ->
                            Box(modifier = Modifier.weight(1f)) {
                                DiscoverVideoTile(video = video, onClick = { selectedVideoDetail = video })
                                if (isOwnProfile && selectedTab == 0) {
                                    var menuExpanded by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                                        IconButton(onClick = { menuExpanded = true }) {
                                            Icon(Icons.Filled.MoreVert, contentDescription = "Options", tint = androidx.compose.ui.graphics.Color.White)
                                        }
                                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                            DropdownMenuItem(
                                                text = { Text(vString(R.string.edit_caption)) },
                                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                                onClick = {
                                                    menuExpanded = false
                                                    editCaptionVideo = video
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(vString(R.string.delete_video)) },
                                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                                onClick = {
                                                    menuExpanded = false
                                                    viewModel.deleteVideo(video.id)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showEditProfile && profile != null) {
        EditProfileDialog(
            profile = profile,
            onDismiss = { showEditProfile = false },
            onSave = { username, displayName, bio, avatarUri, isPrivate ->
                viewModel.updateProfile(username, displayName, bio, avatarUri, profile.avatarUrl, isPrivate)
                showEditProfile = false
            }
        )
    }

    if (editCaptionVideo != null) {
        val target = editCaptionVideo!!
        var newCaption by remember(target.id) { mutableStateOf(target.caption) }
        AlertDialog(
            onDismissRequest = { editCaptionVideo = null },
            title = { Text(vString(R.string.edit_caption)) },
            text = {
                OutlinedTextField(value = newCaption, onValueChange = { newCaption = it }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateVideoCaption(target.id, newCaption)
                    editCaptionVideo = null
                }) { Text(vString(R.string.save_changes)) }
            },
            dismissButton = {
                TextButton(onClick = { editCaptionVideo = null }) { Text(vString(R.string.dismiss)) }
            }
        )
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(vString(R.string.settings_title), style = MaterialTheme.typography.titleLarge)

                Text(vString(R.string.language_label), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = currentLanguage == "ar",
                        onClick = {
                            onChangeLanguage("ar")
                            viewModel.updateUserPreferences("ar", currentThemeMode)
                        },
                        label = { Text("العربية (RTL)") }
                    )
                    FilterChip(
                        selected = currentLanguage == "en",
                        onClick = {
                            onChangeLanguage("en")
                            viewModel.updateUserPreferences("en", currentThemeMode)
                        },
                        label = { Text("English (LTR)") }
                    )
                }

                Text(vString(R.string.theme_label), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("dark" to R.string.theme_dark, "light" to R.string.theme_light, "system" to R.string.theme_system).forEach { (mode, res) ->
                        FilterChip(
                            selected = currentThemeMode == mode,
                            onClick = {
                                onChangeTheme(mode)
                                viewModel.updateUserPreferences(currentLanguage, mode)
                            },
                            label = { Text(vString(res)) }
                        )
                    }
                }

                Text(vString(R.string.export_apk_section), style = MaterialTheme.typography.titleMedium)
                ApkExportControls(
                    onStatusMessage = { msg -> viewModel.showToast(msg) },
                    saveButtonTag = "export_apk_button",
                    shareButtonTag = "share_apk_button"
                )

                Button(
                    onClick = {
                        showSettings = false
                        signOutVyora(credentialManager, scope)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(vString(R.string.sign_out))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    selectedVideoDetail?.let { vid ->
        AlertDialog(
            onDismissRequest = { selectedVideoDetail = null },
            title = { Text("@${vid.creatorUsername}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (vid.caption.isNotBlank()) {
                        Text(vid.caption, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        text = "${formatCompactCount(vid.viewsCount)} • ${formatCompactCount(vid.likesCount)} ${vString(R.string.likes_label)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonCyan
                    )
                    Text(
                        text = vid.soundTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                if (isOwnProfile) {
                    Button(onClick = {
                        selectedVideoDetail = null
                        editCaptionVideo = vid
                    }) {
                        Text(vString(R.string.edit_caption))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedVideoDetail = null }) {
                    Text(vString(R.string.dismiss))
                }
            }
        )
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = NeonCyan)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EditProfileDialog(
    profile: com.example.data.model.UserProfile,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Uri?, Boolean) -> Unit
) {
    var username by remember { mutableStateOf(profile.username) }
    var displayName by remember { mutableStateOf(profile.displayName) }
    var bio by remember { mutableStateOf(profile.bio) }
    var isPrivate by remember { mutableStateOf(profile.isPrivate) }
    var pickedAvatarUri by remember { mutableStateOf<Uri?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pickedAvatarUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(vString(R.string.edit_profile)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Text("Change Profile Photo")
                }
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display Name") }, singleLine = true)
                OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") }, maxLines = 3)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(vString(R.string.private_account))
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(username, displayName, bio, pickedAvatarUri, isPrivate) }) {
                Text(vString(R.string.save_changes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(vString(R.string.dismiss))
            }
        }
    )
}
