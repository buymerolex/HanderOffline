package org.hander.novelreader.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.session.MediaButtonReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.hander.novelreader.HanderApp
import org.hander.novelreader.MainActivity
import org.hander.novelreader.R

/**
 * Spotify-style playback: a foreground service with a MediaSession, a media notification
 * (lock screen + notification shade) and headset / Bluetooth button support.
 * It only mirrors and controls TtsManager through PlaybackBridge.
 */
class PlaybackService : Service() {

    companion object {
        const val ACTION_START = "org.hander.novelreader.action.START"
        const val ACTION_PLAY = "org.hander.novelreader.action.PLAY"
        const val ACTION_PAUSE = "org.hander.novelreader.action.PAUSE"
        const val ACTION_NEXT = "org.hander.novelreader.action.NEXT"
        const val ACTION_PREVIOUS = "org.hander.novelreader.action.PREVIOUS"
        const val ACTION_STOP = "org.hander.novelreader.action.STOP"
        private const val CHANNEL_ID = "hander_playback"
        private const val NOTIF_ID = 4242
        private const val TAG = "PlaybackService"

        private const val SESSION_ACTIONS =
            PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
    }

    private lateinit var session: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private lateinit var nm: NotificationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var fgStarted = false
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false
    private var artPath: String? = null
    private var artBitmap: Bitmap? = null
    private var silentTrack: AudioTrack? = null

    private val tts get() = (application as HanderApp).container.tts

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                PlaybackBridge.controls?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGain = tts.state.value.status == TtsManager.Status.PLAYING
                PlaybackBridge.controls?.pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    PlaybackBridge.controls?.play()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        PlaybackBridge.serviceRunning = true
        nm = getSystemService(NotificationManager::class.java)
        audioManager = getSystemService(AudioManager::class.java)
        createChannel()

        session = MediaSessionCompat(this, "HanderPlayback").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { PlaybackBridge.controls?.play() }
                override fun onPause() { PlaybackBridge.controls?.pause() }
                override fun onSkipToNext() { PlaybackBridge.controls?.next() }
                override fun onSkipToPrevious() { PlaybackBridge.controls?.previous() }
                override fun onStop() { PlaybackBridge.controls?.stop() }

                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    Log.d(TAG, "media button: ${mediaButtonEvent?.extras}")
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
            })
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(SESSION_ACTIONS)
                    .setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f)
                    .build(),
            )
            isActive = true
        }
        observe()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        // Started with startForegroundService (by the app or by a headset button): must go foreground now.
        if (!fgStarted && (action == null || action == ACTION_START || action == Intent.ACTION_MEDIA_BUTTON)) {
            val now = PlaybackBridge.nowPlaying.value
            fgStarted = startForegroundSafely(
                buildNotification(
                    playing = tts.state.value.status == TtsManager.Status.PLAYING,
                    title = now?.title ?: "Hander",
                    subtitle = now?.subtitle ?: "",
                    art = loadArt(now?.coverPath),
                ),
            )
        }
        MediaButtonReceiver.handleIntent(session, intent)
        when (action) {
            ACTION_PLAY -> PlaybackBridge.controls?.play()
            ACTION_PAUSE -> PlaybackBridge.controls?.pause()
            ACTION_NEXT -> PlaybackBridge.controls?.next()
            ACTION_PREVIOUS -> PlaybackBridge.controls?.previous()
            ACTION_STOP -> PlaybackBridge.controls?.stop()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopSilence()
        PlaybackBridge.serviceRunning = false
        scope.cancel()
        abandonFocus()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- state

    private fun observe() {
        scope.launch {
            combine(
                tts.state.map { it.status }.distinctUntilChanged(),
                PlaybackBridge.nowPlaying,
            ) { status, now -> status to now }
                .collectLatest { (status, now) -> handle(status, now) }
        }
    }

    private suspend fun handle(status: TtsManager.Status, now: PlaybackBridge.NowPlaying?) {
        if (now == null) {
            shutdown()
            return
        }
        when (status) {
            TtsManager.Status.PLAYING -> {
                requestFocus()
                startSilence()
                publish(true, now)
            }
            TtsManager.Status.PAUSED -> {
                startSilence()
                publish(false, now)
            }
            TtsManager.Status.IDLE -> {
                // Between pages the reader is briefly idle; wait before removing the notification.
                delay(if (PlaybackBridge.advancing) 30_000L else 800L)
                shutdown()
            }
        }
    }

    private fun publish(playing: Boolean, now: PlaybackBridge.NowPlaying) {
        val art = loadArt(now.coverPath)

        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, now.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, now.subtitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Hander")
                .apply { if (art != null) putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art) }
                .build(),
        )
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(SESSION_ACTIONS)
                .setState(
                    if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                    if (playing) 1f else 0f,
                )
                .build(),
        )

        val notification = buildNotification(playing, now.title, now.subtitle, art)
        if (playing) {
            if (!fgStarted) fgStarted = startForegroundSafely(notification)
            nm.notify(NOTIF_ID, notification)
        } else {
            if (fgStarted) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                fgStarted = false
            }
            nm.notify(NOTIF_ID, notification)
        }
    }

    private fun shutdown() {
        stopSilence()
        PlaybackBridge.serviceRunning = false
        abandonFocus()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        nm.cancel(NOTIF_ID)
        fgStarted = false
        stopSelf()
    }

    private fun startForegroundSafely(n: Notification): Boolean = try {
        ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Could not go foreground", e)
        false
    }

    // ---------------------------------------------------------------- silent track

    /** Silent loop from our own process, so Android treats Hander as the app playing audio. */
    private fun startSilence() {
        if (silentTrack != null) return
        try {
            val rate = 8000
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(rate * 2)
                .build()
            track.write(ShortArray(rate), 0, rate)
            track.setLoopPoints(0, rate, -1)
            track.play()
            silentTrack = track
        } catch (e: Exception) {
            Log.w(TAG, "Could not start silent track", e)
        }
    }

    private fun stopSilence() {
        silentTrack?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        silentTrack = null
    }

    // ---------------------------------------------------------------- audio focus

    private fun requestFocus() {
        if (focusRequest != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        if (audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusRequest = request
        }
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    // ---------------------------------------------------------------- notification

    private fun servicePending(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, PlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(playing: Boolean, title: String, subtitle: String, art: Bitmap?): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hander)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(open)
            .setDeleteIntent(servicePending(ACTION_STOP))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(playing)
            .addAction(android.R.drawable.ic_media_previous, "Previous", servicePending(ACTION_PREVIOUS))
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play",
                servicePending(if (playing) ACTION_PAUSE else ACTION_PLAY),
            )
            .addAction(android.R.drawable.ic_media_next, "Next", servicePending(ACTION_NEXT))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
        if (art != null) builder.setLargeIcon(art)
        return builder.build()
    }

    private fun loadArt(path: String?): Bitmap? {
        if (path == null) return null
        if (path == artPath) return artBitmap
        artPath = path
        artBitmap = try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            null
        }
        return artBitmap
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Hander playback", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Playback controls while Hander reads aloud"
            nm.createNotificationChannel(channel)
        }
    }
}