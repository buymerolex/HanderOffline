package org.hander.novelreader.audio

import kotlinx.coroutines.flow.MutableStateFlow

/** Link between the reader screen and the notification service. */
object PlaybackBridge {
    data class NowPlaying(val title: String, val subtitle: String, val coverPath: String?)

    interface Controls {
        fun play()
        fun pause()
        fun next()
        fun previous()
        fun stop()
    }

    val nowPlaying = MutableStateFlow<NowPlaying?>(null)

    @Volatile var controls: Controls? = null

    /** True while the reader is loading the next page during auto-read. */
    @Volatile var advancing = false

    @Volatile var serviceRunning = false
}