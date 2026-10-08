package org.hander.novelreader.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import org.hander.novelreader.MainActivity

class TtsMediaService : Service() {

    companion object {
        const val CHANNEL_ID = "hander_playback"
        const val NOTIF_ID = 1001
        const val ACTION_TOGGLE = "org.hander.novelreader.TOGGLE"
        const val ACTION_NEXT = "org.hander.novelreader.NEXT"
        const val ACTION_PREV = "org.hander.novelreader.PREV"
        const val ACTION_STOP = "org.hander.novelreader.STOP_SERVICE"
    }

    private lateinit var mediaSession: MediaSessionCompat
    private val mainHandler = Handler(Looper.getMainLooper())
    private val engineListener = { mainHandler.post { updateNotification() }; Unit }

    override fun onCreate() {
        super.onCreate()
        createChannel()

        mediaSession = MediaSessionCompat(this, "Hander").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { if (!ReaderEngine.playing) ReaderEngine.toggle() }
                override fun onPause() { if (ReaderEngine.playing) ReaderEngine.toggle() }
                override fun onSkipToNext() { ReaderEngine.next() }
                override fun onSkipToPrevious() { ReaderEngine.previous() }
                override fun onStop() { ReaderEngine.stop() }
            })
            isActive = true
        }

        ReaderEngine.addListener(engineListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        when (intent?.action) {
            ACTION_TOGGLE -> ReaderEngine.toggle()
            ACTION_NEXT -> ReaderEngine.next()
            ACTION_PREV -> ReaderEngine.previous()
            ACTION_STOP -> {
                ReaderEngine.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        syncSession()
        startForeground(NOTIF_ID, buildNotification())
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ReaderEngine.removeListener(engineListener)
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun subtitle(): String =
        ReaderEngine.unitLabel.ifEmpty {
            "Page ${ReaderEngine.unitIndex + 1} of ${ReaderEngine.unitCount}"
        }

    /** Keeps the MediaSession in sync so Android routes headset buttons to us. */
    private fun syncSession() {
        val state = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(
                if (ReaderEngine.playing) PlaybackStateCompat.STATE_PLAYING
                else PlaybackStateCompat.STATE_PAUSED,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                1f
            )
            .build()
        mediaSession.setPlaybackState(state)

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, ReaderEngine.bookTitle.ifEmpty { "Hander" })
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle())
            .build()
        mediaSession.setMetadata(metadata)
    }

    private fun updateNotification() {
        if (ReaderEngine.bookId.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        syncSession()
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun action(action: String, label: String, icon: Int): NotificationCompat.Action {
            val intent = Intent(this, TtsMediaService::class.java).setAction(action)
            val pending = PendingIntent.getService(
                this, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Action(icon, label, pending)
        }

        val playPauseIcon = if (ReaderEngine.playing)
            android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(ReaderEngine.bookTitle.ifEmpty { "Hander" })
            .setContentText(subtitle())
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(contentIntent)
            .setOngoing(ReaderEngine.playing)
            .setOnlyAlertOnce(true)
            .addAction(action(ACTION_PREV, "Previous", android.R.drawable.ic_media_previous))
            .addAction(action(ACTION_TOGGLE, "Play/Pause", playPauseIcon))
            .addAction(action(ACTION_NEXT, "Next", android.R.drawable.ic_media_next))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Hander playback", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows playback controls while reading aloud" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}