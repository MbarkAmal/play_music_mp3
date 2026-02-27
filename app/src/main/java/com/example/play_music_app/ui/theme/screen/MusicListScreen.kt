package com.example.play_music_app.ui.theme.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.play_music_app.data.MusicItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicListScreen(
    musicList: List<MusicItem>,
    isPlaying: Boolean,
    currentSongTitle: String?,
    onPlayClick: (MusicItem) -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopClick: () -> Unit,
    onDownloadClick: (MusicItem) -> Unit,
    onDeleteClick: (MusicItem) -> Unit,
    onRefreshClick: () -> Unit,
    onSearchClick: () -> Unit,
    isSyncing: Boolean = false,
    onMusicItemClick: (MusicItem) -> Unit
) {
    val libraryMusic = musicList.filter { !it.isDeviceFile }
    val deviceMusic = musicList.filter { it.isDeviceFile }
    val currentSong = musicList.find { it.title == currentSongTitle }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0A0E14),
        bottomBar = {
            if (currentSongTitle != null && currentSong != null) {
                MiniPlayer(
                    music = currentSong,
                    isPlaying = isPlaying,
                    onClick = { onMusicItemClick(currentSong) },
                    onPlayPauseClick = {
                        if (isPlaying) onPauseClick() else onResumeClick()
                    }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header Section
            item {
                PlaylistHeader(
                    songCount = musicList.size,
                    onPlayAll = { if (musicList.isNotEmpty()) onPlayClick(musicList[0]) },
                    onRefresh = onRefreshClick,
                    onAddSong = onSearchClick,
                    isSyncing = isSyncing
                )
            }

            // Library Section
            if (libraryMusic.isNotEmpty()) {
                item {
                    SectionHeader(title = "Library", icon = Icons.Default.MusicNote)
                }
                items(libraryMusic) { music ->
                    MusicListItem(
                        music = music,
                        isCurrent = music.title == currentSongTitle,
                        onClick = { onPlayClick(music) },
                        onDownloadClick = { onDownloadClick(music) },
                        onDeleteClick = { onDeleteClick(music) }
                    )
                }
            }

            // Device Section
            if (deviceMusic.isNotEmpty()) {
                item {
                    SectionHeader(title = "On this Device", icon = Icons.Default.Folder)
                }
                items(deviceMusic) { music ->
                    MusicListItem(
                        music = music,
                        isCurrent = music.title == currentSongTitle,
                        onClick = { onPlayClick(music) },
                        onDownloadClick = { onDownloadClick(music) },
                        onDeleteClick = { onDeleteClick(music) }
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PlaylistHeader(songCount: Int, onPlayAll: () -> Unit, onRefresh: () -> Unit, onAddSong: () -> Unit, isSyncing: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1B5E20).copy(alpha = 0.8f), Color(0xFF0A0E14))
                )
            )
            .padding(24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            IconButton(onClick = onAddSong) {
                Icon(Icons.Default.Add, contentDescription = "Search Music", tint = Color.White)
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
            }
        }

        Text(
            text = "My Playlist",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "$songCount songs",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun MusicListItem(
    music: MusicItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail placeholder
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(24.dp).align(Alignment.Center),
                tint = Color.Gray
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = music.title,
                color = if (isCurrent) Color(0xFF1DB954) else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = music.artist,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
        }

        Text(
            text = music.duration,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        if (music.remoteUrl != null) {
            if (music.isDownloaded) {
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White.copy(alpha = 0.5f))
                }
            } else {
                IconButton(onClick = onDownloadClick) {
                    Icon(painterResource(android.R.drawable.ic_menu_save), contentDescription = "Download", tint = Color(0xFF1DB954))
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(
    music: MusicItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayPauseClick: () -> Unit
) {
    Surface(
        color = Color(0xFF1E1E2C),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.DarkGray)
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Gray)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = music.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(text = music.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1)
            }

            IconButton(onClick = onPlayPauseClick) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White
                )
            }
        }
    }
}
