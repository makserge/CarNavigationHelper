package com.smsoft.carnavigationhelper.ui.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.compose.material3.buttons.NextButton
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import androidx.media3.ui.compose.material3.buttons.PreviousButton

@Composable
fun AudioPlayerControls(player: Player) {
    Row(
        modifier = Modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PreviousButton(player)
        Spacer(modifier = Modifier.width(8.dp))
        PlayPauseButton(player)
        Spacer(modifier = Modifier.width(8.dp))
        NextButton(player)
    }
}