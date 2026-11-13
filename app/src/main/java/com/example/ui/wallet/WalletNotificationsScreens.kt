package com.example.ui.wallet

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.LedgerSyncState
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
fun WalletScreen(
    viewModel: VyoraAppViewModel,
    onBack: () -> Unit
) {
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val withdrawals by viewModel.myWithdrawals.collectAsStateWithLifecycle()
    val settings by viewModel.adminSettings.collectAsStateWithLifecycle()
    val ledgerSyncState by viewModel.ledgerSyncState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshAccountingSync()
    }

    val methods = listOf("PayPal", "Vodafone Cash", "Bank Transfer", "USDT")
    var amountInput by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf(methods.first()) }
    var accountDetails by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vString(R.string.wallet_earnings)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("wallet_back_button")) {
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(ElectricViolet, Color(0xFF4C1D95), NeonCyan.copy(alpha = 0.75f))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = vString(R.string.wallet_earnings),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Stars, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${wallet?.points ?: 0L} ${vString(R.string.points_balance)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = vString(R.string.usd_balance),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = String.format(Locale.US, "$%.2f", wallet?.balanceUsd ?: 0.0),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Total Earned",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = String.format(Locale.US, "$%.2f", wallet?.totalEarnedUsd ?: 0.0),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Text(
                            text = "1 pt = ${String.format(Locale.US, "$%.4f", settings.pointValueUsd)}  •  ${vString(R.string.min_withdrawal_note, settings.minWithdrawalUsd)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            item {
                val (badgeColor, badgeIcon, subtitleText) = when (val st = ledgerSyncState) {
                    is LedgerSyncState.Checking -> Triple(
                        NeonCyan,
                        Icons.Filled.CloudSync,
                        vString(R.string.supabase_ledger_checking)
                    )
                    is LedgerSyncState.Synced -> Triple(
                        EmeraldLive,
                        Icons.Filled.CloudDone,
                        vString(
                            R.string.supabase_ledger_synced,
                            st.summary.transactionsCount,
                            st.summary.videosCount
                        )
                    )
                    is LedgerSyncState.PendingSetup -> {
                        val detail = if (st.diagnostics.restReachable) {
                            "${vString(R.string.supabase_ledger_pending_config)} (REST OK)"
                        } else {
                            vString(R.string.supabase_ledger_pending_config)
                        }
                        Triple(AmberGold, Icons.Filled.CloudSync, detail)
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = badgeColor.copy(alpha = 0.16f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(badgeIcon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = vString(R.string.supabase_ledger_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { viewModel.refreshAccountingSync() },
                            modifier = Modifier.testTag("wallet_sync_supabase_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = vString(R.string.sync_now),
                                tint = badgeColor
                            )
                        }
                    }
                }
            }

            if (!settings.rewardsEnabled) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Creator view rewards are currently paused by the platform admin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = vString(R.string.request_withdrawal),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(methods) { method ->
                                FilterChip(
                                    selected = selectedMethod == method,
                                    onClick = { selectedMethod = method },
                                    label = { Text(method) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = amountInput,
                            onValueChange = { amountInput = it },
                            label = { Text("Amount (USD)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wallet_amount_input")
                        )

                        OutlinedTextField(
                            value = accountDetails,
                            onValueChange = { accountDetails = it },
                            label = { Text("$selectedMethod Account / Wallet / IBAN") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wallet_account_input")
                        )

                        Button(
                            onClick = {
                                val amt = amountInput.toDoubleOrNull() ?: 0.0
                                if (amt > 0 && accountDetails.isNotBlank()) {
                                    viewModel.requestWithdrawal(amt, selectedMethod, accountDetails.trim())
                                    amountInput = ""
                                    accountDetails = ""
                                } else {
                                    viewModel.showToast("Enter valid amount and payout details")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("wallet_withdraw_button")
                        ) {
                            Text(vString(R.string.request_withdrawal))
                        }
                    }
                }
            }

            if (withdrawals.isNotEmpty()) {
                item {
                    Text(
                        text = vString(R.string.admin_withdrawals),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(withdrawals, key = { it.id }) { req ->
                    val statusColor = when (req.status) {
                        "approved" -> EmeraldLive
                        "rejected" -> CrimsonError
                        else -> AmberGold
                    }
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${req.method} • ${req.accountDetails}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = String.format(Locale.US, "$%.2f (%d pts)", req.amountUsd, req.pointsSpent),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = statusColor.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = req.status.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = vString(R.string.transactions_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (transactions.isEmpty()) {
                item {
                    VyoraEmptyState(
                        icon = Icons.Filled.AccountBalanceWallet,
                        title = vString(R.string.transactions_history),
                        subtitle = "Publish videos and receive views or creator rewards to grow your balance."
                    )
                }
            } else {
                items(transactions, key = { it.id }) { tx ->
                    val isPositive = tx.amountDelta >= 0 && tx.pointsDelta >= 0
                    val deltaColor = if (isPositive) EmeraldLive else CrimsonError
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.description.ifBlank { tx.type },
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = tx.type,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val sign = if (isPositive) "+" else ""
                                Text(
                                    text = "$sign${tx.pointsDelta} pts",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = deltaColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = String.format(Locale.US, "%s$%.2f", sign, tx.amountDelta),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: VyoraAppViewModel,
    onBack: () -> Unit,
    onOpenUserProfile: (String) -> Unit
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vString(R.string.notifications_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("notifications_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (notifications.any { !it.isRead }) {
                        TextButton(
                            onClick = { viewModel.markNotificationsRead(notifications) },
                            modifier = Modifier.testTag("notifications_mark_all_read")
                        ) {
                            Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(vString(R.string.mark_all_read))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                VyoraEmptyState(
                    icon = Icons.Filled.Notifications,
                    title = vString(R.string.notifications_title),
                    subtitle = "Likes, comments, new followers, and live notifications will appear here."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications, key = { it.id }) { notif ->
                    val typeIcon = when (notif.type) {
                        "like" -> Icons.Filled.Favorite to PulseMagenta
                        "comment" -> Icons.AutoMirrored.Filled.Chat to NeonCyan
                        "follow", "follow_request" -> Icons.Filled.PersonAdd to ElectricViolet
                        "share" -> Icons.Filled.Share to AmberGold
                        "live" -> Icons.Filled.LiveTv to PulseMagenta
                        else -> Icons.Filled.CheckCircle to EmeraldLive
                    }
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (!notif.isRead) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        tonalElevation = if (!notif.isRead) 3.dp else 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (notif.actorId.isNotBlank()) {
                                    onOpenUserProfile(notif.actorId)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VyoraAvatar(
                                avatarUrl = notif.actorAvatarUrl,
                                name = notif.actorUsername,
                                size = 46.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "@${notif.actorUsername}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = notif.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = typeIcon.first,
                                contentDescription = notif.type,
                                tint = typeIcon.second,
                                modifier = Modifier.size(22.dp)
                            )
                            if (!notif.isRead) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(NeonCyan)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
