package com.statussaver.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [StatusEntity::class, DownloadedStatus::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun statusDao(): StatusDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Remove duplicates before applying unique constraint
                db.execSQL("DELETE FROM statuses WHERE id NOT IN (SELECT MAX(id) FROM statuses GROUP BY filename, source)")
                
                // Add indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_statuses_source_savedAt` ON `statuses` (`source`, `savedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_statuses_source_fileType_savedAt` ON `statuses` (`source`, `fileType`, `savedAt`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_statuses_filename_source` ON `statuses` (`filename`, `source`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new table with composite primary key
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `downloaded_status_new` (
                        `filename` TEXT NOT NULL, 
                        `source` TEXT NOT NULL, 
                        `originalPath` TEXT NOT NULL, 
                        `savedPath` TEXT NOT NULL, 
                        `downloadedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`filename`, `source`)
                    )
                """.trimIndent())
                
                // Copy data from old table with default source LIVE
                db.execSQL("""
                    INSERT INTO `downloaded_status_new` (`filename`, `source`, `originalPath`, `savedPath`, `downloadedAt`) 
                    SELECT `filename`, 'LIVE', `originalPath`, `savedPath`, `downloadedAt` FROM `downloaded_status`
                """.trimIndent())
                
                // Drop old table
                db.execSQL("DROP TABLE `downloaded_status`")
                
                // Rename new table to old table name
                db.execSQL("ALTER TABLE `downloaded_status_new` RENAME TO `downloaded_status`")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "status_saver_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
