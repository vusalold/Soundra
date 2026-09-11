package com.vusal.soundra.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val localFilePath: String,
    val fileSize: String,
    val duration: String,
    val bitrate: String,
    val downloadDate: Long,
    val isFavorite: Boolean = false
)
