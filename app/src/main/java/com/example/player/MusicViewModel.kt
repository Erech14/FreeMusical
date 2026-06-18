package com.example.player

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.MusicDatabase
import com.example.data.Track
import com.example.data.TrackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MusicViewModel(
    private val context: Context,
    private val repository: TrackRepository
) : ViewModel() {

    private val prefs: SharedPreferences = context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)

    // Folder selection state
    private val _selectedFolderUri = MutableStateFlow<String?>(null)
    val selectedFolderUri = _selectedFolderUri.asStateFlow()

    // Scan progress state
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _scanCount = MutableStateFlow(0)
    val scanCount = _scanCount.asStateFlow()

    // Permission state
    private val _isStoragePermissionGranted = MutableStateFlow(false)
    val isStoragePermissionGranted = _isStoragePermissionGranted.asStateFlow()

    // Tracks list from Room database
    val tracksState: StateFlow<List<Track>> = repository.allTracks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Bridge player engine states
    val currentTrack = MusicPlayerEngine.currentTrack
    val isPlaying = MusicPlayerEngine.isPlaying
    val currentPosition = MusicPlayerEngine.currentPosition
    val duration = MusicPlayerEngine.duration
    val isShuffleEnabled = MusicPlayerEngine.isShuffleEnabled
    val mediaError = MusicPlayerEngine.hasMediaError

    init {
        // Load selected folder from SharedPreferences on startup
        val savedUri = prefs.getString("selected_folder_uri", null)
        if (savedUri != null && hasPersistedPermission(Uri.parse(savedUri))) {
            _selectedFolderUri.value = savedUri
        }
        
        // Listen to tracks database and synch player playlist when db updates
        viewModelScope.launch {
            tracksState.collect { tracks ->
                MusicPlayerEngine.setPlaylist(tracks)
            }
        }
    }

    fun setPermissionGranted(isGranted: Boolean) {
        _isStoragePermissionGranted.value = isGranted
    }

    private fun hasPersistedPermission(uri: Uri): Boolean {
        val persistedPermissions = context.contentResolver.persistedUriPermissions
        return persistedPermissions.any { it.uri == uri && (it.isReadPermission || it.isWritePermission) }
    }

    fun selectFolder(uri: Uri) {
        try {
            // Take dynamic persistent permissions so SAF works across restarts
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)

            // Save in prefs
            val uriString = uri.toString()
            prefs.edit().putString("selected_folder_uri", uriString).apply()
            _selectedFolderUri.value = uriString

            // Trigger scanner
            scanDirectory(uri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun scanDirectory(uri: Uri) {
        viewModelScope.launch {
            _isScanning.value = true
            _scanCount.value = 0
            
            // Clean up existing library cache in Room before scan
            repository.clearLibrary()
            
            // Perform SAF content resolver crawl
            val discovered = repository.scanDirectoryForMusic(uri)
            
            // Cache found files in local SQLite
            repository.insertTracks(discovered)
            
            _scanCount.value = discovered.size
            _isScanning.value = false
        }
    }

    fun changeFolder() {
        // Clear prefs, Room database, and stop active music standard releases
        val savedUri = _selectedFolderUri.value
        if (savedUri != null) {
            try {
                val uri = Uri.parse(savedUri)
                val releaseFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(uri, releaseFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        MusicPlayerEngine.release()
        
        prefs.edit().remove("selected_folder_uri").apply()
        _selectedFolderUri.value = null
        
        viewModelScope.launch {
            repository.clearLibrary()
        }
    }

    fun playTrack(track: Track) {
        MusicPlayerEngine.playTrack(context, track)
    }

    fun togglePlayPause() {
        MusicPlayerEngine.togglePlayPause(context)
    }

    fun playNext() {
        MusicPlayerEngine.playNext(context)
    }

    fun playPrevious() {
        MusicPlayerEngine.playPrevious(context)
    }

    fun toggleShuffle() {
        MusicPlayerEngine.toggleShuffle()
    }

    fun seekTo(positionMs: Long) {
        MusicPlayerEngine.seekTo(positionMs)
    }

    override fun onCleared() {
        super.onCleared()
        // Note: we keeping player instances connected, but we can call general releases if necessary.
    }
}

// ViewModel factory for simple injection
class MusicViewModelFactory(
    private val context: Context,
    private val repository: TrackRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MusicViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MusicViewModel(context, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
