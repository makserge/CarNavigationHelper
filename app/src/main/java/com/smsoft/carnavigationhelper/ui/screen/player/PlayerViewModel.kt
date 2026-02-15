package com.smsoft.carnavigationhelper.ui.screen.player

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Environment
import android.text.Html
import androidx.annotation.OptIn
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.smsoft.carnavigationhelper.data.database.repository.PlayerRepository
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
import kotlin.random.Random

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    playerRepository: PlayerRepository
) : ViewModel() {
    private val coroutineScope= CoroutineScope(Dispatchers.IO)

    private val uiStateInt = MutableStateFlow<UIState>(UIState.Initial)
    val uiState = uiStateInt.asStateFlow()

    var trackCurrentMediaId = mutableLongStateOf(0L)
    var trackTitle = mutableStateOf("")
    var trackDuration = mutableLongStateOf(0L)
    var trackProgress = mutableFloatStateOf(0F)
    var trackCurrentPosition = mutableLongStateOf(0L)

    val playerPlaylist = playerRepository.getAll

    private var job: Job? = null

    fun checkAllFilesPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
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
            mediaItems.shuffle(Random(System.currentTimeMillis()))
            setupPlayer(mediaItems, callback)
        }
    }

    @OptIn(UnstableApi::class)
    private fun setupPlayer(items: List<MediaItem>, callback: (player: Player) -> Unit) {
        var player: Player? = null
        val playerListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if ((mediaItem != null) && (mediaItem.mediaId != null) && (mediaItem.mediaId.isNotEmpty())) {
                    trackCurrentMediaId.longValue = mediaItem.mediaId.toLong()
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                trackDuration.longValue = if (mediaMetadata.durationMs != null) mediaMetadata.durationMs!! else 0
                trackTitle.value = convertCharset(mediaMetadata)
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
                        uiStateInt.value = UIState.Ready
                    }
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                stopProgressUpdate()
                if (isPlaying) {
                    job = coroutineScope.launch(Dispatchers.Main) {
                        while (true) {
                            player?.let {
                                trackProgress.floatValue = if (it.currentPosition > 0) (it.currentPosition.toFloat() / it.duration) else 0F
                                trackCurrentPosition.longValue = it.currentPosition
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
                player.prepare()
                player.playWhenReady = true
                callback(player)
            } catch (_: ExecutionException) {
            }
        }, MoreExecutors.directExecutor())
    }

    private fun convertCharset(metaData: MediaMetadata): String {
        var value: CharSequence = metaData.displayTitle ?: ""
        if (!metaData.artist.isNullOrEmpty() && !metaData.title.isNullOrEmpty()) {
            value = "${metaData.artist} - ${metaData.title}"
        }
        return Html.fromHtml(value.toString(), HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    }

    private fun stopProgressUpdate() {
        job?.cancel()
    }
}

sealed class UIState {
    data object Initial : UIState()
    data object Ready : UIState()
}
