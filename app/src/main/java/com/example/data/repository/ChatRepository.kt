package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
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

class ChatRepository(
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

    fun observeConversations(userId: String): Flow<List<Conversation>> =
        db.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(Conversation::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.lastMessageAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "conversations", auth)
                throw error
            }

    fun observeMessages(conversationId: String, currentUserId: String): Flow<List<ChatMessage>> =
        db.collection("conversations").document(conversationId).collection("messages")
            .whereArrayContains("participantIds", currentUserId)
            .snapshots()
            .map { snap ->
                snap.toObjects(ChatMessage::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "conversations/$conversationId/messages", auth)
                throw error
            }

    suspend fun getOrCreateConversation(peerId: String): Result<String> = runCatching {
        val uid = requireUid()
        val sorted = listOf(uid, peerId).sorted()
        val convId = "${sorted[0]}_${sorted[1]}"
        val ref = db.collection("conversations").document(convId)
        val snap = ref.get().await()
        if (!snap.exists()) {
            ref.set(
                mapOf(
                    "participantIds" to sorted,
                    "lastMessage" to "",
                    "lastMessageType" to "text",
                    "lastSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        convId
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "conversations", auth)
    }

    suspend fun sendMessage(
        conversationId: String,
        participantIds: List<String>,
        actor: UserProfile,
        type: String,
        text: String = "",
        mediaUrl: String = "",
        audioDurationMs: Long = 0L
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val msgRef = db.collection("conversations").document(conversationId).collection("messages").document()
        val convRef = db.collection("conversations").document(conversationId)
        val preview = when (type) {
            "image" -> "[Photo]"
            "video" -> "[Video]"
            "audio" -> "[Voice Note]"
            else -> text.take(120)
        }

        val msgPayload = mutableMapOf<String, Any>(
            "senderId" to uid,
            "participantIds" to participantIds,
            "type" to type,
            "text" to text.take(2000),
            "mediaUrl" to mediaUrl.take(1500),
            "audioDurationMs" to audioDurationMs.coerceIn(0L, 600000L),
            "readBy" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp()
        )

        val batch = db.batch()
        batch.set(msgRef, msgPayload)
        batch.update(
            convRef,
            mapOf(
                "lastMessage" to preview,
                "lastMessageType" to type,
                "lastSenderId" to uid,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        val peerId = participantIds.firstOrNull { it != uid }
        if (peerId != null) {
            batch.set(
                db.collection("notifications").document(),
                mapOf(
                    "recipientId" to peerId,
                    "actorId" to uid,
                    "actorUsername" to actor.username,
                    "actorAvatarUrl" to actor.avatarUrl,
                    "type" to "message",
                    "targetId" to conversationId,
                    "message" to "@${actor.username}: $preview",
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "conversations/$conversationId/messages", auth)
    }

    suspend fun markMessageRead(conversationId: String, message: ChatMessage): Result<Unit> = runCatching {
        val uid = requireUid()
        if (uid !in message.readBy && message.senderId != uid) {
            db.collection("conversations").document(conversationId)
                .collection("messages").document(message.id)
                .update("readBy", (message.readBy + uid).distinct().take(2))
                .await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "conversations/$conversationId/messages/${message.id}", auth)
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("conversations").document(conversationId).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "conversations/$conversationId", auth)
    }
}
