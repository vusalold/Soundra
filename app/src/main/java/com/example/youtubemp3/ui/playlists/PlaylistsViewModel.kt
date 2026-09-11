package com.vusal.soundra.ui.playlists

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vusal.soundra.data.AppDatabase
import com.vusal.soundra.data.PlaylistEntity
import com.vusal.soundra.data.PlaylistWithSongs
import com.vusal.soundra.repository.PlaylistRepository
import com.vusal.soundra.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class PlaylistSort {
    RECENTLY_ADDED, OLDEST, NAME_AZ, NAME_ZA, CUSTOM
}

sealed class PlaylistDetailState {
    object Loading : PlaylistDetailState()
    data class Success(val data: PlaylistWithSongs) : PlaylistDetailState()
    object NotFound : PlaylistDetailState()
    data class Error(val message: String) : PlaylistDetailState()
}

class PlaylistsViewModel(private val repository: PlaylistRepository) : ViewModel() {

    private val _sortOrder = MutableStateFlow(PlaylistSort.RECENTLY_ADDED)
    val sortOrder: StateFlow<PlaylistSort> = _sortOrder.asStateFlow()

    val allPlaylists: StateFlow<List<PlaylistEntity>> = combine(
        repository.allPlaylists,
        _sortOrder
    ) { playlists, sort ->
        when (sort) {
            PlaylistSort.RECENTLY_ADDED -> playlists.sortedByDescending { it.createdAt }
            PlaylistSort.OLDEST -> playlists.sortedBy { it.createdAt }
            PlaylistSort.NAME_AZ -> playlists.sortedBy { it.name.lowercase() }
            PlaylistSort.NAME_ZA -> playlists.sortedByDescending { it.name.lowercase() }
            PlaylistSort.CUSTOM -> playlists.sortedByDescending { it.updatedAt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _detailState = MutableStateFlow<PlaylistDetailState>(PlaylistDetailState.Loading)
    val detailState: StateFlow<PlaylistDetailState> = _detailState.asStateFlow()

    private var detailJob: Job? = null

    fun setSortOrder(sort: PlaylistSort) {
        _sortOrder.value = sort
    }

    fun createPlaylist(name: String, description: String? = null) {
        viewModelScope.launch {
            try {
                repository.createPlaylist(name, description)
            } catch (e: Exception) {
                Log.e("PLAYLIST", "Create failed", e)
            }
        }
    }

    fun renamePlaylist(playlist: PlaylistEntity, newName: String) {
        viewModelScope.launch {
            try {
                repository.renamePlaylist(playlist, newName)
            } catch (e: Exception) {
                Log.e("PLAYLIST", "Rename failed", e)
            }
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            try {
                repository.deletePlaylist(playlist)
            } catch (e: Exception) {
                Log.e("PLAYLIST", "Delete failed", e)
            }
        }
    }

    fun loadPlaylistDetail(id: Long) {
        if (id <= 0) {
            _detailState.value = PlaylistDetailState.NotFound
            return
        }
        
        detailJob?.cancel()
        _detailState.value = PlaylistDetailState.Loading
        
        detailJob = viewModelScope.launch {
            repository.getPlaylistWithSongs(id)
                .catch { e -> 
                    Log.e("PLAYLIST_DETAIL", "Error loading playlist $id", e)
                    _detailState.value = PlaylistDetailState.Error(e.message ?: "Unknown error")
                }
                .collect { data ->
                    if (data != null) {
                        Log.d("PLAYLIST_DETAIL", "Success loading playlist ${data.playlist.name}")
                        _detailState.value = PlaylistDetailState.Success(data)
                    } else {
                        Log.d("PLAYLIST_DETAIL", "Playlist $id not found in DB")
                        _detailState.value = PlaylistDetailState.NotFound
                    }
                }
        }
    }

    fun addSongToPlaylist(playlistId: Long, videoId: String, title: String, artist: String, thumbnail: String, url: String, duration: String) {
        viewModelScope.launch {
            try {
                if (repository.isSongInPlaylist(playlistId, videoId)) {
                    _uiEvent.emit(PlaylistUiEvent.ShowSnackbar(R.string.already_in_playlist))
                } else {
                    repository.addSongToPlaylist(playlistId, videoId, title, artist, thumbnail, url, duration)
                    _uiEvent.emit(PlaylistUiEvent.ShowSnackbar(R.string.added_to_playlist))
                }
            } catch (e: Exception) {
                Log.e("PLAYLIST", "Add song failed", e)
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, videoId: String) {
        viewModelScope.launch {
            try {
                repository.removeSongFromPlaylist(playlistId, videoId)
            } catch (e: Exception) {
                Log.e("PLAYLIST", "Remove song failed", e)
            }
        }
    }

    fun updatePlaylistCover(playlistId: Long, uri: String?) {
        viewModelScope.launch {
            try {
                repository.updatePlaylistCover(playlistId, uri, true)
            } catch (e: Exception) {
                Log.e("PLAYLIST", "Update cover failed", e)
            }
        }
    }

    private val _uiEvent = MutableSharedFlow<PlaylistUiEvent>()
    val uiEvent: SharedFlow<PlaylistUiEvent> = _uiEvent.asSharedFlow()

    sealed class PlaylistUiEvent {
        data class ShowSnackbar(val messageResId: Int) : PlaylistUiEvent()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val database = AppDatabase.getDatabase(context)
            val repository = PlaylistRepository(database.playlistDao())
            @Suppress("UNCHECKED_CAST")
            return PlaylistsViewModel(repository) as T
        }
    }
}
