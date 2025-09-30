package com.photovault.locker.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.photovault.locker.database.PhotoVaultDatabase
import com.photovault.locker.models.GalleryPhoto
import com.photovault.locker.models.Photo
import com.photovault.locker.utils.FileManager
import kotlinx.coroutines.launch
import java.util.Date

class PhotoImportViewModel(
    application: Application,
    private val albumId: Long,
    private val albumName: String
) : AndroidViewModel(application) {
    
    private val database = PhotoVaultDatabase.getDatabase(application)
    private val photoDao = database.photoDao()
    private val albumDao = database.albumDao()
    private val fileManager = FileManager(application)
    
    private val _importProgress = MutableLiveData<Int>()
    val importProgress: LiveData<Int> = _importProgress
    
    private val _importComplete = MutableLiveData<Pair<Boolean, Int>>()
    val importComplete: LiveData<Pair<Boolean, Int>> = _importComplete
    
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error
    
    fun importPhotos(galleryPhotos: List<GalleryPhoto>) {
        viewModelScope.launch {
            try {
                var successCount = 0
                val totalCount = galleryPhotos.size
                
                for ((index, galleryPhoto) in galleryPhotos.withIndex()) {
                    try {
                        // Copy photo to app storage
                        val copiedPath = fileManager.copyPhotoToAppStorage(galleryPhoto.uri, albumName)
                        
                        if (copiedPath != null) {
                            // Get image dimensions
                            val (width, height) = fileManager.getImageDimensions(copiedPath)
                            
                            // Create photo record
                            val photo = Photo(
                                albumId = albumId,
                                filePath = copiedPath,
                                originalName = galleryPhoto.displayName,
                                importedDate = Date(),
                                fileSize = galleryPhoto.size,
                                width = width,
                                height = height
                            )
                            
                            // Save to database
                            photoDao.insertPhoto(photo)
                            
                            // Delete from gallery (this is what the user requested)
                            fileManager.deletePhotoFromGallery(galleryPhoto.uri)
                            
                            successCount++
                        }
                        
                        // Update progress
                        _importProgress.value = ((index + 1) * 100) / totalCount
                        
                    } catch (e: Exception) {
                        // Continue with next photo if one fails
                        continue
                    }
                }
                
                // Update album photo count and cover
                albumDao.updatePhotoCount(albumId)
                val firstPhoto = photoDao.getFirstPhotoInAlbum(albumId)
                albumDao.updateCoverPhoto(albumId, firstPhoto?.filePath)
                
                _importComplete.value = Pair(successCount > 0, successCount)
                
            } catch (e: Exception) {
                _error.value = "Import failed: ${e.message}"
                _importComplete.value = Pair(false, 0)
            }
        }
    }
    
    class Factory(
        private val application: Application,
        private val albumId: Long,
        private val albumName: String
    ) : ViewModelProvider.Factory {
        
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PhotoImportViewModel::class.java)) {
                return PhotoImportViewModel(application, albumId, albumName) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

