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
    private val albumId: Long
) : AndroidViewModel(application) {
    
    private val database = PhotoVaultDatabase.getDatabase(application)
    private val photoDao = database.photoDao()
    private val albumDao = database.albumDao()
    private val fileManager = FileManager(application)
    
    val photos: LiveData<List<Photo>> = photoDao.getPhotosByAlbum(albumId)
    
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error
    
    private suspend fun getCurrentCoverPhotoPath(): String? {
        return albumDao.getCoverPhotoSync(albumId)
    }
    
    private suspend fun updateCoverPhotoAfterDeletion() {
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
                // Get current cover photo path before deleting
                val currentCoverPhotoPath = getCurrentCoverPhotoPath()
                val isCoverPhotoBeingDeleted = currentCoverPhotoPath != null && currentCoverPhotoPath == photo.filePath
                
                if (isCoverPhotoBeingDeleted) {
                    android.util.Log.d("PhotoViewViewModel", "Cover photo is being deleted: ${photo.originalName}")
                }
                
                // Delete file from storage
                fileManager.deletePhotoFromAppStorage(photo.filePath)
                
                // Delete from database
                photoDao.deletePhoto(photo)
                
                // If cover photo was deleted, set new cover photo
                if (isCoverPhotoBeingDeleted) {
                    updateCoverPhotoAfterDeletion()
                }
                
                // Update album photo count
                albumDao.updatePhotoCount(albumId)
                
            } catch (e: Exception) {
                android.util.Log.e("PhotoViewViewModel", "Failed to delete photo: ${e.message}")
                _error.value = "Failed to delete photo: ${e.message}"
            }
        }
    }
    
    class Factory(
        private val application: Application,
        private val albumId: Long
    ) : ViewModelProvider.Factory {
        
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PhotoViewViewModel::class.java)) {
                return PhotoViewViewModel(application, albumId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

