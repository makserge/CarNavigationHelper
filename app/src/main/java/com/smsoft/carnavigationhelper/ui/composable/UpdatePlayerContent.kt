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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smsoft.carnavigationhelper.R
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun UpdatePlayerContent(
    modifier: Modifier,
    isPlaylistUpdating: Boolean,
    audioFilesCount: String,
    audioFilesDuration: Long,
    audioFilesSize: Long,
    onClick: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = modifier
    ) {
        Text(
            modifier = Modifier.padding(bottom = 8.dp),
            text = stringResource(R.string.current_playlist),
        )
        Text(
            modifier = Modifier.padding(top = 16.dp),
            text = audioFilesCount + " / " + formatDuration(audioFilesDuration)  + " / " + stringResource(R.string.gb, (audioFilesSize.toDouble() / (1024 * 1024 * 1024))),
        )
        Row(
            modifier = Modifier.padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isPlaylistUpdating) {
                Text(
                    modifier = Modifier
                        .padding(
                            top = 12.dp,
                            bottom = 12.dp,
                            end = 16.dp),
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
                Text(
                    modifier = Modifier
                        .padding(
                            top = 12.dp,
                            bottom = 12.dp,
                            start = 36.dp,
                            end = 16.dp)
                        .clickable(onClick = onStop),
                    text = stringResource(R.string.stop_scan)
                )
            } else {
                Text(
                    modifier = Modifier
                        .clickable(
                            onClick = {
                                onClick()
                            }
                        ),
                    text = stringResource(R.string.update_content)
                )
                IconButton(
                    modifier = Modifier,
                    onClick = {
                        onClick()
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
    }
}