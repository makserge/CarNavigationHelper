package com.smsoft.carnavigationhelper.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// HTTP client for the add-on's sync API. Blocking calls: run them on Dispatchers.IO.
// Network errors are IOException (retry later); everything else is SyncException.
class SyncClient(baseUrl: String, private val token: String) {
    private val base = baseUrl.trim().trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    init {
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            throw SyncException(SyncFailure.BAD_ADDRESS, retryable = false)
        }
    }

    fun listFiles(): List<RemoteFile> {
        val conn = open("$base/api/v1/files")
        try {
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return json.decodeFromString(ListSerializer(RemoteFile.serializer()), text)
        } catch (_: SerializationException) {
            throw SyncException(SyncFailure.BAD_ANSWER, retryable = false)
        } finally {
            conn.disconnect()
        }
    }

    // Copies one file into out and returns the number of bytes copied.
    // stopRequested is checked between chunks, so a stop does not wait for the whole file
    fun download(file: RemoteFile, out: OutputStream, stopRequested: () -> Boolean): Long {
        val encoded = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
        val conn = open("$base/api/v1/files/$encoded")
        try {
            return conn.inputStream.use { input ->
                val buffer = ByteArray(256 * 1024)
                var total = 0L
                while (true) {
                    if (stopRequested()) throw CancellationException("Sync stopped")
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    total += read
                }
                total
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("Authorization", "Bearer $token")
        val code = try {
            conn.responseCode
        } catch (e: IOException) {
            conn.disconnect()
            throw e
        }
        when {
            code == HttpURLConnection.HTTP_OK -> return conn
            code == HttpURLConnection.HTTP_UNAUTHORIZED -> {
                conn.disconnect()
                throw SyncException(SyncFailure.WRONG_TOKEN, retryable = false)
            }
            else -> {
                conn.disconnect()
                throw SyncException(SyncFailure.SERVER_ERROR, retryable = code >= 500)
            }
        }
    }
}
