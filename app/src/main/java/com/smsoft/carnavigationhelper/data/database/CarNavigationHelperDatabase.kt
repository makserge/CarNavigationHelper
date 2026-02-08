package com.smsoft.carnavigationhelper.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.smsoft.carnavigationhelper.data.database.entity.Song
import com.smsoft.carnavigationhelper.data.database.dao.PlayerPlaylistDao

@Database(
    version = 1,
    entities = [
        Song::class
    ],
    exportSchema = true
)

abstract class CarNavigationHelperDatabase: RoomDatabase() {
    abstract fun playerPlaylistDao(): PlayerPlaylistDao
}