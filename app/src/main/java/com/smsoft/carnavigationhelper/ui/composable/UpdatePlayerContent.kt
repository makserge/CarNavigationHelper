package com.smsoft.carnavigationhelper.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smsoft.carnavigationhelper.R
import com.smsoft.carnavigationhelper.ui.screen.settings.SettingsViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun UpdatePlayerContent(
    modifier: Modifier,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
    ) {
        Text(
            modifier = Modifier
                .padding(
                    top = 16.dp,
                    bottom = 8.dp
                ),
            text = stringResource(R.string.player_playlist),
        )
        Text(
            modifier = Modifier
                .padding(
                    top = 16.dp
                ),
            text = viewModel.audioFilesCount.intValue.toString() + " / " + formatDuration(viewModel.audioFilesDuration.longValue)  + " / " +
                    stringResource(R.string.gb, (viewModel.audioFilesSize.longValue.toDouble() / (1024 * 1024 * 1024))),
        )
        Row(
            modifier = Modifier
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (viewModel.isPlaylistUpdating.value) {
                Text(
                    modifier = Modifier
                        .padding(
                            top = 12.dp,
                            bottom = 12.dp,
                            end = 52.dp),
                    text = stringResource(R.string.updating)
                )
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(
                            top = 12.dp,
                            bottom = 12.dp,
                        )
                        .size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    modifier = Modifier
                        .clickable(
                            onClick = {
                                viewModel.rescanAudioFiles(context)
                            }
                        ),
                    text = stringResource(R.string.update_content)
                )
                IconButton(
                    modifier = Modifier,
                    onClick = {
                        viewModel.rescanAudioFiles(context)
                    }
                ) {
                    Icon(
                        modifier = Modifier,
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.rescan_audio_files)
                    )
                }
            }
        }
    }
}

private fun formatDuration(duration: Long): String {
    return duration.milliseconds.toComponents { hours, minutes, seconds, _ ->
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    }.toString()
}