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
    
    fun deletePhotoFromGallery(uri: Uri): Boolean {
        return try {
            // Use MediaStore API for scoped storage (Android 10+)
            // This works for photos without needing MANAGE_EXTERNAL_STORAGE
            val rowsDeleted = context.contentResolver.delete(uri, null, null)
            
            if (rowsDeleted > 0) {
                Log.d(TAG, "Successfully deleted photo from gallery: $uri")
                true
            } else {
                Log.w(TAG, "No rows deleted for URI: $uri")
                false
            }
        } catch (e: SecurityException) {
            // On Android 10+ (API 29+), if the app doesn't own the media,
            // we need user permission. This is handled automatically by the system
            // through RecoverableSecurityException for API 29+, but since we're
            // importing photos that the user selected, we should have permission.
            Log.e(TAG, "SecurityException when deleting photo from gallery: $uri", e)
            
            // For Android 10+, attempt to handle RecoverableSecurityException
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (e is android.app.RecoverableSecurityException) {
                    Log.w(TAG, "RecoverableSecurityException - user permission required: $uri")
                    // The calling activity should handle this via IntentSender
                    // For now, we'll return false as we can't get permission here
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting photo from gallery: $uri", e)
            false
        }
    }
    
    /**
     * Delete multiple photos from gallery using MediaStore API batch operations (Android 11+)
     * This is the most efficient method for Android 11+ devices
     */
    fun deleteMultiplePhotosFromGallery(uris: List<Uri>): BatchGalleryDeletionResult {
        return try {
            Log.d(TAG, "Attempting to delete ${uris.size} photos from gallery using batch operation")
            Log.d(TAG, "Android version: ${Build.VERSION.SDK_INT}, API level: ${Build.VERSION_CODES.R}")
            
            // Use MediaStore batch delete API (Android 11+)
            val deleteRequest = MediaStore.createDeleteRequest(context.contentResolver, uris)
            
            Log.d(TAG, "MediaStore batch delete request created for ${uris.size} photos")
            
            // The system will handle the permission request automatically
            // We need to return the pending intent for the user to approve
            val pendingIntent = deleteRequest
            val intentSender = pendingIntent.intentSender
            
            Log.d(TAG, "IntentSender created for batch deletion")
            BatchGalleryDeletionResult.PermissionRequired(listOf(intentSender))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating batch delete request: ${e.message}", e)
            BatchGalleryDeletionResult.Failed("Batch delete request failed: ${e.message}")
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
    
    fun deletePhotoFromAppStorage(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting photo from app storage", e)
            false
        }
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
    
    fun getFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.available().toLong()
            } ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file size", e)
            0L
        }
    }
    
    fun getFileName(uri: Uri): String {
        var fileName = "unknown"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file name", e)
        }
        return fileName
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
    
    fun getAlbumDirectory(albumName: String): File {
        return File(photosDirectory, albumName).apply {
            if (!exists()) mkdirs()
        }
    }
}

