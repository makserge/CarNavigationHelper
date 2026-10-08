package com.smsoft.carnavigationhelper.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsoft.carnavigationhelper.R
import com.smsoft.carnavigationhelper.data.database.entity.Song
import com.smsoft.carnavigationhelper.ui.theme.TurquoiseGreen
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistItem(
    item: Song,
    isSelected: Boolean,
    onDislike: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        // Small vertical padding, the dislike button already brings a 48dp touch area
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (fontWeight, color) = if (isSelected) {
            FontWeight.Bold to TurquoiseGreen
        } else {
            FontWeight.Normal to Color.Unspecified // Use the default color from the style
        }

        Text(
            modifier = Modifier.weight(1f),
            text = formatTitle(item),
            fontWeight = fontWeight,
            color = color
        )
        Text(
            text = formatTime(item.duration),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = fontWeight,
            color = color
        )
        IconButton(
            onClick = onDislike
        ) {
            Icon(
                imageVector = Icons.Default.ThumbDown,
                contentDescription = stringResource(R.string.dislike)
            )
        }
    }
}

// Also used by the blacklist screen
fun formatTitle(item: Song): String {
    return if (item.artist != null && item.title != null) {
        item.artist + " - " + item.title
    } else item.fileName
}

private fun formatTime(duration: Long): String {
    return duration.milliseconds.toComponents { minutes, seconds, _ ->
        "%02d:%02d".format(minutes, seconds)
    }
}
