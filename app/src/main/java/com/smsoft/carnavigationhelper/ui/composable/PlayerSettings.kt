package com.smsoft.carnavigationhelper.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsoft.carnavigationhelper.R
import com.smsoft.carnavigationhelper.data.PlayerType
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_PLAYER_PLAYLIST_PATH
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_PLAYER_TYPE
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.PLAYER_TYPE
import com.smsoft.carnavigationhelper.ui.screen.player_settings.PlayerSettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun PlayerSettings(
    modifier: Modifier,
    viewModel: PlayerSettingsViewModel,
    onPickFolder: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val playlistPath by viewModel.playlistPath.collectAsStateWithLifecycle(
        initialValue = DEFAULT_PLAYER_PLAYLIST_PATH
    )
    val playerType by viewModel.playerType.collectAsStateWithLifecycle(
        initialValue = DEFAULT_PLAYER_TYPE
    )

    val scrollState = rememberScrollState()
    Spacer(modifier = Modifier.height(16.dp))
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.player_type),
            modifier = Modifier.padding(vertical = 16.dp),
        )
        RadioButtonGroup(
            modifier,
            options = PlayerType.entries.toTypedArray(),
            value = PlayerType.fromName(playerType),
            onOptionSelected = { value ->
                scope.launch {
                    viewModel.updateField(PLAYER_TYPE, value.name)
                }
            },
        )
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
                viewModel.rescanAudioFiles(context)
            },
        )
    }
}