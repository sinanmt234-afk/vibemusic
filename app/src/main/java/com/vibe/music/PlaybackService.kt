package com.vibe.music

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var s: MediaSession? = null
    override fun onCreate() { super.onCreate(); s = MediaSession.Builder(this, Pl.get(this)).build() }
    override fun onGetSession(i: MediaSession.ControllerInfo) = s
    override fun onDestroy() { s?.release(); super.onDestroy() }
}
