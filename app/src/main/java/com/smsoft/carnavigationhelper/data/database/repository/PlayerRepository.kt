package com.smsoft.carnavigationhelper.data.database.repository

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import com.smsoft.carnavigationhelper.data.database.CarNavigationHelperDatabase
import com.smsoft.carnavigationhelper.data.database.entity.Song
import javax.inject.Inject

class PlayerRepository@Inject constructor(
    database: CarNavigationHelperDatabase,
) {
    private val playerPlaylistDao = database.playerPlaylistDao()
    val getAll = playerPlaylistDao.getAll()

    suspend fun updatePlaylist(
        context: Context,
        path: String,
        callback: (Triple<Int, Long, Long>) -> Unit
    ) {
        val songs = scanFiles(context, path, callback)
        if (songs.isNotEmpty()) {
            this.run {
                playerPlaylistDao.clear()
                playerPlaylistDao.insertAll(songs)
            }
        }
    }

    private fun scanFiles(
        context: Context,
        path: String,
        callback: (Triple<Int, Long, Long>) -> Unit,
    ): List<Song> {
        val songs = mutableListOf<Song>()

        val resolver = context.contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        var selection: String? = null
        var selectionArgs = emptyArray<String>()
        if (path.isNotEmpty()) {
            selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.MediaColumns.RELATIVE_PATH + " like ? "
            else MediaStore.Images.Media.DATA + " like ? "
            selectionArgs = arrayOf("%$path%")
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )
        val cursor: Cursor? = resolver.query(uri, projection, selection, selectionArgs, null)
        when {
            cursor == null -> {
                callback(Triple(0, 0, 0))
                return songs
            }
            !cursor.moveToFirst() -> {
                callback(Triple(0, 0, 0))
                return songs
            }
            else -> {
                cursor.use {
                    val idColumn = it.getColumnIndex(MediaStore.Audio.Media._ID)
                    val fileNameColumn = it.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val artistColumn = it.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val titleColumn = it.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val durationColumn = it.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    var totalDuration = 0L
                    var totalSize = 0L
                    do {
                        val id = it.getLong(idColumn)
                        val fileName = it.getString(fileNameColumn)
                        val artist = it.getString(artistColumn)
                        val title = it.getString(titleColumn)
                        val duration = it.getLong(durationColumn)
                        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        val fileSize = it.getLong(sizeColumn)
                        songs.add(Song(
                            id,
                            fileName,
                            artist,
                            title,
                            duration,
                            contentUri.toString(),
                            fileSize
                        ))
                        totalDuration += duration
                        totalSize += fileSize
                    callback(Triple(songs.size, totalDuration, totalSize))
                } while (cursor.moveToNext())
                }
            }
        }
        cursor.close()
        return songs.toList()
    }
}