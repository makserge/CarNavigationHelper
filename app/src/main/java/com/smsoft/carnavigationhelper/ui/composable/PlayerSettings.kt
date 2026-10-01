package com.smsoft.carnavigationhelper.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_PLAYER_PLAYLIST_PATH
import com.smsoft.carnavigationhelper.ui.screen.player_settings.PlayerSettingsViewModel

@Composable
fun PlayerSettings(
    modifier: Modifier,
    viewModel: PlayerSettingsViewModel,
    onPickFolder: () -> Unit
) {
    val context = LocalContext.current

    val playlistPath by viewModel.playlistPath.collectAsStateWithLifecycle(
        initialValue = DEFAULT_PLAYER_PLAYLIST_PATH
    )

    val scrollState = rememberScrollState()
    Spacer(modifier = Modifier.height(16.dp))
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 16.dp)
    ) {
        PlayerPlaylistPath(
            modifier,
            viewModel.getPathFromUri(playlistPath),
            onPickFolder
        )
        UpdatePlayerContent(
            modifier,
            audioFilesCount = viewModel.audioFilesCount.intValue.toString(),
            audioFilesDuration = viewModel.audioFilesDuration.longValue,
            audioFilesSize = viewModel.audioFilesSize.longValue,
            isPlaylistUpdating = viewModel.isPlaylistUpdating.value,
            onClick = {
                // Without access to the folder the scan can not read it, so let the user pick it first
                if (viewModel.hasFolderAccess(context, playlistPath)) {
                    viewModel.rescanAudioFiles(context)
                } else {
                    onPickFolder()
                }
            },
        )
    }
}