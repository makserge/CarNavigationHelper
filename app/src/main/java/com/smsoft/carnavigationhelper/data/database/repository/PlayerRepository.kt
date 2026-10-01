package com.smsoft.carnavigationhelper.data.database.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.smsoft.carnavigationhelper.data.database.CarNavigationHelperDatabase
import com.smsoft.carnavigationhelper.data.database.entity.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class PlayerRepository@Inject constructor(
    database: CarNavigationHelperDatabase,
) {
    private val playerPlaylistDao = database.playerPlaylistDao()
    val getAll = playerPlaylistDao.getAll()

    suspend fun updatePlaylist(
        context: Context,
        path: Uri,
        callback: (Triple<Int, Long, Long>) -> Unit
    ) {
        val songs = scanFiles(context, path, callback)
        if (songs.isNotEmpty()) {
            playerPlaylistDao.replaceAll(songs)
        }
    }

    private fun scanMediaAsFlow(context: Context, path: Uri): Flow<Uri> = flow {
        val rootId = DocumentsContract.getTreeDocumentId(path)
        emitAllFiles(context, path, rootId)
    }.flowOn(Dispatchers.IO)

    private suspend fun kotlinx.coroutines.flow.FlowCollector<Uri>.emitAllFiles(
        context: Context,
        path: Uri,
        parentId: String
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(path, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(idIndex)
                val mimeType = cursor.getString(mimeIndex)
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    emitAllFiles(context, path, docId)
                } else if (mimeType?.startsWith("audio/") == true) {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(path, docId)
                    emit(fileUri)
                }
            }
        }
    }

    private suspend fun scanFiles(
        context: Context,
        path: Uri,
        callback: (Triple<Int, Long, Long>) -> Unit,
    ): List<Song> {
        val songs = mutableListOf<Song>()

        var totalAmount = 0
        var totalDuration = 0L
        var totalSize = 0L

        scanMediaAsFlow(context, path)
            .collect { uri ->
                val item = getSongInfo(context, uri)
                item?.let{
                    songs.add(item)
                    totalAmount++
                    totalDuration += item.duration
                    totalSize += item.fileSize
                    callback(Triple(totalAmount, totalDuration, totalSize))
                }
            }
        return songs.toList()
    }

    private fun getSongInfo(context: Context, uri: Uri): Song? {
        val resolver = context.contentResolver
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE
        )
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }

            val fileNameColumn =
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

            val fileName = cursor.getString(fileNameColumn)
            val fileSize = cursor.getLong(sizeColumn)

            val retriever = MediaMetadataRetriever()
            var artist: String? = null
            var title: String? = null
            var duration = 0L

            try {
                retriever.setDataSource(context, uri)
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                duration =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
            } catch (_: Exception) {
            } finally {
                retriever.release()
            }

            return Song(
                id = 0,
                fileName = fileName,
                artist = artist,
                title = title ?: fileName,
                duration = duration,
                contentUri = uri.toString(),
                fileSize = fileSize
            )
        }
        return null
    }
}