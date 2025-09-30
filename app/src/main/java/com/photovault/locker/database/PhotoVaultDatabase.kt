package com.photovault.locker.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.photovault.locker.models.Album
import com.photovault.locker.models.Photo

@Database(
    entities = [Album::class, Photo::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class PhotoVaultDatabase : RoomDatabase() {
    
    abstract fun albumDao(): AlbumDao
    abstract fun photoDao(): PhotoDao
    
    companion object {
        @Volatile
        private var INSTANCE: PhotoVaultDatabase? = null
        
        fun getDatabase(context: Context): PhotoVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhotoVaultDatabase::class.java,
                    "photo_vault_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

