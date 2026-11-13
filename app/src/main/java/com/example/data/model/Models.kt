package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val isPrivate: Boolean = false,
    val isBanned: Boolean = false,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val likesCount: Long = 0L,
    val interests: List<String> = emptyList(),
    val language: String = "ar",
    val themeMode: String = "dark",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FollowEdge(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FollowRequest(
    @DocumentId val id: String = "",
    val requesterId: String = "",
    val requesterUsername: String = "",
    val requesterAvatarUrl: String = "",
    val targetId: String = "",
    val status: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VideoPost(
    @DocumentId val id: String = "",
    val userId: String = "",
    val creatorUsername: String = "",
    val creatorAvatarUrl: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val caption: String = "",
    val hashtags: List<String> = emptyList(),
    val soundId: String = "",
    val soundTitle: String = "",
    val durationSec: Long = 15L,
    val viewsCount: Long = 0L,
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val sharesCount: Long = 0L,
    val bookmarksCount: Long = 0L,
    val status: String = "active",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VideoComment(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val userId: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class HashtagItem(
    @DocumentId val id: String = "",
    val tag: String = "",
    val videosCount: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SoundTrack(
    @DocumentId val id: String = "",
    val title: String = "",
    val artist: String = "",
    val audioUrl: String = "",
    val coverUrl: String = "",
    val durationSec: Long = 30L,
    val isLicensed: Boolean = true,
    val creatorId: String = "",
    val usesCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageType: String = "text",
    val lastSenderId: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val participantIds: List<String> = emptyList(),
    val type: String = "text",
    val text: String = "",
    val mediaUrl: String = "",
    val audioDurationMs: Long = 0L,
    val readBy: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val recipientId: String = "",
    val actorId: String = "",
    val actorUsername: String = "",
    val actorAvatarUrl: String = "",
    val type: String = "like",
    val targetId: String = "",
    val message: String = "",
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LiveStream(
    @DocumentId val id: String = "",
    val hostId: String = "",
    val hostUsername: String = "",
    val hostAvatarUrl: String = "",
    val channelName: String = "",
    val title: String = "",
    val isActive: Boolean = true,
    val viewerCount: Long = 1L,
    val likesCount: Long = 0L,
    @ServerTimestamp val startedAt: Timestamp? = null,
    val endedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LiveComment(
    @DocumentId val id: String = "",
    val streamId: String = "",
    val userId: String = "",
    val username: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ModerationReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetType: String = "video",
    val targetId: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "pending",
    val reviewedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Wallet(
    @DocumentId val id: String = "",
    val userId: String = "",
    val points: Long = 0L,
    val balanceUsd: Double = 0.0,
    val totalEarnedUsd: Double = 0.0,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class WalletTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = "view_reward",
    val pointsDelta: Long = 0L,
    val amountDelta: Double = 0.0,
    val description: String = "",
    val status: String = "completed",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class WithdrawalRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val amountUsd: Double = 0.0,
    val pointsSpent: Long = 0L,
    val method: String = "",
    val accountDetails: String = "",
    val status: String = "pending",
    val reviewedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AdminSettings(
    @DocumentId val id: String = "",
    val pointValueUsd: Double = 0.01,
    val minWithdrawalUsd: Double = 10.0,
    val rewardsEnabled: Boolean = true,
    val maxVideoDurationSec: Long = 180L,
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AdminRole(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val role: String = "moderator",
    val assignedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
