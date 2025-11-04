package com.photovault.locker.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.photovault.locker.database.PhotoVaultDatabase
import com.photovault.locker.models.Photo
import com.photovault.locker.utils.FileManager
import kotlinx.coroutines.launch

class PhotoViewViewModel(
    application: Application,
    private val albumId: Long,
    private val isFavoritesMode: Boolean = false
) : AndroidViewModel(application) {
    
    private val database = PhotoVaultDatabase.getDatabase(application)
    private val photoDao = database.photoDao()
    private val albumDao = database.albumDao()
    private val fileManager = FileManager(application)
    
    val photos: LiveData<List<Photo>> = if (isFavoritesMode) {
        photoDao.getFavoritePhotos()
    } else {
        photoDao.getPhotosByAlbum(albumId)
    }
    
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error
    
    private suspend fun getCurrentCoverPhotoPath(): String? {
        return if (!isFavoritesMode) albumDao.getCoverPhotoSync(albumId) else null
    }
    
    private suspend fun updateCoverPhotoAfterDeletion() {
        // Only update cover photo if not in favorites mode
        if (isFavoritesMode) return
        
        try {
            // Get the first photo remaining in the album
            val firstPhoto = photoDao.getFirstPhotoInAlbum(albumId)
            
            if (firstPhoto != null) {
                // Set the first photo as the new cover photo
                albumDao.updateCoverPhoto(albumId, firstPhoto.filePath)
                android.util.Log.d("PhotoViewViewModel", "Updated cover photo to: ${firstPhoto.originalName}")
            } else {
                // No photos remaining in album, clear cover photo
                albumDao.updateCoverPhoto(albumId, null)
                android.util.Log.d("PhotoViewViewModel", "No photos remaining in album, cleared cover photo")
            }
        } catch (e: Exception) {
            android.util.Log.e("PhotoViewViewModel", "Failed to update cover photo after deletion: ${e.message}")
            _error.value = "Failed to update cover photo: ${e.message}"
        }
    }
    
    fun deletePhoto(photo: Photo) {
        viewModelScope.launch {
            try {
                // Get current cover photo path before moving to bin
                val currentCoverPhotoPath = getCurrentCoverPhotoPath()
                val isCoverPhotoBeingDeleted = currentCoverPhotoPath != null && currentCoverPhotoPath == photo.filePath
                
                if (isCoverPhotoBeingDeleted) {
                    android.util.Log.d("PhotoViewViewModel", "Cover photo is being moved to bin: ${photo.originalName}")
                }
                
                // Move photo to bin instead of permanently deleting
                photoDao.movePhotoToBin(photo.id, System.currentTimeMillis())
                
                // If cover photo was deleted, set new cover photo
                if (isCoverPhotoBeingDeleted) {
                    updateCoverPhotoAfterDeletion()
                }
                
                // Update album photo count (only if not in favorites mode)
                if (!isFavoritesMode) {
                    albumDao.updatePhotoCount(albumId)
                }
                
            } catch (e: Exception) {
                android.util.Log.e("PhotoViewViewModel", "Failed to move photo to bin: ${e.message}")
                _error.value = "Failed to move photo to bin: ${e.message}"
            }
        }
    }
    
    fun toggleFavorite(photoId: Long, currentStatus: Boolean) {
        viewModelScope.launch {
            try {
                photoDao.updatePhotoFavoriteStatus(photoId, !currentStatus)
            } catch (e: Exception) {
                android.util.Log.e("PhotoViewViewModel", "Failed to toggle favorite: ${e.message}")
                _error.value = "Failed to toggle favorite: ${e.message}"
            }
        }
    }
    
    fun setCoverPhoto(photo: Photo) {
        viewModelScope.launch {
            try {
                // Only set cover photo if not in favorites mode
                if (!isFavoritesMode) {
                    albumDao.updateCoverPhoto(albumId, photo.filePath)
                    android.util.Log.d("PhotoViewViewModel", "Cover photo updated to: ${photo.filePath}")
                }
            } catch (e: Exception) {
                android.util.Log.e("PhotoViewViewModel", "Failed to set cover photo: ${e.message}")
                _error.value = "Failed to set cover photo: ${e.message}"
            }
        }
    }
    
    class Factory(
        private val application: Application,
        private val albumId: Long,
        private val isFavoritesMode: Boolean = false
    ) : ViewModelProvider.Factory {
        
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PhotoViewViewModel::class.java)) {
                return PhotoViewViewModel(application, albumId, isFavoritesMode) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

