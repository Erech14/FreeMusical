package com.example.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.example.MainActivity

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine

class PlaybackService : Service() {
    private var job: kotlinx.coroutines.Job? = null
    private val CHANNEL_ID = "playback_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            kotlinx.coroutines.flow.combine(
                MusicPlayerEngine.currentTrack,
                MusicPlayerEngine.isPlaying
            ) { track, isPlaying ->
                Pair(track, isPlaying)
            }.collect { (track, isPlaying) ->
                if (track != null) {
                    val notification = buildNotification(track, isPlaying)
                    if (isPlaying) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                        } else {
                            startForeground(1, notification)
                        }
                    } else {
                        val notificationManager = getSystemService(NotificationManager::class.java)
                        notificationManager.notify(1, notification)
                        stopForeground(false)
                    }
                } else {
                    stopForeground(true)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(track: com.example.data.Track, isPlaying: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder.setContentTitle(track.title)
            .setContentText(track.artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)


        MusicPlayerEngine.mediaSession?.sessionToken?.let { token ->
            builder.style = Notification.MediaStyle()
                .setMediaSession(token)
        }

        return builder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
