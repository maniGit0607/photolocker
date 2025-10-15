package com.photovault.locker.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
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
            // First try the standard content resolver deletion
            val rowsDeleted = context.contentResolver.delete(uri, null, null)
            val success = rowsDeleted > 0
            
            if (success) {
                Log.d(TAG, "Successfully deleted photo from gallery: $uri")
                return true
            } else {
                Log.w(TAG, "Standard deletion failed, trying alternative method: $uri")
            }
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied when deleting photo from gallery: $uri", e)
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

