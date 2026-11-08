package com.smsoft.carnavigationhelper.data.server

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Second sync: serves the audio files of the synced music folder to another system.
// Same API as the add-on: GET /api/v1/files (JSON list) and GET /api/v1/files/<name>.
// Plain HTTP on the local network, one request per connection. Every request needs "Authorization: Bearer <key>".
class ContentServer(
    private val context: Context,
    private val folder: Uri,
    private val port: Int,
    private val key: String,
) {
    private var server: ServerSocket? = null
    private var pool: ExecutorService? = null
    @Volatile private var running = false

    fun start() {
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(port))
        server = socket
        pool = Executors.newCachedThreadPool()
        running = true
        Thread({ acceptLoop(socket) }, "content-server").apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        pool?.shutdownNow()
        server = null
        pool = null
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            try {
                val client = socket.accept()
                pool?.execute { handle(client) }
            } catch (_: IOException) {
                if (!running) break
            }
        }
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 15_000
            val input = BufferedInputStream(socket.getInputStream())
            val out = socket.getOutputStream()
            val requestLine = readLine(input) ?: return
            val headers = HashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                respond(out, 400, "Bad Request")
                return
            }
            if (parts[0] != "GET") {
                respond(out, 405, "Method Not Allowed")
                return
            }
            if (!authorized(headers["authorization"])) {
                respond(out, 401, "Unauthorized", extra = "WWW-Authenticate: Bearer\r\n")
                return
            }
            val path = parts[1].substringBefore('?')
            when {
                path == "/api/v1/files" -> sendList(out)
                path.startsWith("/api/v1/files/") -> sendFile(out, decodeName(path.removePrefix("/api/v1/files/")))
                else -> respond(out, 404, "Not Found")
            }
        }
    }

    // One line of the request, without the line break. Null at the end of the stream or if the line is too long
    private fun readLine(input: InputStream): String? {
        val buffer = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buffer.isEmpty()) null else buffer.toString()
            if (b == '\n'.code) return buffer.toString().trimEnd('\r')
            buffer.append(b.toChar())
            if (buffer.length > 8192) return null
        }
    }

    private fun authorized(header: String?): Boolean {
        if (key.isBlank() || header == null) return false
        val scheme = header.substringBefore(' ')
        val token = header.substringAfter(' ', "").trim()
        return scheme.equals("Bearer", ignoreCase = true) &&
            MessageDigest.isEqual(token.toByteArray(Charsets.UTF_8), key.toByteArray(Charsets.UTF_8))
    }

    private fun decodeName(raw: String): String = URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8")

    private class Entry(val name: String, val uri: Uri, val size: Long, val mtimeMs: Long)

    // A path such as "Album/CD 1/01.mp3" with safe parts only and an audio extension
    private fun isSafeAudioName(name: String): Boolean {
        if (name.isBlank() || name.contains('\\')) return false
        val parts = name.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." || it.startsWith(".") }) return false
        return parts.last().substringAfterLast('.', "").lowercase() in AUDIO_EXTS
    }

    // Audio files in the synced music folder and its subfolders, with their path relative to the folder
    private fun listFiles(): List<Entry> {
        val entries = mutableListOf<Entry>()
        val pending = mutableListOf(DocumentsContract.getTreeDocumentId(folder) to "")
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        while (pending.isNotEmpty()) {
            val (docId, prefix) = pending.removeAt(0)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, docId)
            context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val childId = cursor.getString(0)
                    val name = cursor.getString(1) ?: continue
                    if (name.startsWith(".")) continue
                    val rel = prefix + name
                    if (cursor.getString(4) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.add(childId to "$rel/")
                        continue
                    }
                    if (!isSafeAudioName(rel)) continue
                    entries.add(
                        Entry(
                            name = rel,
                            uri = DocumentsContract.buildDocumentUriUsingTree(folder, childId),
                            size = cursor.getLong(2),
                            mtimeMs = cursor.getLong(3),
                        )
                    )
                }
            }
        }
        return entries
    }

    private fun sendList(out: OutputStream) {
        val array = JSONArray()
        for (entry in listFiles()) {
            array.put(
                JSONObject()
                    .put("name", entry.name)
                    .put("size", entry.size)
                    .put("mtime", entry.mtimeMs / 1000)
            )
        }
        respond(out, 200, "OK", array.toString().toByteArray(Charsets.UTF_8), "application/json; charset=utf-8")
    }

    private fun sendFile(out: OutputStream, name: String) {
        val entry = listFiles().firstOrNull { it.name == name }
        if (entry == null) {
            respond(out, 404, "Not Found")
            return
        }
        val input = context.contentResolver.openInputStream(entry.uri)
        if (input == null) {
            respond(out, 404, "Not Found")
            return
        }
        input.use { stream ->
            writeHead(out, 200, "OK", "application/octet-stream", if (entry.size > 0) entry.size else null)
            stream.copyTo(out, 256 * 1024)
            out.flush()
        }
    }

    private fun respond(
        out: OutputStream,
        code: Int,
        reason: String,
        body: ByteArray = ByteArray(0),
        contentType: String = "text/plain; charset=utf-8",
        extra: String = "",
    ) {
        writeHead(out, code, reason, contentType, body.size.toLong(), extra)
        out.write(body)
        out.flush()
    }

    private fun writeHead(
        out: OutputStream,
        code: Int,
        reason: String,
        contentType: String,
        length: Long?,
        extra: String = "",
    ) {
        val head = StringBuilder("HTTP/1.1 $code $reason\r\nContent-Type: $contentType\r\nConnection: close\r\n")
        if (length != null) head.append("Content-Length: $length\r\n")
        head.append(extra).append("\r\n")
        out.write(head.toString().toByteArray(Charsets.UTF_8))
    }

    companion object {
        private val AUDIO_EXTS = setOf("mp3", "flac", "wav", "ogg", "m4a")
    }
}
