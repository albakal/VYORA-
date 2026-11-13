package com.example.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ApkExportHelper {
    const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    fun defaultApkFileName(): String = "VYORA-v${BuildConfig.VERSION_NAME}.apk"

    suspend fun copyInstalledApkToUri(context: Context, destinationUri: Uri): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sourcePath = context.applicationInfo.sourceDir
                    ?: throw IllegalStateException("Installed APK source path unavailable")
                val sourceFile = File(sourcePath)
                require(sourceFile.exists() && sourceFile.length() > 0L) {
                    "Installed APK file could not be read"
                }

                var totalBytes = 0L
                context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    sourceFile.inputStream().use { input ->
                        totalBytes = input.copyTo(out, bufferSize = 64 * 1024)
                    }
                    out.flush()
                } ?: throw IllegalStateException("Unable to open destination file stream")

                totalBytes
            }
        }

    suspend fun createShareApkIntent(context: Context): Result<Intent> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sourcePath = context.applicationInfo.sourceDir
                    ?: throw IllegalStateException("Installed APK source path unavailable")
                val sourceFile = File(sourcePath)
                require(sourceFile.exists() && sourceFile.length() > 0L) {
                    "Installed APK file could not be read"
                }

                val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val exportFile = File(exportsDir, defaultApkFileName())
                sourceFile.inputStream().use { input ->
                    exportFile.outputStream().use { out ->
                        input.copyTo(out, bufferSize = 64 * 1024)
                    }
                }

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    exportFile
                )

                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = APK_MIME_TYPE
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, defaultApkFileName())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                Intent.createChooser(sendIntent, defaultApkFileName()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
}
