package com.photovault.locker.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.photovault.locker.database.PhotoVaultDatabase
import com.photovault.locker.models.Photo

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = PhotoVaultDatabase.getDatabase(application)
    private val photoDao = database.photoDao()
    
    val favoritePhotos: LiveData<List<Photo>> = photoDao.getFavoritePhotos()
    
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error
}




