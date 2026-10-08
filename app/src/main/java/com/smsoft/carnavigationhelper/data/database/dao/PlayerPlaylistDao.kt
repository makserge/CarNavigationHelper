package com.smsoft.carnavigationhelper.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.smsoft.carnavigationhelper.data.database.entity.Song
import kotlinx.coroutines.flow.Flow

// Measured loudness of a file, without the image blob
data class SongLoudness(val contentUri: String, val loudness: Double, val peak: Double)

@Dao
interface PlayerPlaylistDao {
    // Playable songs only, blacklisted ones are never played
    @Query("SELECT * FROM ${Song.TABLE_NAME} WHERE isBlacklisted = 0 ORDER BY id")
    fun getAll(): Flow<List<Song>>

    @Query("SELECT * FROM ${Song.TABLE_NAME} WHERE isBlacklisted = 1 ORDER BY artist, title")
    fun getBlacklisted(): Flow<List<Song>>

    @Query("SELECT * FROM ${Song.TABLE_NAME} WHERE id = :id")
    fun get(id: Long): Song?

    @Query("SELECT contentUri, loudness, peak FROM ${Song.TABLE_NAME} WHERE loudness IS NOT NULL AND peak IS NOT NULL")
    suspend fun getMeasured(): List<SongLoudness>

    @Query("UPDATE ${Song.TABLE_NAME} SET isBlacklisted = :blacklisted WHERE id = :id")
    suspend fun setBlacklisted(id: Long, blacklisted: Boolean)

    @Query("UPDATE ${Song.TABLE_NAME} SET loudness = :loudness, peak = :peak WHERE id = :id")
    suspend fun setLoudness(id: Long, loudness: Double, peak: Double)

    @Insert
    suspend fun insertAll(entities: List<Song>)

    @Query("DELETE FROM ${Song.TABLE_NAME}")
    suspend fun clear()

    // One transaction, so a failed insert keeps the old playlist instead of leaving it empty.
    // Loudness of a file is carried over by its uri, the blacklist resets because new rows default to false
    @Transaction
    suspend fun replaceAll(entities: List<Song>) {
        val measured = getMeasured().associateBy { it.contentUri }
        clear()
        insertAll(entities.map { song ->
            measured[song.contentUri]?.let { song.copy(loudness = it.loudness, peak = it.peak) } ?: song
        })
    }
}
