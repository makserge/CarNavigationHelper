package com.smsoft.carnavigationhelper.data.database.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.smsoft.carnavigationhelper.data.database.CarNavigationHelperDatabase
import com.smsoft.carnavigationhelper.data.database.entity.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PlayerRepository@Inject constructor(
    database: CarNavigationHelperDatabase,
) {
    private val playerPlaylistDao = database.playerPlaylistDao()
    // Playable songs only
    val getAll = playerPlaylistDao.getAll()
    val getBlacklisted: Flow<List<Song>> = playerPlaylistDao.getBlacklisted()

    suspend fun setBlacklisted(id: Long, blacklisted: Boolean) =
        playerPlaylistDao.setBlacklisted(id, blacklisted)

    suspend fun setLoudness(id: Long, loudness: Double, peak: Double) =
        playerPlaylistDao.setLoudness(id, loudness, peak)

    // Replaces the rows, which resets the blacklist. Loudness is carried over by contentUri in replaceAll.
    // A stopped scan replaces the playlist with the songs scanned so far, the rest of the folder is left out
    suspend fun updatePlaylist(
        context: Context,
        path: Uri,
        callback: (Triple<Int, Long, Long>) -> Unit
    ) {
        val songs = mutableListOf<Song>()
        try {
            scanFiles(context, path, songs, callback)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                playerPlaylistDao.replaceAll(songs)
            }
            throw e
        }
        if (songs.isNotEmpty()) {
            playerPlaylistDao.replaceAll(songs)
        }
    }

    // An audio file of the folder, name and size come from the folder listing
    private data class ScanFile(val uri: Uri, val fileName: String, val fileSize: Long)

    // One children query per folder, subfolders are listed recursively
    // Checks for a stop at every entry, so that a big folder can be stopped while it is listed
    private suspend fun listAudioFiles(context: Context, path: Uri, parentId: String): List<ScanFile> {
        val files = mutableListOf<ScanFile>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(path, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val docId = cursor.getString(idIndex)
                val mimeType = cursor.getString(mimeIndex)
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    files.addAll(listAudioFiles(context, path, docId))
                } else if (mimeType?.startsWith("audio/") == true) {
                    files.add(
                        ScanFile(
                            uri = DocumentsContract.buildDocumentUriUsingTree(path, docId),
                            fileName = cursor.getString(nameIndex) ?: docId,
                            fileSize = cursor.getLong(sizeIndex)
                        )
                    )
                }
            }
        }
        return files
    }

    // Songs are added to the given list chunk by chunk, so a stopped scan keeps the ones it has read
    private suspend fun scanFiles(
        context: Context,
        path: Uri,
        songs: MutableList<Song>,
        callback: (Triple<Int, Long, Long>) -> Unit,
    ) {
        val files = withContext(Dispatchers.IO) {
            listAudioFiles(context, path, DocumentsContract.getTreeDocumentId(path))
        }
        // Songs of the last scan by uri. A file with the same known size and a readable duration keeps its tags and is not opened again
        val previousSongs = (getAll.first() + getBlacklisted.first()).associateBy { it.contentUri }

        var totalAmount = 0
        var totalDuration = 0L
        var totalSize = 0L

        files.chunked(PARALLEL_SCANS).forEach { chunk ->
            val chunkSongs = coroutineScope {
                chunk.map { file ->
                    async(Dispatchers.IO) { readSong(context, file, previousSongs[file.uri.toString()]) }
                }.awaitAll()
            }
            songs.addAll(chunkSongs)
            chunkSongs.forEach { song ->
                totalAmount++
                totalDuration += song.duration
                totalSize += song.fileSize
            }
            callback(Triple(totalAmount, totalDuration, totalSize))
        }
    }

    private fun readSong(context: Context, file: ScanFile, previous: Song?): Song {
        var artist: String? = null
        var title: String? = null
        var duration = 0L

        if (previous != null && file.fileSize > 0 && previous.fileSize == file.fileSize && previous.duration > 0) {
            artist = previous.artist
            title = previous.title
            duration = previous.duration
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, file.uri)
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                duration =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
            } catch (_: Exception) {
            } finally {
                retriever.release()
            }
        }

        return Song(
            id = 0,
            fileName = file.fileName,
            artist = artist,
            title = title ?: file.fileName,
            duration = duration,
            contentUri = file.uri.toString(),
            fileSize = file.fileSize
        )
    }

    companion object {
        // Files whose tags are read at the same time
        private const val PARALLEL_SCANS = 4
    }
}
