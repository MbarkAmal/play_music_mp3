package com.example.play_music_app

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.play_music_app.data.MusicItem
import com.example.play_music_app.data.MusicStorageManager
import com.example.play_music_app.service.MusicService
import com.example.play_music_app.ui.theme.screen.MusicDetailScreen
import com.example.play_music_app.ui.theme.screen.MusicListScreen
import com.example.play_music_app.ui.theme.screen.MusicSearchScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val isPlaying = mutableStateOf(false)
    private val currentSongTitle = mutableStateOf<String?>(null)
    private val isDetailVisible = mutableStateOf(false)
    private val isSyncing = mutableStateOf(false)
    private val isSearchVisible = mutableStateOf(false)
    private val searchResults = mutableStateOf<List<MusicItem>>(emptyList())
    private val isSearching = mutableStateOf(false)
    private val hasSearched = mutableStateOf(false)
    private val musicListState = mutableStateOf<List<MusicItem>>(emptyList())
    
    private lateinit var storageManager: MusicStorageManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> if (isGranted) loadMusicList() }

    private val musicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MusicService.ACTION_STATUS_CHANGE) {
                isPlaying.value = intent.getBooleanExtra("IS_PLAYING", false)
                currentSongTitle.value = intent.getStringExtra("SONG_TITLE")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storageManager = MusicStorageManager(this)
        
        checkAndRequestPermissions()
        loadMusicList()
        
        setContent {
            val musicList = musicListState.value
            val currentSong = musicList.find { it.title == currentSongTitle.value }

            when {
                isSearchVisible.value -> {
                    MusicSearchScreen(
                        searchResults = searchResults.value,
                        isSearching = isSearching.value,
                        hasSearched = hasSearched.value,
                        onSearch = ::searchMusic,
                        onDownloadClick = ::handleDownloadFromSearch,
                        onBackClick = { resetSearchState(); isSearchVisible.value = false }
                    )
                }

                isDetailVisible.value && currentSong != null -> {
                    MusicDetailScreen(
                        music = currentSong,
                        isPlaying = isPlaying.value,
                        onBackClick = { isDetailVisible.value = false },
                        onPlayPauseClick = { if (isPlaying.value) pauseMusic() else resumeMusic() },
                        onPreviousClick = { playAdjacentSong(isNext = false) },
                        onNextClick = { playAdjacentSong(isNext = true) }
                    )
                }

                else -> {
                    MusicListScreen(
                        musicList = musicList,
                        isPlaying = isPlaying.value,
                        currentSongTitle = currentSongTitle.value,
                        onPlayClick = ::playMusic,
                        onPauseClick = ::pauseMusic,
                        onResumeClick = ::resumeMusic,
                        onStopClick = ::stopMusic,
                        onDownloadClick = ::downloadMusic,
                        onDeleteClick = ::deleteMusic,
                        onRefreshClick = ::loadMusicList,
                        onSearchClick = { isSearchVisible.value = true },
                        isSyncing = isSyncing.value,
                        onMusicItemClick = { isDetailVisible.value = true }
                    )
                }
            }
        }
    }

    private fun searchMusic(query: String) {
        lifecycleScope.launch {
            isSearching.value = true
            searchResults.value = storageManager.searchMusic(query)
            isSearching.value = false
            hasSearched.value = true
        }
    }
// first step download save file in internal storage
    private fun handleDownloadFromSearch(music: MusicItem) {
        lifecycleScope.launch {
            music.remoteUrl?.let { url ->
                val path = storageManager.downloadMusic(url, "${music.title}.mp3")
                if (path != null) {
                    storageManager.saveUserSong(music.title, url)
                    showToast("Downloaded: ${music.title}")
                    resetSearchState()
                    isSearchVisible.value = false
                    loadMusicList()
                }
            }
        }
    }

    private fun resetSearchState() {
        searchResults.value = emptyList()
        hasSearched.value = false
    }

    private fun playMusic(music: MusicItem) {
        if (music.remoteUrl != null && !storageManager.isFileDownloaded("${music.title}.mp3")) {
            showToast("Please download first")
            return
        }

        currentSongTitle.value = music.title
        isPlaying.value = true
        
        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY
            when {
                music.isDeviceFile && music.localPath != null -> putExtra("LOCAL_PATH", music.localPath)
                music.resId != -1 -> putExtra("MUSIC_ID", music.resId)
                music.localPath != null -> putExtra("LOCAL_PATH", music.localPath)
                else -> putExtra("LOCAL_PATH", storageManager.getMusicFile("${music.title}.mp3").absolutePath)
            }
            putExtra("SONG_TITLE", music.title)
        }
        startServiceCompat(intent, true)
    }

    private fun playAdjacentSong(isNext: Boolean) {
        val musicList = musicListState.value
        val currentIndex = musicList.indexOfFirst { it.title == currentSongTitle.value }
        if (currentIndex == -1) return

        val nextIndex = if (isNext) currentIndex + 1 else currentIndex - 1
        if (nextIndex in musicList.indices) {
            playMusic(musicList[nextIndex])
        }
    }

    private fun pauseMusic() {
        isPlaying.value = false
        sendServiceAction(MusicService.ACTION_PAUSE)
    }

    private fun resumeMusic() {
        isPlaying.value = true
        sendServiceAction(MusicService.ACTION_RESUME)
    }

    private fun stopMusic() {
        isPlaying.value = false
        currentSongTitle.value = null
        sendServiceAction(MusicService.ACTION_STOP)
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action }
        startServiceCompat(intent, action == MusicService.ACTION_RESUME)
    }

    private fun downloadMusic(music: MusicItem) {
        music.remoteUrl?.let { url ->
            lifecycleScope.launch {
                val path = storageManager.downloadMusic(url, "${music.title}.mp3")
                if (path != null) refreshDownloadStatus()
            }
        }
    }

    private fun deleteMusic(music: MusicItem) {
        storageManager.deleteMusic("${music.title}.mp3")
        music.remoteUrl?.let { storageManager.removeUserSong(it) }
        loadMusicList()
        showToast("Deleted: ${music.title}")
    }

    private fun loadMusicList() {
        val list = mutableListOf<MusicItem>()
        
        // 1. User added songs
        list.addAll(storageManager.getUserSongs())

        // 2. Fetch remote playlist
        fetchRemotePlaylist()

        // 3. Scan local app storage (Downloaded)
        val currentTitles = list.map { it.title }.toMutableSet()
        storageManager.getLocalMusicFiles().forEach { file ->
            val title = file.nameWithoutExtension
            if (title !in currentTitles) {
                list.add(MusicItem(title = title, artist = "Downloaded", duration = "Unknown", localPath = file.absolutePath, isDownloaded = true))
                currentTitles.add(title)
            }
        }

        // 4. Scan device storage (MediaStore)
        if (hasStoragePermission()) {
            storageManager.getDeviceMusicFiles().forEach { song ->
                if (song.title !in currentTitles) {
                    list.add(song)
                    currentTitles.add(song.title)
                }
            }
        }

        // 5. Update download flags
        musicListState.value = list.map { item ->
            if (item.remoteUrl != null) {
                item.copy(isDownloaded = storageManager.isFileDownloaded("${item.title}.mp3"))
            } else {
                item
            }
        }
    }

    private fun fetchRemotePlaylist() {
        lifecycleScope.launch {
            isSyncing.value = true
            val remoteSongs = storageManager.fetchRemotePlaylist(REMOTE_PLAYLIST_URL)
            isSyncing.value = false
            
            if (remoteSongs.isNotEmpty()) {
                val currentList = musicListState.value.toMutableList()
                val existingUrls = currentList.mapNotNull { it.remoteUrl }.toSet()
                val newSongs = remoteSongs.filter { it.remoteUrl !in existingUrls }
                if (newSongs.isNotEmpty()) {
                    musicListState.value = currentList + newSongs
                    showToast("${newSongs.size} new songs synced!")
                }
            }
        }
    }

    private fun refreshDownloadStatus() {
        musicListState.value = musicListState.value.map { item ->
            if (item.remoteUrl != null) {
                item.copy(isDownloaded = storageManager.isFileDownloaded("${item.title}.mp3"))
            } else {
                item
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permission = getStoragePermission()
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, getStoragePermission()) == PackageManager.PERMISSION_GRANTED
    }

    private fun getStoragePermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        if (isDetailVisible.value) isDetailVisible.value = false else super.onBackPressed()
    }

    private fun startServiceCompat(intent: Intent, useForeground: Boolean) {
        if (useForeground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        // Use regular startService for background status query
        val queryIntent = Intent(this, MusicService::class.java).apply { action = MusicService.ACTION_QUERY_STATUS }
        startService(queryIntent)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(musicReceiver)
    }

    companion object {
        private const val REMOTE_PLAYLIST_URL = "https://gist.githubusercontent.com/username/gist_id/raw/playlist.json"
    }
}
