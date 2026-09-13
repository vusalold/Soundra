package com.vm.soundra.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        DownloadHistoryEntity::class, 
        DownloadTaskEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class
    ], 
    version = 7, 
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadHistoryDao(): DownloadHistoryDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `download_tasks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `videoId` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `thumbnail` TEXT NOT NULL, 
                        `url` TEXT NOT NULL, 
                        `bitrate` TEXT NOT NULL, 
                        `state` TEXT NOT NULL, 
                        `progress` INTEGER NOT NULL, 
                        `downloadedBytes` INTEGER NOT NULL, 
                        `totalBytes` INTEGER NOT NULL, 
                        `speed` TEXT NOT NULL, 
                        `eta` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `errorMessage` TEXT, 
                        `localFilePath` TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `download_tasks` ADD COLUMN `artist` TEXT NOT NULL DEFAULT 'Bilinmir'")
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `download_tasks` ADD COLUMN `priority` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, 
                        `name` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlist_songs` (
                        `playlistId` INTEGER NOT NULL, 
                        `videoId` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `artist` TEXT NOT NULL, 
                        `thumbnail` TEXT NOT NULL, 
                        `url` TEXT NOT NULL, 
                        `duration` TEXT NOT NULL, 
                        `addedAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`playlistId`, `videoId`), 
                        FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_songs_playlistId` ON `playlist_songs` (`playlistId`)")
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlists` ADD COLUMN `coverUri` TEXT")
            }
        }

        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Update Playlists table
                db.execSQL("ALTER TABLE `playlists` ADD COLUMN `description` TEXT")
                db.execSQL("ALTER TABLE `playlists` ADD COLUMN `isCustomCover` INTEGER NOT NULL DEFAULT 0")
                
                // Update Playlist Songs table
                db.execSQL("ALTER TABLE `playlist_songs` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `playlist_songs` ADD COLUMN `lastPlayed` INTEGER")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "soundra_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
