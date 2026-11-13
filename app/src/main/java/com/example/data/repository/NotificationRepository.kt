package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AppNotification
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class NotificationRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    fun observeNotifications(userId: String): Flow<List<AppNotification>> =
        db.collection("notifications")
            .whereEqualTo("recipientId", userId)
            .limit(50)
            .snapshots()
            .map { snap ->
                snap.toObjects(AppNotification::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "notifications", auth)
                throw error
            }

    suspend fun markAsRead(notificationId: String): Result<Unit> = runCatching {
        db.collection("notifications").document(notificationId)
            .update("isRead", true)
            .await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "notifications/$notificationId", auth)
    }

    suspend fun markAllRead(notifications: List<AppNotification>): Result<Unit> = runCatching {
        val unread = notifications.filter { !it.isRead }
        if (unread.isNotEmpty()) {
            val batch = db.batch()
            unread.forEach { n ->
                batch.update(db.collection("notifications").document(n.id), "isRead", true)
            }
            batch.commit().await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "notifications", auth)
    }
}
