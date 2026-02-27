package com.example.play_music_app.data

data class MusicItem(
    val title: String,
    val artist: String = "Unknown Artist",
    val duration: String = "0:00",
    val resId: Int = -1,
    val remoteUrl: String? = null,
    val localPath: String? = null,
    val isDownloaded: Boolean = false,
    val albumArtThumb: String? = null,
    val isDeviceFile: Boolean = false
)
