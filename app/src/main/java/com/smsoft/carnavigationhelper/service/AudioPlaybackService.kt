package com.smsoft.carnavigationhelper.service

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class AudioPlaybackService : MediaSessionService() {
    @Inject
    lateinit var mediaSession: MediaSession

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        // The session and its BassPlayer are app-wide singletons (PlayerModule), so they are only detached here.
        // Releasing them would hand out a released session to the next service instance
        if (isSessionAdded(mediaSession)) {
            removeSession(mediaSession)
        }
        super.onDestroy()
    }
}
