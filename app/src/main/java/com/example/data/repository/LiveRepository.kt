package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.LiveComment
import com.example.data.model.LiveStream
import com.example.data.model.UserProfile
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

class LiveRepository(
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

    fun observeActiveStreams(): Flow<List<LiveStream>> =
        db.collection("liveStreams")
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { snap ->
                snap.toObjects(LiveStream::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.viewerCount }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "liveStreams", auth)
                throw error
            }

    fun observeStream(streamId: String): Flow<LiveStream?> =
        db.collection("liveStreams").document(streamId)
            .snapshots()
            .map { it.toObject(LiveStream::class.java, ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                handleFirestoreError(error, OperationType.GET, "liveStreams/$streamId", auth)
                throw error
            }

    fun observeLiveComments(streamId: String): Flow<List<LiveComment>> =
        db.collection("liveStreams").document(streamId).collection("comments")
            .whereEqualTo("streamId", streamId)
            .snapshots()
            .map { snap ->
                snap.toObjects(LiveComment::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "liveStreams/$streamId/comments", auth)
                throw error
            }

    suspend fun startStream(host: UserProfile, title: String): Result<LiveStream> = runCatching {
        val uid = requireUid()
        val ref = db.collection("liveStreams").document()
        val channelName = "vyora_${uid.take(8)}_${ref.id.take(8)}"
        val payload = mapOf(
            "hostId" to uid,
            "hostUsername" to host.username,
            "hostAvatarUrl" to host.avatarUrl,
            "channelName" to channelName,
            "title" to title.trim().ifBlank { "${host.displayName} LIVE" }.take(120),
            "isActive" to true,
            "viewerCount" to 1L,
            "likesCount" to 0L,
            "startedAt" to FieldValue.serverTimestamp()
        )
        ref.set(payload).await()
        LiveStream(
            id = ref.id,
            hostId = uid,
            hostUsername = host.username,
            hostAvatarUrl = host.avatarUrl,
            channelName = channelName,
            title = payload["title"] as String,
            isActive = true,
            viewerCount = 1L
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "liveStreams", auth)
    }

    suspend fun updateViewerCount(streamId: String, delta: Long): Result<Unit> = runCatching {
        requireUid()
        db.collection("liveStreams").document(streamId)
            .update("viewerCount", FieldValue.increment(delta))
            .await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "liveStreams/$streamId", auth)
    }

    suspend fun sendLiveLike(streamId: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("liveStreams").document(streamId)
            .update("likesCount", FieldValue.increment(1))
            .await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "liveStreams/$streamId", auth)
    }

    suspend fun sendLiveComment(streamId: String, actor: UserProfile, text: String): Result<Unit> = runCatching {
        val uid = requireUid()
        val trimmed = text.trim()
        require(trimmed.isNotEmpty())
        db.collection("liveStreams").document(streamId).collection("comments").document()
            .set(
                mapOf(
                    "streamId" to streamId,
                    "userId" to uid,
                    "username" to actor.username,
                    "text" to trimmed.take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "liveStreams/$streamId/comments", auth)
    }

    suspend fun endStream(streamId: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("liveStreams").document(streamId).update(
            mapOf(
                "isActive" to false,
                "endedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "liveStreams/$streamId", auth)
    }
}
