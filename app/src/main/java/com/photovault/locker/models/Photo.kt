package com.photovault.locker.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.Date

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = Album::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("album_id"),
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["album_id"])]
)
data class Photo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "album_id")
    val albumId: Long,
    
    @ColumnInfo(name = "file_path")
    val filePath: String,
    
    @ColumnInfo(name = "original_name")
    val originalName: String,
    
    @ColumnInfo(name = "imported_date")
    val importedDate: Date = Date(),
    
    @ColumnInfo(name = "file_size")
    val fileSize: Long,
    
    @ColumnInfo(name = "width")
    val width: Int = 0,
    
    @ColumnInfo(name = "height")
    val height: Int = 0,
    
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
    
    @ColumnInfo(name = "deleted_date")
    val deletedDate: Date? = null
)

