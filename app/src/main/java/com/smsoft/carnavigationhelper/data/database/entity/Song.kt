package com.smsoft.carnavigationhelper.data.database.entity

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.smsoft.carnavigationhelper.data.database.entity.Song.Companion.TABLE_NAME

@Entity(tableName = TABLE_NAME)
data class Song(
    // Generated on insert (pass 0), a hash of the uri could collide. Also used as the player's mediaId
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val artist: String? = null,
    val title: String? = null,
    val duration: Long,
    val contentUri: String,
    val fileSize: Long,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val image: ByteArray? = null,
) {
    companion object {
        const val TABLE_NAME = "player_playlist"
    }
}