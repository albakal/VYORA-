package com.example.ui.admin

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.UserProfile
import com.example.media.formatCompactCount
import com.example.ui.VyoraAppViewModel
import com.example.ui.components.VyoraAvatar
import com.example.ui.components.VyoraEmptyState
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PulseMagenta
import com.example.ui.theme.vString
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: VyoraAppViewModel,
    onBack: () -> Unit,
    onOpenUserProfile: (String) -> Unit
) {
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val users by viewModel.adminUsers.collectAsStateWithLifecycle()
    val videos by viewModel.adminVideos.collectAsStateWithLifecycle()
    val reports by viewModel.adminReports.collectAsStateWithLifecycle()
    val withdrawals by viewModel.adminWithdrawals.collectAsStateWithLifecycle()
    val adminRoles by viewModel.adminRoles.collectAsStateWithLifecycle()
    val liveStreams by viewModel.adminLiveStreams.collectAsStateWithLifecycle()
    val settings by viewModel.adminSettings.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var userSearch by remember { mutableStateOf("") }
    var grantTargetUser by remember { mutableStateOf<UserProfile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vString(R.string.admin_dashboard)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (!isAdmin) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                VyoraEmptyState(
                    icon = Icons.Filled.AdminPanelSettings,
                    title = "Admin Access Restricted",
                    subtitle = "Only verified administrators or assigned moderators can access the VYORA console."
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            val tabs = listOf(
                vString(R.string.admin_overview),
                vString(R.string.admin_users),
                vString(R.string.admin_reports),
                vString(R.string.admin_withdrawals),
                vString(R.string.admin_settings_tab)
            )
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label) }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    val activeVideos = videos.filter { it.status == "active" }
                    val totalViews = activeVideos.sumOf { it.viewsCount }
                    val pendingReports = reports.count { it.status == "pending" }
                    val pendingWithdrawals = withdrawals.count { it.status == "pending" }
                    val activeLives = liveStreams.filter { it.isActive }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AdminStatCard("Users", users.size.toString(), ElectricViolet, Modifier.weight(1f))
                                AdminStatCard("Active Videos", activeVideos.size.toString(), NeonCyan, Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AdminStatCard("Total Views", formatCompactCount(totalViews), EmeraldLive, Modifier.weight(1f))
                                AdminStatCard("Active LIVE", activeLives.size.toString(), PulseMagenta, Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AdminStatCard("Pending Reports", pendingReports.toString(), CrimsonError, Modifier.weight(1f))
                                AdminStatCard("Pending Payouts", pendingWithdrawals.toString(), AmberGold, Modifier.weight(1f))
                            }
                        }

                        if (activeLives.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Active Live Broadcasts",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(activeLives, key = { it.id }) { stream ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 2.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.LiveTv, contentDescription = null, tint = PulseMagenta)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(stream.title, style = MaterialTheme.typography.titleSmall)
                                            Text("@${stream.hostUsername} • ${stream.viewerCount} viewers", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Button(
                                            onClick = { viewModel.adminEndLiveStream(stream.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonError)
                                        ) {
                                            Text(vString(R.string.end_live_btn))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    val filteredUsers = remember(users, userSearch) {
                        val q = userSearch.trim().lowercase().removePrefix("@")
                        if (q.isEmpty()) users else users.filter {
                            it.username.lowercase().contains(q) ||
                                it.displayName.lowercase().contains(q) ||
                                it.userId.lowercase().contains(q)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = userSearch,
                                onValueChange = { userSearch = it },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                label = { Text("Search users by name or UID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        items(filteredUsers, key = { it.userId }) { user ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        VyoraAvatar(
                                            avatarUrl = user.avatarUrl,
                                            name = user.displayName,
                                            size = 44.dp,
                                            onClick = { onOpenUserProfile(user.userId) }
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(user.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("@${user.username} • ${user.followersCount} followers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (user.isBanned) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = CrimsonError.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = "BANNED",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = CrimsonError,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { grantTargetUser = user },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.Stars, contentDescription = null, modifier = Modifier.size(16.dp), tint = AmberGold)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Grant Pts")
                                        }
                                        if (user.userId != viewModel.currentUserId) {
                                            Button(
                                                onClick = { viewModel.adminSetUserBanned(user.userId, !user.isBanned) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (user.isBanned) EmeraldLive else CrimsonError
                                                ),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(if (user.isBanned) vString(R.string.unban_user) else vString(R.string.ban_user))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "Moderation Reports (${reports.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (reports.isEmpty()) {
                            item {
                                Text(
                                    text = "No moderation reports submitted yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(reports, key = { it.id }) { rep ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 2.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${rep.targetType.uppercase()} • ${rep.reason}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(rep.status.uppercase(), style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                                        }
                                        if (rep.details.isNotBlank()) {
                                            Text(rep.details, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Text("Target ID: ${rep.targetId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (rep.targetType == "video") {
                                                Button(
                                                    onClick = {
                                                        viewModel.adminRemoveVideo(rep.targetId)
                                                        viewModel.adminResolveReport(rep.id, "reviewed")
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonError)
                                                ) {
                                                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(vString(R.string.remove_content))
                                                }
                                            }
                                            OutlinedButton(onClick = { viewModel.adminResolveReport(rep.id, "reviewed") }) {
                                                Text(vString(R.string.approve))
                                            }
                                            TextButton(onClick = { viewModel.adminResolveReport(rep.id, "dismissed") }) {
                                                Text(vString(R.string.dismiss))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Recent Active Videos (${videos.count { it.status == "active" }})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(videos.filter { it.status == "active" }.take(25), key = { it.id }) { vid ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(vid.caption.ifBlank { vid.id }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("@${vid.creatorUsername} • ${formatCompactCount(vid.viewsCount)} views", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { viewModel.adminRemoveVideo(vid.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = vString(R.string.remove_content), tint = CrimsonError)
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (withdrawals.isEmpty()) {
                            item {
                                VyoraEmptyState(
                                    icon = Icons.Filled.AdminPanelSettings,
                                    title = vString(R.string.admin_withdrawals),
                                    subtitle = "No withdrawal requests found."
                                )
                            }
                        } else {
                            items(withdrawals, key = { it.id }) { req ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 2.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = String.format(Locale.US, "$%.2f (%s)", req.amountUsd, req.method),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = req.status.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = when (req.status) {
                                                    "approved" -> EmeraldLive
                                                    "rejected" -> CrimsonError
                                                    else -> AmberGold
                                                }
                                            )
                                        }
                                        Text("Account: ${req.accountDetails}", style = MaterialTheme.typography.bodyMedium)
                                        Text("User UID: ${req.userId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (req.status == "pending") {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = { viewModel.adminReviewWithdrawal(req, approve = true) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldLive),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(vString(R.string.approve))
                                                }
                                                Button(
                                                    onClick = { viewModel.adminReviewWithdrawal(req, approve = false) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonError),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(vString(R.string.reject))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    var pointValue by remember(settings.pointValueUsd) { mutableStateOf(settings.pointValueUsd.toString()) }
                    var minWithdraw by remember(settings.minWithdrawalUsd) { mutableStateOf(settings.minWithdrawalUsd.toString()) }
                    var maxDuration by remember(settings.maxVideoDurationSec) { mutableStateOf(settings.maxVideoDurationSec.toString()) }
                    var rewardsEnabled by remember(settings.rewardsEnabled) { mutableStateOf(settings.rewardsEnabled) }

                    var newAdminUid by remember { mutableStateOf("") }
                    var newAdminEmail by remember { mutableStateOf("") }

                    LaunchedEffect(settings) {
                        pointValue = settings.pointValueUsd.toString()
                        minWithdraw = settings.minWithdrawalUsd.toString()
                        maxDuration = settings.maxVideoDurationSec.toString()
                        rewardsEnabled = settings.rewardsEnabled
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Global Platform Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    OutlinedTextField(
                                        value = pointValue,
                                        onValueChange = { pointValue = it },
                                        label = { Text("Point Value (USD)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = minWithdraw,
                                        onValueChange = { minWithdraw = it },
                                        label = { Text("Minimum Withdrawal (USD)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = maxDuration,
                                        onValueChange = { maxDuration = it },
                                        label = { Text("Max Video Duration (sec)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Creator View Rewards Enabled", style = MaterialTheme.typography.bodyLarge)
                                        Switch(checked = rewardsEnabled, onCheckedChange = { rewardsEnabled = it })
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.adminSaveSettings(
                                                pointValueUsd = pointValue.toDoubleOrNull() ?: 0.01,
                                                minWithdrawalUsd = minWithdraw.toDoubleOrNull() ?: 10.0,
                                                rewardsEnabled = rewardsEnabled,
                                                maxVideoDurationSec = maxDuration.toLongOrNull() ?: 180L
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(vString(R.string.save_changes))
                                    }
                                }
                            }
                        }

                        item {
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Assign Admin / Moderator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    OutlinedTextField(
                                        value = newAdminUid,
                                        onValueChange = { newAdminUid = it },
                                        label = { Text("Target User UID") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = newAdminEmail,
                                        onValueChange = { newAdminEmail = it },
                                        label = { Text("User Email") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                if (newAdminUid.isNotBlank()) {
                                                    viewModel.adminAssignRole(newAdminUid.trim(), newAdminEmail.trim(), "moderator")
                                                    newAdminUid = ""
                                                    newAdminEmail = ""
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Add Moderator")
                                        }
                                        Button(
                                            onClick = {
                                                if (newAdminUid.isNotBlank()) {
                                                    viewModel.adminAssignRole(newAdminUid.trim(), newAdminEmail.trim(), "super_admin")
                                                    newAdminUid = ""
                                                    newAdminEmail = ""
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Add Admin")
                                        }
                                    }
                                }
                            }
                        }

                        items(adminRoles, key = { it.userId }) { role ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(role.email.ifBlank { role.userId }, style = MaterialTheme.typography.titleSmall)
                                        Text("${role.role.uppercase()} • ${role.userId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (role.userId != viewModel.currentUserId) {
                                        IconButton(onClick = { viewModel.adminRemoveRole(role.userId) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Remove Role", tint = CrimsonError)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    grantTargetUser?.let { target ->
        var pointsStr by remember { mutableStateOf("100") }
        var reasonStr by remember { mutableStateOf("Creator bonus") }
        AlertDialog(
            onDismissRequest = { grantTargetUser = null },
            title = { Text("Grant Points to @${target.username}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pointsStr,
                        onValueChange = { pointsStr = it },
                        label = { Text("Points") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reasonStr,
                        onValueChange = { reasonStr = it },
                        label = { Text("Reason") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val pts = pointsStr.toLongOrNull() ?: 0L
                    if (pts != 0L) {
                        viewModel.adminGrantPoints(target.userId, pts, reasonStr.ifBlank { "Admin adjustment" })
                    }
                    grantTargetUser = null
                }) {
                    Text(vString(R.string.approve))
                }
            },
            dismissButton = {
                TextButton(onClick = { grantTargetUser = null }) {
                    Text(vString(R.string.dismiss))
                }
            }
        )
    }
}

@Composable
private fun AdminStatCard(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}
