package com.smsoft.carnavigationhelper.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AudioPlayerProgress(
    modifier: Modifier,
    progress: Float,
    currentPosition: Long,
    duration: Long
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = 32.dp
            )
    ) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 16.dp
                ),
            progress = { progress },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentPosition),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun formatTime(duration: Long): String {
    return duration.milliseconds.toComponents { minutes, seconds, _ ->
        "%02d:%02d".format(minutes, seconds)
    }
}