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
                
                // Restore photos in database
                photoDao.restorePhotosFromBin(photoIds)

                // Fetch each restored photo's albumId
                val albumIds = photoIds.mapNotNull { photoId ->
                    photoDao.getPhotoById(photoId)?.albumId
                }.distinct()

                for (albumId in albumIds)
                {
                    albumDao.updatePhotoCount(albumId)
                    val album = albumDao.getAlbumById(albumId)
                    if(album?.coverPhotoPath == null)
                    {
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
