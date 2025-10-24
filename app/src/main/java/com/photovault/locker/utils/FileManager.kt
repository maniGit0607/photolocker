package com.photovault.locker.utils

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class FileManager(private val context: Context) {
    
    companion object {
        private const val TAG = "FileManager"
        private const val PHOTOS_DIRECTORY = "PhotoVault"
    }
    
    private val photosDirectory: File by lazy {
        File(context.getExternalFilesDir(null), PHOTOS_DIRECTORY).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    fun copyPhotoToAppStorage(uri: Uri, albumName: String): String? {
        return try {
            val albumDir = File(photosDirectory, albumName).apply {
                if (!exists()) mkdirs()
            }
            
            val fileName = generateUniqueFileName()
            val destinationFile = File(albumDir, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            destinationFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error copying photo to app storage", e)
            null
        }
    }
    
    /**
     * Delete multiple photos from gallery using MediaStore API batch operations (Android 11+)
     * This is the most efficient method for Android 11+ devices
     */
    fun deleteMultiplePhotosFromGalleryWithFallback(uris: List<Uri>): BatchGalleryDeletionResult {
        return try {
            Log.d(TAG, "Attempting to delete ${uris.size} photos using MediaStore batch API")
            Log.d(TAG, "Android version: ${Build.VERSION.SDK_INT}, API level: ${Build.VERSION_CODES.R}")
            
            // First, try to delete photos directly without permission
            var successCount = 0
            val remainingUris = mutableListOf<Uri>()
            
            for (uri in uris) {
                try {
                    val rowsDeleted = context.contentResolver.delete(uri, null, null)
                    if (rowsDeleted > 0) {
                        successCount++
                        Log.d(TAG, "Successfully deleted photo directly: $uri")
                    } else {
                        remainingUris.add(uri)
                        Log.w(TAG, "No rows deleted for URI: $uri")
                    }
                } catch (e: SecurityException) {
                    remainingUris.add(uri)
                    Log.w(TAG, "SecurityException for URI: $uri - will request permission")
                } catch (e: Exception) {
                    remainingUris.add(uri)
                    Log.w(TAG, "Exception for URI: $uri - ${e.message}")
                }
            }
            
            Log.d(TAG, "Direct deletion completed. Success: $successCount, Remaining: ${remainingUris.size}")
            
            if (remainingUris.isEmpty()) {
                // All photos deleted successfully
                BatchGalleryDeletionResult.Success(successCount, 0)
            } else {
                // Some photos need permission, use MediaStore batch API
                try {
                    Log.d(TAG, "Creating MediaStore batch delete request for ${remainingUris.size} remaining photos")
                    val deleteRequest = MediaStore.createDeleteRequest(context.contentResolver, remainingUris)
                    
                    Log.d(TAG, "MediaStore batch delete request created")
                    
                    // Get the pending intent from the delete request
                    val pendingIntent = deleteRequest
                    val intentSender = pendingIntent.intentSender
                    
                    Log.d(TAG, "IntentSender created for batch deletion: ${intentSender != null}")
                    
                    if (intentSender != null) {
                        BatchGalleryDeletionResult.PermissionRequired(listOf(intentSender))
                    } else {
                        Log.e(TAG, "IntentSender is null - cannot request permission")
                        BatchGalleryDeletionResult.Failed("IntentSender is null")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating batch delete request: ${e.message}", e)
                    if (successCount > 0) {
                        BatchGalleryDeletionResult.Success(successCount, remainingUris.size)
                    } else {
                        BatchGalleryDeletionResult.Failed("Batch delete request failed: ${e.message}")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in deletion process: ${e.message}", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            BatchGalleryDeletionResult.Failed("Deletion failed: ${e.message}")
        }
    }
    
    /**
     * Enhanced method to delete photo from gallery with proper permission handling
     * Returns a result that includes whether permission is needed
     */
    fun deletePhotoFromGalleryWithPermission(uri: Uri): GalleryDeletionResult {
        return try {
            Log.d(TAG, "Attempting to delete photo from gallery: $uri")
            Log.d(TAG, "Android version: ${Build.VERSION.SDK_INT}, API level: ${Build.VERSION_CODES.R}")
            
            // Use MediaStore API for scoped storage (Android 11+)
            val rowsDeleted = context.contentResolver.delete(uri, null, null)
            
            Log.d(TAG, "ContentResolver.delete() returned: $rowsDeleted rows deleted")
            
            if (rowsDeleted > 0) {
                Log.d(TAG, "Successfully deleted photo from gallery: $uri")
                GalleryDeletionResult.Success
            } else {
                Log.w(TAG, "No rows deleted for URI: $uri")
                GalleryDeletionResult.Failed("No rows deleted")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException when deleting photo from gallery: $uri", e)
            Log.e(TAG, "SecurityException type: ${e.javaClass.simpleName}")
            Log.e(TAG, "SecurityException message: ${e.message}")
            
            // For Android 11+, handle RecoverableSecurityException
            if (e is android.app.RecoverableSecurityException) {
                Log.w(TAG, "RecoverableSecurityException - user permission required: $uri")
                val intentSender = e.userAction.actionIntent.intentSender
                Log.w(TAG, "IntentSender available: ${intentSender != null}")
                return GalleryDeletionResult.PermissionRequired(intentSender)
            }
            GalleryDeletionResult.Failed("SecurityException: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting photo from gallery: $uri", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            GalleryDeletionResult.Failed("Exception: ${e.message}")
        }
    }
    
    /**
     * Result of gallery deletion attempt
     */
    sealed class GalleryDeletionResult {
        object Success : GalleryDeletionResult()
        data class Failed(val reason: String) : GalleryDeletionResult()
        data class PermissionRequired(val intentSender: android.content.IntentSender) : GalleryDeletionResult()
    }
    
    /**
     * Result of batch gallery deletion attempt
     */
    sealed class BatchGalleryDeletionResult {
        data class Success(val deletedCount: Int, val failedCount: Int) : BatchGalleryDeletionResult()
        data class Failed(val reason: String) : BatchGalleryDeletionResult()
        data class PermissionRequired(val intentSenders: List<android.content.IntentSender>) : BatchGalleryDeletionResult()
    }
    
    fun getImageDimensions(filePath: String): Pair<Int, Int> {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)
            Pair(options.outWidth, options.outHeight)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting image dimensions", e)
            Pair(0, 0)
        }
    }
    
    private fun generateUniqueFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val random = (1000..9999).random()
        return "IMG_${timestamp}_$random.jpg"
    }
    
    fun deleteAlbumDirectory(albumName: String) {
        try {
            val albumDir = File(photosDirectory, albumName)
            if (albumDir.exists()) {
                albumDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting album directory", e)
        }
    }
}

