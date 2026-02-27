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
import androidx.lifecycle.lifecycleScope
import com.example.play_music_app.data.MusicItem
import com.example.play_music_app.data.MusicStorageManager
import com.example.play_music_app.service.MusicService
import com.example.play_music_app.ui.theme.screen.MusicListScreen
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
    private lateinit var storageManager: MusicStorageManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            loadMusicList()
        }
    }

    // We use a list of music items to track download status
    private val musicListState = mutableStateOf<List<MusicItem>>(emptyList())

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
                    com.example.play_music_app.ui.theme.screen.MusicSearchScreen(
                        searchResults = searchResults.value,
                        isSearching = isSearching.value,
                        hasSearched = hasSearched.value,
                        onSearch = { query ->
                            lifecycleScope.launch {
                                isSearching.value = true
                                searchResults.value = storageManager.searchMusic(query)
                                isSearching.value = false
                                hasSearched.value = true
                            }
                        },
                        onDownloadClick = { music ->
                            lifecycleScope.launch {
                                if (music.remoteUrl != null) {
                                    val path = storageManager.downloadMusic(
                                        music.remoteUrl,
                                        music.title + ".mp3"
                                    )
                                    if (path != null) {
                                        // Add to persistent user songs so it survives restart
                                        storageManager.saveUserSong(music.title, music.remoteUrl)
                                        android.widget.Toast.makeText(
                                            this@MainActivity,
                                            "Downloaded: ${music.title}",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        isSearchVisible.value = false
                                        // Reset search state for next time
                                        searchResults.value = emptyList()
                                        hasSearched.value = false
                                        loadMusicList()
                                    }
                                }
                            }
                        },
                        onBackClick = {
                            isSearchVisible.value = false
                            searchResults.value = emptyList()
                            hasSearched.value = false
                        }
                    )
                }

                isDetailVisible.value && currentSong != null -> {
                com.example.play_music_app.ui.theme.screen.MusicDetailScreen(
                    music = currentSong,
                    isPlaying = isPlaying.value,
                    onBackClick = { isDetailVisible.value = false },
                    onPlayPauseClick = {
                        if (isPlaying.value) pauseMusic() else resumeMusic()
                    },
                    onPreviousClick = {
                        val currentIndex = musicList.indexOfFirst { it.title == currentSongTitle.value }
                        if (currentIndex > 0) {
                            playMusic(musicList[currentIndex - 1])
                        }
                    },
                    onNextClick = {
                        val currentIndex = musicList.indexOfFirst { it.title == currentSongTitle.value }
                        if (currentIndex != -1 && currentIndex < musicList.size - 1) {
                            playMusic(musicList[currentIndex + 1])
                        }
                    }
                )
                }

                else -> {
                    com.example.play_music_app.ui.theme.screen.MusicListScreen(
                        musicList = musicList,
                        isPlaying = isPlaying.value,
                        currentSongTitle = currentSongTitle.value,
                        onPlayClick = { music -> playMusic(music) },
                        onPauseClick = { pauseMusic() },
                        onResumeClick = { resumeMusic() },
                        onStopClick = { stopMusic() },
                        onDownloadClick = { music -> downloadMusic(music) },
                        onDeleteClick = { music -> deleteMusic(music) },
                        onRefreshClick = { loadMusicList() },
                        onSearchClick = { isSearchVisible.value = true },
                        isSyncing = isSyncing.value,
                        onMusicItemClick = { isDetailVisible.value = true }
                    )
                } // else
            } // when
        } // setContent
    } // onCreate

    private fun playMusic(music: MusicItem) {
        if (music.remoteUrl != null && !storageManager.isFileDownloaded(music.title + ".mp3")) {
            return
        }

        currentSongTitle.value = music.title
        isPlaying.value = true
        
        val intent = Intent(this, MusicService::class.java)
        intent.action = MusicService.ACTION_PLAY
        
        if (music.isDeviceFile && music.localPath != null) {
            // Priority 1: Device storage file (MediaStore path)
            intent.putExtra("LOCAL_PATH", music.localPath)
        } else if (music.resId != -1) {
            // Priority 2: Bundled raw resource
            intent.putExtra("MUSIC_ID", music.resId)
        } else if (music.localPath != null) {
            // Priority 3: App-managed download
            intent.putExtra("LOCAL_PATH", music.localPath)
        } else {
            // Fallback for app-managed downloads indexed by title only
            val path = storageManager.getMusicFile(music.title + ".mp3").absolutePath
            intent.putExtra("LOCAL_PATH", path)
        }
        
        intent.putExtra("SONG_TITLE", music.title)
        startServiceCompat(intent)
    }

    private fun pauseMusic() {
        isPlaying.value = false
        val intent = Intent(this, MusicService::class.java)
        intent.action = MusicService.ACTION_PAUSE
        startServiceCompat(intent)
    }

    private fun resumeMusic() {
        isPlaying.value = true
        val intent = Intent(this, MusicService::class.java)
        intent.action = MusicService.ACTION_RESUME
        startServiceCompat(intent)
    }

    private fun stopMusic() {
        isPlaying.value = false
        currentSongTitle.value = null
        val intent = Intent(this, MusicService::class.java)
        intent.action = MusicService.ACTION_STOP
        startServiceCompat(intent)
    }

    private fun downloadMusic(music: MusicItem) {
        if (music.remoteUrl != null) {
            lifecycleScope.launch {
                val path = storageManager.downloadMusic(music.remoteUrl, music.title + ".mp3")
                if (path != null) refreshMusicList()
            }
        }
    }

    private fun deleteMusic(music: MusicItem) {
        // 1. Delete physical file
        storageManager.deleteMusic(music.title + ".mp3")
        
        // 2. Remove from persistent list if it's a user-added song
        music.remoteUrl?.let { url ->
            storageManager.removeUserSong(url)
        }
        
        // 3. Complete reload of the list
        loadMusicList()
        
        android.widget.Toast.makeText(this, "Deleted: ${music.title}", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun loadMusicList() {
        val list = mutableListOf<MusicItem>()
        
        // 1. Add bundled resources
        list.add(MusicItem(title = "Be The One", artist = "Dua Lipa", duration = "3:22", resId = R.raw.dualipa_be_the_one_lyrics))
        list.add(MusicItem(title = "Fendi", artist = "Artist", duration = "3:30", resId = R.raw.fendi))
        
        // 2. Add known remote items
        list.add(MusicItem(title = "SoundHelix Song 1", artist = "SoundHelix", duration = "6:12", remoteUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"))
        list.add(MusicItem(title = "SoundHelix Song 2", artist = "SoundHelix", duration = "7:05", remoteUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"))
        list.add(MusicItem(title = "SoundHelix Song 3", artist = "SoundHelix", duration = "5:42", remoteUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"))
        list.add(MusicItem(title = "SoundHelix Song 4", artist = "SoundHelix", duration = "6:48", remoteUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"))
        list.add(MusicItem(title = "SoundHelix Song 5", artist = "SoundHelix", duration = "8:21", remoteUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3"))

        // 3. Add user-added links from SharedPreferences
        list.addAll(storageManager.getUserSongs())

        // 4. Fetch dynamic remote playlist (Optional URL)
        lifecycleScope.launch {
            isSyncing.value = true
            val remoteUrl = "https://gist.githubusercontent.com/username/gist_id/raw/playlist.json" 
            val remoteSongs = storageManager.fetchRemotePlaylist(remoteUrl)
            isSyncing.value = false
            
            if (remoteSongs.isNotEmpty()) {
                val currentList = musicListState.value.toMutableList()
                val existingUrls = currentList.mapNotNull { it.remoteUrl }.toSet()
                val newSongs = remoteSongs.filter { it.remoteUrl !in existingUrls }
                if (newSongs.isNotEmpty()) {
                    musicListState.value = currentList + newSongs
                    android.widget.Toast.makeText(this@MainActivity, "${newSongs.size} new songs synced from API!", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 5. Scan local app storage (Downloaded)
        val localFiles = storageManager.getLocalMusicFiles()
        val currentTitles = list.map { it.title }.toMutableSet()
        
        for (file in localFiles) {
            val title = file.nameWithoutExtension
            if (title !in currentTitles) {
                list.add(MusicItem(title = title, artist = "Downloaded", duration = "Unknown", localPath = file.absolutePath, isDownloaded = true))
                currentTitles.add(title)
            }
        }

        // 4. Scan device storage (MediaStore)
        if (hasStoragePermission()) {
            val deviceSongs = storageManager.getDeviceMusicFiles()
            for (song in deviceSongs) {
                if (song.title !in currentTitles) {
                    list.add(song)
                    currentTitles.add(song.title)
                }
            }
        }

        // 5. Mark downloaded
        val updatedList = list.map { item ->
            if (item.remoteUrl != null) {
                val downloaded = storageManager.isFileDownloaded(item.title + ".mp3")
                item.copy(isDownloaded = downloaded)
            } else {
                item
            }
        }
        
        musicListState.value = updatedList
    }

    private fun refreshMusicList() {
        musicListState.value = musicListState.value.map { item ->
            if (item.remoteUrl != null) {
                item.copy(isDownloaded = storageManager.isFileDownloaded(item.title + ".mp3"))
            } else {
                item
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun hasStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onBackPressed() {
        if (isDetailVisible.value) {
            isDetailVisible.value = false
        } else {
            super.onBackPressed()
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

        // Query current status from service to sync UI
        val queryIntent = Intent(this, MusicService::class.java)
        queryIntent.action = MusicService.ACTION_QUERY_STATUS
        startService(queryIntent)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(musicReceiver)
    }
}
