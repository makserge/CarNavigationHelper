package com.smsoft.carnavigationhelper.ui.screen.blacklist

import androidx.lifecycle.ViewModel
import com.smsoft.carnavigationhelper.data.database.entity.Song
import com.smsoft.carnavigationhelper.data.database.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlacklistViewModel @Inject constructor(
    private val playerRepository: PlayerRepository
) : ViewModel() {
    val blacklist: Flow<List<Song>>
        get() = playerRepository.getBlacklisted

    // The player screen adds the song to the running playlist when it is shown again
    fun removeFromBlacklist(song: Song) {
        CoroutineScope(Dispatchers.IO).launch {
            playerRepository.setBlacklisted(song.id, false)
        }
    }
}
