package com.smsoft.carnavigationhelper.ui.screen.player

import android.content.ComponentName
import android.content.Context
import android.text.Html
import androidx.annotation.OptIn
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.text.HtmlCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.toRoute
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.smsoft.carnavigationhelper.data.Player as PlayerRoute
import com.smsoft.carnavigationhelper.data.database.repository.PlayerRepository
import com.smsoft.carnavigationhelper.service.AudioPlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    playerRepository: PlayerRepository
) : ViewModel() {
    // Player(true) is the one that starts the music for the trip
    private val isForceNavigation = savedStateHandle.toRoute<PlayerRoute>().isForceNavigation

    private val uiStateInt = MutableStateFlow<UIState>(UIState.Initial)
    val uiState = uiStateInt.asStateFlow()

    // Set once the player is connected and playing, PlayerScreen turns it into a single onPlay call
    private val playbackStartedInt = MutableStateFlow(false)
    val playbackStarted = playbackStartedInt.asStateFlow()

    var trackCurrentMediaId = mutableLongStateOf(0L)
    var trackTitle = mutableStateOf("")
    var trackDuration = mutableLongStateOf(0L)
    var trackProgress = mutableFloatStateOf(0F)
    var trackCurrentPosition = mutableLongStateOf(0L)

    val playerPlaylist = playerRepository.getAll

    private var job: Job? = null

    // Kept in the ViewModel so coming back from the settings screen reuses the running player
    // instead of building a new one (which would reshuffle and trigger the auto navigation again)
    var player by mutableStateOf<Player?>(null)
        private set
    private var isStarting = false
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val playerListener = object : Player.Listener {
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
                job = viewModelScope.launch {
                    while (true) {
                        updateProgress()
                        delay(500)
                    }
                }
            }
        }
    }

    fun onStart() {
        if (isStarting) return
        isStarting = true
        viewModelScope.launch {
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
            val controller = player
            if (controller == null) {
                setupPlayer(mediaItems)
            } else {
                // Shown again (e.g. back from the player settings): only a rescan changes the playlist
                syncPlaylist(controller, mediaItems)
                isStarting = false
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun setupPlayer(items: List<MediaItem>) {
        val sessionToken = SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val controller = future.get()
                controller.addListener(playerListener)
                player = controller
                syncPlaylist(controller, items)
                playbackStartedInt.value = true
            } catch (_: ExecutionException) {
                // No music, but the auto navigation must still go on
                playbackStartedInt.value = true
            } catch (_: CancellationException) {
            } finally {
                isStarting = false
            }
        }, MoreExecutors.directExecutor())
    }

    // The session outlives this ViewModel (e.g. the player is opened again from the music icon while music plays),
    // so a new shuffled playlist is only set when the session has none or a rescan changed the songs.
    // Player(true) also sets it when the music is paused or over (e.g. paused at the end of the last trip)
    private fun syncPlaylist(controller: Player, items: List<MediaItem>) {
        val sessionIds = (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId }.toSet()
        if (sessionIds.isNotEmpty() && sessionIds == items.map { it.mediaId }.toSet()
            && (controller.playWhenReady || !isForceNavigation)) {
            // Keep playing untouched. No callback fires for the item that is already playing, so show it directly
            playerListener.onMediaItemTransition(controller.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
            playerListener.onMediaMetadataChanged(controller.mediaMetadata)
            playerListener.onIsPlayingChanged(controller.isPlaying)
            updateProgress()
            uiStateInt.value = UIState.Ready
        } else {
            controller.setMediaItems(items.shuffled(Random(System.currentTimeMillis())))
            controller.prepare()
            controller.playWhenReady = true
        }
    }

    private fun updateProgress() {
        player?.let {
            trackProgress.floatValue = if (it.currentPosition > 0 && it.duration > 0) (it.currentPosition.toFloat() / it.duration) else 0F
            trackCurrentPosition.longValue = it.currentPosition
        }
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

    override fun onCleared() {
        // The progress loop stops with viewModelScope. Releasing the controller only unbinds from the service,
        // the music keeps playing there and the next player screen connects again
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}

sealed class UIState {
    data object Initial : UIState()
    data object Ready : UIState()
}
