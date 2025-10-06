package com.photovault.locker.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.photovault.locker.models.Album
import com.photovault.locker.models.Photo

@Database(
    entities = [Album::class, Photo::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class PhotoVaultDatabase : RoomDatabase() {
    
    abstract fun albumDao(): AlbumDao
    abstract fun photoDao(): PhotoDao
    
    companion object {
        @Volatile
        private var INSTANCE: PhotoVaultDatabase? = null
        
        // Migration from version 1 to 2 - Add bin fields to photos table
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE photos ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE photos ADD COLUMN deleted_date INTEGER")
            }
        }
        
        fun getDatabase(context: Context): PhotoVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhotoVaultDatabase::class.java,
                    "photo_vault_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

