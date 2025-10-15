package com.photovault.locker.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.photovault.locker.database.PhotoVaultDatabase
import com.photovault.locker.models.Album
import com.photovault.locker.models.Photo
import com.photovault.locker.utils.Constants
import com.photovault.locker.utils.FileManager
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

class BinViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = PhotoVaultDatabase.getDatabase(application)
    private val photoDao = database.photoDao()
    private val albumDao = database.albumDao()
    private val fileManager = FileManager(application)
    
    val deletedPhotos: LiveData<List<Photo>> = photoDao.getDeletedPhotos()
    
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    fun refreshDeletedPhotos() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                android.util.Log.d("BinViewModel", "Refreshing deleted photos")
                // The LiveData will automatically update when the database changes
            } catch (e: Exception) {
                android.util.Log.e("BinViewModel", "Failed to refresh deleted photos: ${e.message}")
                _error.value = "Failed to refresh deleted photos: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun restorePhotos(photoIds: List<Long>) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                android.util.Log.d("BinViewModel", "Restoring ${photoIds.size} photos from bin")
                
                val dummyAlbum = albumDao.getAlbumByName(Constants.DUMMY_BIN_ALBUM_NAME)
                
                // First, check if there are any orphaned photos that need the "Restored" album
                val orphanedPhotoIds = mutableListOf<Long>()
                
                for (photoId in photoIds) {
                    val photo = photoDao.getPhotoById(photoId)
                    if (photo != null) {
                        val album = albumDao.getAlbumById(photo.albumId)
                        
                        // Check if photo is orphaned:
                        // 1. Album doesn't exist (null), OR
                        // 2. Album is the dummy bin album
                        if (album == null || (dummyAlbum != null && photo.albumId == dummyAlbum.id)) {
                            orphanedPhotoIds.add(photoId)
                        }
                    }
                }
                
                // Only create "Restored" album if there are orphaned photos
                if (orphanedPhotoIds.isNotEmpty()) {
                    val restoredAlbum = getOrCreateRestoredAlbum()
                    
                    // Move orphaned photos to "Restored" album
                    for (photoId in orphanedPhotoIds) {
                        val photo = photoDao.getPhotoById(photoId)
                        if (photo != null) {
                            android.util.Log.d("BinViewModel", "Album not found or dummy album for photo ${photo.originalName}, moving to Restored album")
                            val updatedPhoto = photo.copy(albumId = restoredAlbum.id)
                            photoDao.updatePhoto(updatedPhoto)
                        }
                    }
                }
                
                // Restore photos in database (set is_deleted = 0)
                photoDao.restorePhotosFromBin(photoIds)

                // Fetch each restored photo's albumId and update album info
                val albumIds = photoIds.mapNotNull { photoId ->
                    photoDao.getPhotoById(photoId)?.albumId
                }.distinct()

                for (albumId in albumIds) {
                    // Skip updating dummy album
                    if (dummyAlbum == null || albumId != dummyAlbum.id) {
                        albumDao.updatePhotoCount(albumId)
                        val album = albumDao.getAlbumById(albumId)
                        if (album?.coverPhotoPath == null) {
                            albumDao.updateCoverPhoto(albumId, photoDao.getFirstPhotoInAlbum(albumId)?.filePath)
                        }
                    }
                }
                
                android.util.Log.d("BinViewModel", "Successfully restored ${photoIds.size} photos")
                
            } catch (e: Exception) {
                android.util.Log.e("BinViewModel", "Failed to restore photos: ${e.message}")
                _error.value = "Failed to restore photos: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private suspend fun getOrCreateRestoredAlbum(): Album {
        var restoredAlbum = albumDao.getAlbumByName(Constants.RESTORED_ALBUM_NAME)
        
        if (restoredAlbum == null) {
            // Create "Restored" album
            val newAlbum = Album(
                name = Constants.RESTORED_ALBUM_NAME,
                createdDate = Date()
            )
            val albumId = albumDao.insertAlbum(newAlbum)
            restoredAlbum = albumDao.getAlbumById(albumId)
                ?: throw Exception("Failed to create Restored album")
            android.util.Log.d("BinViewModel", "Created 'Restored' album with id: ${restoredAlbum.id}")
        }
        
        return restoredAlbum
    }
    
    fun permanentlyDeletePhotos(photoIds: List<Long>) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                android.util.Log.d("BinViewModel", "Permanently deleting ${photoIds.size} photos")
                
                // Get photo details before deletion for file cleanup
                val photosToDelete = mutableListOf<Photo>()
                for (photoId in photoIds) {
                    val photo = photoDao.getPhotoById(photoId)
                    photo?.let { photosToDelete.add(it) }
                }
                
                // Delete photo files from storage
                for (photo in photosToDelete) {
                    try {
                        val file = File(photo.filePath)
                        if (file.exists()) {
                            val deleted = file.delete()
                            android.util.Log.d("BinViewModel", "File deletion ${if (deleted) "successful" else "failed"}: ${photo.originalName}")
                        } else {
                            android.util.Log.w("BinViewModel", "File not found: ${photo.originalName}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BinViewModel", "Failed to delete file: ${photo.originalName}, error: ${e.message}")
                    }
                }
                
                // Delete photos from database
                photoDao.permanentlyDeletePhotos(photoIds)
                
                android.util.Log.d("BinViewModel", "Successfully permanently deleted ${photoIds.size} photos")
                
            } catch (e: Exception) {
                android.util.Log.e("BinViewModel", "Failed to permanently delete photos: ${e.message}")
                _error.value = "Failed to permanently delete photos: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BinViewModel::class.java)) {
                return BinViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
