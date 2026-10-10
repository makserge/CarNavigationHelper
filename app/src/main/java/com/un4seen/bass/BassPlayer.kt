package com.un4seen.bass
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.smsoft.carnavigationhelper.data.TrackInfo
import com.smsoft.carnavigationhelper.data.database.repository.PlayerRepository
import com.smsoft.carnavigationhelper.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.roundToInt

@UnstableApi
class BassPlayer(
    val context: Context,
    looper: Looper,
    private val playerRepository: PlayerRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : SimpleBasePlayer(looper) {
    companion object {
        // Stored loudness of a song, put into MediaItem.mediaMetadata.extras by the UI (Double, only when known)
        const val EXTRA_LOUDNESS = "loudness"
        const val EXTRA_PEAK = "peak"

        // Songs after the loaded one that are measured ahead
        private const val MEASURE_AHEAD = 3
        // From the default to the measured gain
        private const val GAIN_SLIDE_MS = 1500
    }

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

    // Format of the loaded song for the player screen, null when nothing is loaded
    private val trackInfoInt = MutableStateFlow<TrackInfo?>(null)
    val trackInfo: StateFlow<TrackInfo?> = trackInfoInt.asStateFlow()

    // Runs on the player's looper, the DB writes switch to IO
    private val scope = CoroutineScope(SupervisorJob() + handler.asCoroutineDispatcher())

    private var normalizationEnabled = UserPreferencesRepository.DEFAULT_VOLUME_NORMALIZATION
    // The gain slider of the player settings, added to the normalisation gain of every song
    private var userGainDb = UserPreferencesRepository.DEFAULT_PLAYER_GAIN_DB
    // Normalisation gain of the loaded song, null when it is off or the song is not measured yet
    private var appliedGainDb: Double? = null

    // Measurements of this run by mediaId (null = failed, not tried again). Stored ones come in the extras
    private val measured = HashMap<String, Loudness?>()
    private val pendingMeasurements = ArrayDeque<MediaItem>()
    private var measuringId: String? = null
    // Decoding a whole file takes a while, so it runs on one background thread with a low priority
    private val analyzer = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "Loudness")
    }

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
        // Switching the normalisation applies to the playing song right away
        scope.launch {
            userPreferencesRepository.volumeNormalizationFlow.distinctUntilChanged().collect { enabled ->
                normalizationEnabled = enabled
                applyGain(slide = false)
                queueMeasurements()
            }
        }
        // The gain slider applies to the playing song at once
        scope.launch {
            userPreferencesRepository.playerGainFlow.distinctUntilChanged().collect { gainDb ->
                userGainDb = gainDb
                applyGain(slide = false)
            }
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
            freeStream()
            invalidateState()
            return Futures.immediateVoidFuture()
        }

        val item = playlistItems[currentIndex]
        currentMediaItem = item

        loadBassStream(item)

        return Futures.immediateVoidFuture()
    }

    // Songs taken off the blacklist are appended to the running playlist, so the music goes on untouched
    override fun handleAddMediaItems(index: Int, mediaItems: MutableList<MediaItem>): ListenableFuture<*> {
        // The same song twice would break the playlist
        val newItems = mediaItems.distinctBy { it.mediaId }.filter { item -> playlistItems.none { it.mediaId == item.mediaId } }
        val wasEmpty = playlistItems.isEmpty()
        playlistItems.addAll(index, newItems)

        if (wasEmpty && playlistItems.isNotEmpty()) {
            currentIndex = 0
            currentMediaItem = playlistItems[0]
            loadBassStream(playlistItems[0])
        } else if (index <= currentIndex) {
            currentIndex += newItems.size
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    // A dislike in the track list: the caller blacklists the song in the DB, here it only leaves the playlist
    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        removeItems(fromIndex, toIndex)
        return Futures.immediateVoidFuture()
    }

    // When the loaded song is removed, the one that followed it is loaded (the first one when it was the last)
    // and keeps playing if the removed one was playing. A paused one stays paused (Next starts it in handleSeek)
    private fun removeItems(fromIndex: Int, toIndex: Int) {
        val currentRemoved = currentIndex in fromIndex until toIndex
        val wasPlaying = BASS.BASS_ChannelIsActive(bassHandle) == BASS.BASS_ACTIVE_PLAYING
        playlistItems.subList(fromIndex, toIndex).clear()

        when {
            playlistItems.isEmpty() -> {
                // Nothing left to play
                resumeOnFocusGain = false
                audioManager.abandonAudioFocusRequest(audioFocusRequest)
                currentIndex = 0
                freeStream()
            }
            currentRemoved -> {
                currentIndex = if (fromIndex < playlistItems.size) fromIndex else 0
                currentMediaItem = playlistItems[currentIndex]
                loadBassStream(playlistItems[currentIndex])
                if (wasPlaying) {
                    BASS.BASS_ChannelPlay(bassHandle, false)
                }
            }
            currentIndex >= toIndex -> currentIndex -= toIndex - fromIndex
        }
        invalidateState()
    }

    private fun freeStream() {
        currentMediaItem = null
        isPrepared = false
        if (bassHandle != 0) {
            BASS.BASS_StreamFree(bassHandle)
            bassHandle = 0
        }
        appliedGainDb = null
        trackInfoInt.value = null
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
            queueMeasurements()
        }
        // Before the callers start the new stream
        applyGain(slide = false)

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

    // Volume normalisation as one static gain per song with BASS_ATTRIB_VOLDSP (may go above 1), apart from
    // BASS_ATTRIB_VOL that carries the user volume and the ducking. A song that is not measured yet plays
    // at the default gain and slides to its own one when the measurement is done
    private fun applyGain(slide: Boolean) {
        if (bassHandle == 0) {
            appliedGainDb = null
            trackInfoInt.value = null
            return
        }
        val loudness = currentMediaItem?.let { loudnessOf(it) }
        appliedGainDb = if (normalizationEnabled && loudness != null) LoudnessAnalyzer.gainDb(loudness.lufs, loudness.peak) else null
        val normalisationDb = if (normalizationEnabled) appliedGainDb ?: LoudnessAnalyzer.DEFAULT_GAIN_DB else 0.0
        // The user gain also applies with normalisation off. It is not limited by the peak, so it may clip
        val gainDb = normalisationDb + userGainDb
        val volume = 10.0.pow(gainDb / 20.0).toFloat()
        if (!slide || !BASS.BASS_ChannelSlideAttribute(bassHandle, BASS.BASS_ATTRIB_VOLDSP, volume, GAIN_SLIDE_MS)) {
            BASS.BASS_ChannelSetAttribute(bassHandle, BASS.BASS_ATTRIB_VOLDSP, volume)
        }
        updateTrackInfo()
    }

    // A measurement of this run, else the stored one that the UI put into the extras
    private fun loudnessOf(item: MediaItem): Loudness? {
        measured[item.mediaId]?.let { return it }
        val extras = item.mediaMetadata.extras ?: return null
        if (!extras.containsKey(EXTRA_LOUDNESS) || !extras.containsKey(EXTRA_PEAK)) return null
        return Loudness(extras.getDouble(EXTRA_LOUDNESS), extras.getDouble(EXTRA_PEAK))
    }

    // The loaded song (when unknown) first, then the next ones, so that they start at their own gain
    private fun queueMeasurements() {
        pendingMeasurements.clear()
        if (!normalizationEnabled || bassHandle == 0) return
        val last = minOf(currentIndex + MEASURE_AHEAD, playlistItems.size - 1)
        for (index in currentIndex..last) {
            val item = playlistItems[index]
            if (item.mediaId != measuringId && !measured.containsKey(item.mediaId) && loudnessOf(item) == null) {
                pendingMeasurements.add(item)
            }
        }
        measureNext()
    }

    private fun measureNext() {
        if (measuringId != null || analyzer.isShutdown) return
        val item = pendingMeasurements.removeFirstOrNull() ?: return
        measuringId = item.mediaId
        val uri = item.localConfiguration?.uri
        val fileName = item.mediaMetadata.displayTitle?.toString() ?: ""
        analyzer.execute {
            val loudness = uri?.let { LoudnessAnalyzer.measure(context, it, fileName) }
            handler.post { onMeasured(item, loudness) }
        }
    }

    private fun onMeasured(item: MediaItem, loudness: Loudness?) {
        measuringId = null
        measured[item.mediaId] = loudness
        if (loudness != null) {
            item.mediaId.toLongOrNull()?.let { id ->
                scope.launch(Dispatchers.IO) { playerRepository.setLoudness(id, loudness.lufs, loudness.peak) }
            }
            if (item.mediaId == currentMediaItem?.mediaId) {
                applyGain(slide = true)
            }
        }
        measureNext()
    }

    private fun updateTrackInfo() {
        val info = BASS.BASS_CHANNELINFO()
        if (bassHandle == 0 || !BASS.BASS_ChannelGetInfo(bassHandle, info)) {
            trackInfoInt.value = null
            return
        }
        val bitrate = BASS.FloatValue()
        BASS.BASS_ChannelGetAttribute(bassHandle, BASS.BASS_ATTRIB_BITRATE, bitrate)
        trackInfoInt.value = TrackInfo(
            codec = codecName(info.ctype, currentMediaItem?.mediaMetadata?.displayTitle?.toString() ?: ""),
            bitrateKbps = if (bitrate.value.isFinite()) bitrate.value.roundToInt() else 0,
        )
    }

    private fun codecName(ctype: Int, fileName: String): String = when (ctype) {
        BASS.BASS_CTYPE_STREAM_MP3 -> "MP3"
        BASS.BASS_CTYPE_STREAM_MP2 -> "MP2"
        BASS.BASS_CTYPE_STREAM_MP1 -> "MP1"
        BASS.BASS_CTYPE_STREAM_OGG -> "OGG"
        BASS.BASS_CTYPE_STREAM_AIFF -> "AIFF"
        BASSFLAC.BASS_CTYPE_STREAM_FLAC, BASSFLAC.BASS_CTYPE_STREAM_FLAC_OGG -> "FLAC"
        BASSAPE.BASS_CTYPE_STREAM_APE -> "APE"
        else -> when {
            (ctype and BASS.BASS_CTYPE_STREAM_WAV) != 0 -> "WAV"
            // Decoded by the OS codecs (BASS_CTYPE_STREAM_AM/MF/CA), the file type tells more, e.g. M4A
            else -> fileName.substringAfterLast('.', "").uppercase().ifEmpty { "?" }
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
        // A Next press (app, media notification, car buttons) blacklists the playing song and moves on to the
        // following one. The end of a song advances in setupAutoAdvance, never here. On the last song the index is unset.
        // Once the last song has ended (not prepared) nothing is playing, so Next does nothing, as before
        if (seekCommand == COMMAND_SEEK_TO_NEXT || seekCommand == COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) {
            if (playlistItems.isNotEmpty() && isPrepared) {
                playlistItems[currentIndex].mediaId.toLongOrNull()?.let { id ->
                    scope.launch(Dispatchers.IO) { playerRepository.setBlacklisted(id, true) }
                }
                removeItems(currentIndex, currentIndex + 1)
                // Next also starts the music when it was paused, as before (through the audio focus).
                // A start that waits for the focus happens when the focus comes back
                if (bassHandle != 0 && !resumeOnFocusGain && BASS.BASS_ChannelIsActive(bassHandle) != BASS.BASS_ACTIVE_PLAYING) {
                    handleSetPlayWhenReady(true)
                }
            }
            return Futures.immediateVoidFuture()
        }

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
        scope.cancel()
        analyzer.shutdownNow()
        BASS.BASS_StreamFree(bassHandle)
        bassHandle = 0
        trackInfoInt.value = null
        BASS.BASS_Free()
        return Futures.immediateVoidFuture()
    }
}
