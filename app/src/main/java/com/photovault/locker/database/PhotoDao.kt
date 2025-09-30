package com.photovault.locker.database

import androidx.room.*
import androidx.lifecycle.LiveData
import com.photovault.locker.models.Photo

@Dao
interface PhotoDao {
    
    @Query("SELECT * FROM photos WHERE album_id = :albumId ORDER BY imported_date DESC")
    fun getPhotosByAlbum(albumId: Long): LiveData<List<Photo>>
    
    @Query("SELECT * FROM photos WHERE album_id = :albumId ORDER BY imported_date DESC")
    suspend fun getPhotosByAlbumSync(albumId: Long): List<Photo>
    
    @Query("SELECT * FROM photos WHERE id = :photoId")
    suspend fun getPhotoById(photoId: Long): Photo?
    
    @Query("SELECT COUNT(*) FROM photos WHERE album_id = :albumId")
    suspend fun getPhotoCountByAlbum(albumId: Long): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: Photo): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<Photo>): List<Long>
    
    @Update
    suspend fun updatePhoto(photo: Photo)
    
    @Delete
    suspend fun deletePhoto(photo: Photo)
    
    @Query("DELETE FROM photos WHERE id = :photoId")
    suspend fun deletePhotoById(photoId: Long)
    
    @Query("DELETE FROM photos WHERE album_id = :albumId")
    suspend fun deletePhotosByAlbum(albumId: Long)
    
    @Query("SELECT * FROM photos WHERE album_id = :albumId ORDER BY imported_date ASC LIMIT 1")
    suspend fun getFirstPhotoInAlbum(albumId: Long): Photo?
}

