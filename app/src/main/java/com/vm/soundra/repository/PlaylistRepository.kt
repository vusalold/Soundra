package com.vm.soundra.repository

import com.vm.soundra.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class PlaylistRepository(private val playlistDao: PlaylistDao) {
    val allPlaylists: Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    fun getPlaylistWithSongs(id: Long): Flow<PlaylistWithSongs?> {
        return playlistDao.getPlaylistWithSongs(id).map { list ->
            list.firstOrNull()?.let { p ->
                // Sort songs by position by default
                p.copy(songs = p.songs.sortedBy { it.position })
            }
        }
    }

    suspend fun createPlaylist(name: String, description: String? = null): Long = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(PlaylistEntity(name = name, description = description))
    }

    suspend fun renamePlaylist(playlist: PlaylistEntity, newName: String) = withContext(Dispatchers.IO) {
        playlistDao.updatePlaylist(playlist.copy(name = newName, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePlaylist(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun addSongToPlaylist(playlistId: Long, videoId: String, title: String, artist: String, thumbnail: String, url: String, duration: String) = withContext(Dispatchers.IO) {
        // Find current max position
        val current = playlistDao.getPlaylistWithSongs(playlistId).first().firstOrNull()
        val nextPos = (current?.songs?.maxByOrNull { it.position }?.position ?: -1) + 1
        
        playlistDao.addSongToPlaylist(
            PlaylistSongEntity(
                playlistId = playlistId,
                videoId = videoId,
                title = title,
                artist = artist,
                thumbnail = thumbnail,
                url = url,
                duration = duration,
                position = nextPos
            )
        )
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, videoId: String) = withContext(Dispatchers.IO) {
        playlistDao.removeSongFromPlaylist(playlistId, videoId)
    }

    suspend fun isSongInPlaylist(playlistId: Long, videoId: String): Boolean = withContext(Dispatchers.IO) {
        playlistDao.isSongInPlaylist(playlistId, videoId) > 0
    }

    suspend fun updatePlaylistCover(playlistId: Long, coverUri: String?, isCustom: Boolean) = withContext(Dispatchers.IO) {
        playlistDao.updatePlaylistCover(playlistId, coverUri, isCustom, System.currentTimeMillis())
    }

    suspend fun updateSongPosition(playlistId: Long, videoId: String, position: Int) = withContext(Dispatchers.IO) {
        playlistDao.updateSongPosition(playlistId, videoId, position)
    }
}
