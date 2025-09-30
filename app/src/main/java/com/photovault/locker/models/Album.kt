package com.photovault.locker.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.Date

@Entity(tableName = "albums")
data class Album(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "created_date")
    val createdDate: Date = Date(),
    
    @ColumnInfo(name = "photo_count")
    val photoCount: Int = 0,
    
    @ColumnInfo(name = "cover_photo_path")
    val coverPhotoPath: String? = null
)

