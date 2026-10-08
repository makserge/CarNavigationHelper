package com.smsoft.carnavigationhelper.ui.composable

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.smsoft.carnavigationhelper.data.database.entity.Song

@Composable
fun PlayerPlaylistItemsList(
    modifier: Modifier = Modifier,
    padding: PaddingValues,
    items: List<Song>,
    selectedItemId: Long,
    onDislike: (Song) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedItemId) {
        val index = items.indexOfFirst { it.id == selectedItemId }
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = padding,
        state = listState
    ) {
        items(items.size, key = { items[it].id }) { index ->
            val isSelected = items[index].id == selectedItemId
            PlaylistItem(
                modifier = Modifier,
                item = items[index],
                isSelected = isSelected,
                onDislike = { onDislike(items[index]) }
            )
        }
    }
}

