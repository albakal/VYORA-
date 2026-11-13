package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.HashtagItem
import com.example.data.model.SoundTrack
import com.example.data.model.UserProfile
import com.example.data.model.VideoComment
import com.example.data.model.VideoPost
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class VideoRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeActiveVideos(): Flow<List<VideoPost>> =
        db.collection("videos")
            .whereEqualTo("status", "active")
            .limit(60)
            .snapshots()
            .map { snap ->
                snap.toObjects(VideoPost::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "videos", auth)
                throw error
            }

    fun observeUserVideos(userId: String): Flow<List<VideoPost>> =
        db.collection("videos")
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", "active")
            .snapshots()
            .map { snap ->
                snap.toObjects(VideoPost::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "videos?userId=$userId", auth)
                throw error
            }

    fun observeBookmarkedIds(userId: String): Flow<Set<String>> =
        db.collection("users").document(userId).collection("bookmarks")
            .snapshots()
            .map { snap -> snap.documents.map { it.id }.toSet() }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "users/$userId/bookmarks", auth)
                throw error
            }

    fun observeComments(videoId: String): Flow<List<VideoComment>> =
        db.collection("videos").document(videoId).collection("comments")
            .whereEqualTo("videoId", videoId)
            .snapshots()
            .map { snap ->
                snap.toObjects(VideoComment::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "videos/$videoId/comments", auth)
                throw error
            }

    fun observeSounds(): Flow<List<SoundTrack>> =
        db.collection("sounds")
            .whereEqualTo("isLicensed", true)
            .snapshots()
            .map { snap ->
                snap.toObjects(SoundTrack::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.usesCount }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "sounds", auth)
                throw error
            }

    fun observeHashtags(): Flow<List<HashtagItem>> =
        db.collection("hashtags")
            .whereGreaterThanOrEqualTo("videosCount", 0L)
            .limit(30)
            .snapshots()
            .map { snap ->
                snap.toObjects(HashtagItem::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.videosCount }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "hashtags", auth)
                throw error
            }

    suspend fun isVideoLikedByMe(videoId: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            db.collection("videos").document(videoId).collection("likes").document(uid).get().await().exists()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun publishVideo(
        creator: UserProfile,
        videoUrl: String,
        thumbnailUrl: String,
        caption: String,
        soundId: String,
        soundTitle: String,
        durationSec: Long
    ): Result<String> = runCatching {
        val uid = requireUid()
        val hashtags = Regex("#([\\p{L}0-9_]+)")
            .findAll(caption)
            .map { it.groupValues[1].lowercase() }
            .distinct()
            .take(15)
            .toList()

        val docRef = db.collection("videos").document()
        val payload = mapOf(
            "userId" to uid,
            "creatorUsername" to creator.username,
            "creatorAvatarUrl" to creator.avatarUrl,
            "videoUrl" to videoUrl,
            "thumbnailUrl" to thumbnailUrl,
            "caption" to caption.take(500),
            "hashtags" to hashtags,
            "soundId" to soundId,
            "soundTitle" to soundTitle.ifBlank { "Original Sound - ${creator.username}" }.take(120),
            "durationSec" to durationSec.coerceIn(1L, 300L),
            "viewsCount" to 0L,
            "likesCount" to 0L,
            "commentsCount" to 0L,
            "sharesCount" to 0L,
            "bookmarksCount" to 0L,
            "status" to "active",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()

        // Update hashtag counts
        for (tag in hashtags) {
            val cleanId = tag.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(64).ifBlank { "tag" }
            val tagRef = db.collection("hashtags").document(cleanId)
            val existingTag = tagRef.get().await()
            if (existingTag.exists()) {
                tagRef.update(mapOf("videosCount" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp())).await()
            } else {
                tagRef.set(mapOf("tag" to tag, "videosCount" to 1L, "updatedAt" to FieldValue.serverTimestamp())).await()
            }
        }

        if (soundId.isNotBlank()) {
            val sndRef = db.collection("sounds").document(soundId)
            if (sndRef.get().await().exists()) {
                sndRef.update("usesCount", FieldValue.increment(1)).await()
            }
        }
        docRef.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "videos", auth)
    }

    suspend fun updateVideoCaption(videoId: String, caption: String): Result<Unit> = runCatching {
        requireUid()
        val hashtags = Regex("#([\\p{L}0-9_]+)")
            .findAll(caption)
            .map { it.groupValues[1].lowercase() }
            .distinct()
            .take(15)
            .toList()
        db.collection("videos").document(videoId).update(
            mapOf(
                "caption" to caption.take(500),
                "hashtags" to hashtags,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "videos/$videoId", auth)
    }

    suspend fun deleteVideo(videoId: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("videos").document(videoId).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "videos/$videoId", auth)
    }

    suspend fun toggleLike(video: VideoPost, actor: UserProfile): Result<Boolean> = runCatching {
        val uid = requireUid()
        val likeRef = db.collection("videos").document(video.id).collection("likes").document(uid)
        val videoRef = db.collection("videos").document(video.id)
        val ownerRef = db.collection("users").document(video.userId)
        val alreadyLiked = likeRef.get().await().exists()

        val batch = db.batch()
        if (alreadyLiked) {
            batch.delete(likeRef)
            batch.update(videoRef, mapOf("likesCount" to FieldValue.increment(-1), "updatedAt" to FieldValue.serverTimestamp()))
            batch.update(ownerRef, mapOf("likesCount" to FieldValue.increment(-1), "updatedAt" to FieldValue.serverTimestamp()))
        } else {
            batch.set(
                likeRef,
                mapOf(
                    "userId" to uid,
                    "targetId" to video.id,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(videoRef, mapOf("likesCount" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp()))
            batch.update(ownerRef, mapOf("likesCount" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp()))
            if (video.userId != uid) {
                batch.set(
                    db.collection("notifications").document(),
                    mapOf(
                        "recipientId" to video.userId,
                        "actorId" to uid,
                        "actorUsername" to actor.username,
                        "actorAvatarUrl" to actor.avatarUrl,
                        "type" to "like",
                        "targetId" to video.id,
                        "message" to "@${actor.username} liked your video",
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }
        batch.commit().await()
        !alreadyLiked
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "videos/${video.id}/likes", auth)
    }

    suspend fun toggleBookmark(videoId: String): Result<Boolean> = runCatching {
        val uid = requireUid()
        val bmRef = db.collection("users").document(uid).collection("bookmarks").document(videoId)
        val videoRef = db.collection("videos").document(videoId)
        val exists = bmRef.get().await().exists()
        val batch = db.batch()
        if (exists) {
            batch.delete(bmRef)
            batch.update(videoRef, mapOf("bookmarksCount" to FieldValue.increment(-1), "updatedAt" to FieldValue.serverTimestamp()))
        } else {
            batch.set(
                bmRef,
                mapOf(
                    "userId" to uid,
                    "targetId" to videoId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(videoRef, mapOf("bookmarksCount" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp()))
        }
        batch.commit().await()
        !exists
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/bookmarks/$videoId", auth)
    }

    suspend fun recordViewIfNew(videoId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        val viewRef = db.collection("videos").document(videoId).collection("views").document(uid)
        if (!viewRef.get().await().exists()) {
            val batch = db.batch()
            batch.set(
                viewRef,
                mapOf(
                    "userId" to uid,
                    "targetId" to videoId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("videos").document(videoId),
                mapOf("viewsCount" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.commit().await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "videos/$videoId/views", auth)
    }

    suspend fun addComment(video: VideoPost, actor: UserProfile, text: String): Result<Unit> = runCatching {
        val uid = requireUid()
        val trimmed = text.trim()
        require(trimmed.isNotEmpty())
        val commentRef = db.collection("videos").document(video.id).collection("comments").document()
        val batch = db.batch()
        batch.set(
            commentRef,
            mapOf(
                "videoId" to video.id,
                "userId" to uid,
                "username" to actor.username,
                "avatarUrl" to actor.avatarUrl,
                "text" to trimmed.take(500),
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            db.collection("videos").document(video.id),
            mapOf("commentsCount" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp())
        )
        if (video.userId != uid) {
            batch.set(
                db.collection("notifications").document(),
                mapOf(
                    "recipientId" to video.userId,
                    "actorId" to uid,
                    "actorUsername" to actor.username,
                    "actorAvatarUrl" to actor.avatarUrl,
                    "type" to "comment",
                    "targetId" to video.id,
                    "message" to "@${actor.username}: ${trimmed.take(80)}",
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "videos/${video.id}/comments", auth)
    }

    suspend fun recordShare(video: VideoPost, actor: UserProfile): Result<Unit> = runCatching {
        val uid = requireUid()
        val batch = db.batch()
        batch.update(
            db.collection("videos").document(video.id),
            mapOf("sharesCount" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp())
        )
        if (video.userId != uid) {
            batch.set(
                db.collection("notifications").document(),
                mapOf(
                    "recipientId" to video.userId,
                    "actorId" to uid,
                    "actorUsername" to actor.username,
                    "actorAvatarUrl" to actor.avatarUrl,
                    "type" to "share",
                    "targetId" to video.id,
                    "message" to "@${actor.username} shared your video",
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "videos/${video.id}", auth)
    }

    suspend fun createSoundTrack(
        title: String,
        artist: String,
        audioUrl: String,
        durationSec: Long,
        isLicensed: Boolean
    ): Result<String> = runCatching {
        val uid = requireUid()
        val ref = db.collection("sounds").document()
        ref.set(
            mapOf(
                "title" to title.take(120),
                "artist" to artist.take(100),
                "audioUrl" to audioUrl.take(1500),
                "coverUrl" to "",
                "durationSec" to durationSec.coerceIn(1L, 300L),
                "isLicensed" to isLicensed,
                "creatorId" to uid,
                "usesCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        ref.id
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "sounds", auth)
    }
}
