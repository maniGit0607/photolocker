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

class AlbumViewViewModel(
    application: Application,
    private val albumId: Long
) : AndroidViewModel(application) {
    
    private val database = PhotoVaultDatabase.getDatabase(application)
    private val photoDao = database.photoDao()
    private val albumDao = database.albumDao()
    private val fileManager = FileManager(application)
    
    // Use MutableLiveData instead of direct Room LiveData for better control
    val photos: LiveData<List<Photo>> = photoDao.getPhotosByAlbum(albumId)
    
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error
    
    fun refreshPhotos() {
        viewModelScope.launch {
            try {
                android.util.Log.d("AlbumViewViewModel", "Refreshing photos for album $albumId")
                
                // Update album photo count only
                albumDao.updatePhotoCount(albumId)
                
                // Reload photos from database and update LiveData
                val currentPhotos = photoDao.getPhotosByAlbumSync(albumId)
                android.util.Log.d("AlbumViewViewModel", "Album now has ${currentPhotos.size} photos")
                
            } catch (e: Exception) {
                android.util.Log.e("AlbumViewViewModel", "Failed to refresh photos: ${e.message}")
                _error.value = "Failed to refresh photos: ${e.message}"
            }
        }
    }
    
    fun setCoverPhoto(photo: Photo) {
        viewModelScope.launch {
            try {
                albumDao.updateCoverPhoto(albumId, photo.filePath)
                android.util.Log.d("AlbumViewViewModel", "Cover photo updated to: ${photo.filePath}")
            } catch (e: Exception) {
                android.util.Log.e("AlbumViewViewModel", "Failed to set cover photo: ${e.message}")
                _error.value = "Failed to set cover photo: ${e.message}"
            }
        }
    }
    
    fun getCoverPhoto(): LiveData<String?> {
        return albumDao.getCoverPhoto(albumId)
    }
    
    private suspend fun getCurrentCoverPhotoPath(): String? {
        return albumDao.getCoverPhotoSync(albumId)
    }
    
    private suspend fun updateCoverPhotoAfterMove() {
        try {
            // Get the first photo remaining in the album
            val firstPhoto = photoDao.getFirstPhotoInAlbum(albumId)
            
            if (firstPhoto != null) {
                // Set the first photo as the new cover photo
                albumDao.updateCoverPhoto(albumId, firstPhoto.filePath)
                android.util.Log.d("AlbumViewViewModel", "Updated cover photo to: ${firstPhoto.originalName}")
            } else {
                // No photos remaining in album, clear cover photo
                albumDao.updateCoverPhoto(albumId, null)
                android.util.Log.d("AlbumViewViewModel", "No photos remaining in album, cleared cover photo")
            }
        } catch (e: Exception) {
            android.util.Log.e("AlbumViewViewModel", "Failed to update cover photo after move: ${e.message}")
            _error.value = "Failed to update cover photo: ${e.message}"
        }
    }
    
    fun movePhotosToBin(photoIds: List<Long>) {
        viewModelScope.launch {
            try {
                // Get current cover photo path before moving photos
                val currentCoverPhotoPath = getCurrentCoverPhotoPath()
                var isCoverPhotoBeingMoved = false
                
                for (photoId in photoIds) {
                    val photo = photoDao.getPhotoById(photoId)
                    photo?.let {
                        // Check if this photo is the current cover photo
                        if (currentCoverPhotoPath != null && currentCoverPhotoPath == it.filePath) {
                            isCoverPhotoBeingMoved = true
                            android.util.Log.d("AlbumViewViewModel", "Cover photo is being moved to bin: ${it.originalName}")
                        }
                        
                        // Move photo to bin (mark as deleted instead of actually deleting)
                        photoDao.movePhotoToBin(photoId)
                        android.util.Log.d("AlbumViewViewModel", "Moved photo to bin: ${it.originalName}")
                    }
                }
                
                // If cover photo was moved, set new cover photo
                if (isCoverPhotoBeingMoved) {
                    updateCoverPhotoAfterMove()
                }
                
                // Update album photo count
                albumDao.updatePhotoCount(albumId)
                
                // Refresh the photos list
                refreshPhotos()
                
            } catch (e: Exception) {
                android.util.Log.e("AlbumViewViewModel", "Failed to move photos to bin: ${e.message}")
                _error.value = "Failed to move photos to bin: ${e.message}"
            }
        }
    }
    
    class Factory(
        private val application: Application,
        private val albumId: Long
    ) : ViewModelProvider.Factory {
        
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AlbumViewViewModel::class.java)) {
                return AlbumViewViewModel(application, albumId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

