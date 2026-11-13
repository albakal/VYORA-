package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.remote.CloudFunctionsRepository
import com.example.data.remote.StorageMediaRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.LiveRepository
import com.example.data.repository.NotificationRepository
import com.example.data.repository.UserRepository
import com.example.data.repository.VideoRepository
import com.example.data.repository.WalletAdminRepository
import com.example.media.LocalNotificationHelper
import com.example.ui.VyoraAppViewModel
import com.example.ui.admin.AdminDashboardScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.authStateFlow
import com.example.ui.discover.DiscoverSearchScreen
import com.example.ui.discover.SoundDetailScreen
import com.example.ui.feed.HomeFeedScreen
import com.example.ui.friends.FriendsScreen
import com.example.ui.live.LiveLobbyScreen
import com.example.ui.live.LiveRoomScreen
import com.example.ui.messages.ChatRoomScreen
import com.example.ui.messages.ConversationsListScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.studio.CreateStudioScreen
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PulseMagenta
import com.example.ui.theme.VyoraTheme
import com.example.ui.theme.vString
import com.example.ui.wallet.NotificationsScreen
import com.example.ui.wallet.WalletScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

object VyoraRoutes {
    const val HOME = "home"
    const val FRIENDS = "friends"
    const val MESSAGES = "messages"
    const val PROFILE = "profile"
    const val CREATE = "create"
    const val DISCOVER = "discover"
    const val NOTIFICATIONS = "notifications"
    const val WALLET = "wallet"
    const val ADMIN = "admin"
    const val LIVE_LOBBY = "live_lobby"
    const val LIVE_ROOM = "live_room/{streamId}/{isHost}"
    const val CHAT = "chat/{convId}/{peerId}"
    const val USER_PROFILE = "user_profile/{userId}"
    const val SOUND = "sound?id={soundId}&title={soundTitle}"

    fun liveRoom(streamId: String, isHost: Boolean) = "live_room/${Uri.encode(streamId)}/$isHost"
    fun chat(convId: String, peerId: String) = "chat/${Uri.encode(convId)}/${Uri.encode(peerId)}"
    fun userProfile(userId: String) = "user_profile/${Uri.encode(userId)}"
    fun sound(soundId: String, soundTitle: String) =
        "sound?id=${Uri.encode(soundId)}&title=${Uri.encode(soundTitle)}"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LocalNotificationHelper.ensureChannel(this)

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val prefsRepo = remember(context) { AppPreferencesRepository(context.applicationContext) }
            val prefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(initialValue = AppPreferences())
            val currentUser by Firebase.auth.authStateFlow()
                .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

            val credentialManager = remember(context) { CredentialManager.create(context) }

            LaunchedEffect(Unit) {
                attemptAutoSignIn(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = {},
                    onUnauthenticated = {},
                    scope = scope
                )
            }

            VyoraTheme(themeMode = prefs.themeMode, language = prefs.language) {
                val user = currentUser
                if (user == null) {
                    AuthScreen(
                        currentLanguage = prefs.language,
                        onToggleLanguage = {
                            val nextLang = if (prefs.language == "ar") "en" else "ar"
                            scope.launch { prefsRepo.setLanguage(nextLang) }
                        },
                        onAuthSuccess = {}
                    )
                } else {
                    key(user.uid) {
                        val vm: VyoraAppViewModel = viewModel(
                            key = user.uid,
                            factory = viewModelFactory {
                                initializer {
                                    val appCtx = context.applicationContext
                                    VyoraAppViewModel(
                                        currentUserId = user.uid,
                                        userRepo = UserRepository(appCtx),
                                        videoRepo = VideoRepository(appCtx),
                                        chatRepo = ChatRepository(appCtx),
                                        liveRepo = LiveRepository(appCtx),
                                        notifRepo = NotificationRepository(appCtx),
                                        walletAdminRepo = WalletAdminRepository(appCtx),
                                        storageRepo = StorageMediaRepository(),
                                        cloudFunctionsRepo = CloudFunctionsRepository()
                                    )
                                }
                            }
                        )

                        VyoraAuthenticatedApp(
                            viewModel = vm,
                            prefs = prefs,
                            onChangeLanguage = { lang ->
                                scope.launch {
                                    prefsRepo.setLanguage(lang)
                                    vm.updateUserPreferences(lang, prefs.themeMode)
                                }
                            },
                            onChangeTheme = { theme ->
                                scope.launch {
                                    prefsRepo.setThemeMode(theme)
                                    vm.updateUserPreferences(prefs.language, theme)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VyoraAuthenticatedApp(
    viewModel: VyoraAppViewModel,
    prefs: AppPreferences,
    onChangeLanguage: (String) -> Unit,
    onChangeTheme: (String) -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val snackbarHostState = remember { SnackbarHostState() }
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    // Request POST_NOTIFICATIONS once on Android 13+ when entering authenticated shell
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional notification alerts */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(toastMessage) {
        val msg = toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    // Trigger local notifications for newly arriving unread activity items
    var seenNotifIds by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(notifications) {
        val currentIds = notifications.map { it.id }.toSet()
        val previous = seenNotifIds
        if (previous == null) {
            seenNotifIds = currentIds
        } else {
            val newlyArrived = notifications.filter { it.id !in previous && !it.isRead }
            newlyArrived.take(2).forEach { notif ->
                LocalNotificationHelper.showNotification(
                    context = context,
                    id = notif.id.hashCode(),
                    title = "@${notif.actorUsername}",
                    body = notif.message
                )
            }
            seenNotifIds = previous + currentIds
        }
    }

    val topLevelRoutes = setOf(
        VyoraRoutes.HOME,
        VyoraRoutes.FRIENDS,
        VyoraRoutes.MESSAGES,
        VyoraRoutes.PROFILE
    )
    val showBottomBar = currentRoute in topLevelRoutes
    val isDarkFeedBar = currentRoute == VyoraRoutes.HOME

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                VyoraBottomNavBar(
                    currentRoute = currentRoute,
                    isOverlayDark = isDarkFeedBar,
                    onSelectTab = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onCreateClick = {
                        navController.navigate(VyoraRoutes.CREATE)
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            NavHost(
                navController = navController,
                startDestination = VyoraRoutes.HOME
            ) {
                composable(VyoraRoutes.HOME) {
                    HomeFeedScreen(
                        viewModel = viewModel,
                        isScreenActive = currentRoute == VyoraRoutes.HOME,
                        onOpenSearch = { navController.navigate(VyoraRoutes.DISCOVER) },
                        onOpenNotifications = { navController.navigate(VyoraRoutes.NOTIFICATIONS) },
                        onOpenLiveLobby = { navController.navigate(VyoraRoutes.LIVE_LOBBY) },
                        onOpenUserProfile = { uid -> navController.navigate(VyoraRoutes.userProfile(uid)) },
                        onOpenSoundDetail = { id, title -> navController.navigate(VyoraRoutes.sound(id, title)) },
                        onOpenCreate = { navController.navigate(VyoraRoutes.CREATE) }
                    )
                }

                composable(VyoraRoutes.FRIENDS) {
                    FriendsScreen(
                        viewModel = viewModel,
                        onOpenUserProfile = { uid -> navController.navigate(VyoraRoutes.userProfile(uid)) },
                        onOpenChat = { convId, peerId -> navController.navigate(VyoraRoutes.chat(convId, peerId)) }
                    )
                }

                composable(VyoraRoutes.MESSAGES) {
                    ConversationsListScreen(
                        viewModel = viewModel,
                        onOpenChat = { convId, peerId -> navController.navigate(VyoraRoutes.chat(convId, peerId)) }
                    )
                }

                composable(VyoraRoutes.PROFILE) {
                    ProfileScreen(
                        targetUserId = viewModel.currentUserId,
                        viewModel = viewModel,
                        currentLanguage = prefs.language,
                        currentThemeMode = prefs.themeMode,
                        onChangeLanguage = onChangeLanguage,
                        onChangeTheme = onChangeTheme,
                        onOpenWallet = { navController.navigate(VyoraRoutes.WALLET) },
                        onOpenAdmin = { navController.navigate(VyoraRoutes.ADMIN) },
                        onOpenNotifications = { navController.navigate(VyoraRoutes.NOTIFICATIONS) },
                        onOpenChat = { convId, peerId -> navController.navigate(VyoraRoutes.chat(convId, peerId)) }
                    )
                }

                composable(VyoraRoutes.CREATE) {
                    CreateStudioScreen(
                        viewModel = viewModel,
                        onClose = { navController.popBackStack() },
                        onGoLive = {
                            navController.popBackStack()
                            navController.navigate(VyoraRoutes.LIVE_LOBBY)
                        }
                    )
                }

                composable(VyoraRoutes.DISCOVER) {
                    DiscoverSearchScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenUserProfile = { uid -> navController.navigate(VyoraRoutes.userProfile(uid)) },
                        onOpenSoundDetail = { id, title -> navController.navigate(VyoraRoutes.sound(id, title)) }
                    )
                }

                composable(VyoraRoutes.NOTIFICATIONS) {
                    NotificationsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenUserProfile = { uid -> navController.navigate(VyoraRoutes.userProfile(uid)) }
                    )
                }

                composable(VyoraRoutes.WALLET) {
                    WalletScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(VyoraRoutes.ADMIN) {
                    AdminDashboardScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenUserProfile = { uid -> navController.navigate(VyoraRoutes.userProfile(uid)) }
                    )
                }

                composable(VyoraRoutes.LIVE_LOBBY) {
                    LiveLobbyScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onEnterStream = { streamId, isHost ->
                            navController.navigate(VyoraRoutes.liveRoom(streamId, isHost))
                        }
                    )
                }

                composable(
                    route = VyoraRoutes.LIVE_ROOM,
                    arguments = listOf(
                        navArgument("streamId") { type = NavType.StringType },
                        navArgument("isHost") { type = NavType.BoolType }
                    )
                ) { entry ->
                    val streamId = Uri.decode(entry.arguments?.getString("streamId").orEmpty())
                    val isHost = entry.arguments?.getBoolean("isHost") ?: false
                    LiveRoomScreen(
                        streamId = streamId,
                        isHost = isHost,
                        viewModel = viewModel,
                        onExit = { navController.popBackStack() }
                    )
                }

                composable(
                    route = VyoraRoutes.CHAT,
                    arguments = listOf(
                        navArgument("convId") { type = NavType.StringType },
                        navArgument("peerId") { type = NavType.StringType }
                    )
                ) { entry ->
                    val convId = Uri.decode(entry.arguments?.getString("convId").orEmpty())
                    val peerId = Uri.decode(entry.arguments?.getString("peerId").orEmpty())
                    ChatRoomScreen(
                        conversationId = convId,
                        peerId = peerId,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = VyoraRoutes.USER_PROFILE,
                    arguments = listOf(navArgument("userId") { type = NavType.StringType })
                ) { entry ->
                    val uid = Uri.decode(entry.arguments?.getString("userId").orEmpty())
                    ProfileScreen(
                        targetUserId = uid,
                        viewModel = viewModel,
                        currentLanguage = prefs.language,
                        currentThemeMode = prefs.themeMode,
                        onChangeLanguage = onChangeLanguage,
                        onChangeTheme = onChangeTheme,
                        onOpenWallet = { navController.navigate(VyoraRoutes.WALLET) },
                        onOpenAdmin = { navController.navigate(VyoraRoutes.ADMIN) },
                        onOpenNotifications = { navController.navigate(VyoraRoutes.NOTIFICATIONS) },
                        onOpenChat = { convId, peerId -> navController.navigate(VyoraRoutes.chat(convId, peerId)) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = VyoraRoutes.SOUND,
                    arguments = listOf(
                        navArgument("soundId") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("soundTitle") {
                            type = NavType.StringType
                            defaultValue = "Original Sound"
                        }
                    )
                ) { entry ->
                    val soundId = Uri.decode(entry.arguments?.getString("soundId").orEmpty())
                    val soundTitle = Uri.decode(entry.arguments?.getString("soundTitle").orEmpty())
                        .ifBlank { vString(R.string.studio_original_sound) }
                    SoundDetailScreen(
                        soundId = soundId,
                        soundTitle = soundTitle,
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onUseSound = { navController.navigate(VyoraRoutes.CREATE) },
                        onOpenUserProfile = { uid -> navController.navigate(VyoraRoutes.userProfile(uid)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VyoraBottomNavBar(
    currentRoute: String?,
    isOverlayDark: Boolean,
    onSelectTab: (String) -> Unit,
    onCreateClick: () -> Unit
) {
    val containerColor = if (isOverlayDark) Color(0xF007070C) else MaterialTheme.colorScheme.surface
    val activeColor = if (isOverlayDark) Color.White else MaterialTheme.colorScheme.primary
    val inactiveColor = if (isOverlayDark) Color.White.copy(alpha = 0.62f) else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = containerColor,
        tonalElevation = if (isOverlayDark) 0.dp else 4.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            VyoraNavTabItem(
                icon = Icons.Filled.Home,
                label = vString(R.string.nav_home),
                selected = currentRoute == VyoraRoutes.HOME,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                testTag = "nav_home_tab",
                onClick = { onSelectTab(VyoraRoutes.HOME) },
                modifier = Modifier.weight(1f)
            )
            VyoraNavTabItem(
                icon = Icons.Filled.Group,
                label = vString(R.string.nav_friends),
                selected = currentRoute == VyoraRoutes.FRIENDS,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                testTag = "nav_friends_tab",
                onClick = { onSelectTab(VyoraRoutes.FRIENDS) },
                modifier = Modifier.weight(1f)
            )

            // Center Elevated VYORA (+) Button
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(width = 52.dp, height = 38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(ElectricViolet, PulseMagenta, NeonCyan)
                        )
                    )
                    .clickable { onCreateClick() }
                    .testTag("nav_create_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = vString(R.string.nav_create),
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            VyoraNavTabItem(
                icon = Icons.AutoMirrored.Filled.Chat,
                label = vString(R.string.nav_messages),
                selected = currentRoute == VyoraRoutes.MESSAGES,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                testTag = "nav_messages_tab",
                onClick = { onSelectTab(VyoraRoutes.MESSAGES) },
                modifier = Modifier.weight(1f)
            )
            VyoraNavTabItem(
                icon = Icons.Filled.Person,
                label = vString(R.string.nav_profile),
                selected = currentRoute == VyoraRoutes.PROFILE,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                testTag = "nav_profile_tab",
                onClick = { onSelectTab(VyoraRoutes.PROFILE) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun VyoraNavTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) activeColor else inactiveColor
    Column(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
