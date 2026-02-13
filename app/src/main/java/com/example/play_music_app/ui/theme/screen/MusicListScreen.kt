package com.example.play_music_app.ui.theme.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.play_music_app.R


data class MusicItem(val title: String, val resId: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicListScreen(
    isPlaying: Boolean,
    currentSongTitle: String?,
    onPlayClick: (Int, String) -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopClick: () -> Unit
) {

    // Dynamically fetch all raw resources
    val musicList = java.util.ArrayList<MusicItem>()
    val fields = R.raw::class.java.fields
    for (field in fields) {
        try {
            val name = field.name
            val resId = field.getInt(null)
            // Improve readability of titles: remove underscores, capitalize
            val title = name.replace("_", " ").split(" ")
                .joinToString(" ") { str ->
                    str.replaceFirstChar { char ->
                        if (char.isLowerCase()) char.titlecase(java.util.Locale.getDefault()) else char.toString()
                    }
                } 
            musicList.add(MusicItem(title, resId))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Myy Music Player") })
        },
        bottomBar = {
            // Show bar if we have a song title (active session), regardless of playing/paused
            if (currentSongTitle != null) {
                BottomPlayerBar(
                    isPlaying = isPlaying,
                    songTitle = currentSongTitle,
                    onPauseClick = onPauseClick,
                    onResumeClick = onResumeClick,
                    onStopClick = onStopClick
                )
            }
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            items(musicList) { music ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable {
                            onPlayClick(music.resId, music.title)
                        }
                ) {
                    Text(
                        text = music.title,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BottomPlayerBar(
    isPlaying: Boolean,
    songTitle: String,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = songTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPlaying) {
                    IconButton(onClick = onPauseClick) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_media_pause),
                            contentDescription = "Pause",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    IconButton(onClick = onResumeClick) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_media_play),
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                IconButton(onClick = onStopClick) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel), // Use 'close' for stop/clear
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
