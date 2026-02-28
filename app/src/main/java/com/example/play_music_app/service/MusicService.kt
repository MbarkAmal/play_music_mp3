package com.example.play_music_app.service

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

    private var mediaPlayer: MediaPlayer? = null //Android audio engine.
    private lateinit var mediaSession: MediaSessionCompat //allows system & notification controls
    private var currentTrackTitle: String? = null  //stores current song title.

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, TAG).apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val musicResId = intent.getIntExtra("MUSIC_ID", -1)
                val localPath = intent.getStringExtra("LOCAL_PATH")
                val title = intent.getStringExtra("SONG_TITLE") ?: DEFAULT_TITLE
                playMusic(musicResId, localPath, title)
            }
            ACTION_PAUSE -> pauseMusic()
            ACTION_RESUME -> resumeMusic()
            ACTION_STOP -> stopMusic()
            ACTION_QUERY_STATUS -> broadcastStatus()
        }
        return START_STICKY
    }

    private fun playMusic(resId: Int, localPath: String?, title: String) {
        try {
            currentTrackTitle = title
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                if (localPath != null) setDataSource(localPath) //plays downloaded file
                else if (resId != -1) { //plays app resource
                    resources.openRawResourceFd(resId).use { afd ->
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    }
                } else return@apply
                
                prepare() //decodes audio & gets ready.
                setOnCompletionListener {
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                    updateNotification(isPlaying = false, title = currentTrackTitle ?: DEFAULT_TITLE)
                }
                start() //sends sound to speakers.
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
            updateNotification(isPlaying = true, title = currentTrackTitle ?: DEFAULT_TITLE)
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        }
    }

    private fun pauseMusic() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
        updateNotification(isPlaying = false, title = currentTrackTitle ?: DEFAULT_TITLE)
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
    }

    private fun stopMusic() {
        mediaPlayer?.release() //releases memory + stops service
        mediaPlayer = null
        currentTrackTitle = null
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        mediaSession.isActive = false
        broadcastStatus()
        stopForeground(true)
        stopSelf()
    }

    private fun broadcastStatus() {  //send playback status to MainActivity.
        val statusIntent = Intent(ACTION_STATUS_CHANGE).apply {
            putExtra("IS_PLAYING", mediaPlayer?.isPlaying == true)
            putExtra("SONG_TITLE", currentTrackTitle)
        }
        sendBroadcast(statusIntent)
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP)
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState) //Tells Android: palying , paused , stopped
    }

    private fun updateNotification(isPlaying: Boolean, title: String) {
        broadcastStatus()
        createNotificationChannel()

        val playPendingIntent = createPendingIntent(ACTION_PLAY, 0)
        val pausePendingIntent = createPendingIntent(ACTION_PAUSE, 1)
        val stopPendingIntent = createPendingIntent(ACTION_STOP, 2)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(if (isPlaying) "Playing" else "Paused")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0))
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                if (isPlaying) pausePendingIntent else playPendingIntent
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(isPlaying)

        if (isPlaying) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notificationBuilder.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notificationBuilder.build())
            }
        } else {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notificationBuilder.build())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                stopForeground(false)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "music_channel_id"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_RESUME = "action_resume"
        const val ACTION_STOP = "action_stop"
        const val ACTION_QUERY_STATUS = "action_query_status"
        const val ACTION_STATUS_CHANGE = "com.example.play_music_app.STATUS_CHANGE"
        private const val TAG = "MusicService"
        private const val DEFAULT_TITLE = "Music Player"
    }
}
