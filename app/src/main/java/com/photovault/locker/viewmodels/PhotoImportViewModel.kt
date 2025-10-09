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
import java.io.File
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
                android.util.Log.d("PhotoImportViewModel", "Starting import of ${galleryPhotos.size} photos to album $albumId")
                var successCount = 0
                val totalCount = galleryPhotos.size
                
                
                for ((index, galleryPhoto) in galleryPhotos.withIndex()) {
                    try {
                        android.util.Log.d("PhotoImportViewModel", "Importing photo ${index + 1}/$totalCount: ${galleryPhoto.displayName}")
                        
                            // Copy photo to app storage
                            val copiedPath = fileManager.copyPhotoToAppStorage(galleryPhoto.uri, albumName)
                            
                            if (copiedPath != null) {
                                android.util.Log.d("PhotoImportViewModel", "Photo copied to: $copiedPath")
                                
                                // Get image dimensions
                                val (width, height) = fileManager.getImageDimensions(copiedPath)
                                
                                // Extract the actual filename from the copied path
                                val actualFileName = File(copiedPath).name
                                
                                // Create photo record
                                val photo = Photo(
                                    albumId = albumId,
                                    filePath = copiedPath,
                                    originalName = actualFileName, // Use the actual filename, not the gallery name
                                    importedDate = Date(),
                                    fileSize = galleryPhoto.size,
                                    width = width,
                                    height = height
                                )
                            
                            // Save to database
                            val photoId = photoDao.insertPhoto(photo)
                            android.util.Log.d("PhotoImportViewModel", "Photo saved to database with ID: $photoId")
                            
                            // Verify the photo was saved by querying it back
                            val savedPhoto = photoDao.getPhotoById(photoId)
                            android.util.Log.d("PhotoImportViewModel", "Verified photo in database: ${savedPhoto != null}")
                            
                            
                            successCount++
                        } else {
                            android.util.Log.e("PhotoImportViewModel", "Failed to copy photo: ${galleryPhoto.displayName}")
                        }
                        
                        // Update progress
                        _importProgress.value = ((index + 1) * 100) / totalCount
                        
                    } catch (e: Exception) {
                        android.util.Log.e("PhotoImportViewModel", "Error importing photo ${galleryPhoto.displayName}: ${e.message}")
                        // Continue with next photo if one fails
                        continue
                    }
                }
                
                android.util.Log.d("PhotoImportViewModel", "Import completed. Success count: $successCount")
                
                // Update album photo count and cover
                albumDao.updatePhotoCount(albumId)
                
                // Set first photo as cover photo if no cover is set yet
                val currentAlbum = albumDao.getAlbumById(albumId)
                if (currentAlbum?.coverPhotoPath == null) {
                    val firstPhoto = photoDao.getFirstPhotoInAlbum(albumId)
                    albumDao.updateCoverPhoto(albumId, firstPhoto?.filePath)
                    android.util.Log.d("PhotoImportViewModel", "Set first photo as cover: ${firstPhoto?.filePath}")
                }
                
                // Final verification - check total photos in album
                val finalPhotoCount = photoDao.getPhotoCountByAlbum(albumId)
                android.util.Log.d("PhotoImportViewModel", "Final photo count in album: $finalPhotoCount")
                
                android.util.Log.d("PhotoImportViewModel", "Album metadata updated")
                
                _importComplete.value = Pair(successCount > 0, successCount)
                
            } catch (e: Exception) {
                android.util.Log.e("PhotoImportViewModel", "Import failed with exception: ${e.message}", e)
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

