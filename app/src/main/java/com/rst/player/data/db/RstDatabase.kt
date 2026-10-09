package com.rst.player.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rst.player.data.db.dao.PlayHistoryDao
import com.rst.player.data.db.dao.PlaylistDao
import com.rst.player.data.db.dao.PlaylistSongDao
import com.rst.player.data.db.dao.SongMoodDao
import com.rst.player.data.db.entity.PlayHistoryEntity
import com.rst.player.data.db.entity.PlaylistEntity
import com.rst.player.data.db.entity.PlaylistSongEntity
import com.rst.player.data.db.entity.SongMoodEntity

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        PlayHistoryEntity::class,
        SongMoodEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class RstDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistSongDao(): PlaylistSongDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun songMoodDao(): SongMoodDao

    companion object {
        @Volatile
        private var INSTANCE: RstDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `song_moods` (" +
                        "`songId` TEXT NOT NULL, " +
                        "`bpm` REAL NOT NULL, " +
                        "`energy` REAL NOT NULL, " +
                        "`brightness` REAL NOT NULL, " +
                        "`bassiness` REAL NOT NULL, " +
                        "`darkness` REAL NOT NULL, " +
                        "PRIMARY KEY(`songId`))"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `song_moods` ADD COLUMN `spectralFlux` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `song_moods` ADD COLUMN `rhythmicDensity` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `song_moods` ADD COLUMN `dynamicRange` REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `song_moods` ADD COLUMN `valence` REAL NOT NULL DEFAULT 0.5")
            }
        }

        fun get(context: Context): RstDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RstDatabase::class.java,
                    "rst_player.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
