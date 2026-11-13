package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminRole
import com.example.data.model.AdminSettings
import com.example.data.model.AppNotification
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.FollowRequest
import com.example.data.model.HashtagItem
import com.example.data.model.LiveComment
import com.example.data.model.LiveStream
import com.example.data.model.ModerationReport
import com.example.data.model.SoundTrack
import com.example.data.model.UserProfile
import com.example.data.model.VideoComment
import com.example.data.model.VideoPost
import com.example.data.model.Wallet
import com.example.data.model.WalletTransaction
import com.example.data.model.WithdrawalRequest
import com.example.data.remote.AgoraLiveSession
import com.example.data.remote.CloudFunctionsRepository
import com.example.data.remote.StorageMediaRepository
import com.example.data.remote.SupabaseClientDiagnostics
import com.example.data.remote.SupabaseLedgerClient
import com.example.data.remote.SupabaseWalletSummary
import com.example.data.remote.UploadStatus
import com.example.data.remote.unwrapFirestoreException
import com.example.data.repository.ChatRepository
import com.example.data.repository.LiveRepository
import com.example.data.repository.NotificationRepository
import com.example.data.repository.UserRepository
import com.example.data.repository.VideoRepository
import com.example.data.repository.WalletAdminRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface LedgerSyncState {
    data object Checking : LedgerSyncState
    data class Synced(
        val summary: SupabaseWalletSummary,
        val diagnostics: SupabaseClientDiagnostics
    ) : LedgerSyncState
    data class PendingSetup(
        val diagnostics: SupabaseClientDiagnostics,
        val serverDeployed: Boolean
    ) : LedgerSyncState
}

@OptIn(ExperimentalCoroutinesApi::class)
class VyoraAppViewModel(
    val currentUserId: String,
    private val userRepo: UserRepository,
    private val videoRepo: VideoRepository,
    private val chatRepo: ChatRepository,
    private val liveRepo: LiveRepository,
    private val notifRepo: NotificationRepository,
    private val walletAdminRepo: WalletAdminRepository,
    private val storageRepo: StorageMediaRepository,
    private val cloudFunctionsRepo: CloudFunctionsRepository = CloudFunctionsRepository(),
    private val supabaseLedgerClient: SupabaseLedgerClient = SupabaseLedgerClient()
) : ViewModel() {

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadStatus?>(null)
    val uploadState: StateFlow<UploadStatus?> = _uploadState.asStateFlow()

    private val _ledgerSyncState = MutableStateFlow<LedgerSyncState>(LedgerSyncState.Checking)
    val ledgerSyncState: StateFlow<LedgerSyncState> = _ledgerSyncState.asStateFlow()

    val currentUserProfile: StateFlow<UserProfile?> = userRepo.observeUser(currentUserId)
        .catch { e ->
            if (e.unwrapFirestoreException() == null && e is CancellationException) throw e
            Log.w(TAG, "Profile observe error", e)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val feedVideos: StateFlow<UiState<List<VideoPost>>> = videoRepo.observeActiveVideos()
        .map<List<VideoPost>, UiState<List<VideoPost>>> { UiState.Success(it) }
        .catch { e ->
            val fs = e.unwrapFirestoreException()
            if (fs == null && e is CancellationException) throw e
            emit(UiState.Error((fs ?: e).localizedMessage ?: "Failed to load feed"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val activeUsers: StateFlow<List<UserProfile>> = userRepo.observeActiveUsers()
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val followingIds: StateFlow<Set<String>> = userRepo.observeFollowingIds(currentUserId)
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val blockedIds: StateFlow<Set<String>> = userRepo.observeBlockedIds(currentUserId)
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val bookmarkedIds: StateFlow<Set<String>> = videoRepo.observeBookmarkedIds(currentUserId)
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val followRequests: StateFlow<List<FollowRequest>> = userRepo.observeIncomingFollowRequests(currentUserId)
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val conversations: StateFlow<List<Conversation>> = chatRepo.observeConversations(currentUserId)
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val notifications: StateFlow<List<AppNotification>> = notifRepo.observeNotifications(currentUserId)
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeLiveStreams: StateFlow<List<LiveStream>> = liveRepo.observeActiveStreams()
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val sounds: StateFlow<List<SoundTrack>> = videoRepo.observeSounds()
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val hashtags: StateFlow<List<HashtagItem>> = videoRepo.observeHashtags()
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val wallet: StateFlow<Wallet?> = walletAdminRepo.observeWallet(currentUserId)
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val transactions: StateFlow<List<WalletTransaction>> = walletAdminRepo.observeTransactions(currentUserId)
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val myWithdrawals: StateFlow<List<WithdrawalRequest>> = walletAdminRepo.observeMyWithdrawals(currentUserId)
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminSettings: StateFlow<AdminSettings> = walletAdminRepo.observeAdminSettings()
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AdminSettings())

    // Admin Streams (only subscribed when isAdmin == true)
    val adminUsers: StateFlow<List<UserProfile>> = _isAdmin
        .flatMapLatest { admin -> if (admin) walletAdminRepo.observeAllUsersAdmin() else flowOf(emptyList()) }
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminVideos: StateFlow<List<VideoPost>> = _isAdmin
        .flatMapLatest { admin -> if (admin) walletAdminRepo.observeAllVideosAdmin() else flowOf(emptyList()) }
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminReports: StateFlow<List<ModerationReport>> = _isAdmin
        .flatMapLatest { admin -> if (admin) walletAdminRepo.observeReportsAdmin() else flowOf(emptyList()) }
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminWithdrawals: StateFlow<List<WithdrawalRequest>> = _isAdmin
        .flatMapLatest { admin -> if (admin) walletAdminRepo.observeWithdrawalsAdmin() else flowOf(emptyList()) }
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminRoles: StateFlow<List<AdminRole>> = _isAdmin
        .flatMapLatest { admin -> if (admin) walletAdminRepo.observeAdmins() else flowOf(emptyList()) }
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminLiveStreams: StateFlow<List<LiveStream>> = _isAdmin
        .flatMapLatest { admin -> if (admin) walletAdminRepo.observeLiveStreamsAdmin() else flowOf(emptyList()) }
        .catch { e -> if (e.unwrapFirestoreException() == null && e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            val firebaseUser = Firebase.auth.currentUser
            if (firebaseUser != null) {
                userRepo.ensureUserProfile(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email.orEmpty(),
                    displayName = firebaseUser.displayName,
                    avatarUrl = firebaseUser.photoUrl?.toString()
                )
                _isAdmin.value = walletAdminRepo.checkIsAdmin(firebaseUser.uid, firebaseUser.email)
                cloudFunctionsRepo.syncUserAndVideoToSupabase()
                refreshAccountingSync()
            }
        }
    }

    fun refreshAccountingSync() {
        viewModelScope.launch {
            _ledgerSyncState.value = LedgerSyncState.Checking
            val diagDeferred = async { supabaseLedgerClient.checkPublicCatalogConnectivity() }
            val summaryDeferred = async { cloudFunctionsRepo.fetchWalletAccountingSummary() }

            val diag = diagDeferred.await()
            val summaryRes = summaryDeferred.await()
            val summary = summaryRes.getOrNull()

            _ledgerSyncState.value = if (summary != null && summary.supabaseConfigured && summary.synced) {
                LedgerSyncState.Synced(summary = summary, diagnostics = diag)
            } else {
                LedgerSyncState.PendingSetup(
                    diagnostics = diag,
                    serverDeployed = summaryRes.isSuccess
                )
            }
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun clearUploadState() {
        _uploadState.value = null
    }

    fun observeComments(videoId: String) = videoRepo.observeComments(videoId)
    fun observeMessages(conversationId: String) = chatRepo.observeMessages(conversationId, currentUserId)
    fun observeLiveComments(streamId: String) = liveRepo.observeLiveComments(streamId)
    fun observeLiveStream(streamId: String) = liveRepo.observeStream(streamId)

    suspend fun isVideoLiked(videoId: String): Boolean = videoRepo.isVideoLikedByMe(videoId)

    fun toggleLike(video: VideoPost) {
        val actor = currentUserProfile.value ?: return
        viewModelScope.launch {
            videoRepo.toggleLike(video, actor).onFailure {
                _toastMessage.value = it.localizedMessage
            }
        }
    }

    fun toggleBookmark(videoId: String) {
        viewModelScope.launch {
            videoRepo.toggleBookmark(videoId).onFailure {
                _toastMessage.value = it.localizedMessage
            }
        }
    }

    suspend fun fetchAgoraLiveSession(streamId: String): Result<AgoraLiveSession> =
        cloudFunctionsRepo.fetchAgoraLiveSession(streamId)

    fun recordView(videoId: String) {
        viewModelScope.launch {
            cloudFunctionsRepo.recordVideoViewServer(videoId).onFailure { err ->
                if (cloudFunctionsRepo.isFunctionUnavailable(err)) {
                    videoRepo.recordViewIfNew(videoId)
                }
            }
        }
    }

    fun recordShare(video: VideoPost) {
        val actor = currentUserProfile.value ?: return
        viewModelScope.launch {
            videoRepo.recordShare(video, actor)
        }
    }

    fun addComment(video: VideoPost, text: String) {
        val actor = currentUserProfile.value ?: return
        viewModelScope.launch {
            videoRepo.addComment(video, actor, text).onFailure {
                _toastMessage.value = it.localizedMessage
            }
        }
    }

    fun followOrUnfollow(targetUser: UserProfile) {
        val actor = currentUserProfile.value ?: return
        val currentlyFollowing = targetUser.userId in followingIds.value
        viewModelScope.launch {
            if (currentlyFollowing) {
                userRepo.unfollowUser(targetUser.userId)
            } else {
                userRepo.followOrRequestUser(actor, targetUser).onSuccess { result ->
                    if (result == "requested") {
                        _toastMessage.value = "Follow request sent"
                    }
                }
            }.onFailure {
                _toastMessage.value = it.localizedMessage
            }
        }
    }

    fun respondToFollowRequest(request: FollowRequest, accept: Boolean) {
        val me = currentUserProfile.value ?: return
        viewModelScope.launch {
            userRepo.respondToFollowRequest(request, me, accept).onFailure {
                _toastMessage.value = it.localizedMessage
            }
        }
    }

    fun toggleBlockUser(targetUserId: String) {
        val blocked = targetUserId in blockedIds.value
        viewModelScope.launch {
            userRepo.toggleBlockUser(targetUserId, blocked).onFailure {
                _toastMessage.value = it.localizedMessage
            }
        }
    }

    fun uploadAndPublishVideo(
        context: Context,
        videoUri: Uri,
        customCoverUri: Uri?,
        caption: String,
        soundId: String,
        soundTitle: String,
        durationSec: Long,
        onPublished: () -> Unit
    ) {
        val creator = currentUserProfile.value ?: return
        viewModelScope.launch {
            storageRepo.uploadVideoWithThumbnail(context, currentUserId, videoUri, customCoverUri)
                .collect { status ->
                    _uploadState.value = status
                    if (status is UploadStatus.Completed) {
                        videoRepo.publishVideo(
                            creator = creator,
                            videoUrl = status.downloadUrl,
                            thumbnailUrl = status.thumbnailUrl,
                            caption = caption,
                            soundId = soundId,
                            soundTitle = soundTitle,
                            durationSec = durationSec
                        ).onSuccess { newVideoId ->
                            cloudFunctionsRepo.syncUserAndVideoToSupabase(newVideoId)
                            _toastMessage.value = "Video published!"
                            onPublished()
                        }.onFailure { e ->
                            _uploadState.value = UploadStatus.Failed(e.localizedMessage ?: "Failed to save video")
                        }
                    }
                }
        }
    }

    fun updateVideoCaption(videoId: String, caption: String) {
        viewModelScope.launch {
            videoRepo.updateVideoCaption(videoId, caption).onSuccess {
                cloudFunctionsRepo.syncUserAndVideoToSupabase(videoId)
                _toastMessage.value = "Updated"
            }.onFailure {
                _toastMessage.value = it.localizedMessage
            }
        }
    }

    fun deleteVideo(videoId: String) {
        viewModelScope.launch {
            videoRepo.deleteVideo(videoId).onSuccess {
                _toastMessage.value = "Deleted"
            }.onFailure {
                _toastMessage.value = it.localizedMessage
            }
        }
    }

    fun updateProfile(
        username: String,
        displayName: String,
        bio: String,
        avatarUri: Uri?,
        currentAvatarUrl: String,
        isPrivate: Boolean,
        interests: List<String>? = null
    ) {
        viewModelScope.launch {
            val finalAvatar = if (avatarUri != null) {
                storageRepo.uploadImage(currentUserId, "avatars", avatarUri).getOrDefault(currentAvatarUrl)
            } else {
                currentAvatarUrl
            }
            userRepo.updateProfile(username, displayName, bio, finalAvatar, isPrivate, interests)
                .onSuccess {
                    cloudFunctionsRepo.syncUserAndVideoToSupabase()
                    _toastMessage.value = "Profile saved"
                }
                .onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun updateUserPreferences(language: String, themeMode: String) {
        viewModelScope.launch {
            userRepo.updatePreferences(language, themeMode)
        }
    }

    suspend fun openConversationWith(peerId: String): String? {
        return chatRepo.getOrCreateConversation(peerId).onFailure {
            _toastMessage.value = it.localizedMessage
        }.getOrNull()
    }

    fun sendChatMessage(
        conversationId: String,
        participantIds: List<String>,
        type: String,
        text: String = "",
        mediaUri: Uri? = null,
        audioDurationMs: Long = 0L
    ) {
        val actor = currentUserProfile.value ?: return
        viewModelScope.launch {
            var mediaUrl = ""
            if (mediaUri != null) {
                val ext = when (type) {
                    "video" -> "mp4"
                    "audio" -> "m4a"
                    else -> "jpg"
                }
                val uploaded = storageRepo.uploadMediaFile(currentUserId, "chat_media", mediaUri, ext)
                if (uploaded.isFailure) {
                    _toastMessage.value = uploaded.exceptionOrNull()?.localizedMessage ?: "Media upload failed"
                    return@launch
                }
                mediaUrl = uploaded.getOrThrow()
            }
            chatRepo.sendMessage(conversationId, participantIds, actor, type, text, mediaUrl, audioDurationMs)
                .onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun markMessageRead(conversationId: String, message: ChatMessage) {
        viewModelScope.launch {
            chatRepo.markMessageRead(conversationId, message)
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            chatRepo.deleteConversation(conversationId).onFailure {
                _toastMessage.value = it.localizedMessage
            }
        }
    }

    suspend fun startLiveStream(title: String): LiveStream? {
        val host = currentUserProfile.value ?: return null
        return liveRepo.startStream(host, title).onFailure {
            _toastMessage.value = it.localizedMessage
        }.getOrNull()
    }

    fun endLiveStream(streamId: String) {
        viewModelScope.launch {
            liveRepo.endStream(streamId)
        }
    }

    fun joinOrLeaveLiveViewer(streamId: String, delta: Long) {
        viewModelScope.launch {
            liveRepo.updateViewerCount(streamId, delta)
        }
    }

    fun sendLiveLike(streamId: String) {
        viewModelScope.launch {
            liveRepo.sendLiveLike(streamId)
        }
    }

    fun sendLiveComment(streamId: String, text: String) {
        val actor = currentUserProfile.value ?: return
        viewModelScope.launch {
            liveRepo.sendLiveComment(streamId, actor, text)
        }
    }

    fun createSoundTrack(title: String, artist: String, audioUri: Uri?, durationSec: Long) {
        viewModelScope.launch {
            val url = if (audioUri != null) {
                storageRepo.uploadMediaFile(currentUserId, "sounds", audioUri, "m4a").getOrElse {
                    _toastMessage.value = it.localizedMessage
                    return@launch
                }
            } else {
                ""
            }
            videoRepo.createSoundTrack(title, artist, url, durationSec, isLicensed = true)
                .onSuccess { _toastMessage.value = "Sound added" }
                .onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun markNotificationsRead(list: List<AppNotification>) {
        viewModelScope.launch {
            notifRepo.markAllRead(list)
        }
    }

    fun submitReport(targetType: String, targetId: String, reason: String, details: String) {
        viewModelScope.launch {
            walletAdminRepo.submitReport(targetType, targetId, reason, details)
                .onSuccess { _toastMessage.value = "Report submitted" }
                .onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun requestWithdrawal(amountUsd: Double, method: String, accountDetails: String) {
        val settings = adminSettings.value
        val myWallet = wallet.value
        if (amountUsd < settings.minWithdrawalUsd) {
            _toastMessage.value = "Minimum withdrawal is $${settings.minWithdrawalUsd}"
            return
        }
        if (myWallet == null || myWallet.balanceUsd < amountUsd) {
            _toastMessage.value = "Insufficient balance"
            return
        }
        val pointsNeeded = (amountUsd / settings.pointValueUsd.coerceAtLeast(0.0001)).toLong().coerceAtLeast(1L)
        viewModelScope.launch {
            cloudFunctionsRepo.submitWithdrawalServer(amountUsd, method, accountDetails)
                .onSuccess {
                    _toastMessage.value = "Withdrawal request submitted"
                    refreshAccountingSync()
                }
                .onFailure { err ->
                    if (cloudFunctionsRepo.isFunctionUnavailable(err)) {
                        walletAdminRepo.submitWithdrawalRequest(amountUsd, pointsNeeded, method, accountDetails)
                            .onSuccess { _toastMessage.value = "Withdrawal request submitted" }
                            .onFailure { _toastMessage.value = it.localizedMessage }
                    } else {
                        _toastMessage.value = err.localizedMessage
                    }
                }
        }
    }

    // --- Admin Actions ---
    fun adminSetUserBanned(targetUid: String, banned: Boolean) {
        viewModelScope.launch {
            walletAdminRepo.setUserBanned(targetUid, banned).onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun adminRemoveVideo(videoId: String) {
        viewModelScope.launch {
            walletAdminRepo.removeVideoAdmin(videoId).onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun adminResolveReport(reportId: String, status: String) {
        viewModelScope.launch {
            walletAdminRepo.updateReportStatus(reportId, status).onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun adminReviewWithdrawal(request: WithdrawalRequest, approve: Boolean) {
        viewModelScope.launch {
            cloudFunctionsRepo.adminReviewWithdrawalServer(request.id, approve)
                .onSuccess { refreshAccountingSync() }
                .onFailure { err ->
                    if (cloudFunctionsRepo.isFunctionUnavailable(err)) {
                        walletAdminRepo.reviewWithdrawalAdmin(request, approve)
                            .onFailure { _toastMessage.value = it.localizedMessage }
                    } else {
                        _toastMessage.value = err.localizedMessage
                    }
                }
        }
    }

    fun adminGrantPoints(targetUid: String, points: Long, reason: String) {
        viewModelScope.launch {
            cloudFunctionsRepo.adminGrantPointsServer(targetUid, points, reason)
                .onSuccess {
                    _toastMessage.value = "Points updated"
                    refreshAccountingSync()
                }
                .onFailure { err ->
                    if (cloudFunctionsRepo.isFunctionUnavailable(err)) {
                        walletAdminRepo.grantCreatorPointsAdmin(targetUid, points, adminSettings.value.pointValueUsd, reason)
                            .onSuccess { _toastMessage.value = "Points updated" }
                            .onFailure { _toastMessage.value = it.localizedMessage }
                    } else {
                        _toastMessage.value = err.localizedMessage
                    }
                }
        }
    }

    fun adminSaveSettings(pointValueUsd: Double, minWithdrawalUsd: Double, rewardsEnabled: Boolean, maxVideoDurationSec: Long) {
        viewModelScope.launch {
            walletAdminRepo.saveAdminSettings(pointValueUsd, minWithdrawalUsd, rewardsEnabled, maxVideoDurationSec)
                .onSuccess { _toastMessage.value = "Settings saved" }
                .onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun adminAssignRole(targetUid: String, email: String, role: String) {
        viewModelScope.launch {
            walletAdminRepo.assignAdminRole(targetUid, email, role)
                .onSuccess { _toastMessage.value = "Admin role updated" }
                .onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun adminRemoveRole(targetUid: String) {
        viewModelScope.launch {
            walletAdminRepo.removeAdminRole(targetUid).onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun adminEndLiveStream(streamId: String) {
        viewModelScope.launch {
            liveRepo.endStream(streamId)
        }
    }

    private companion object {
        const val TAG = "VyoraAppVM"
    }
}
