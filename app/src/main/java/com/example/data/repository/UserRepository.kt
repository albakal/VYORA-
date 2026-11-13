package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.FollowEdge
import com.example.data.model.FollowRequest
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

class UserRepository(
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

    fun observeUser(userId: String): Flow<UserProfile?> =
        db.collection("users").document(userId)
            .snapshots()
            .map { it.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                handleFirestoreError(error, OperationType.GET, "users/$userId", auth)
                throw error
            }

    fun observeActiveUsers(): Flow<List<UserProfile>> =
        db.collection("users")
            .whereEqualTo("isBanned", false)
            .limit(60)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.followersCount }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "users", auth)
                throw error
            }

    fun observeFollowingIds(userId: String): Flow<Set<String>> =
        db.collection("users").document(userId).collection("following")
            .snapshots()
            .map { snap -> snap.documents.map { it.id }.toSet() }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "users/$userId/following", auth)
                throw error
            }

    fun observeFollowers(userId: String): Flow<List<FollowEdge>> =
        db.collection("users").document(userId).collection("followers")
            .snapshots()
            .map { it.toObjects(FollowEdge::class.java, ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "users/$userId/followers", auth)
                throw error
            }

    fun observeFollowing(userId: String): Flow<List<FollowEdge>> =
        db.collection("users").document(userId).collection("following")
            .snapshots()
            .map { it.toObjects(FollowEdge::class.java, ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "users/$userId/following", auth)
                throw error
            }

    fun observeBlockedIds(userId: String): Flow<Set<String>> =
        db.collection("users").document(userId).collection("blocked")
            .snapshots()
            .map { snap -> snap.documents.map { it.id }.toSet() }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "users/$userId/blocked", auth)
                throw error
            }

    fun observeIncomingFollowRequests(userId: String): Flow<List<FollowRequest>> =
        db.collection("followRequests")
            .whereEqualTo("targetId", userId)
            .whereEqualTo("status", "pending")
            .snapshots()
            .map { snap ->
                snap.toObjects(FollowRequest::class.java, ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                handleFirestoreError(error, OperationType.LIST, "followRequests", auth)
                throw error
            }

    suspend fun getUser(userId: String): Result<UserProfile?> = runCatching {
        val snap = db.collection("users").document(userId).get().await()
        snap.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.GET, "users/$userId", auth)
    }

    suspend fun ensureUserProfile(
        uid: String,
        email: String,
        displayName: String?,
        avatarUrl: String?
    ): Result<UserProfile> = runCatching {
        val userRef = db.collection("users").document(uid)
        val snap = userRef.get().await()
        val existing = snap.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)

        // Isolate PII into users/{uid}/private/account
        val privatePayload = mapOf(
            "userId" to uid,
            "email" to (email.ifBlank { "$uid@vyora.app" }),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(uid).collection("private").document("account")
            .set(privatePayload).await()

        // Ensure wallet exists at 0 balance
        val walletRef = db.collection("wallets").document(uid)
        val walletSnap = walletRef.get().await()
        if (!walletSnap.exists()) {
            walletRef.set(
                mapOf(
                    "userId" to uid,
                    "points" to 0L,
                    "balanceUsd" to 0.0,
                    "totalEarnedUsd" to 0.0,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        if (existing != null) {
            existing
        } else {
            val baseName = (displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@").ifBlank { "vyora_user" })
            val cleanHandle = baseName.lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .trim('_')
                .ifBlank { "creator_${uid.take(6)}" }
                .take(30)

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "username" to cleanHandle,
                "displayName" to baseName.take(60),
                "bio" to "",
                "avatarUrl" to (avatarUrl ?: ""),
                "isPrivate" to false,
                "isBanned" to false,
                "followersCount" to 0L,
                "followingCount" to 0L,
                "likesCount" to 0L,
                "interests" to listOf("comedy", "music", "tech"),
                "language" to "ar",
                "themeMode" to "dark",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userRef.set(payload).await()
            userRef.get().await().toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(id = uid, userId = uid, username = cleanHandle, displayName = baseName)
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/$uid", auth)
    }

    suspend fun updateProfile(
        username: String,
        displayName: String,
        bio: String,
        avatarUrl: String,
        isPrivate: Boolean,
        interests: List<String>? = null
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val updates = mutableMapOf<String, Any>(
            "username" to username.trim().lowercase().take(40),
            "displayName" to displayName.trim().take(60),
            "bio" to bio.trim().take(250),
            "avatarUrl" to avatarUrl.trim(),
            "isPrivate" to isPrivate,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (interests != null) {
            updates["interests"] = interests.take(20)
        }
        db.collection("users").document(uid).update(updates).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}", auth)
    }

    suspend fun updatePreferences(language: String, themeMode: String): Result<Unit> = runCatching {
        val uid = requireUid()
        db.collection("users").document(uid).update(
            mapOf(
                "language" to language,
                "themeMode" to themeMode,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}", auth)
    }

    suspend fun followOrRequestUser(currentUser: UserProfile, targetUser: UserProfile): Result<String> = runCatching {
        val uid = requireUid()
        require(uid != targetUser.userId) { "Cannot follow yourself" }

        if (targetUser.isPrivate) {
            val reqId = "${uid}_${targetUser.userId}"
            val reqRef = db.collection("followRequests").document(reqId)
            val existing = reqRef.get().await()
            if (!existing.exists()) {
                reqRef.set(
                    mapOf(
                        "requesterId" to uid,
                        "requesterUsername" to currentUser.username,
                        "requesterAvatarUrl" to currentUser.avatarUrl,
                        "targetId" to targetUser.userId,
                        "status" to "pending",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            "requested"
        } else {
            val batch = db.batch()
            val followingRef = db.collection("users").document(uid).collection("following").document(targetUser.userId)
            val followerRef = db.collection("users").document(targetUser.userId).collection("followers").document(uid)
            val currentUserRef = db.collection("users").document(uid)
            val targetUserRef = db.collection("users").document(targetUser.userId)

            batch.set(
                followingRef,
                mapOf(
                    "userId" to targetUser.userId,
                    "username" to targetUser.username,
                    "avatarUrl" to targetUser.avatarUrl,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                followerRef,
                mapOf(
                    "userId" to uid,
                    "username" to currentUser.username,
                    "avatarUrl" to currentUser.avatarUrl,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(currentUserRef, mapOf("followingCount" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp()))
            batch.update(targetUserRef, mapOf("followersCount" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp()))

            val notifRef = db.collection("notifications").document()
            batch.set(
                notifRef,
                mapOf(
                    "recipientId" to targetUser.userId,
                    "actorId" to uid,
                    "actorUsername" to currentUser.username,
                    "actorAvatarUrl" to currentUser.avatarUrl,
                    "type" to "follow",
                    "targetId" to uid,
                    "message" to "@${currentUser.username} started following you",
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            "followed"
        }
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${targetUser.userId}/followers", auth)
    }

    suspend fun unfollowUser(targetUserId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        val batch = db.batch()
        batch.delete(db.collection("users").document(uid).collection("following").document(targetUserId))
        batch.delete(db.collection("users").document(targetUserId).collection("followers").document(uid))
        batch.update(db.collection("users").document(uid), mapOf("followingCount" to FieldValue.increment(-1), "updatedAt" to FieldValue.serverTimestamp()))
        batch.update(db.collection("users").document(targetUserId), mapOf("followersCount" to FieldValue.increment(-1), "updatedAt" to FieldValue.serverTimestamp()))
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.DELETE, "users/$targetUserId/followers", auth)
    }

    suspend fun respondToFollowRequest(request: FollowRequest, currentUser: UserProfile, accept: Boolean): Result<Unit> = runCatching {
        val uid = requireUid()
        require(request.targetId == uid)
        val batch = db.batch()
        val reqRef = db.collection("followRequests").document(request.id)
        batch.update(
            reqRef,
            mapOf(
                "status" to if (accept) "accepted" else "rejected",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        if (accept) {
            batch.set(
                db.collection("users").document(uid).collection("followers").document(request.requesterId),
                mapOf(
                    "userId" to request.requesterId,
                    "username" to request.requesterUsername,
                    "avatarUrl" to request.requesterAvatarUrl,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(uid),
                mapOf("followersCount" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp())
            )
        }
        batch.commit().await()
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.UPDATE, "followRequests/${request.id}", auth)
    }

    suspend fun toggleBlockUser(targetUserId: String, currentlyBlocked: Boolean): Result<Unit> = runCatching {
        val uid = requireUid()
        val ref = db.collection("users").document(uid).collection("blocked").document(targetUserId)
        if (currentlyBlocked) {
            ref.delete().await()
        } else {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "targetId" to targetUserId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Unit
    }.onFailure { e ->
        if (e is CancellationException) throw e
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/blocked/$targetUserId", auth)
    }
}
