package com.vusal.soundra.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED,
    PREPARING,
    DOWNLOADING,
    CONVERTING,
    WRITING_METADATA,
    COMPLETED,
    FAILED,
    CANCELLED,
    PAUSED,
    RESUMING
}

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val videoId: String,
    val title: String,
    val artist: String = "Bilinmir",
    val thumbnail: String,
    val url: String,
    val bitrate: String = "192k",
    val state: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speed: String = "",
    val eta: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    val localFilePath: String? = null,
    val priority: Int = 0
)
