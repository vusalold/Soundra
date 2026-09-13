package com.vm.soundra.logic

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

object MediaSessionHolder {
    var mediaSession: MediaSession? = null
}

class PlaybackService : MediaSessionService() {
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return MediaSessionHolder.mediaSession
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        MediaSessionHolder.mediaSession?.let { addSession(it) }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        MediaSessionHolder.mediaSession?.let { removeSession(it) }
        super.onDestroy()
    }
}
