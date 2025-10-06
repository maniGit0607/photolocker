package com.photovault.locker.database

import androidx.room.*
import androidx.lifecycle.LiveData
import com.photovault.locker.models.Photo

@Dao
interface PhotoDao {
    
    @Query("SELECT * FROM photos WHERE album_id = :albumId AND is_deleted = 0 ORDER BY imported_date DESC")
    fun getPhotosByAlbum(albumId: Long): LiveData<List<Photo>>
    
    @Query("SELECT * FROM photos WHERE album_id = :albumId AND is_deleted = 0 ORDER BY imported_date DESC")
    suspend fun getPhotosByAlbumSync(albumId: Long): List<Photo>
    
    @Query("SELECT * FROM photos WHERE id = :photoId")
    suspend fun getPhotoById(photoId: Long): Photo?
    
    @Query("SELECT COUNT(*) FROM photos WHERE album_id = :albumId AND is_deleted = 0")
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
    
    @Query("SELECT * FROM photos WHERE album_id = :albumId AND is_deleted = 0 ORDER BY imported_date ASC LIMIT 1")
    suspend fun getFirstPhotoInAlbum(albumId: Long): Photo?
    
    // Bin functionality
    @Query("UPDATE photos SET is_deleted = 1, deleted_date = :deletedDate WHERE id = :photoId")
    suspend fun movePhotoToBin(photoId: Long, deletedDate: Long = System.currentTimeMillis())
    
    @Query("SELECT * FROM photos WHERE is_deleted = 1 ORDER BY deleted_date DESC")
    suspend fun getPhotosInBin(): List<Photo>
    
    @Query("UPDATE photos SET is_deleted = 0, deleted_date = NULL WHERE id = :photoId")
    suspend fun restorePhotoFromBin(photoId: Long)
    
    @Query("DELETE FROM photos WHERE is_deleted = 1")
    suspend fun permanentlyDeletePhotosInBin()
}

