package com.quicktimer.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.VolumeProvider
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import java.io.File
import android.net.Uri
import android.content.Context
import android.content.pm.ServiceInfo

class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }

        getSharedPreferences("timer", Context.MODE_PRIVATE).edit()
            .putBoolean("running", false)
            .putBoolean("ringing", true)
            .putLong("endAt", 0L)
            .putLong("remaining", 0L)
            .apply()

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireWakeLock()
        startAlarmSound()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تنبيه مؤقت التصوير",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيه صوتي مستمر عند انتهاء المؤقت"
                setSound(null, null)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 201, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPending = PendingIntent.getService(
            this, 202,
            Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("انتهى مؤقت التصوير")
            .setContentText("الجرس يعمل الآن — اضغط لإيقافه")
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف الجرس", stopPending)

        return builder.build()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "QuickTimer:AlarmPlayback"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
        }
    }

    private fun startAlarmSound() {
        activateVolumeKeyStopper()
        if (player?.isPlaying == true) return

        val timerPrefs = getSharedPreferences("timer", Context.MODE_PRIVATE)
        val customPath = timerPrefs.getString("alarmSoundPath", null)
        if (!customPath.isNullOrBlank() && File(customPath).isFile && tryPlaySound(customPath, null)) {
            return
        }

        val savedUri = timerPrefs.getString("alarmSoundUri", null)?.let { Uri.parse(it) }
        val defaultAlarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val defaultNotification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val candidates = listOfNotNull(savedUri, defaultAlarm, defaultNotification).distinct()
        for (soundUri in candidates) {
            if (tryPlaySound(null, soundUri)) return
        }
    }

    private fun tryPlaySound(filePath: String?, soundUri: Uri?): Boolean {
        var media: MediaPlayer? = null
        return try {
            media = MediaPlayer()
            media.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            media.isLooping = true
            media.setVolume(1f, 1f)
            if (filePath != null) media.setDataSource(filePath)
            else if (soundUri != null) media.setDataSource(this, soundUri)
            else throw IllegalArgumentException("No alarm sound")
            media.prepare()
            media.start()
            player = media
            true
        } catch (_: Exception) {
            try {
                media?.release()
            } catch (_: Exception) {
            }
            false
        }
    }

    private fun activateVolumeKeyStopper() {
        if (Build.VERSION.SDK_INT < 21) return
        releaseMediaSession()
        try {
            val session = MediaSession(this, "MutawerAlarm")
            session.setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            session.setPlaybackToRemote(object : VolumeProvider(
                VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50
            ) {
                override fun onAdjustVolume(direction: Int) {
                    stopAlarm()
                }
            })
            session.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_STOP
                    )
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                    .build()
            )
            session.isActive = true
            mediaSession = session
        } catch (_: Exception) {
            releaseMediaSession()
        }
    }

    private fun releaseMediaSession() {
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (_: Exception) {
        }
        mediaSession = null
    }

    private fun stopAlarm() {
        releasePlayer()
        releaseMediaSession()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
        getSharedPreferences("timer", Context.MODE_PRIVATE).edit()
            .putBoolean("ringing", false)
            .putBoolean("running", false)
            .putLong("endAt", 0L)
            .putLong("remaining", 0L)
            .putLong("total", 0L)
            .apply()

        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun releasePlayer() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    override fun onDestroy() {
        releasePlayer()
        releaseMediaSession()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.quicktimer.app.STOP_ALARM"
        private const val CHANNEL_ID = "timer_alarm_playback"
        private const val NOTIFICATION_ID = 701
    }
}
