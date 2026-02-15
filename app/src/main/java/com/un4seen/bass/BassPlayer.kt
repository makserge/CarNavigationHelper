package com.un4seen.bass
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class BassPlayer(val context: Context, looper: Looper) : SimpleBasePlayer(looper) {
    private val handler = Handler(looper)

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var bassHandle: Int = 0
    private var isPrepared = false
    private var currentMediaItem: MediaItem? = null
    private val playlistItems = mutableListOf<MediaItem>()
    private var currentIndex = 0
    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setOnAudioFocusChangeListener {}.build()

    private var userVolume = 0.8f

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                BASS.BASS_ChannelSlideAttribute(bassHandle, BASS.BASS_ATTRIB_VOL, userVolume * 0.2f, 500)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                BASS.BASS_ChannelSetAttribute(bassHandle, BASS.BASS_ATTRIB_VOL, userVolume)
                if (BASS.BASS_ChannelIsActive(bassHandle) == BASS.BASS_ACTIVE_PAUSED) {
                    BASS.BASS_ChannelPlay(bassHandle, false)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                handleSetPlayWhenReady(false)
            }
        }
        handler.post { invalidateState() }
    }

    init {
        if (!BASS.BASS_Init(-1, 44100, 0)) {
            Log.e("BassPlayer", "BASS Init Error: ${BASS.BASS_ErrorGetCode()}")
        }
    }

    override fun getState(): State {
        val durationMs = if (bassHandle != 0) {
            (BASS.BASS_ChannelBytes2Seconds(bassHandle, BASS.BASS_ChannelGetLength(bassHandle, 0)) * 1000).toLong()
        } else C.TIME_UNSET

        val currentPosMs = if (bassHandle != 0) {
            (BASS.BASS_ChannelBytes2Seconds(bassHandle, BASS.BASS_ChannelGetPosition(bassHandle, 0)) * 1000).toLong()
        } else 0L

        val playlistData = playlistItems.map { mediaItem ->
            val builder = MediaItemData.Builder(mediaItem)
                .setMediaItem(mediaItem)
                .setMediaMetadata(mediaItem.mediaMetadata)
            if (mediaItem == currentMediaItem) {
                builder.setDurationUs(Util.msToUs(durationMs))
            }
            builder.build()
        }

        val stateBuilder = State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder().addAll(
                    COMMAND_PLAY_PAUSE,
                    COMMAND_PREPARE,
                    COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    COMMAND_GET_CURRENT_MEDIA_ITEM,
                    COMMAND_GET_TIMELINE,
                    COMMAND_CHANGE_MEDIA_ITEMS,
                    COMMAND_GET_METADATA,
                    COMMAND_SEEK_TO_NEXT,
                    COMMAND_SEEK_TO_PREVIOUS,
                    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            ).build())
            .setPlaybackState(if (isPrepared) STATE_READY else STATE_IDLE)
            .setCurrentMediaItemIndex(if (playlistItems.isEmpty()) C.INDEX_UNSET else currentIndex)
            .setPlayWhenReady(
                BASS.BASS_ChannelIsActive(bassHandle) == BASS.BASS_ACTIVE_PLAYING,
                PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
            )
            .setContentPositionMs(currentPosMs)
            .setPlaylist(playlistData)

        return stateBuilder.build()
    }

    override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPos: Long): ListenableFuture<*> {
        playlistItems.clear()
        playlistItems.addAll(mediaItems)
        if (startIndex != C.INDEX_UNSET) {
            currentIndex = startIndex
        }

        if (playlistItems.isEmpty()) {
            currentMediaItem = null
            isPrepared = false
            if (bassHandle != 0) {
                BASS.BASS_StreamFree(bassHandle)
                bassHandle = 0
            }
            invalidateState()
            return Futures.immediateVoidFuture()
        }

        val item = playlistItems[currentIndex]
        currentMediaItem = item

        loadBassStream(item)

        return Futures.immediateVoidFuture()
    }

    private fun loadBassStream(item: MediaItem) {
        isPrepared = false
        invalidateState()

        if (bassHandle != 0) BASS.BASS_StreamFree(bassHandle)

        val path = item.mediaMetadata.displayTitle ?: ""
        val pfd = context.contentResolver.openFileDescriptor(item.localConfiguration?.uri!!, "r")
        if (pfd != null) {
            bassHandle = when {
                path.endsWith(".flac", ignoreCase = true) -> BASSFLAC.BASS_FLAC_StreamCreateFile(pfd, 0, 0, 0)
                path.endsWith(".ape", ignoreCase = true) -> BASSAPE.BASS_APE_StreamCreateFile(pfd, 0, 0, 0)
                else -> BASS.BASS_StreamCreateFile(pfd, 0, 0, 0)
            }
            if (bassHandle == 0) {
                val error = BASS.BASS_ErrorGetCode()
                Log.e("BassPlayer", "BASS File Error: $error.")
                handleLoadFailure()
            } else {
                isPrepared = true
                setupAutoAdvance(bassHandle)
            }
            pfd.close()
        }

        invalidateState()
    }

    private fun setupAutoAdvance(handle: Int) {
        BASS.BASS_ChannelSetSync(handle, BASS.BASS_SYNC_END, 0, { _, _, _, _ ->
            handler.post {
                if (currentIndex < playlistItems.size - 1) {
                    currentIndex++
                    currentMediaItem = playlistItems[currentIndex]
                    loadBassStream(playlistItems[currentIndex])
                    BASS.BASS_ChannelPlay(bassHandle, false)
                    invalidateState()
                } else {
                    BASS.BASS_ChannelStop(bassHandle)
                    isPrepared = false
                    invalidateState()
                }
            }
        }, 0)
    }

    private fun handleLoadFailure() {
        invalidateState()

        val nextIndex = currentMediaItemIndex + 1
        if (nextIndex < mediaItemCount) {
            seekToNext()
        } else {
            stop()
        }
    }

    override fun handlePrepare(): ListenableFuture<*> {
        if (bassHandle != 0) {
            isPrepared = true
            invalidateState()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady && bassHandle != 0) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()

            val result = audioManager.requestAudioFocus(focusRequest)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                BASS.BASS_ChannelPlay(bassHandle, false)
            }
        } else {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
            BASS.BASS_ChannelPause(bassHandle)
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> {
        if (mediaItemIndex == C.INDEX_UNSET || mediaItemIndex >= playlistItems.size) {
            return Futures.immediateVoidFuture()
        }

        var effectiveMediaItemIndex = mediaItemIndex

        if (mediaItemIndex == currentIndex && positionMs == 0L && (seekCommand == COMMAND_SEEK_TO_PREVIOUS || seekCommand == COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
            if (currentIndex > 0) {
                effectiveMediaItemIndex = currentIndex - 1
            }
        }

        if (effectiveMediaItemIndex != currentIndex) {
            currentIndex = effectiveMediaItemIndex
            currentMediaItem = playlistItems[currentIndex]
            loadBassStream(playlistItems[currentIndex])
            BASS.BASS_ChannelPlay(bassHandle, false)
        } else {
            val bytes = BASS.BASS_ChannelSeconds2Bytes(bassHandle, positionMs / 1000.0)
            BASS.BASS_ChannelSetPosition(bassHandle, bytes, BASS.BASS_POS_BYTE)
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        BASS.BASS_StreamFree(bassHandle)
        BASS.BASS_Free()
        return Futures.immediateVoidFuture()
    }
}
