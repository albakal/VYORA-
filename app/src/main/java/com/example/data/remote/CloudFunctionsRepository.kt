package com.example.data.remote

import com.google.firebase.Firebase
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.functions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

data class AgoraLiveSession(
    val appId: String,
    val token: String,
    val channelName: String,
    val rtcUid: Int = 0,
    val role: String = "subscriber"
)

data class SupabaseWalletSummary(
    val supabaseConfigured: Boolean,
    val synced: Boolean,
    val points: Long,
    val balanceUsd: Double,
    val totalEarnedUsd: Double,
    val transactionsCount: Int,
    val videosCount: Int
)

class CloudFunctionsRepository(
    private val functions: FirebaseFunctions = Firebase.functions("us-central1")
) {
    suspend fun fetchAgoraLiveSession(streamId: String): Result<AgoraLiveSession> = runCatching {
        val result = functions
            .getHttpsCallable("getAgoraLiveToken")
            .call(mapOf("streamId" to streamId))
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?>
            ?: throw IllegalStateException("Empty response from getAgoraLiveToken")

        val appId = (data["appId"] as? String).orEmpty().trim()
        val token = (data["token"] as? String).orEmpty().trim()
        val channelName = (data["channelName"] as? String).orEmpty().trim()
        val rtcUid = (data["rtcUid"] as? Number)?.toInt() ?: 0
        val role = (data["role"] as? String).orEmpty().ifBlank { "subscriber" }

        if (appId.isBlank() || token.isBlank() || channelName.isBlank()) {
            throw IllegalStateException("Incomplete Agora session payload")
        }

        AgoraLiveSession(
            appId = appId,
            token = token,
            channelName = channelName,
            rtcUid = rtcUid,
            role = role
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
    }

    suspend fun syncUserAndVideoToSupabase(videoId: String? = null): Result<Boolean> = runCatching {
        val payload = if (videoId.isNullOrBlank()) emptyMap() else mapOf("videoId" to videoId)
        val result = functions
            .getHttpsCallable("syncUserAndVideoToSupabase")
            .call(payload)
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?>
        (data?.get("synced") as? Boolean) ?: false
    }.onFailure { e ->
        if (e is CancellationException) throw e
    }

    suspend fun fetchWalletAccountingSummary(): Result<SupabaseWalletSummary> = runCatching {
        val result = functions
            .getHttpsCallable("getWalletAccountingSummary")
            .call()
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?>
            ?: throw IllegalStateException("Empty response from getWalletAccountingSummary")

        SupabaseWalletSummary(
            supabaseConfigured = (data["supabaseConfigured"] as? Boolean) ?: false,
            synced = (data["synced"] as? Boolean) ?: false,
            points = (data["points"] as? Number)?.toLong() ?: 0L,
            balanceUsd = (data["balanceUsd"] as? Number)?.toDouble() ?: 0.0,
            totalEarnedUsd = (data["totalEarnedUsd"] as? Number)?.toDouble() ?: 0.0,
            transactionsCount = (data["transactionsCount"] as? Number)?.toInt() ?: 0,
            videosCount = (data["videosCount"] as? Number)?.toInt() ?: 0
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
    }

    suspend fun recordVideoViewServer(videoId: String): Result<Boolean> = runCatching {
        val result = functions
            .getHttpsCallable("recordVideoViewReward")
            .call(mapOf("videoId" to videoId))
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?>
        (data?.get("counted") as? Boolean) ?: true
    }.onFailure { e ->
        if (e is CancellationException) throw e
    }

    suspend fun submitWithdrawalServer(
        amountUsd: Double,
        method: String,
        accountDetails: String
    ): Result<String> = runCatching {
        val result = functions
            .getHttpsCallable("requestCreatorWithdrawal")
            .call(
                mapOf(
                    "amountUsd" to amountUsd,
                    "method" to method,
                    "accountDetails" to accountDetails
                )
            )
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?>
        (data?.get("requestId") as? String).orEmpty()
    }.onFailure { e ->
        if (e is CancellationException) throw e
    }

    suspend fun adminGrantPointsServer(
        targetUid: String,
        pointsDelta: Long,
        reason: String
    ): Result<Boolean> = runCatching {
        val result = functions
            .getHttpsCallable("adminGrantCreatorPoints")
            .call(
                mapOf(
                    "targetUid" to targetUid,
                    "pointsDelta" to pointsDelta,
                    "reason" to reason
                )
            )
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?>
        (data?.get("ok") as? Boolean) ?: true
    }.onFailure { e ->
        if (e is CancellationException) throw e
    }

    suspend fun adminReviewWithdrawalServer(
        requestId: String,
        approve: Boolean
    ): Result<Boolean> = runCatching {
        val result = functions
            .getHttpsCallable("adminReviewWithdrawal")
            .call(
                mapOf(
                    "requestId" to requestId,
                    "approve" to approve
                )
            )
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.data as? Map<String, Any?>
        (data?.get("ok") as? Boolean) ?: true
    }.onFailure { e ->
        if (e is CancellationException) throw e
    }

    fun isFunctionUnavailable(error: Throwable): Boolean {
        val fnEx = error as? FirebaseFunctionsException ?: return false
        return fnEx.code == FirebaseFunctionsException.Code.NOT_FOUND ||
            fnEx.code == FirebaseFunctionsException.Code.UNAVAILABLE ||
            fnEx.code == FirebaseFunctionsException.Code.UNIMPLEMENTED
    }
}
