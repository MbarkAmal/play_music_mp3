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
        const val ACTION_STOP = "action_stop"
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
                playMusic(musicResId)
            }
            ACTION_PAUSE -> {
                pauseMusic()
            }
            ACTION_STOP -> {
                stopMusic()
            }
        }
        return START_NOT_STICKY
    }

    private fun playMusic(resId: Int) {
        if (resId != -1) {
            // New song requested
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, resId)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        } else {
            // Resume existing
            mediaPlayer?.start()
        }
        
        updateNotification(isPlaying = true)
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
    }

    private fun pauseMusic() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
        updateNotification(isPlaying = false)
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
    }

    private fun stopMusic() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        mediaSession.isActive = false
        mediaSession.release()
        
        // Broadcast stopped state
        val statusIntent = Intent(ACTION_STATUS_CHANGE)
        statusIntent.putExtra("IS_PLAYING", false)
        sendBroadcast(statusIntent)

        stopForeground(true)
        stopSelf()
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

    private fun updateNotification(isPlaying: Boolean) {
        // Broadcast status to UI
        val statusIntent = Intent(ACTION_STATUS_CHANGE)
        statusIntent.putExtra("IS_PLAYING", isPlaying)
        sendBroadcast(statusIntent)

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
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this exists, or use a system icon
            .setContentTitle("Music Player")
            .setContentText(if (isPlaying) "Playing" else "Paused")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1) // Indexes of actions to show
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                if (isPlaying) pausePendingIntent else playPendingIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Stop", stopPendingIntent) // Using 'next' icon as stop generic if needed, or clear
            .setOngoing(isPlaying)

        // For older versions, we might need a distinct icon for Stop if ic_media_stop isn't readily available in android.R.drawable without newer API checks, 
        // but typically android.R.drawable.ic_media_pause/play exist. 
        // Let's use standard android drawables.

        startForeground(1, notificationBuilder.build())
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
