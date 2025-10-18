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

