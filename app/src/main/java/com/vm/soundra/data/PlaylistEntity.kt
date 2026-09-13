package com.vm.soundra.data

import androidx.room.*

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val coverUri: String? = null,
    val isCustomCover: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"])]
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnail: String,
    val url: String,
    val duration: String,
    val position: Int = 0,
    val lastPlayed: Long? = null,
    val addedAt: Long = System.currentTimeMillis()
)

data class PlaylistWithSongs(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlistId"
    )
    val songs: List<PlaylistSongEntity>
)
