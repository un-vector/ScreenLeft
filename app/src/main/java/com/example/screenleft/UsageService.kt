package com.example.screenleft

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat

class UsageService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 60_000)
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            handler.removeCallbacks(tick)
            tick.run()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH, "Screen time left", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        val n = build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, n)
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(receiver, f)
        handler.postDelayed(tick, 60_000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        update()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun update() {
        getSystemService(NotificationManager::class.java).notify(ID, build())
    }

    private fun build(): Notification {
        val limit = Prefs.limitMin(this).toLong()
        val used = if (Usage.hasAccess(this)) Usage.todayMs(this) / 60_000 else 0L
        val left = limit - used
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(IconCompat.createWithBitmap(icon(Usage.fmt(left))))
            .setContentTitle(if (left >= 0) "${Usage.fmt(left)} left today" else "Over limit by ${Usage.fmt(-left)}")
            .setContentText("Used ${Usage.fmt(used)} of ${Usage.fmt(limit)} · tap to change limit")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    /** White "• h:mm" drawn on a transparent bitmap; Android uses only the alpha channel. */
    private fun icon(text: String): Bitmap {
        val h = 96
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 74f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val dotArea = 30
        val w = dotArea + paint.measureText(text).toInt() + 6
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawCircle(11f, h / 2f, 8f, paint)
        val y = h / 2f - (paint.descent() + paint.ascent()) / 2f
        c.drawText(text, dotArea.toFloat(), y, paint)
        return bmp
    }

    companion object {
        const val CH = "screen_left"
        const val ID = 1

        fun start(c: Context) {
            c.startForegroundService(Intent(c, UsageService::class.java))
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, UsageService::class.java))
        }
    }
}
