package com.smsoft.carnavigationhelper.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smsoft.carnavigationhelper.R

@Composable
fun PlayerPlaylistPath(
    modifier: Modifier,
    path: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.padding(top = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.player_playlist_path),
            modifier = Modifier,
        )
        Row(
            modifier = modifier.padding(
                top = 8.dp,
                bottom = 16.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = path,
                modifier = Modifier,
            )
            IconButton(
                modifier = Modifier,
                onClick = onClick
            ) {
                Icon(
                    modifier = Modifier.padding(start = 16.dp),
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = stringResource(R.string.pick_playlist_path)
                )
            }
        }
    }
}