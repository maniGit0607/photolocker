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
    
    val photos: LiveData<List<Photo>> = photoDao.getPhotosByAlbum(albumId)
    
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error
    
    fun deletePhotos(photoIds: List<Long>) {
        viewModelScope.launch {
            try {
                for (photoId in photoIds) {
                    val photo = photoDao.getPhotoById(photoId)
                    photo?.let {
                        // Delete file from storage
                        fileManager.deletePhotoFromAppStorage(it.filePath)
                        
                        // Delete from database
                        photoDao.deletePhotoById(photoId)
                    }
                }
                
                // Update album photo count and cover
                albumDao.updatePhotoCount(albumId)
                val firstPhoto = photoDao.getFirstPhotoInAlbum(albumId)
                albumDao.updateCoverPhoto(albumId, firstPhoto?.filePath)
                
            } catch (e: Exception) {
                _error.value = "Failed to delete photos: ${e.message}"
            }
        }
    }
    
    fun refreshPhotos() {
        viewModelScope.launch {
            try {
                android.util.Log.d("AlbumViewViewModel", "Refreshing photos for album $albumId")
                
                // Update album photo count and cover
                albumDao.updatePhotoCount(albumId)
                val firstPhoto = photoDao.getFirstPhotoInAlbum(albumId)
                albumDao.updateCoverPhoto(albumId, firstPhoto?.filePath)
                
                // Check current photo count
                val currentPhotos = photoDao.getPhotosByAlbumSync(albumId)
                android.util.Log.d("AlbumViewViewModel", "Album now has ${currentPhotos.size} photos")
                
            } catch (e: Exception) {
                android.util.Log.e("AlbumViewViewModel", "Failed to refresh photos: ${e.message}")
                _error.value = "Failed to refresh photos: ${e.message}"
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

