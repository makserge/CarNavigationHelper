package com.smsoft.carnavigationhelper.ui.screen.player_settings

import android.content.Context
import android.provider.DocumentsContract
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import com.smsoft.carnavigationhelper.data.database.repository.PlayerRepository
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerSettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playerRepository: PlayerRepository
) : ViewModel() {
    val playlistPath: Flow<String>
        get() = userPreferencesRepository.playerPlaylistPathFlow

    var audioFilesCount = mutableIntStateOf(0)
    var audioFilesDuration = mutableLongStateOf(0)
    var audioFilesSize = mutableLongStateOf(0)
    var isPlaylistUpdating = mutableStateOf(false)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            loadPlaylistSummary()
        }
    }

    private suspend fun loadPlaylistSummary() {
        val items = playerRepository.getAll.first()
        var totalDuration = 0L
        var totalSize = 0L
        for (item in items) {
            totalDuration += item.duration
            totalSize += item.fileSize
        }
        audioFilesCount.intValue = items.count()
        audioFilesDuration.longValue = totalDuration
        audioFilesSize.longValue = totalSize
    }

    suspend fun updateField(key: Preferences.Key<out Any>, value: String) {
        if (value.isNotEmpty()) {
            userPreferencesRepository.setValue(key, value)
        }
    }

    // The folder can only be read with the grant persisted when it was picked. A fresh install (default path)
    // or a backup restore has no grant
    fun hasFolderAccess(context: Context, path: String): Boolean {
        val uri = path.toUri()
        return context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    }

    fun rescanAudioFiles(context: Context) {
        isPlaylistUpdating.value = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val path = userPreferencesRepository.playerPlaylistPathFlow.first()
                if (path.isNotEmpty()) {
                    playerRepository.updatePlaylist(context, path.toUri()) {
                        audioFilesCount.intValue = it.first
                        audioFilesDuration.longValue = it.second
                        audioFilesSize.longValue = it.third
                    }
                }
            } catch (_: Exception) {
                // e.g. SecurityException when the folder grant was revoked, the old playlist stays
            } finally {
                isPlaylistUpdating.value = false
            }
        }
    }

    fun getPathFromUri(path: String): String {
        val docId = DocumentsContract.getTreeDocumentId(path.toUri()) // Returns "1234-ABCD:Music"
        val split = docId.split(":")
        val volumeId = split[0]
        val relativePath = if (split.size > 1) split[1] else ""
        return if (volumeId == "primary") {
            "/storage/emulated/0/$relativePath"
        } else {
            "/storage/$volumeId/$relativePath"
        }
    }
}