package com.smsoft.carnavigationhelper.ui.screen.player_settings

import android.content.Context
import android.provider.DocumentsContract
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import com.smsoft.carnavigationhelper.R
import com.smsoft.carnavigationhelper.data.database.repository.PlayerRepository
import com.smsoft.carnavigationhelper.data.sync.ContentSyncRepository
import com.smsoft.carnavigationhelper.data.sync.SyncException
import com.smsoft.carnavigationhelper.data.sync.SyncScheduler
import com.smsoft.carnavigationhelper.data.wifi.WifiNetworks
import com.smsoft.carnavigationhelper.service.ContentServerService
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import com.smsoft.carnavigationhelper.data.database.entity.Song
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class PlayerSettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playerRepository: PlayerRepository,
    private val contentSyncRepository: ContentSyncRepository,
) : ViewModel() {
    val playlistPath: Flow<String>
        get() = userPreferencesRepository.playerPlaylistPathFlow

    val volumeNormalization: Flow<Boolean>
        get() = userPreferencesRepository.volumeNormalizationFlow

    val playerGain: Flow<Double>
        get() = userPreferencesRepository.playerGainFlow

    val blacklist: Flow<List<Song>>
        get() = playerRepository.getBlacklisted

    var audioFilesCount = mutableIntStateOf(0)
    var audioFilesDuration = mutableLongStateOf(0)
    var audioFilesSize = mutableLongStateOf(0)
    var isPlaylistUpdating = mutableStateOf(false)
    private var scanJob: Job? = null

    val syncUrl: Flow<String>
        get() = userPreferencesRepository.syncUrlFlow

    val syncToken: Flow<String>
        get() = userPreferencesRepository.syncTokenFlow

    val syncAuto: Flow<Boolean>
        get() = userPreferencesRepository.syncAutoFlow

    val syncMaxPercent: Flow<Int>
        get() = userPreferencesRepository.syncMaxPercentFlow

    val syncWifi: Flow<String>
        get() = userPreferencesRepository.syncWifiFlow

    val serverEnabled: Flow<Boolean>
        get() = userPreferencesRepository.serverEnabledFlow

    val serverPort: Flow<Int>
        get() = userPreferencesRepository.serverPortFlow

    val serverKey: Flow<String>
        get() = userPreferencesRepository.serverKeyFlow

    fun setSyncWifi(ssid: String) {
        CoroutineScope(Dispatchers.IO).launch { userPreferencesRepository.setSyncWifi(ssid) }
    }

    // Networks in range from the last Wi-Fi scan. Needs location permission; calls back with the list
    fun loadNetworks(context: Context, onResult: (List<String>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            onResult(WifiNetworks.visibleNetworks(context))
        }
    }

    fun setServerEnabled(context: Context, enabled: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            userPreferencesRepository.setServerEnabled(enabled)
            if (enabled) {
                ContentServerService.start(context.applicationContext)
            } else {
                ContentServerService.stop(context.applicationContext)
            }
        }
    }

    // Saves port and key; restarts the server if it is on
    fun applyServer(context: Context, port: Int, key: String) {
        CoroutineScope(Dispatchers.IO).launch {
            userPreferencesRepository.setServer(port, key)
            if (userPreferencesRepository.serverEnabledFlow.first()) {
                ContentServerService.start(context.applicationContext)
            }
        }
    }

    // Applied at the next sync: the oldest synced files are removed if the new limit is smaller
    fun saveSyncMaxPercent(percent: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            userPreferencesRepository.setSyncMaxPercent(percent)
        }
    }

    var isSyncing = mutableStateOf(false)
    var syncStatus = mutableStateOf("")

    init {
        // The totals follow the playlist table, so they change during a sync as songs are added
        CoroutineScope(Dispatchers.IO).launch {
            combine(playerRepository.getAll, playerRepository.getBlacklisted) { active, blacklisted ->
                active + blacklisted
            }.collect { items -> applySummary(items) }
        }
    }

    // All songs of the folder like the rescan counts them, blacklisted ones included
    private fun applySummary(items: List<Song>) {
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

    // The player applies it to the playing song at once
    fun setVolumeNormalization(enabled: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            userPreferencesRepository.setVolumeNormalization(enabled)
        }
    }

    // Saved when the slider is released, the player applies it to the playing song at once
    fun setPlayerGain(gainDb: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            userPreferencesRepository.setPlayerGain(gainDb)
        }
    }

    // The folder can only be read with the grant persisted when it was picked. A fresh install (default path)
    // or a backup restore has no grant
    fun hasFolderAccess(context: Context, path: String): Boolean {
        val uri = path.toUri()
        return context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    }

    fun rescanAudioFiles(context: Context) {
        if (scanJob?.isActive == true) return
        isPlaylistUpdating.value = true
        scanJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val path = userPreferencesRepository.playerPlaylistPathFlow.first()
                if (path.isNotEmpty()) {
                    playerRepository.updatePlaylist(context, path.toUri()) {
                        audioFilesCount.intValue = it.first
                        audioFilesDuration.longValue = it.second
                        audioFilesSize.longValue = it.third
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // e.g. SecurityException when the folder grant was revoked, the old playlist stays
            } finally {
                withContext(NonCancellable) {
                    isPlaylistUpdating.value = false
                    // A stopped scan shows the counts of the playlist that is still there
                    applySummary(playerRepository.getAll.first() + playerRepository.getBlacklisted.first())
                }
            }
        }
    }

    // The playlist is replaced only when a scan finishes, so a stopped one leaves the old playlist in place
    fun stopRescan() {
        scanJob?.cancel()
    }

    // Turns the automatic sync on or off; the schedule is kept by WorkManager
    fun setAutomaticSync(context: Context, enabled: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            userPreferencesRepository.setAutoSync(enabled)
            SyncScheduler.setAutomatic(context, enabled)
        }
    }

    fun saveSyncSettings(url: String, token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            userPreferencesRepository.setSyncSettings(url, token)
        }
    }

    // Saves the address and token, then copies new files from the add-on. Shows the result in syncStatus
    private var syncJob: Job? = null

    fun syncNow(context: Context, url: String, token: String) {
        if (isSyncing.value) return
        isSyncing.value = true
        syncStatus.value = context.getString(R.string.sync_syncing)
        syncJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                userPreferencesRepository.setSyncSettings(url, token)
                val result = contentSyncRepository.syncNow(context) { done, total ->
                    syncStatus.value = context.getString(R.string.sync_progress, done, total)
                }
                syncStatus.value = if (result.noSpace) {
                    context.getString(R.string.sync_no_space)
                } else {
                    context.getString(
                        R.string.sync_done,
                        result.downloaded, result.removed, result.alreadyThere, result.conflicts, result.failed
                    )
                }
            } catch (e: CancellationException) {
                syncStatus.value = context.getString(R.string.sync_stopped)
                throw e
            } catch (e: SyncException) {
                syncStatus.value = context.getString(e.reason.messageRes)
            } catch (_: IOException) {
                syncStatus.value = context.getString(R.string.sync_unreachable)
            } finally {
                isSyncing.value = false
                syncJob = null
            }
        }
    }

    // Stops a running sync. Files already copied stay, and the player list is updated
    fun stopSync() {
        syncJob?.cancel()
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