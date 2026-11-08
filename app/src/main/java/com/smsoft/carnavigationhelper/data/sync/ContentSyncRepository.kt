package com.smsoft.carnavigationhelper.data.sync

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.smsoft.carnavigationhelper.data.database.repository.PlayerRepository
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

// One way sync: add-on -> device.
// - Keeps the folder structure of the add-on: "Album/CD 1/01.mp3" is copied to the same place below the music folder.
// - Keeps the newest files (by the add-on's file date, i.e. arrival time) that fit in the share of the storage set in
//   the settings. Strict FIFO: the first file that does not fit ends the window, older files never jump ahead.
// - Files that leave the window are removed, but only those the sync itself added (tracked by path).
// - Files are never overwritten. A file with the same path but another size is a conflict and is left alone.
// - The player is rescanned whenever files were added or removed.
@Singleton
class ContentSyncRepository @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playerRepository: PlayerRepository,
) {
    private val mutex = Mutex()

    suspend fun autoSyncEnabled(): Boolean = userPreferencesRepository.syncAutoFlow.first()

    suspend fun selectedWifi(): String = userPreferencesRepository.syncWifiFlow.first()

    suspend fun syncNow(
        context: Context,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): SyncResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val url = userPreferencesRepository.syncUrlFlow.first()
            val token = userPreferencesRepository.syncTokenFlow.first()
            if (url.isBlank() || token.isBlank()) {
                throw SyncException(SyncFailure.NOT_CONFIGURED, retryable = false)
            }
            val folder = userPreferencesRepository.playerPlaylistPathFlow.first().toUri()
            val maxPercent = userPreferencesRepository.syncMaxPercentFlow.first()
            val tracked = userPreferencesRepository.syncTrackedFlow.first()
            val client = SyncClient(url, token)

            // Network errors here propagate as IOException; the worker retries later
            val remote = client.listFiles().filter { isSafeAudioName(it.name) }

            val nodes = try {
                listTree(context, folder)
            } catch (_: SecurityException) {
                throw SyncException(SyncFailure.NO_FOLDER_ACCESS, retryable = false)
            }

            // Leftovers of an interrupted copy are removed
            nodes.filter { !it.isDir && it.name.endsWith(PART_SUFFIX) }.forEach { node ->
                deleteQuietly(context, node.uri)
            }
            val existing = nodes
                .filter { !it.isDir && !it.name.endsWith(PART_SUFFIX) }
                .associateTo(mutableMapOf()) { it.relPath to it.size }

            // The storage share is taken from the volume the music folder is on
            val volume = volumePath(folder)
            val totalBytes = volume?.let { volumeStat(it)?.totalBytes }
            val quota = totalBytes?.let { it * maxPercent / 100 }
            val window = remoteWindow(remote, quota)
            val windowPaths = window.map { it.name }.toSet()
            val mtimeByPath = remote.associate { it.name to it.mtime }

            val dirCache = mutableMapOf<String, String>()
            val job = coroutineContext[Job]
            val stopRequested: () -> Boolean = { job?.isActive == false }
            var removed = 0
            var downloaded = 0
            var alreadyThere = 0
            var conflicts = 0
            var failed = 0
            var noSpace = false
            val copied = mutableSetOf<String>()
            try {
                // 1. Make room: synced files that left the window are removed, oldest first
                nodes
                    .filter { !it.isDir && it.relPath in tracked && it.relPath !in windowPaths }
                    .sortedBy { node -> mtimeByPath[node.relPath] ?: 0L }
                    .forEach { node ->
                        currentCoroutineContext().ensureActive()
                        if (deleteQuietly(context, node.uri)) {
                            existing.remove(node.relPath)
                            removed++
                            runCatching { playerRepository.removeSong(node.uri) }
                        }
                    }

                // 2. Only files that are not on the device yet are copied. Files copied so far are kept, even if stopped
                for (file in window) {
                    val localSize = existing[file.name] ?: continue
                    if (localSize == file.size || file.size <= 0) alreadyThere++ else conflicts++
                }
                val missing = window.filter { existing[it.name] == null }
                missing.forEachIndexed { index, file ->
                    currentCoroutineContext().ensureActive()
                    onProgress(index + 1, missing.size)
                    if (noSpace) return@forEachIndexed
                    if (volume != null && !hasRoomFor(volume, file.size)) {
                        noSpace = true
                        return@forEachIndexed
                    }
                    val copy = try {
                        copyFile(context, client, file, folder, dirCache, stopRequested)
                    } catch (_: IOException) {
                        CopyResult(ok = false, uri = null)
                    }
                    if (copy.ok) {
                        downloaded++
                        copied += file.name
                        existing[file.name] = file.size
                        // The totals of the playlist follow the sync: the new song is added now, not at the end
                        copy.uri?.let { uri ->
                            runCatching {
                                playerRepository.addSong(context, uri, file.name.substringAfterLast('/'), file.size)
                            }
                        }
                    } else {
                        failed++
                    }
                }
            } finally {
                // Runs on every exit, including a stop: tracking and the player list must match the files on disk
                withContext(NonCancellable) {
                    userPreferencesRepository.setSyncTracked((tracked + copied).filter { it in existing.keys }.toSet())
                    // Also after a stop: the player must show what is on disk
                    if (downloaded > 0 || removed > 0 || job?.isActive == false) {
                        playerRepository.updatePlaylist(context, folder) { }
                    }
                }
            }
            SyncResult(downloaded, removed, alreadyThere, conflicts, failed, noSpace)
        }
    }

    // The newest files that fit in the quota, by file date. Strict: the first file that does not fit ends the list.
    // quota == null means no limit
    private fun remoteWindow(files: List<RemoteFile>, quota: Long?): List<RemoteFile> {
        val newestFirst = files.sortedByDescending { it.mtime }
        if (quota == null) return newestFirst
        val window = mutableListOf<RemoteFile>()
        var used = 0L
        for (file in newestFirst) {
            val size = file.size.coerceAtLeast(0L)
            if (used + size > quota) break
            used += size
            window += file
        }
        return window
    }

    // /storage/emulated/0 or /storage/<id>, the volume the music folder is on
    private fun volumePath(folder: Uri): String? {
        val volume = DocumentsContract.getTreeDocumentId(folder).substringBefore(':')
        if (volume.isBlank()) return null
        return if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
    }

    private fun volumeStat(path: String): StatFs? =
        try {
            StatFs(path)
        } catch (_: IllegalArgumentException) {
            null
        }

    // Keeps a reserve free for the system and the other apps
    private fun hasRoomFor(path: String, bytes: Long): Boolean {
        val free = volumeStat(path)?.availableBytes ?: return true
        return free - bytes >= MIN_FREE_BYTES
    }

    private fun deleteQuietly(context: Context, uri: Uri): Boolean =
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }.getOrDefault(false)

    private data class CopyResult(val ok: Boolean, val uri: Uri?)

    // Copies one file to its path below the music folder, creating the folders it needs.
    // Written under a temporary name first and renamed when complete, so a broken copy is never a song
    private fun copyFile(
        context: Context,
        client: SyncClient,
        file: RemoteFile,
        folder: Uri,
        dirCache: MutableMap<String, String>,
        stopRequested: () -> Boolean,
    ): CopyResult {
        val resolver = context.contentResolver
        val parts = file.name.split('/')
        val baseName = parts.last()
        val dirDocId = ensureDirectory(context, folder, parts.dropLast(1), dirCache)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(folder, dirDocId)
        val tempUri = DocumentsContract.createDocument(
            resolver, parentUri, mimeFor(baseName), baseName + PART_SUFFIX
        ) ?: return CopyResult(ok = false, uri = null)
        var finished = false
        try {
            val out = resolver.openOutputStream(tempUri, "wt") ?: return CopyResult(ok = false, uri = null)
            val bytes = out.use { stream -> client.download(file, stream, stopRequested) }
            if (file.size > 0 && bytes != file.size) return CopyResult(ok = false, uri = null)
            DocumentsContract.renameDocument(resolver, tempUri, baseName)
            finished = true
            val uri = children(context, folder, dirDocId, "").firstOrNull { !it.isDir && it.name == baseName }?.uri
            return CopyResult(ok = true, uri = uri)
        } finally {
            if (!finished) deleteQuietly(context, tempUri)
        }
    }

    // Document id of the folder with the given path parts below the music folder, created if missing.
    // dirCache maps a folder path ("Album/CD 1") to its document id, so each folder is looked up once
    private fun ensureDirectory(
        context: Context,
        folder: Uri,
        parts: List<String>,
        dirCache: MutableMap<String, String>,
    ): String {
        var parentDocId = DocumentsContract.getTreeDocumentId(folder)
        var key = ""
        for (part in parts) {
            key = if (key.isEmpty()) part else "$key/$part"
            val cached = dirCache[key]
            if (cached != null) {
                parentDocId = cached
                continue
            }
            val existing = children(context, folder, parentDocId, "").firstOrNull { it.isDir && it.name == part }
            val docId = existing?.docId ?: run {
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(folder, parentDocId)
                val created = DocumentsContract.createDocument(
                    context.contentResolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, part
                ) ?: throw IOException("cannot create folder $key")
                DocumentsContract.getDocumentId(created)
            }
            dirCache[key] = docId
            parentDocId = docId
        }
        return parentDocId
    }

    private data class Node(
        val relPath: String,
        val name: String,
        val docId: String,
        val size: Long,
        val isDir: Boolean,
        val uri: Uri,
    )

    // Direct children of one folder. prefix is the path of that folder ("" for the music folder itself, else "Album/")
    private fun children(context: Context, folder: Uri, parentDocId: String, prefix: String): List<Node> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folder, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val nodes = mutableListOf<Node>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val name = cursor.getString(1) ?: docId
                nodes.add(
                    Node(
                        relPath = prefix + name,
                        name = name,
                        docId = docId,
                        size = cursor.getLong(2),
                        isDir = cursor.getString(3) == DocumentsContract.Document.MIME_TYPE_DIR,
                        uri = DocumentsContract.buildDocumentUriUsingTree(folder, docId),
                    )
                )
            }
        }
        return nodes
    }

    // Every file and folder below the music folder, with its path. Hidden names are skipped
    private fun listTree(context: Context, folder: Uri): List<Node> {
        val result = mutableListOf<Node>()
        val pending = mutableListOf(DocumentsContract.getTreeDocumentId(folder) to "")
        while (pending.isNotEmpty()) {
            val (docId, prefix) = pending.removeAt(0)
            for (node in children(context, folder, docId, prefix)) {
                if (node.name.startsWith(".")) continue
                result.add(node)
                if (node.isDir) pending.add(node.docId to "${node.relPath}/")
            }
        }
        return result
    }

    // A path such as "Album/CD 1/01.mp3" with safe parts only and an audio extension
    private fun isSafeAudioName(name: String): Boolean {
        if (name.isBlank() || name.contains('\\')) return false
        val parts = name.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." || it.startsWith(".") }) return false
        return parts.last().substringAfterLast('.', "").lowercase() in AUDIO_EXTS
    }

    private fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    companion object {
        private const val PART_SUFFIX = ".sync.part"
        // Same audio types the add-on extracts and the player plays
        private val AUDIO_EXTS = setOf("mp3", "flac", "wav", "ogg", "m4a")
        // Free space the sync never uses up
        private const val MIN_FREE_BYTES = 300L * 1024 * 1024
    }
}
