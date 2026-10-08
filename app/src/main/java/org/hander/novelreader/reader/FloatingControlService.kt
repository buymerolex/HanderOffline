package org.hander.novelreader.reader

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

/**
 * A small floating triangle (play) / two bars (pause) bubble that stays on
 * top of whatever app is in front, so reading can be paused without
 * switching back to Hander. Deliberately tiny and draggable out of the way.
 * Requires the "draw over other apps" permission, requested from Settings.
 */
class FloatingControlService : Service() {

    private var windowManager: WindowManager? = null
    private var bubble: View? = null
    private val engineListener = { refreshIcon() }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubble()
        ReaderEngine.addListener(engineListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ReaderEngine.bookId.isEmpty()) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ReaderEngine.removeListener(engineListener)
        bubble?.let { runCatching { windowManager?.removeView(it) } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun addBubble() {
        val sizePx = (40 * resources.displayMetrics.density).toInt()

        val view = ImageView(this).apply {
            setBackgroundColor(Color.parseColor("#CC11191C")) // translucent charcoal
            setColorFilter(Color.parseColor("#3F7277"))
            setPadding(sizePx / 4, sizePx / 4, sizePx / 4, sizePx / 4)
        }
        bubble = view
        refreshIconOn(view)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            sizePx, sizePx, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0
        var moved = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = params.x; startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    runCatching { windowManager?.updateViewLayout(v, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) ReaderEngine.toggle()
                    true
                }
                else -> false
            }
        }

        runCatching { windowManager?.addView(view, params) }
    }

    private fun refreshIcon() {
        bubble?.let { refreshIconOn(it as ImageView) }
    }

    private fun refreshIconOn(view: ImageView) {
        view.setImageResource(
            if (ReaderEngine.playing) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }
}
