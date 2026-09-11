package com.vusal.soundra.logic

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vusal.soundra.data.AppDatabase
import com.vusal.soundra.data.DownloadStatus
import com.vusal.soundra.data.DownloadTaskRepository
import com.vusal.soundra.data.DownloadTaskEntity
import com.vusal.soundra.repository.DownloadHistoryRepository
import com.vusal.soundra.update.DownloadState
import com.vusal.soundra.R
import com.vusal.soundra.model.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.LruCache
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.core.net.toUri
import java.io.File
import java.util.*

enum class PlaybackRepeatMode {
    OFF, ALL, ONE
}

sealed class PreviewState {
    object Idle : PreviewState()
    object Buffering : PreviewState()
    object Ready : PreviewState()
    object Playing : PreviewState()
    object Paused : PreviewState()
    object Ended : PreviewState()
    data class Error(val messageResId: Int) : PreviewState()
}

data class PartialVideoMetadata(
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val videoUrl: String
)

sealed class MetadataState {
    object Idle : MetadataState()
    data class Loading(val partial: PartialVideoMetadata? = null) : MetadataState()
    data class Success(val metadata: VideoMetadata) : MetadataState()
    data class Error(val messageResId: Int) : MetadataState()
    object Timeout : MetadataState()
}

class Mp3DownloadViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getDatabase(appContext)
    private val historyRepository = DownloadHistoryRepository(database.downloadHistoryDao())
    private val taskRepository = DownloadTaskRepository(database.downloadTaskDao())

    private val prefs = appContext.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)

    val downloadState: StateFlow<DownloadState> = DownloadForegroundService.downloadState

    private val _snackbarMessageResId = MutableSharedFlow<Int>()
    val snackbarMessageResId: SharedFlow<Int> = _snackbarMessageResId.asSharedFlow()

    private val _selectedBitrate = MutableStateFlow(prefs.getString("last_bitrate", "") ?: "")
    val selectedBitrate: StateFlow<String> = _selectedBitrate.asStateFlow()

    private val _isLoadingMetadata = MutableStateFlow(false)
    val isLoadingMetadata: StateFlow<Boolean> = _isLoadingMetadata.asStateFlow()

    private val _metadataState = MutableStateFlow<MetadataState>(MetadataState.Idle)
    val metadataState: StateFlow<MetadataState> = _metadataState.asStateFlow()

    private val downloader = YoutubeDownloader(appContext)
    private val downloadManager = DownloadManager.getInstance(appContext)

    private val metadataCache = LruCache<String, VideoMetadata>(50)
    private var metadataJob: Job? = null

    private val _previewState = MutableStateFlow<PreviewState>(PreviewState.Idle)
    val previewState: StateFlow<PreviewState> = _previewState.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0L)
    val playbackDuration: StateFlow<Long> = _playbackDuration.asStateFlow()

    private val _bufferPosition = MutableStateFlow(0L)
    val bufferPosition: StateFlow<Long> = _bufferPosition.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
    
    private val _currentPlayingTrackId = MutableStateFlow<String?>(null)
    val currentTrackId: StateFlow<String?> = _currentPlayingTrackId.asStateFlow()
    
    private var currentPlayingUrl: String? = null
    private var mainPlayerWasPlayingBeforePreview: Boolean = false

    // Dedicated preview player so Download sheet previews don't interfere with main playlist
    private var previewExoPlayer: ExoPlayer? = null
    private var previewProgressJob: Job? = null
    
    private val _previewPlayingTrackId = MutableStateFlow<String?>(null)
    val previewTrackId: StateFlow<String?> = _previewPlayingTrackId.asStateFlow()
    
    private var previewPlayingUrl: String? = null

    // Preview-only player UI state (separate from main player's previewState)
    private val _previewOnlyState = MutableStateFlow<PreviewState>(PreviewState.Idle)
    val previewOnlyState: StateFlow<PreviewState> = _previewOnlyState.asStateFlow()

    private val _previewPlaybackPosition = MutableStateFlow(0L)
    val previewPlaybackPosition: StateFlow<Long> = _previewPlaybackPosition.asStateFlow()

    private val _previewPlaybackDuration = MutableStateFlow(0L)
    val previewPlaybackDuration: StateFlow<Long> = _previewPlaybackDuration.asStateFlow()

    private val _previewBufferPosition = MutableStateFlow(0L)
    val previewBufferPosition: StateFlow<Long> = _previewBufferPosition.asStateFlow()

    // Preview-only queue and index (separate from main playlist)
    private val _previewQueue = MutableStateFlow<List<SearchResult>>(emptyList())
    val previewQueue: StateFlow<List<SearchResult>> = _previewQueue.asStateFlow()

    private val _previewIndex = MutableStateFlow(-1)
    val previewIndex: StateFlow<Int> = _previewIndex.asStateFlow()

    // Queue management (Spotify Style)
    private val _originalQueue = MutableStateFlow<List<SearchResult>>(emptyList())
    private val _currentQueue = MutableStateFlow<List<SearchResult>>(emptyList())
    val currentQueue: StateFlow<List<SearchResult>> = _currentQueue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(PlaybackRepeatMode.OFF)
    val repeatMode: StateFlow<PlaybackRepeatMode> = _repeatMode.asStateFlow()

    // UI state for players
    private val _isFullScreenPlayerVisible = MutableStateFlow(false)
    val isFullScreenPlayerVisible: StateFlow<Boolean> = _isFullScreenPlayerVisible.asStateFlow()

    init {
        setupPlayer()
    }

    private fun setupPlayer() {
        if (exoPlayer != null) return
        
        exoPlayer = ExoPlayer.Builder(appContext).build().apply {
            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            _previewState.value = PreviewState.Buffering
                        }
                        Player.STATE_READY -> {
                            val mediaId = exoPlayer?.currentMediaItem?.mediaId
                            if (mediaId == _currentPlayingTrackId.value) {
                                _previewState.value = if (isPlaying) PreviewState.Playing else PreviewState.Ready
                                _playbackDuration.value = duration
                            }
                        }
                        Player.STATE_ENDED -> {
                            val mediaId = exoPlayer?.currentMediaItem?.mediaId
                            if (mediaId == _currentPlayingTrackId.value) {
                                _previewState.value = PreviewState.Ended
                                _playbackPosition.value = duration
                                stopProgressUpdates()
                                handlePlaybackEnded()
                            }
                        }
                        Player.STATE_IDLE -> {}
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    val mediaId = exoPlayer?.currentMediaItem?.mediaId
                    if (mediaId != _currentPlayingTrackId.value) return

                    if (playing) {
                        _previewState.value = PreviewState.Playing
                        startProgressUpdates()
                    } else {
                        if (_previewState.value != PreviewState.Ended && _previewState.value != PreviewState.Idle) {
                            _previewState.value = PreviewState.Paused
                        }
                        stopProgressUpdates()
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val mediaId = exoPlayer?.currentMediaItem?.mediaId
                    if (mediaId == _currentPlayingTrackId.value) {
                        _previewState.value = PreviewState.Error(R.string.err_preview_failed)
                        Log.e("PLAYER", "PreviewFailed: ${error.message}")
                    }
                }
            })
        }
    }

    private fun handlePlaybackEnded() {
        when (_repeatMode.value) {
            PlaybackRepeatMode.ONE -> {
                exoPlayer?.seekTo(0)
                exoPlayer?.play()
            }
            PlaybackRepeatMode.ALL -> {
                val nextIndex = if (_currentQueue.value.isNotEmpty()) {
                    (_currentIndex.value + 1) % _currentQueue.value.size
                } else -1
                if (nextIndex != -1) playAtIndex(nextIndex)
            }
            PlaybackRepeatMode.OFF -> {
                if (_currentIndex.value < _currentQueue.value.size - 1) {
                    playNext()
                } else {
                   stopMainPlayer()
                }
            }
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        val trackId = _currentPlayingTrackId.value
        
        progressJob = viewModelScope.launch {
            while (true) {
                val player = exoPlayer ?: break
                val currentMediaId = player.currentMediaItem?.mediaId
                
                // Atomic check: only update if track matches and session is valid
                if (currentMediaId == trackId && trackId != null) {
                    _playbackPosition.value = player.currentPosition
                    _bufferPosition.value = player.bufferedPosition
                    _playbackDuration.value = player.duration
                } else {
                    break
                }
                delay(250)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
    }

    fun togglePlayPause(url: String? = null, previewOnly: Boolean = false) {
        if (previewOnly) {
            val player = previewExoPlayer
            if (url != null && url != previewPlayingUrl) {
                loadPreview(url, previewOnly = true)
                return
            }
            if (player == null) return
            if (_previewOnlyState.value is PreviewState.Idle || _previewOnlyState.value is PreviewState.Error) {
                url?.let { loadPreview(it, previewOnly = true) }
            } else if (_previewOnlyState.value == PreviewState.Ended) {
                player.seekTo(0)
                player.play()
            } else {
                if (player.isPlaying) player.pause() else player.play()
            }
        } else {
            val player = exoPlayer ?: return
            if (url != null && url != currentPlayingUrl) {
                loadPreview(url, previewOnly = false)
                return
            }
            if (_previewState.value is PreviewState.Idle || _previewState.value is PreviewState.Error) {
                url?.let { loadPreview(it, previewOnly = false) }
            } else if (_previewState.value == PreviewState.Ended) {
                player.seekTo(0)
                player.play()
            } else {
                if (player.isPlaying) player.pause() else player.play()
            }
        }
    }

    private suspend fun internalPlayTrack(
        trackId: String,
        uri: android.net.Uri,
        isLocal: Boolean = true,
        originalUrl: String? = null,
        startPosition: Long = 0L
    ) {
        withContext(Dispatchers.Main) {
            progressJob?.cancel()
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()

            _currentPlayingTrackId.value = trackId
            currentPlayingUrl = originalUrl ?: uri.toString()
            _playbackPosition.value = startPosition
            _playbackDuration.value = 0L
            _bufferPosition.value = 0L
            _previewState.value = PreviewState.Buffering

            val mediaItem = MediaItem.Builder()
                .setMediaId(trackId)
                .setUri(uri)
                .build()

            exoPlayer?.let {
                it.setMediaItem(mediaItem)
                if (startPosition > 0) it.seekTo(startPosition)
                it.prepare()
                it.play()
                Log.d("PLAYER_SYNC", "Switching to track: $trackId (Pos: $startPosition)")
            }
            startProgressUpdates()
        }
    }

    private fun setupPreviewPlayer() {
        if (previewExoPlayer != null) return
        previewExoPlayer = ExoPlayer.Builder(appContext).build().apply {
            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> _previewOnlyState.value = PreviewState.Buffering
                        Player.STATE_READY -> {
                            if (previewExoPlayer?.currentMediaItem?.mediaId == _previewPlayingTrackId.value) {
                                _previewOnlyState.value = if (previewExoPlayer?.isPlaying == true) PreviewState.Playing else PreviewState.Ready
                                _previewPlaybackDuration.value = previewExoPlayer?.duration ?: 0L
                            }
                        }
                        Player.STATE_ENDED -> {
                            if (previewExoPlayer?.currentMediaItem?.mediaId == _previewPlayingTrackId.value) {
                                _previewOnlyState.value = PreviewState.Ended
                                _previewPlaybackPosition.value = previewExoPlayer?.duration ?: 0L
                                stopPreviewProgressUpdates()
                                if (mainPlayerWasPlayingBeforePreview) {
                                    exoPlayer?.play()
                                    mainPlayerWasPlayingBeforePreview = false
                                }
                            }
                        }
                        else -> {}
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    if (previewExoPlayer?.currentMediaItem?.mediaId != _previewPlayingTrackId.value) return
                    if (playing) {
                        _previewOnlyState.value = PreviewState.Playing
                        startPreviewProgressUpdates()
                    } else {
                        if (_previewOnlyState.value != PreviewState.Ended && _previewOnlyState.value != PreviewState.Idle) {
                            _previewOnlyState.value = PreviewState.Paused
                        }
                        stopPreviewProgressUpdates()
                    }
                }
            })
        }
    }

    private fun startPreviewProgressUpdates() {
        previewProgressJob?.cancel()
        val trackId = _previewPlayingTrackId.value
        previewProgressJob = viewModelScope.launch {
            while (true) {
                val player = previewExoPlayer ?: break
                if (player.currentMediaItem?.mediaId == trackId && trackId != null) {
                    _previewPlaybackPosition.value = player.currentPosition
                    _previewBufferPosition.value = player.bufferedPosition
                    _previewPlaybackDuration.value = player.duration
                } else break
                delay(250)
            }
        }
    }

    private fun stopPreviewProgressUpdates() {
        previewProgressJob?.cancel()
    }

    private suspend fun internalPlayPreviewTrack(trackId: String, uri: android.net.Uri, originalUrl: String? = null) {
        withContext(Dispatchers.Main) {
            if (exoPlayer?.isPlaying == true) mainPlayerWasPlayingBeforePreview = true
            setupPreviewPlayer()
            previewProgressJob?.cancel()
            previewExoPlayer?.stop()
            previewExoPlayer?.clearMediaItems()

            _previewPlayingTrackId.value = trackId
            previewPlayingUrl = originalUrl ?: uri.toString()
            _previewOnlyState.value = PreviewState.Buffering
            _previewPlaybackPosition.value = 0L
            _previewPlaybackDuration.value = 0L
            _previewBufferPosition.value = 0L

            val mediaItem = MediaItem.Builder().setMediaId(trackId).setUri(uri).build()
            previewExoPlayer?.let {
                it.setMediaItem(mediaItem)
                it.prepare()
                it.play()
            }
        }
    }

    fun playLocalTrack(song: SearchResult, startPosition: Long = 0L) {
        viewModelScope.launch {
            val localPath = getLocalFilePath(song.id)
            val resolved = LocalMusicResolver.resolve(appContext, localPath)
            if (resolved != null && resolved.exists) {
                internalPlayTrack(song.id, resolved.uri, isLocal = true, originalUrl = song.url, startPosition = startPosition)
            } else {
                _previewState.value = PreviewState.Error(R.string.err_file_unavailable)
                _snackbarMessageResId.emit(R.string.err_file_unavailable)
            }
        }
    }

    suspend fun isFileAvailable(videoId: String): Boolean = withContext(Dispatchers.IO) {
        val path = getLocalFilePath(videoId)
        LocalMusicResolver.resolve(appContext, path)?.exists == true
    }

    private suspend fun getLocalFilePath(videoId: String): String? = withContext(Dispatchers.IO) {
        val tasks = taskRepository.allTasks.first()
        tasks.find { it.videoId == videoId && it.state == DownloadStatus.COMPLETED }?.localFilePath
            ?: tasks.find { it.title.hashCode().toString() == videoId && it.state == DownloadStatus.COMPLETED }?.localFilePath
    }

    private fun loadPreview(url: String, previewOnly: Boolean = false) {
        viewModelScope.launch {
            try {
                val videoId = extractVideoId(url)
                val localPath = getLocalFilePath(videoId)
                val resolved = LocalMusicResolver.resolve(appContext, localPath)

                if (previewOnly) {
                    if (resolved != null && resolved.exists) {
                        internalPlayPreviewTrack(videoId, resolved.uri, originalUrl = url)
                    } else {
                        val streamUrl = withContext(Dispatchers.IO) { downloader.getAudioStreamUrl(url) }
                        if (streamUrl == null) {
                            _previewState.value = PreviewState.Idle
                            _snackbarMessageResId.emit(R.string.err_link_not_found)
                            return@launch
                        }
                        internalPlayPreviewTrack(videoId, streamUrl.toUri(), originalUrl = url)
                    }
                } else {
                    if (resolved != null && resolved.exists) {
                        internalPlayTrack(videoId, resolved.uri, isLocal = true, originalUrl = url)
                    } else {
                        val streamUrl = withContext(Dispatchers.IO) { downloader.getAudioStreamUrl(url) }
                        if (streamUrl == null) {
                            _previewState.value = PreviewState.Idle
                            _snackbarMessageResId.emit(R.string.err_link_not_found)
                            return@launch
                        }
                        internalPlayTrack(videoId, streamUrl.toUri(), isLocal = false, originalUrl = url)
                    }
                }
            } catch (e: Exception) {
                _previewState.value = PreviewState.Error(R.string.err_video_not_loaded)
            }
        }
    }

    fun seekTo(position: Long) = exoPlayer?.let { it.seekTo(position); _playbackPosition.value = position }
    fun seekPreviewTo(position: Long) = previewExoPlayer?.let { it.seekTo(position); _previewPlaybackPosition.value = position }

    fun setPreviewQueue(queue: List<SearchResult>, initialIndex: Int) {
        _previewQueue.value = queue
        _previewIndex.value = initialIndex
    }

    fun playPreviewNext() {
        val nextIndex = _previewIndex.value + 1
        if (nextIndex < _previewQueue.value.size) {
            _previewIndex.value = nextIndex
            loadPreview(_previewQueue.value[nextIndex].url, previewOnly = true)
        }
    }

    fun playPreviewPrevious() {
        val prevIndex = _previewIndex.value - 1
        if (prevIndex >= 0) {
            _previewIndex.value = prevIndex
            loadPreview(_previewQueue.value[prevIndex].url, previewOnly = true)
        }
    }

    fun playNext() {
        val nextIndex = _currentIndex.value + 1
        if (nextIndex < _currentQueue.value.size) playAtIndex(nextIndex)
        else if (_repeatMode.value == PlaybackRepeatMode.ALL && _currentQueue.value.isNotEmpty()) playAtIndex(0)
    }

    fun playPrevious() {
        val prevIndex = _currentIndex.value - 1
        if (prevIndex >= 0) playAtIndex(prevIndex)
        else if (_repeatMode.value == PlaybackRepeatMode.ALL && _currentQueue.value.isNotEmpty()) playAtIndex(_currentQueue.value.size - 1)
    }

    fun playAtIndex(index: Int, startPosition: Long = 0L) {
        if (index !in _currentQueue.value.indices) return
        _currentIndex.value = index
        playLocalTrack(_currentQueue.value[index], startPosition)
    }

    fun toggleShuffle() {
        val enabled = !_shuffleEnabled.value
        _shuffleEnabled.value = enabled
        val currentTrack = if (_currentIndex.value in _currentQueue.value.indices) _currentQueue.value[_currentIndex.value] else null
        if (enabled) {
            val list = _originalQueue.value.toMutableList()
            if (currentTrack != null) {
                list.remove(currentTrack)
                list.shuffle()
                list.add(0, currentTrack)
                _currentQueue.value = list
                _currentIndex.value = 0
            } else {
                list.shuffle()
                _currentQueue.value = list
            }
        } else {
            _currentQueue.value = _originalQueue.value
            if (currentTrack != null) _currentIndex.value = _originalQueue.value.indexOf(currentTrack)
        }
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            PlaybackRepeatMode.OFF -> PlaybackRepeatMode.ALL
            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.ONE
            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.OFF
        }
    }

    fun stopPreview() {
        previewProgressJob?.cancel()
        previewExoPlayer?.stop()
        previewExoPlayer?.clearMediaItems()
        _previewOnlyState.value = PreviewState.Idle
        _previewPlaybackPosition.value = 0L
        _previewPlaybackDuration.value = 0L
        _previewBufferPosition.value = 0L
        stopPreviewProgressUpdates()
        previewPlayingUrl = null
        _previewPlayingTrackId.value = null
        _previewQueue.value = emptyList()
        _previewIndex.value = -1
        if (mainPlayerWasPlayingBeforePreview) {
            exoPlayer?.play()
            mainPlayerWasPlayingBeforePreview = false
        }
    }

    fun stopMainPlayer() {
        progressJob?.cancel()
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        _currentPlayingTrackId.value = null
        currentPlayingUrl = null
        _previewState.value = PreviewState.Idle
        _playbackPosition.value = 0
        _playbackDuration.value = 0
        _bufferPosition.value = 0
        stopProgressUpdates()
    }

    fun setBitrate(bitrate: String) { _selectedBitrate.value = bitrate; prefs.edit().putString("last_bitrate", bitrate).apply() }
    fun resetState() = DownloadForegroundService.resetState()
    fun showFullScreenPlayer() { _isFullScreenPlayerVisible.value = true }
    fun hideFullScreenPlayer() { _isFullScreenPlayerVisible.value = false }

    private fun extractVideoId(url: String): String {
        return try {
            if (url.contains("watch?v=")) url.split("v=")[1].split("&")[0]
            else if (url.contains("youtu.be/")) url.split("youtu.be/")[1].split("?")[0]
            else if (url.contains("music.youtube.com/watch?v=")) url.split("v=")[1].split("&")[0]
            else url
        } catch (e: Exception) { url }
    }

    fun setLocalQueue(queue: List<SearchResult>, initialIndex: Int) {
        viewModelScope.launch {
            val validQueue = queue.filter { isFileAvailable(it.id) }
            if (validQueue.isEmpty()) {
                _snackbarMessageResId.emit(R.string.err_file_unavailable)
                return@launch
            }
            _originalQueue.value = validQueue
            val currentSong = if (initialIndex in queue.indices) queue[initialIndex] else validQueue[0]
            val actualIndex = if (validQueue.contains(currentSong)) validQueue.indexOf(currentSong) else 0
            if (_shuffleEnabled.value) {
                val shuffled = validQueue.toMutableList()
                val selected = validQueue[actualIndex]
                shuffled.remove(selected)
                shuffled.shuffle()
                shuffled.add(0, selected)
                _currentQueue.value = shuffled
                playAtIndex(0)
            } else {
                _currentQueue.value = validQueue
                playAtIndex(actualIndex)
            }
        }
    }

    fun setQueue(queue: List<SearchResult>, initialIndex: Int) {
        _originalQueue.value = queue
        if (_shuffleEnabled.value) {
            val currentSong = queue[initialIndex]
            val shuffled = queue.shuffled().toMutableList()
            _currentQueue.value = shuffled
            _currentIndex.value = shuffled.indexOf(currentSong)
        } else {
            _currentQueue.value = queue
            _currentIndex.value = initialIndex
        }
    }

    fun reorderQueue(from: Int, to: Int) {
        val list = _currentQueue.value.toMutableList()
        val currentTrack = if (_currentIndex.value != -1) list[_currentIndex.value] else null
        val moved = list.removeAt(from)
        list.add(to, moved)
        _currentQueue.value = list
        if (currentTrack != null) _currentIndex.value = list.indexOf(currentTrack)
    }

    fun removeFromQueue(index: Int) {
        val list = _currentQueue.value.toMutableList()
        if (index == _currentIndex.value) playNext()
        list.removeAt(index)
        _currentQueue.value = list
    }

    fun playTask(task: DownloadTaskEntity, allCompletedTasks: List<DownloadTaskEntity>) {
        val searchResults = allCompletedTasks.map { it.toSearchResult() }
        val index = allCompletedTasks.indexOf(task)
        setLocalQueue(searchResults, if (index != -1) index else 0)
    }

    fun prepareDownload(url: String, partial: PartialVideoMetadata? = null) {
        val videoId = extractVideoId(url)
        val cached = metadataCache.get(videoId)
        if (cached != null) { _metadataState.value = MetadataState.Success(cached); return }
        _metadataState.value = MetadataState.Loading(partial)
        if (metadataJob?.isActive == true) {
             val currentMetadataState = _metadataState.value
             if (currentMetadataState is MetadataState.Loading && currentMetadataState.partial?.videoUrl == url) return
        }
        metadataJob?.cancel()
        metadataJob = viewModelScope.launch {
            try {
                withTimeout(15000) {
                    val metadata = withContext(Dispatchers.IO) { downloader.getVideoMetadata(url) }
                    if (metadata != null) {
                        metadataCache.put(videoId, metadata)
                        val availableBitrates = metadata.audioStreams.map { "${it.bitrateKbps}k" }
                        if (_selectedBitrate.value.isBlank() || _selectedBitrate.value !in availableBitrates) {
                            val best = metadata.audioStreams.maxByOrNull { it.bitrateKbps }?.bitrateKbps
                            if (best != null) _selectedBitrate.value = "${best}k"
                        }
                        _metadataState.value = MetadataState.Success(metadata)
                    } else _metadataState.value = MetadataState.Error(R.string.err_metadata_not_found)
                }
            } catch (e: Exception) {
                _metadataState.value = if (e is kotlinx.coroutines.TimeoutCancellationException) MetadataState.Timeout else MetadataState.Error(R.string.err_analysis_failed)
            }
        }
    }

    fun cancelMetadataAnalysis() { if (metadataJob?.isActive == true) metadataJob?.cancel(); _metadataState.value = MetadataState.Idle }
    suspend fun fetchMetadata(url: String): VideoMetadata? {
        _isLoadingMetadata.value = true
        return try { withContext(Dispatchers.IO) { downloader.getVideoMetadata(url) } } finally { _isLoadingMetadata.value = false }
    }

    fun estimateSize(durationSeconds: Long, bitrateKbps: Int, sizeBytes: Long = -1L): String {
        if (sizeBytes > 0) {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1) String.format(java.util.Locale.US, "%.1f MB", mb) else String.format(java.util.Locale.US, "%.0f KB", sizeBytes / 1024.0)
        }
        val sizeMb = ((bitrateKbps * durationSeconds) / 8.0) / 1024.0
        return if (sizeMb >= 1) String.format(java.util.Locale.US, "%.1f MB", sizeMb) else String.format(java.util.Locale.US, "%.0f KB", sizeMb * 1024.0)
    }

    fun downloadMp3(url: String, title: String, artist: String? = null, thumbnail: String = "", bitrate: String = "192k") {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { historyRepository.allHistory.first() }
            if (history.any { it.title == title }) { _snackbarMessageResId.emit(R.string.err_already_downloaded); return@launch }
            val videoId = extractVideoId(url)
            if (downloadManager.allTasks.value.any { it.videoId == videoId && it.state !in listOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.CANCELLED) }) {
                _snackbarMessageResId.emit(R.string.err_already_in_queue); return@launch
            }
            withContext(Dispatchers.IO) { downloadManager.enqueue(videoId, title, artist ?: "Unknown", thumbnail, url, bitrate) }
        }
    }

    override fun onCleared() { exoPlayer?.release(); previewExoPlayer?.release(); exoPlayer = null; previewExoPlayer = null }

    private fun DownloadTaskEntity.toSearchResult() = SearchResult(videoId, title, artist, "", thumbnail, url)

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(Mp3DownloadViewModel::class.java)) return Mp3DownloadViewModel(context) as T
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
