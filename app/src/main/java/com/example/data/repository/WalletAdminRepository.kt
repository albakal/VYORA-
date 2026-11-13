package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AdminRole
import com.example.data.model.AdminSettings
import com.example.data.model.LiveStream
import com.example.data.model.ModerationReport
import com.example.data.model.UserProfile
import com.example.data.model.VideoPost
import com.example.data.model.Wallet
import com.example.data.model.WalletTransaction
import com.example.data.model.WithdrawalRequest
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

class WalletAdminRepository(
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

    fun observeWallet(userId: String): Flow<Wallet?> =
        db.collection("wallets").document(userId)
            .snapshots()
            .map { it.toObject(Wallet::class.java, ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                handleFirestoreError(error, OperationType.GET, "wallets/$userId", auth)
                throw error
            }

    fun observeTransactions(userId: String): Flow<List<WalletTransaction>> =
        db.collection("transactions")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(WalletTransaction::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "transactions", auth)
                throw error
            }

    fun observeMyWithdrawals(userId: String): Flow<List<WithdrawalRequest>> =
        db.collection("withdrawalRequests")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(WithdrawalRequest::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "withdrawalRequests", auth)
                throw error
            }

    fun observeAdminSettings(): Flow<AdminSettings> =
        db.collection("adminSettings").document("global")
            .snapshots()
            .map { snap ->
                snap.toObject(AdminSettings::class.java, ServerTimestampBehavior.ESTIMATE)
                    ?: AdminSettings(id = "global")
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.GET, "adminSettings/global", auth)
                throw error
            }

    suspend fun checkIsAdmin(uid: String, email: String?): Boolean {
        if (email.equals("mahmoudomran324@gmail.com", ignoreCase = true) && auth.currentUser?.isEmailVerified == true) {
            return true
        }
        return try {
            db.collection("admins").document(uid).get().await().exists()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun submitReport(targetType: String, targetId: String, reason: String, details: String): Result<Unit> = runCatching {
        val uid = requireUid()
        db.collection("reports").document().set(
            mapOf(
                "reporterId" to uid,
                "targetType" to targetType,
                "targetId" to targetId,
                "reason" to reason.take(120),
                "details" to details.take(1000),
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "reports", auth)
    }

    suspend fun submitWithdrawalRequest(
        amountUsd: Double,
        pointsSpent: Long,
        method: String,
        accountDetails: String
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        require(amountUsd > 0.0 && pointsSpent > 0L)
        db.collection("withdrawalRequests").document().set(
            mapOf(
                "userId" to uid,
                "amountUsd" to amountUsd,
                "pointsSpent" to pointsSpent,
                "method" to method.take(60),
                "accountDetails" to accountDetails.take(200),
                "status" to "pending",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.CREATE, "withdrawalRequests", auth)
    }

    // --- Admin Dashboard Operations ---
    fun observeAllUsersAdmin(): Flow<List<UserProfile>> =
        db.collection("users").limit(100).snapshots()
            .map { it.toObjects(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "users", auth)
                throw error
            }

    fun observeAllVideosAdmin(): Flow<List<VideoPost>> =
        db.collection("videos").limit(100).snapshots()
            .map { it.toObjects(VideoPost::class.java, ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "videos", auth)
                throw error
            }

    fun observeReportsAdmin(): Flow<List<ModerationReport>> =
        db.collection("reports").limit(100).snapshots()
            .map { snap ->
                snap.toObjects(ModerationReport::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "reports", auth)
                throw error
            }

    fun observeWithdrawalsAdmin(): Flow<List<WithdrawalRequest>> =
        db.collection("withdrawalRequests").limit(100).snapshots()
            .map { snap ->
                snap.toObjects(WithdrawalRequest::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "withdrawalRequests", auth)
                throw error
            }

    fun observeAdmins(): Flow<List<AdminRole>> =
        db.collection("admins").snapshots()
            .map { it.toObjects(AdminRole::class.java, ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "admins", auth)
                throw error
            }

    fun observeLiveStreamsAdmin(): Flow<List<LiveStream>> =
        db.collection("liveStreams").limit(50).snapshots()
            .map { it.toObjects(LiveStream::class.java, ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "liveStreams", auth)
                throw error
            }

    suspend fun setUserBanned(targetUid: String, banned: Boolean): Result<Unit> = runCatching {
        requireUid()
        db.collection("users").document(targetUid)
            .update(mapOf("isBanned" to banned, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/$targetUid", auth)
    }

    suspend fun removeVideoAdmin(videoId: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("videos").document(videoId)
            .update(mapOf("status" to "removed", "updatedAt" to FieldValue.serverTimestamp()))
            .await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "videos/$videoId", auth)
    }

    suspend fun updateReportStatus(reportId: String, status: String): Result<Unit> = runCatching {
        val adminUid = requireUid()
        db.collection("reports").document(reportId).update(
            mapOf(
                "status" to status,
                "reviewedBy" to adminUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "reports/$reportId", auth)
    }

    suspend fun grantCreatorPointsAdmin(targetUid: String, pointsDelta: Long, pointValueUsd: Double, reason: String): Result<Unit> = runCatching {
        requireUid()
        val amountDelta = pointsDelta * pointValueUsd
        val walletRef = db.collection("wallets").document(targetUid)
        val snap = walletRef.get().await()
        val currentPoints = snap.getLong("points") ?: 0L
        val currentBalance = snap.getDouble("balanceUsd") ?: 0.0
        val currentTotal = snap.getDouble("totalEarnedUsd") ?: 0.0

        val batch = db.batch()
        if (snap.exists()) {
            batch.update(
                walletRef,
                mapOf(
                    "points" to (currentPoints + pointsDelta).coerceAtLeast(0L),
                    "balanceUsd" to (currentBalance + amountDelta).coerceAtLeast(0.0),
                    "totalEarnedUsd" to (currentTotal + amountDelta.coerceAtLeast(0.0)),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            batch.set(
                walletRef,
                mapOf(
                    "userId" to targetUid,
                    "points" to pointsDelta.coerceAtLeast(0L),
                    "balanceUsd" to amountDelta.coerceAtLeast(0.0),
                    "totalEarnedUsd" to amountDelta.coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.set(
            db.collection("transactions").document(),
            mapOf(
                "userId" to targetUid,
                "type" to "adjustment",
                "pointsDelta" to pointsDelta,
                "amountDelta" to amountDelta,
                "description" to reason.take(200),
                "status" to "completed",
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "wallets/$targetUid", auth)
    }

    suspend fun reviewWithdrawalAdmin(request: WithdrawalRequest, approve: Boolean): Result<Unit> = runCatching {
        val adminUid = requireUid()
        val batch = db.batch()
        batch.update(
            db.collection("withdrawalRequests").document(request.id),
            mapOf(
                "status" to if (approve) "approved" else "rejected",
                "reviewedBy" to adminUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        if (approve) {
            val walletRef = db.collection("wallets").document(request.userId)
            val walletSnap = walletRef.get().await()
            val curPts = walletSnap.getLong("points") ?: 0L
            val curBal = walletSnap.getDouble("balanceUsd") ?: 0.0
            batch.update(
                walletRef,
                mapOf(
                    "points" to (curPts - request.pointsSpent).coerceAtLeast(0L),
                    "balanceUsd" to (curBal - request.amountUsd).coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                db.collection("transactions").document(),
                mapOf(
                    "userId" to request.userId,
                    "type" to "withdrawal",
                    "pointsDelta" to -request.pointsSpent,
                    "amountDelta" to -request.amountUsd,
                    "description" to "Withdrawal via ${request.method}",
                    "status" to "completed",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "withdrawalRequests/${request.id}", auth)
    }

    suspend fun saveAdminSettings(
        pointValueUsd: Double,
        minWithdrawalUsd: Double,
        rewardsEnabled: Boolean,
        maxVideoDurationSec: Long
    ): Result<Unit> = runCatching {
        val adminUid = requireUid()
        db.collection("adminSettings").document("global").set(
            mapOf(
                "pointValueUsd" to pointValueUsd.coerceAtLeast(0.0001),
                "minWithdrawalUsd" to minWithdrawalUsd.coerceAtLeast(1.0),
                "rewardsEnabled" to rewardsEnabled,
                "maxVideoDurationSec" to maxVideoDurationSec.coerceIn(15L, 300L),
                "updatedBy" to adminUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "adminSettings/global", auth)
    }

    suspend fun assignAdminRole(targetUid: String, email: String, role: String): Result<Unit> = runCatching {
        val adminUid = requireUid()
        db.collection("admins").document(targetUid).set(
            mapOf(
                "userId" to targetUid,
                "email" to email.trim().take(200),
                "role" to role,
                "assignedBy" to adminUid,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "admins/$targetUid", auth)
    }

    suspend fun removeAdminRole(targetUid: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("admins").document(targetUid).delete().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "admins/$targetUid", auth)
    }
}
