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
                
                // Get or create "Restored" album for orphaned photos
                val restoredAlbum = getOrCreateRestoredAlbum()
                
                // Check each photo and move to "Restored" album if original album doesn't exist
                for (photoId in photoIds) {
                    val photo = photoDao.getPhotoById(photoId)
                    if (photo != null) {
                        val album = albumDao.getAlbumById(photo.albumId)
                        if (album == null) {
                            // Original album doesn't exist, move to "Restored" album
                            android.util.Log.d("BinViewModel", "Album not found for photo ${photo.originalName}, moving to Restored album")
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
                    albumDao.updatePhotoCount(albumId)
                    val album = albumDao.getAlbumById(albumId)
                    if (album?.coverPhotoPath == null) {
                        albumDao.updateCoverPhoto(albumId, photoDao.getFirstPhotoInAlbum(albumId)?.filePath)
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
        val restoredAlbumName = "Restored"
        var restoredAlbum = albumDao.getAlbumByName(restoredAlbumName)
        
        if (restoredAlbum == null) {
            // Create "Restored" album
            val newAlbum = Album(
                name = restoredAlbumName,
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
