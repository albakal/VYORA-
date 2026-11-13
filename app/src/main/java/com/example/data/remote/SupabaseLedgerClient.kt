package com.example.data.remote

import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SupabaseClientDiagnostics(
    val clientKeysConfigured: Boolean,
    val restReachable: Boolean,
    val publicCatalogCount: Int,
    val statusDetail: String
)

/**
 * Lightweight client-side connector using only SUPABASE_URL and the RLS-protected
 * SUPABASE_PUBLISHABLE_KEY supplied via BuildConfig (Secrets Panel).
 * Elevated writes and accounting mutations always execute through Firebase Cloud Functions.
 */
class SupabaseLedgerClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    private val baseUrl: String
        get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')

    private val publishableKey: String
        get() = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()

    val isClientConfigured: Boolean
        get() = baseUrl.startsWith("https://") &&
            !baseUrl.contains("YOUR_PROJECT_REF") &&
            publishableKey.isNotBlank() &&
            !publishableKey.contains("YOUR_SUPABASE")

    suspend fun checkPublicCatalogConnectivity(): SupabaseClientDiagnostics = withContext(Dispatchers.IO) {
        if (!isClientConfigured) {
            return@withContext SupabaseClientDiagnostics(
                clientKeysConfigured = false,
                restReachable = false,
                publicCatalogCount = 0,
                statusDetail = "Configure SUPABASE_URL & SUPABASE_PUBLISHABLE_KEY in AI Studio Secrets"
            )
        }

        try {
            val request = Request.Builder()
                .url("$baseUrl/rest/v1/vyora_videos_ledger?select=video_id&status=eq.active&limit=1")
                .addHeader("apikey", publishableKey)
                .addHeader("Authorization", "Bearer $publishableKey")
                .addHeader("Prefer", "count=exact")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext SupabaseClientDiagnostics(
                        clientKeysConfigured = true,
                        restReachable = false,
                        publicCatalogCount = 0,
                        statusDetail = "HTTP ${response.code}"
                    )
                }

                // Content-Range header format: "0-0/42" or "*/0"
                val contentRange = response.header("Content-Range").orEmpty()
                val totalCount = contentRange.substringAfter("/", "0").toIntOrNull() ?: 0
                SupabaseClientDiagnostics(
                    clientKeysConfigured = true,
                    restReachable = true,
                    publicCatalogCount = totalCount,
                    statusDetail = "Connected"
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SupabaseClientDiagnostics(
                clientKeysConfigured = true,
                restReachable = false,
                publicCatalogCount = 0,
                statusDetail = e.localizedMessage ?: "Network unavailable"
            )
        }
    }
}
