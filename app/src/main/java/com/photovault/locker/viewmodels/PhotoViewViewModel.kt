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
    
    fun deletePhoto(photo: Photo) {
        viewModelScope.launch {
            try {
                // Delete file from storage
                fileManager.deletePhotoFromAppStorage(photo.filePath)
                
                // Delete from database
                photoDao.deletePhoto(photo)
                
                // Update album photo count
                albumDao.updatePhotoCount(albumId)
                
            } catch (e: Exception) {
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

