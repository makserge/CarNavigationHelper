package com.smsoft.carnavigationhelper.ui.screen.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.Html
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.smsoft.carnavigationhelper.data.database.repository.PlayerRepository
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository
import com.smsoft.carnavigationhelper.service.AudioPlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepository: PlayerRepository
) : ViewModel() {
    private val coroutineScope= CoroutineScope(Dispatchers.IO)

    private val uiStateInt = MutableStateFlow<UIState>(UIState.Initial)
    val uiState = uiStateInt.asStateFlow()

    var currentMediaId = mutableStateOf("")
    var metaTitle = mutableStateOf("")
    var duration = mutableLongStateOf(0L)
    var progress = mutableFloatStateOf(0F)
    var currentPosition = mutableLongStateOf(0L)

    val playerPlaylist = playerRepository.getAll

    private var job: Job? = null

    fun getMediaPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun checkMediaPermission(context: Context): Boolean {
        return context.checkSelfPermission(getMediaPermission()) == PackageManager.PERMISSION_GRANTED
    }

    fun onStart(callback: (player: Player) -> Unit) {
        coroutineScope.launch {
            val mediaItems = mutableListOf<MediaItem>()
            val items = playerPlaylist.first()
            for (item in items) {
                val mediaItem = MediaItem.Builder()
                    .setMediaId(item.id.toString())
                    .setUri(item.contentUri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setDisplayTitle(item.fileName)
                            .setArtist(item.artist)
                            .setTitle(item.title)
                            .setDurationMs(item.duration)
                            .build()
                    )
                    .build()
                mediaItems.add(mediaItem)
            }
            setupPlayer(mediaItems, callback)
        }
    }

    private fun setupPlayer(items: List<MediaItem>, callback: (player: Player) -> Unit) {
        var player: Player? = null
        val playerListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentMediaId.value = mediaItem?.mediaId.toString()
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                duration.longValue = if (mediaMetadata.durationMs != null) mediaMetadata.durationMs!! else 0
                metaTitle.value = convertCharset(mediaMetadata)
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                    }
                    Player.STATE_READY -> {
                        uiStateInt.value = UIState.Ready
                    }
                    Player.STATE_ENDED -> {
                        uiStateInt.value = UIState.Ready
                    }
                    Player.STATE_IDLE -> {
                    }
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                stopProgressUpdate()
                if (isPlaying) {
                    job = coroutineScope.launch(Dispatchers.Main) {
                        while (true) {
                            player?.let {
                                progress.floatValue = if (it.currentPosition > 0) (it.currentPosition.toFloat() / it.duration).toFloat() else 0F
                                currentPosition.longValue = it.currentPosition
                            }
                            delay(500)
                        }
                    }
                }
            }
        }
        val sessionToken = SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                player = controllerFuture.get()
                player.addListener(playerListener)
                player.setMediaItems(items)
                player.setShuffleModeEnabled(true)
                player.volume = 1f
                player.prepare()
                player.playWhenReady = true
                callback(player)
            } catch (_: ExecutionException) {
            }
        }, MoreExecutors.directExecutor())
    }

    private fun convertCharset(metaData: MediaMetadata): String {
        var value = ""
        if ((metaData.artist != null) && (metaData.artist!!.isNotEmpty()) && (metaData.title != null) && (metaData.title!!.isNotEmpty())) {
            value = metaData.artist!!.toString() + " - " + metaData.title!!
        }
        if (value.isNotEmpty()) {
            return Html.fromHtml(value, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        }
        return value
    }

    private fun stopProgressUpdate() {
        job?.cancel()
    }
}

sealed class UIState {
    data object Initial : UIState()
    data object Ready : UIState()
}