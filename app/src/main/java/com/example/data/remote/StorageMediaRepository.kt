package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

sealed interface UploadStatus {
    data class Progress(val percent: Int) : UploadStatus
    data class Completed(val downloadUrl: String, val thumbnailUrl: String = "") : UploadStatus
    data class Failed(val message: String) : UploadStatus
}

class StorageMediaRepository(
    private val storage: FirebaseStorage = Firebase.storage
) {
    fun uploadVideoWithThumbnail(
        context: Context,
        userId: String,
        videoUri: Uri,
        customCoverUri: Uri? = null
    ): Flow<UploadStatus> = callbackFlow {
        val fileId = UUID.randomUUID().toString()
        val videoRef = storage.reference.child("videos/$userId/$fileId.mp4")
        val thumbRef = storage.reference.child("thumbnails/$userId/$fileId.jpg")

        val uploadTask = videoRef.putFile(videoUri)
        uploadTask.addOnProgressListener { snapshot ->
            val total = snapshot.totalByteCount
            if (total > 0) {
                val pct = ((100.0 * snapshot.bytesTransferred) / total).toInt().coerceIn(0, 95)
                trySend(UploadStatus.Progress(pct))
            }
        }.addOnSuccessListener {
            videoRef.downloadUrl.addOnSuccessListener { videoDownloadUri ->
                // Upload thumbnail in background
                trySend(UploadStatus.Progress(97))
                if (customCoverUri != null) {
                    thumbRef.putFile(customCoverUri).addOnSuccessListener {
                        thumbRef.downloadUrl.addOnSuccessListener { thumbDownloadUri ->
                            trySend(UploadStatus.Completed(videoDownloadUri.toString(), thumbDownloadUri.toString()))
                            close()
                        }.addOnFailureListener {
                            trySend(UploadStatus.Completed(videoDownloadUri.toString(), ""))
                            close()
                        }
                    }.addOnFailureListener {
                        trySend(UploadStatus.Completed(videoDownloadUri.toString(), ""))
                        close()
                    }
                } else {
                    val thumbBytes = extractVideoFrameBytes(context, videoUri)
                    if (thumbBytes != null) {
                        thumbRef.putBytes(thumbBytes).addOnSuccessListener {
                            thumbRef.downloadUrl.addOnSuccessListener { thumbDownloadUri ->
                                trySend(UploadStatus.Completed(videoDownloadUri.toString(), thumbDownloadUri.toString()))
                                close()
                            }.addOnFailureListener {
                                trySend(UploadStatus.Completed(videoDownloadUri.toString(), ""))
                                close()
                            }
                        }.addOnFailureListener {
                            trySend(UploadStatus.Completed(videoDownloadUri.toString(), ""))
                            close()
                        }
                    } else {
                        trySend(UploadStatus.Completed(videoDownloadUri.toString(), ""))
                        close()
                    }
                }
            }.addOnFailureListener { e ->
                trySend(UploadStatus.Failed(e.localizedMessage ?: "Failed to resolve video URL"))
                close()
            }
        }.addOnFailureListener { e ->
            trySend(UploadStatus.Failed(e.localizedMessage ?: "Video upload failed (verify Firebase Storage bucket is active)"))
            close()
        }

        awaitClose {
            if (!uploadTask.isComplete) {
                uploadTask.cancel()
            }
        }
    }

    suspend fun uploadImage(userId: String, folder: String, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val ref = storage.reference.child("$folder/$userId/${UUID.randomUUID()}.jpg")
            ref.putFile(uri).await()
            ref.downloadUrl.await().toString()
        }
    }

    suspend fun uploadMediaFile(userId: String, folder: String, uri: Uri, extension: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val ref = storage.reference.child("$folder/$userId/${UUID.randomUUID()}.$extension")
                ref.putFile(uri).await()
                ref.downloadUrl.await().toString()
            }
        }

    private fun extractVideoFrameBytes(context: Context, videoUri: Uri): ByteArray? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            val bitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            if (bitmap != null) {
                val scaled = Bitmap.createScaledBitmap(bitmap, 480, (480 * bitmap.height / bitmap.width.coerceAtLeast(1)).coerceAtLeast(480), true)
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 78, baos)
                baos.toByteArray()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
