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

    private var userVolume = 0.8f

    // Playback starts on the next AUDIOFOCUS_GAIN: the focus was delayed (e.g. during a call)
    // or lost for a while. A pause by the user clears it, so that music is not resumed by itself
    private var resumeOnFocusGain = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                BASS.BASS_ChannelSlideAttribute(bassHandle, BASS.BASS_ATTRIB_VOL, userVolume * 0.2f, 500)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                BASS.BASS_ChannelSetAttribute(bassHandle, BASS.BASS_ATTRIB_VOL, userVolume)
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    BASS.BASS_ChannelPlay(bassHandle, false)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Pause but keep the focus, so playback resumes when the call or the assistant is over
                if (BASS.BASS_ChannelIsActive(bassHandle) == BASS.BASS_ACTIVE_PLAYING) {
                    resumeOnFocusGain = true
                    BASS.BASS_ChannelPause(bassHandle)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                handleSetPlayWhenReady(false)
            }
        }
        handler.post { invalidateState() }
    }

    // One request for both request and abandon: AudioManager matches them by the listener
    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAcceptsDelayedFocusGain(true)
        .setOnAudioFocusChangeListener(focusChangeListener)
        .build()

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
            // A start that waits for the audio focus still counts as playing (like ExoPlayer does), so that
            // MediaSessionService stays in the foreground until the focus comes back
            .setPlayWhenReady(
                BASS.BASS_ChannelIsActive(bassHandle) == BASS.BASS_ACTIVE_PLAYING || resumeOnFocusGain,
                PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
            )
            .setPlaybackSuppressionReason(
                if (resumeOnFocusGain) PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS else PLAYBACK_SUPPRESSION_REASON_NONE
            )
            .setContentPositionMs(currentPosMs)
            .setPlaylist(playlistData)

        return stateBuilder.build()
    }

    override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPos: Long): ListenableFuture<*> {
        playlistItems.clear()
        playlistItems.addAll(mediaItems)
        // C.INDEX_UNSET resets the position, the old index may not exist in the new (shorter) playlist
        currentIndex = if (startIndex in playlistItems.indices) startIndex else 0

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

        if (bassHandle != 0) {
            BASS.BASS_StreamFree(bassHandle)
            bassHandle = 0
        }

        bassHandle = createBassStream(item)
        // An unreadable song is skipped to the next one in this loop. Skipping with seekToNext() would recurse
        // once per song and block the main thread (or overflow its stack) when the whole folder is gone
        while (bassHandle == 0 && currentIndex < playlistItems.size - 1) {
            currentIndex++
            currentMediaItem = playlistItems[currentIndex]
            bassHandle = createBassStream(playlistItems[currentIndex])
        }
        if (bassHandle != 0) {
            isPrepared = true
            setupAutoAdvance(bassHandle)
        }

        invalidateState()
    }

    private fun createBassStream(item: MediaItem): Int {
        val path = item.mediaMetadata.displayTitle ?: ""
        // The file may be gone since the last scan (deleted, renamed, SD card removed) or the folder grant lost
        val pfd = try {
            context.contentResolver.openFileDescriptor(item.localConfiguration?.uri!!, "r")
        } catch (e: Exception) {
            Log.e("BassPlayer", "Open File Error: $e")
            null
        }
        if (pfd == null) return 0

        // BASS keeps its own copy of the descriptor, so it is closed once the stream is created
        val handle = pfd.use {
            when {
                path.endsWith(".flac", ignoreCase = true) -> BASSFLAC.BASS_FLAC_StreamCreateFile(it, 0, 0, 0)
                path.endsWith(".ape", ignoreCase = true) -> BASSAPE.BASS_APE_StreamCreateFile(it, 0, 0, 0)
                else -> BASS.BASS_StreamCreateFile(it, 0, 0, 0)
            }
        }
        if (handle == 0) {
            val error = BASS.BASS_ErrorGetCode()
            Log.e("BassPlayer", "BASS File Error: $error.")
        }
        return handle
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

    override fun handlePrepare(): ListenableFuture<*> {
        if (bassHandle != 0) {
            isPrepared = true
            invalidateState()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady && bassHandle != 0) {
            val result = audioManager.requestAudioFocus(audioFocusRequest)
            // A delayed focus (e.g. during a call) starts playback on the later AUDIOFOCUS_GAIN,
            // even though the stream has never played yet
            resumeOnFocusGain = result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                BASS.BASS_ChannelPlay(bassHandle, false)
            }
        } else {
            resumeOnFocusGain = false
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
