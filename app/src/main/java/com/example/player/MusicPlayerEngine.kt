package com.example.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.example.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object MusicPlayerEngine {
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null
    private var mediaSession: android.media.session.MediaSession? = null

    // Player state flows
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _hasMediaError = MutableStateFlow<String?>(null)
    val hasMediaError: StateFlow<String?> = _hasMediaError.asStateFlow()

    // Playback playlist structures
    private var originalList: List<Track> = emptyList()
    private var playingList: List<Track> = emptyList()
    private var currentPlayingIndex: Int = -1

    fun setPlaylist(tracks: List<Track>) {
        originalList = tracks
        rebuildPlayingList(maintainPlayback = true)
    }

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
        rebuildPlayingList(maintainPlayback = true)
    }

    private fun rebuildPlayingList(maintainPlayback: Boolean) {
        val currTrack = _currentTrack.value
        if (_isShuffleEnabled.value) {
            // Keep current track at the beginning or shuffle all and find its index
            playingList = originalList.shuffled()
            if (maintainPlayback && currTrack != null) {
                val indexInShuffled = playingList.indexOfFirst { it.uriString == currTrack.uriString }
                if (indexInShuffled != -1) {
                    currentPlayingIndex = indexInShuffled
                }
            }
        } else {
            playingList = originalList
            if (maintainPlayback && currTrack != null) {
                val indexInOriginal = playingList.indexOfFirst { it.uriString == currTrack.uriString }
                if (indexInOriginal != -1) {
                    currentPlayingIndex = indexInOriginal
                }
            }
        }
    }

    fun playTrack(context: Context, track: Track) {
        initMediaSessionIfNeeded(context)
        // Ensure track is in playing catalog
        _hasMediaError.value = null
        val index = playingList.indexOfFirst { it.uriString == track.uriString }
        if (index != -1) {
            currentPlayingIndex = index
        } else {
            // Rebuild with track
            originalList = originalList + track
            rebuildPlayingList(maintainPlayback = false)
            currentPlayingIndex = playingList.indexOfFirst { it.uriString == track.uriString }
        }

        startPlayback(context, track)
    }

    private fun startPlayback(context: Context, track: Track) {
        stopProgressTracker()
        initMediaSessionIfNeeded(context)
        try {
            mediaPlayer?.release()
            mediaPlayer = null

            val player = MediaPlayer().apply {
                setDataSource(context, Uri.parse(track.uriString))
                prepare()
                start()
            }
            mediaPlayer = player

            _currentTrack.value = track
            _isPlaying.value = true
            _duration.value = player.duration.toLong()
            _currentPosition.value = 0L

            player.setOnCompletionListener {
                playNext(context)
            }

            player.setOnErrorListener { _, what, extra ->
                _hasMediaError.value = "Ошибка проигрывателя: $what ($extra)"
                _isPlaying.value = false
                updateMediaSessionState(android.media.session.PlaybackState.STATE_ERROR)
                false
            }

            startProgressTracker()
            updateMediaSessionMetadata(track)
            updateMediaSessionState(android.media.session.PlaybackState.STATE_PLAYING)
        } catch (e: Exception) {
            e.printStackTrace()
            _hasMediaError.value = "Не удалось воспроизвести файл: ${e.localizedMessage}"
            _isPlaying.value = false
        }
    }

    fun togglePlayPause(context: Context) {
        val player = mediaPlayer
        if (player != null) {
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
                stopProgressTracker()
                updateMediaSessionState(android.media.session.PlaybackState.STATE_PAUSED)
            } else {
                try {
                    player.start()
                    _isPlaying.value = true
                    startProgressTracker()
                    updateMediaSessionState(android.media.session.PlaybackState.STATE_PLAYING)
                } catch (e: Exception) {
                    e.printStackTrace()
                    _hasMediaError.value = "Не удалось возобновить воспроизведение"
                }
            }
        } else {
            // Nothing loaded, try playing first track
            if (playingList.isNotEmpty()) {
                playTrack(context, playingList.first())
            }
        }
    }

    fun playNext(context: Context) {
        if (playingList.isEmpty()) return
        
        currentPlayingIndex = if (currentPlayingIndex == -1) {
            0
        } else {
            (currentPlayingIndex + 1) % playingList.size
        }
        
        val nextTrack = playingList.getOrNull(currentPlayingIndex)
        if (nextTrack != null) {
            startPlayback(context, nextTrack)
        }
    }

    fun playPrevious(context: Context) {
        if (playingList.isEmpty()) return

        currentPlayingIndex = if (currentPlayingIndex <= 0) {
            playingList.size - 1
        } else {
            currentPlayingIndex - 1
        }

        val prevTrack = playingList.getOrNull(currentPlayingIndex)
        if (prevTrack != null) {
            startPlayback(context, prevTrack)
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { player ->
            try {
                player.seekTo(positionMs.toInt())
                _currentPosition.value = positionMs
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startProgressTracker() {
        progressJob = scope.launch {
            while (true) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        _currentPosition.value = player.currentPosition.toLong()
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun initMediaSessionIfNeeded(context: Context) {
        if (mediaSession == null) {
            mediaSession = android.media.session.MediaSession(context, "MusicPlayerEngine").apply {
                setCallback(object : android.media.session.MediaSession.Callback() {
                    override fun onPlay() { togglePlayPause(context) }
                    override fun onPause() { togglePlayPause(context) }
                    override fun onSkipToNext() { playNext(context) }
                    override fun onSkipToPrevious() { playPrevious(context) }
                    override fun onSeekTo(pos: Long) { seekTo(pos) }
                })
                isActive = true
            }
        }
    }

    private fun updateMediaSessionMetadata(track: Track) {
        val metadataBuilder = android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, track.title)
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, track.artist)
            .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, track.duration)
        
        mediaSession?.setMetadata(metadataBuilder.build())
    }

    private fun updateMediaSessionState(state: Int) {
        val playbackStateBuilder = android.media.session.PlaybackState.Builder()
            .setActions(
                android.media.session.PlaybackState.ACTION_PLAY or
                android.media.session.PlaybackState.ACTION_PAUSE or
                android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT or
                android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                android.media.session.PlaybackState.ACTION_PLAY_PAUSE
            )
            .setState(state, mediaPlayer?.currentPosition?.toLong() ?: 0L, 1.0f)
        
        mediaSession?.setPlaybackState(playbackStateBuilder.build())
    }

    fun release() {
        stopProgressTracker()
        mediaSession?.release()
        mediaSession = null
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        _currentTrack.value = null
    }
}
