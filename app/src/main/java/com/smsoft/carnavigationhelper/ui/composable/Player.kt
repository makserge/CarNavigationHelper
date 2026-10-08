package com.smsoft.carnavigationhelper.ui.composable

import LoadingCircleWithText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.smsoft.carnavigationhelper.R
import com.smsoft.carnavigationhelper.data.TrackInfo
import com.smsoft.carnavigationhelper.data.database.entity.Song
import com.smsoft.carnavigationhelper.ui.screen.player.PlayerViewModel
import com.smsoft.carnavigationhelper.ui.screen.player.UIState

@Composable
fun Player(
    modifier: Modifier,
    padding: PaddingValues,
    viewModel: PlayerViewModel,
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val items = viewModel.playerPlaylist.collectAsStateWithLifecycle(
        initialValue = null
    )

    LaunchedEffect(Unit) {
        viewModel.onStart()
    }

    Spacer(modifier = Modifier.height(16.dp))
    val player = viewModel.player
    when {
        state.value == UIState.Initial || player == null -> LoadingCircleWithText()
        else -> PlayerContainer(
            modifier,
            padding,
            items.value,
            player,
            viewModel
        )
    }
}

@Composable
fun PlayerContainer(
    modifier: Modifier,
    padding: PaddingValues,
    items: List<Song>?,
    player: Player,
    viewModel: PlayerViewModel
) {
    val trackInfo by viewModel.trackInfo.collectAsStateWithLifecycle()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = viewModel.trackTitle.value, style = MaterialTheme.typography.headlineMedium)
        trackInfo?.let {
            Text(text = formatTrackInfo(it), style = MaterialTheme.typography.bodyMedium)
        }

        AudioPlayerProgress(
            modifier = Modifier,
            progress = viewModel.trackProgress.floatValue,
            currentPosition = viewModel.trackCurrentPosition.longValue,
            duration = viewModel.trackDuration.longValue
        )
        AudioPlayerControls(player)
        if (items != null) {
            if (items.isNotEmpty()) {
                PlayerPlaylistItemsList(
                    modifier = Modifier,
                    padding = padding,
                    items = items,
                    selectedItemId = viewModel.trackCurrentMediaId.longValue,
                    onDislike = { viewModel.dislike(it) }
                )
            } else {
                NoItems(
                    modifier = Modifier,
                    title = stringResource(R.string.no_player_items),
                )
            }
        }
    }
}

// e.g. "FLAC · 905 kbps", unknown parts are left out
private fun formatTrackInfo(info: TrackInfo): String {
    val parts = mutableListOf<String>()
    if (info.codec.isNotEmpty()) parts.add(info.codec)
    if (info.bitrateKbps > 0) parts.add("${info.bitrateKbps} kbps")
    return parts.joinToString(" · ")
}