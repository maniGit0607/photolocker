package com.photovault.locker.database

import androidx.room.*
import androidx.lifecycle.LiveData
import com.photovault.locker.models.Album

@Dao
interface AlbumDao {
    
    @Query("SELECT * FROM albums ORDER BY created_date DESC")
    fun getAllAlbums(): LiveData<List<Album>>
    
    @Query("SELECT * FROM albums ORDER BY created_date DESC")
    suspend fun getAllAlbumsSync(): List<Album>
    
    @Query("SELECT * FROM albums WHERE id = :albumId")
    suspend fun getAlbumById(albumId: Long): Album?
    
    @Query("SELECT * FROM albums WHERE name = :name")
    suspend fun getAlbumByName(name: String): Album?
    
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAlbum(album: Album): Long
    
    @Update
    suspend fun updateAlbum(album: Album)
    
    @Delete
    suspend fun deleteAlbum(album: Album)
    
    @Query("UPDATE albums SET photo_count = (SELECT COUNT(*) FROM photos WHERE album_id = :albumId) WHERE id = :albumId")
    suspend fun updatePhotoCount(albumId: Long)
    
    @Query("UPDATE albums SET cover_photo_path = :coverPhotoPath WHERE id = :albumId")
    suspend fun updateCoverPhoto(albumId: Long, coverPhotoPath: String?)
}

