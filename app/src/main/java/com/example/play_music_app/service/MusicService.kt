package com.example.play_music_app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.play_music_app.R

class MusicService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var mediaSession: MediaSessionCompat

    companion object {
        const val CHANNEL_ID = "music_channel_id"
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_RESUME = "action_resume"
        const val ACTION_STOP = "action_stop"
        const val ACTION_QUERY_STATUS = "action_query_status"
        const val ACTION_STATUS_CHANGE = "com.example.play_music_app.STATUS_CHANGE"
    }

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "MusicService")
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                // Handle play from media session (e.g. bluetooth headset)
                // For simplicity, we might just rely on startCommand for now,
                // or broadcast an intent to ourselves.
            }

            override fun onPause() {
                // Handle pause
            }

            override fun onStop() {
                // Handle stop
            }
        })
        mediaSession.isActive = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val musicResId = intent.getIntExtra("MUSIC_ID", -1)
                val localPath = intent.getStringExtra("LOCAL_PATH")
                val title = intent.getStringExtra("SONG_TITLE") ?: "Music Player"
                playMusic(musicResId, localPath, title)
            }
            ACTION_PAUSE -> {
                pauseMusic()
            }
            ACTION_RESUME -> {
                resumeMusic()
            }
            ACTION_STOP -> {
                stopMusic()
            }
            ACTION_QUERY_STATUS -> {
                broadcastStatus()
            }
        }
        return START_STICKY
    }

    private var currentTrackTitle: String? = null

    private fun playMusic(resId: Int, localPath: String?, title: String) {
        try {
            currentTrackTitle = title
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                if (localPath != null) {
                    setDataSource(localPath)
                } else if (resId != -1) {
                    val afd = resources.openRawResourceFd(resId)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                } else {
                    return
                }
                prepare()
                isLooping = false // Let OnCompletion handle it if we want custom behavior, or keep true for loop
                setOnCompletionListener {
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                    updateNotification(isPlaying = false, title = currentTrackTitle ?: "Music Player")
                }
                start()
            }
            updateNotification(isPlaying = true, title = title)
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resumeMusic() {
        if (mediaPlayer != null && !mediaPlayer!!.isPlaying) {
            mediaPlayer?.start()
            updateNotification(isPlaying = true, title = currentTrackTitle ?: "Music Player")
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        }
    }

    private fun pauseMusic() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
        updateNotification(isPlaying = false, title = currentTrackTitle ?: "Music Player")
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
    }

    private fun stopMusic() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentTrackTitle = null
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        mediaSession.isActive = false
        
        broadcastStatus()

        stopForeground(true)
        stopSelf()
    }

    private fun broadcastStatus() {
        val statusIntent = Intent(ACTION_STATUS_CHANGE).apply {
            putExtra("IS_PLAYING", mediaPlayer?.isPlaying == true)
            putExtra("SONG_TITLE", currentTrackTitle)
        }
        sendBroadcast(statusIntent)
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun updateNotification(isPlaying: Boolean, title: String) {
        // Broadcast status to UI
        broadcastStatus()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // Intents for actions
        val playIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PLAY }
        val pauseIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PAUSE }
        val stopIntent = Intent(this, MusicService::class.java).apply { action = ACTION_STOP }

        val playPendingIntent = PendingIntent.getService(
            this, 0, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pausePendingIntent = PendingIntent.getService(
            this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(if (isPlaying) "Playing" else "Paused")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                if (isPlaying) pausePendingIntent else playPendingIntent
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(isPlaying)

        if (isPlaying) {
            startForeground(1, notificationBuilder.build())
        } else {
            // Keep notification visible but allow dismissal if user wants
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(1, notificationBuilder.build())
            // Remove foreground status but NOT the notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                stopForeground(false)
            }
        }
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
