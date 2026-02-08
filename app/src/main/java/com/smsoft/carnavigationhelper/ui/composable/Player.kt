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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.smsoft.carnavigationhelper.R
import com.smsoft.carnavigationhelper.data.database.entity.Song
import com.smsoft.carnavigationhelper.ui.screen.player.PlayerViewModel
import com.smsoft.carnavigationhelper.ui.screen.player.UIState

@Composable
fun Player(
    modifier: Modifier,
    padding: PaddingValues,
    viewModel: PlayerViewModel,
    onPlaybackStarted: () -> Unit,
) {
    val context = LocalContext.current
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    val items = viewModel.playerPlaylist.collectAsStateWithLifecycle(
        initialValue = null
    )

    var player by remember { mutableStateOf<Player?>(null) }
    LaunchedEffect(Unit) {
        viewModel.onStart() {
            player = it
            onPlaybackStarted()
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    when (state.value) {
        UIState.Initial -> LoadingCircleWithText()
        UIState.Ready -> PlayerContainer(
            modifier,
            padding,
            items.value,
            player!!,
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
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = viewModel.metaTitle.value, style = MaterialTheme.typography.headlineMedium)

        AudioPlayerProgress(
            modifier = Modifier,
            progress = viewModel.progress.floatValue,
            currentPosition = viewModel.currentPosition.longValue,
            duration = viewModel.duration.longValue
        )
        AudioPlayerControls(player)
        if (items != null) {
            if (items.isNotEmpty()) {
                ItemsList(
                    modifier = Modifier,
                    padding = padding,
                    items = items,
                    selectedItemId = viewModel.currentMediaId.value.toLong(),
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