package com.smsoft.carnavigationhelper.ui.screen.settings

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.smsoft.carnavigationhelper.data.GeoPoint
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
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playerRepository: PlayerRepository
) : ViewModel() {
    val playerPlaylistPath: Flow<String>
        get() = userPreferencesRepository.playerPlaylistPathFlow

    val homePosition: Flow<GeoPoint>
        get() = userPreferencesRepository.homePositionFlow

    val workPosition: Flow<GeoPoint>
        get() = userPreferencesRepository.workPositionFlow

    val countdownTimerDelay: Flow<Int>
        get() = userPreferencesRepository.countdownTimerDelayFlow

    val navType: Flow<String>
        get() = userPreferencesRepository.navTypeFlow

    val playerType: Flow<String>
        get() = userPreferencesRepository.playerTypeFlow

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
        val mediaItems = mutableListOf<MediaItem>()
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

    fun rescanAudioFiles(context: Context) {
        isPlaylistUpdating.value = true
        CoroutineScope(Dispatchers.IO).launch {
            val path = userPreferencesRepository.playerPlaylistPathFlow.first()
            playerRepository.updatePlaylist(context, path) {
                audioFilesCount.intValue = it.first
                audioFilesDuration.longValue = it.second
                audioFilesSize.longValue = it.third
            }
            isPlaylistUpdating.value = false
        }
    }
}