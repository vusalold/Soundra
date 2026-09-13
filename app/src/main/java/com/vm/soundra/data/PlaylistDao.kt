package com.vm.soundra.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :id")
    fun getPlaylistWithSongs(id: Long): Flow<List<PlaylistWithSongs>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity): Int

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(song: PlaylistSongEntity): Long

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeSongFromPlaylist(playlistId: Long, videoId: String): Int

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun isSongInPlaylist(playlistId: Long, videoId: String): Int

    @Query("UPDATE playlists SET coverUri = :coverUri, isCustomCover = :isCustom, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun updatePlaylistCover(playlistId: Long, coverUri: String?, isCustom: Boolean, updatedAt: Long): Int

    @Query("UPDATE playlists SET name = :name, description = :description, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePlaylistDetails(id: Long, name: String, description: String?, updatedAt: Long): Int

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun updateSongPosition(playlistId: Long, videoId: String, position: Int): Int

    @Query("UPDATE playlist_songs SET lastPlayed = :timestamp WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun updateLastPlayed(playlistId: Long, videoId: String, timestamp: Long): Int
}
