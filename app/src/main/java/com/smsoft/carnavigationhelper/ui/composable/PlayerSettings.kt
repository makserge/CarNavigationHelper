package com.smsoft.carnavigationhelper.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsoft.carnavigationhelper.R
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_PLAYER_GAIN_DB
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_PLAYER_PLAYLIST_PATH
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.DEFAULT_VOLUME_NORMALIZATION
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository.Companion.PLAYER_GAIN_DB_RANGE
import com.smsoft.carnavigationhelper.ui.screen.player_settings.PlayerSettingsViewModel
import kotlin.math.roundToInt

@Composable
fun PlayerSettings(
    modifier: Modifier,
    viewModel: PlayerSettingsViewModel,
    onPickFolder: () -> Unit,
    onBlacklistAction: () -> Unit
) {
    val context = LocalContext.current

    val playlistPath by viewModel.playlistPath.collectAsStateWithLifecycle(
        initialValue = DEFAULT_PLAYER_PLAYLIST_PATH
    )
    val volumeNormalization by viewModel.volumeNormalization.collectAsStateWithLifecycle(
        initialValue = DEFAULT_VOLUME_NORMALIZATION
    )
    val playerGain by viewModel.playerGain.collectAsStateWithLifecycle(
        initialValue = DEFAULT_PLAYER_GAIN_DB
    )
    val blacklist by viewModel.blacklist.collectAsStateWithLifecycle(
        initialValue = emptyList()
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
            onStop = { viewModel.stopRescan() },
        )
        VolumeNormalization(
            checked = volumeNormalization,
            onCheckedChange = { viewModel.setVolumeNormalization(it) }
        )
        PlayerGain(
            gainDb = playerGain,
            onGainChange = { viewModel.setPlayerGain(it) }
        )
        Blacklist(
            count = blacklist.size,
            onClick = onBlacklistAction
        )
    }
}

@Composable
private fun VolumeNormalization(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    // The whole row switches, not only the small switch (easier to hit in the car)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.volume_normalization)
            )
            Text(
                text = stringResource(R.string.volume_normalization_hint),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}

// Extra gain for all songs, saved to 0.1 dB when the slider is released. It moves while dragging
@Composable
private fun PlayerGain(
    gainDb: Double,
    onGainChange: (Double) -> Unit
) {
    // Follows the stored value, but keeps the dragged value until it is saved
    val sliderValue = remember(gainDb) { mutableFloatStateOf(gainDb.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.player_gain)
            )
            Text(
                text = stringResource(R.string.gain_value, sliderValue.floatValue)
            )
        }
        Text(
            text = stringResource(R.string.player_gain_hint),
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = sliderValue.floatValue,
            onValueChange = { sliderValue.floatValue = it },
            onValueChangeFinished = {
                onGainChange((sliderValue.floatValue * 10).roundToInt() / 10.0)
            },
            valueRange = PLAYER_GAIN_DB_RANGE.start.toFloat()..PLAYER_GAIN_DB_RANGE.endInclusive.toFloat()
        )
    }
}

// Blacklisted by Next or the dislike button, the list itself is on its own screen. A rescan clears it
@Composable
private fun Blacklist(
    count: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 16.dp, bottom = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.blacklist, count),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null
        )
    }
}
