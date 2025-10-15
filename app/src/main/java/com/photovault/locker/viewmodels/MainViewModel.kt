package com.photovault.locker.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.photovault.locker.database.PhotoVaultDatabase
import com.photovault.locker.models.Album
import com.photovault.locker.utils.FileManager
import kotlinx.coroutines.launch
import java.util.Date

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = PhotoVaultDatabase.getDatabase(application)
    private val albumDao = database.albumDao()
    private val photoDao = database.photoDao()
    private val fileManager = FileManager(application)
    
    val albums: LiveData<List<Album>> = albumDao.getAllAlbums()
    
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error
    
    fun createAlbum(name: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                // Check if album already exists
                val existingAlbum = albumDao.getAlbumByName(name)
                if (existingAlbum != null) {
                    callback(false)
                    return@launch
                }
                
                // Create new album
                val album = Album(
                    name = name,
                    createdDate = Date()
                )
                
                albumDao.insertAlbum(album)
                callback(true)
            } catch (e: Exception) {
                _error.value = "Failed to create album: ${e.message}"
                callback(false)
            }
        }
    }
    
    fun deleteAlbum(album: Album) {
        viewModelScope.launch {
            try {
                // Delete all active (non-bin) photos in the album
                // Bin photos remain with their album_id, but album won't exist
                // They will be moved to "Restored" album when restored
                photoDao.deletePhotosByAlbum(album.id)
                
                // Delete album directory from file system
                fileManager.deleteAlbumDirectory(album.name)
                
                // Delete album from database
                albumDao.deleteAlbum(album)
            } catch (e: Exception) {
                _error.value = "Failed to delete album: ${e.message}"
            }
        }
    }
    
    fun updateAlbumPhotoCounts() {
        viewModelScope.launch {
            try {
                val albumsList = albumDao.getAllAlbumsSync()
                albumsList.forEach { album ->
                    albumDao.updatePhotoCount(album.id)
                    
                    // Set cover photo only if no cover exists
                    if (album.coverPhotoPath == null) {
                        val firstPhoto = photoDao.getFirstPhotoInAlbum(album.id)
                        albumDao.updateCoverPhoto(album.id, firstPhoto?.filePath)
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to update album information: ${e.message}"
            }
        }
    }
    
    fun renameAlbum(album: Album, newName: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                // Check if an album with the new name already exists
                val existingAlbum = albumDao.getAlbumByName(newName)
                if (existingAlbum != null && existingAlbum.id != album.id) {
                    callback(false)
                    return@launch
                }
                
                // Update the album with the new name
                val updatedAlbum = album.copy(name = newName)
                albumDao.updateAlbum(updatedAlbum)
                callback(true)
            } catch (e: Exception) {
                _error.value = "Failed to rename album: ${e.message}"
                callback(false)
            }
        }
    }
}

