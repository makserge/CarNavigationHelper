package com.smsoft.carnavigationhelper.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.smsoft.carnavigationhelper.data.database.entity.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerPlaylistDao {
    @Query("SELECT * FROM ${Song.TABLE_NAME} ORDER BY id")
    fun getAll(): Flow<List<Song>>

    @Query("SELECT * FROM ${Song.TABLE_NAME} WHERE id = :id")
    fun get(id: Long): Song?

    @Insert
    suspend fun insertAll(entities: List<Song>)

    @Query("DELETE FROM ${Song.TABLE_NAME}")
    fun clear()
}