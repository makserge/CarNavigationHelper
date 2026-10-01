package com.smsoft.carnavigationhelper.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.smsoft.carnavigationhelper.data.database.entity.Song
import com.smsoft.carnavigationhelper.data.database.dao.PlayerPlaylistDao

@Database(
    version = 2,
    entities = [
        Song::class
    ],
    exportSchema = false
)

abstract class CarNavigationHelperDatabase: RoomDatabase() {
    abstract fun playerPlaylistDao(): PlayerPlaylistDao
}