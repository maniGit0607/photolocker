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
    version = 4,
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
        
        // Migration from version 2 to 3 - Add favorites field to photos table
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE photos ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        // Migration from version 3 to 4 - Change foreign key from CASCADE to NO_ACTION
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // SQLite doesn't support modifying foreign keys directly
                // We need to recreate the table
                
                // Create new table with updated foreign key
                database.execSQL("""
                    CREATE TABLE photos_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        album_id INTEGER NOT NULL,
                        file_path TEXT NOT NULL,
                        original_name TEXT NOT NULL,
                        imported_date INTEGER NOT NULL,
                        file_size INTEGER NOT NULL,
                        width INTEGER NOT NULL DEFAULT 0,
                        height INTEGER NOT NULL DEFAULT 0,
                        is_deleted INTEGER NOT NULL DEFAULT 0,
                        deleted_date INTEGER,
                        is_favorite INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(album_id) REFERENCES albums(id) ON DELETE NO ACTION
                    )
                """.trimIndent())
                
                // Copy data from old table to new table
                database.execSQL("""
                    INSERT INTO photos_new (id, album_id, file_path, original_name, imported_date, 
                        file_size, width, height, is_deleted, deleted_date, is_favorite)
                    SELECT id, album_id, file_path, original_name, imported_date, 
                        file_size, width, height, is_deleted, deleted_date, is_favorite
                    FROM photos
                """.trimIndent())
                
                // Drop old table
                database.execSQL("DROP TABLE photos")
                
                // Rename new table to photos
                database.execSQL("ALTER TABLE photos_new RENAME TO photos")
                
                // Recreate index
                database.execSQL("CREATE INDEX index_photos_album_id ON photos(album_id)")
            }
        }
        
        fun getDatabase(context: Context): PhotoVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhotoVaultDatabase::class.java,
                    "photo_vault_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

