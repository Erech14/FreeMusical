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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

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

    // Tracks list from Room database, sorted descending by prefix number
    val tracksState: StateFlow<List<Track>> = repository.allTracks
        .map { list ->
            list.sortedByDescending { track ->
                track.fileName.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            }
        }
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

    // Settings states
    private val _appTheme = MutableStateFlow(prefs.getInt("app_theme", 2)) // Default to 2 (System theme)
    val appTheme = _appTheme.asStateFlow()

    private val _appStyle = MutableStateFlow(2) // Locked to Glassmorphism (2)
    val appStyle = _appStyle.asStateFlow()

    private val _appLanguage = MutableStateFlow(prefs.getString("app_language", "Cute Russian") ?: "Cute Russian")
    val appLanguage = _appLanguage.asStateFlow()

    private val _apiToken = MutableStateFlow(prefs.getString("api_token", "") ?: "")
    val apiToken = _apiToken.asStateFlow()

    fun setApiToken(token: String) {
        prefs.edit().putString("api_token", token).apply()
        _apiToken.value = token
    }

    // API logic
    private val _apiTracks = MutableStateFlow<List<com.example.api.TrackRemote>>(emptyList())
    val apiTracks = _apiTracks.asStateFlow()

    private val _isApiLoading = MutableStateFlow(false)
    val isApiLoading = _isApiLoading.asStateFlow()

    fun fetchApiTracks() {
        if (_apiToken.value.isEmpty()) return
        viewModelScope.launch {
            _isApiLoading.value = true
            try {
                val tracks = com.example.api.ApiClient.apiService.getTracks("Bearer ${_apiToken.value}")
                _apiTracks.value = tracks
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Failed to load tracks: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally {
                _isApiLoading.value = false
            }
        }
    }

    fun downloadApiTrack(track: com.example.api.TrackRemote) {
        val folderUriStr = _selectedFolderUri.value ?: return
        if (_apiToken.value.isEmpty()) return
        viewModelScope.launch {
            try {
                val response = com.example.api.ApiClient.apiService.downloadTrack("Bearer ${_apiToken.value}", track.id)
                if (response.isSuccessful) {
                    val body = response.body() ?: return@launch
                    val uri = Uri.parse(folderUriStr)
                    val rootDocId = android.provider.DocumentsContract.getTreeDocumentId(uri)
                    val dirUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, rootDocId)
                    
                    // Determine next track number
                    val existing = tracksState.value.map { it.fileName }
                    var maxNum = 0
                    val regex = Regex("^(\\d{3})_.*")
                    for (name in existing) {
                        val match = regex.find(name)
                        if (match != null) {
                            val num = match.groupValues[1].toIntOrNull() ?: 0
                            if (num > maxNum) maxNum = num
                        }
                    }
                    val nextNum = maxNum + 1
                    
                    // Format file name
                    val artistNames = track.artists.joinToString("_") { it.name }.replace("/", "-")
                    val title = track.title.replace("/", "-")
                    val fileName = String.format("%03d_%s_%s.mp3", nextNum, artistNames, title)
                    
                    val newFileUri = android.provider.DocumentsContract.createDocument(
                        context.contentResolver,
                        dirUri,
                        "audio/mpeg",
                        fileName
                    )
                    
                    if (newFileUri != null) {
                        context.contentResolver.openOutputStream(newFileUri)?.use { out ->
                            body.byteStream().use { input ->
                                input.copyTo(out)
                            }
                        }
                        // Refresh directory
                        scanDirectory(uri)
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Track downloaded: $fileName", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Download failed", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Download error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun uploadLocalTrack(track: Track, artists: String, title: String, forRussia: String, under18: String) {
        if (_apiToken.value.isEmpty()) return
        viewModelScope.launch {
            try {
                val uri = Uri.parse(track.uriString)
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val bytes = inputStream.readBytes()
                val audioMediaType = "audio/mpeg".toMediaTypeOrNull()
                val textMediaType = "text/plain".toMediaTypeOrNull()

                val requestBody = bytes.toRequestBody(audioMediaType)
                val part = okhttp3.MultipartBody.Part.createFormData("file", track.fileName, requestBody)
                
                val titleBody = title.toRequestBody(textMediaType)
                val artistsBody = artists.toRequestBody(textMediaType)
                val forRussiaBody = forRussia.toRequestBody(textMediaType)
                val under18Body = under18.toRequestBody(textMediaType)
                
                com.example.api.ApiClient.apiService.uploadTrack(
                    "Bearer ${_apiToken.value}",
                    part,
                    titleBody,
                    artistsBody,
                    forRussiaBody,
                    under18Body
                )
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Upload successful", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Upload failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun setAppTheme(theme: Int) {
        prefs.edit().putInt("app_theme", theme).apply()
        _appTheme.value = theme
    }

    fun setAppStyle(style: Int) {
        prefs.edit().putInt("app_style", style).apply()
        _appStyle.value = style
    }

    fun setAppLanguage(lang: String) {
        prefs.edit().putString("app_language", lang).apply()
        _appLanguage.value = lang
    }

    // Playback control from header
    fun playSequential() {
        if (tracksState.value.isEmpty()) return
        if (isShuffleEnabled.value) toggleShuffle()
        playTrack(tracksState.value.first())
    }

    fun playRandomly() {
        if (tracksState.value.isEmpty()) return
        if (!isShuffleEnabled.value) toggleShuffle()
        playTrack(tracksState.value.random())
    }

    data class SimplePlaylist(val id: String, val name: String, val uri: String)

    private val _playlists = MutableStateFlow<List<SimplePlaylist>>(emptyList())
    val playlists = _playlists.asStateFlow()

    private val _defaultPlaylistId = MutableStateFlow(prefs.getString("default_playlist_id", null))
    val defaultPlaylistId = _defaultPlaylistId.asStateFlow()

    fun loadPlaylists() {
        val jsonStr = prefs.getString("playlists_json", "[]") ?: "[]"
        try {
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<SimplePlaylist>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(SimplePlaylist(obj.getString("id"), obj.getString("name"), obj.getString("uri")))
            }
            _playlists.value = list
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun savePlaylists(list: List<SimplePlaylist>) {
        val arr = org.json.JSONArray()
        list.forEach { 
            val obj = org.json.JSONObject()
            obj.put("id", it.id)
            obj.put("name", it.name)
            obj.put("uri", it.uri)
            arr.put(obj)
        }
        prefs.edit().putString("playlists_json", arr.toString()).apply()
        _playlists.value = list
    }

    fun addPlaylist(name: String, uri: Uri) {
        val newList = _playlists.value.toMutableList()
        val id = java.util.UUID.randomUUID().toString()
        newList.add(SimplePlaylist(id, name, uri.toString()))
        savePlaylists(newList)
    }

    fun deletePlaylist(id: String) {
        val newList = _playlists.value.filter { it.id != id }
        savePlaylists(newList)
        if (_defaultPlaylistId.value == id) {
            setDefaultPlaylist(null)
        }
    }

    fun setDefaultPlaylist(id: String?) {
        prefs.edit().putString("default_playlist_id", id).apply()
        _defaultPlaylistId.value = id
    }

    init {
        loadPlaylists()
        
        val defaultId = _defaultPlaylistId.value
        val defaultPlaylist = _playlists.value.find { it.id == defaultId }
        
        var startupUriStr: String? = null
        if (defaultPlaylist != null) {
            startupUriStr = defaultPlaylist.uri
        } else {
            startupUriStr = prefs.getString("selected_folder_uri", null)
        }

        if (startupUriStr != null) {
            val uri = Uri.parse(startupUriStr)
            if (hasPersistedPermission(uri)) {
                _selectedFolderUri.value = startupUriStr
                scanDirectory(uri)
            }
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

    private fun createMainPlaylistIfNeeded(uri: Uri) {
        val hasMain = _playlists.value.any { it.name == "Main" || it.name == "Главный" || it.name == "Главный :3" }
        if (!hasMain) {
            val name = when(_appLanguage.value) {
                "Russian" -> "Главный"
                "Cute Russian" -> "Главный :3"
                else -> "Main"
            }
            // Add playlist
            val newList = _playlists.value.toMutableList()
            val id = java.util.UUID.randomUUID().toString()
            val newPlaylist = SimplePlaylist(id, name, uri.toString())
            newList.add(newPlaylist)
            savePlaylists(newList)

            // Set as default if we don't have one
            if (_defaultPlaylistId.value == null) {
                setDefaultPlaylist(id)
            }
        }
    }

    fun scanDirectory(uri: Uri) {
        viewModelScope.launch {
            _isScanning.value = true
            _scanCount.value = 0
            
            val existingTracks = tracksState.value.associateBy { it.uriString }
            // Perform SAF content resolver crawl BEFORE clearing the library
            val discovered = repository.scanDirectoryForMusic(uri, existingTracks)
            
            // Cache found files in local SQLite. We only clear and update if we successfully found files,
            // otherwise we retain existing files so they don't disappear on restarts.
            if (discovered.isNotEmpty()) {
                repository.replaceAll(discovered)
                _scanCount.value = discovered.size
            } else {
                _scanCount.value = tracksState.value.size
            }
            
            createMainPlaylistIfNeeded(uri)
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
    
    fun stopPlayback() {
        MusicPlayerEngine.release()
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
