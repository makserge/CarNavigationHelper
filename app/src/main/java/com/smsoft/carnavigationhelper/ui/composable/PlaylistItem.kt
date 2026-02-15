package com.smsoft.carnavigationhelper.ui.composable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsoft.carnavigationhelper.data.database.entity.Song
import com.smsoft.carnavigationhelper.ui.theme.TurquoiseGreen
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistItem(
    item: Song,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
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
    }
}

private fun formatTitle(item: Song): String {
    return if (item.artist != null && item.title != null) {
        item.artist + " - " + item.title
    } else item.fileName
}

private fun formatTime(duration: Long): String {
    return duration.milliseconds.toComponents { minutes, seconds, _ ->
        "%02d:%02d".format(minutes, seconds)
    }
}