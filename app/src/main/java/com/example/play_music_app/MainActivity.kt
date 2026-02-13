package com.example.play_music_app

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.play_music_app.service.MusicService
import com.example.play_music_app.ui.theme.screen.MusicListScreen

class MainActivity : ComponentActivity() {

    private val isPlaying = mutableStateOf(false)
    private val currentSongTitle = mutableStateOf<String?>(null)

    private val musicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MusicService.ACTION_STATUS_CHANGE) {
                isPlaying.value = intent.getBooleanExtra("IS_PLAYING", false)
                // Update title if broadcast contains it (or null if stopped)
                if (intent.hasExtra("SONG_TITLE")) {
                     currentSongTitle.value = intent.getStringExtra("SONG_TITLE")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicListScreen(
                isPlaying = isPlaying.value,
                currentSongTitle = currentSongTitle.value,
                onPlayClick = { musicResId, title ->
                    // Optimistic update
                    currentSongTitle.value = title
                    isPlaying.value = true
                    
                    val intent = Intent(this, MusicService::class.java)
                    intent.action = MusicService.ACTION_PLAY
                    intent.putExtra("MUSIC_ID", musicResId)
                    intent.putExtra("SONG_TITLE", title)
                    startServiceCompat(intent)
                },
                onPauseClick = {
                    // Optimistic update
                    isPlaying.value = false

                    val intent = Intent(this, MusicService::class.java)
                    intent.action = MusicService.ACTION_PAUSE
                    startServiceCompat(intent)
                },
                onResumeClick = {
                    // Optimistic update
                    isPlaying.value = true

                    val intent = Intent(this, MusicService::class.java)
                    intent.action = MusicService.ACTION_PLAY
                    startServiceCompat(intent)
                },
                onStopClick = {
                    // Optimistic update
                    isPlaying.value = false
                    currentSongTitle.value = null

                    val intent = Intent(this, MusicService::class.java)
                    intent.action = MusicService.ACTION_STOP
                    startServiceCompat(intent)
                }
            )
        }
    }

    private fun startServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(MusicService.ACTION_STATUS_CHANGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(musicReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(musicReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(musicReceiver)
    }
}
