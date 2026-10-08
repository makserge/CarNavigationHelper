package com.smsoft.carnavigationhelper.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smsoft.carnavigationhelper.data.database.entity.Song
import com.smsoft.carnavigationhelper.data.database.dao.PlayerPlaylistDao

@Database(
    version = 3,
    entities = [
        Song::class
    ],
    exportSchema = false
)

abstract class CarNavigationHelperDatabase: RoomDatabase() {
    abstract fun playerPlaylistDao(): PlayerPlaylistDao

    companion object {
        // Keeps the scanned songs, the new columns must match Song exactly for Room's schema check
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ${Song.TABLE_NAME} ADD COLUMN isBlacklisted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE ${Song.TABLE_NAME} ADD COLUMN loudness REAL")
                db.execSQL("ALTER TABLE ${Song.TABLE_NAME} ADD COLUMN peak REAL")
            }
        }
    }
}