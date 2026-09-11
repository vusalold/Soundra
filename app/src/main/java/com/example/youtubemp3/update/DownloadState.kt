package com.vusal.soundra.update

import java.io.File

sealed class DownloadState {
    object Idle : DownloadState()
    object Preparing : DownloadState()
    data class Downloading(
        val progress: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speed: String = "",
        val eta: String = ""
    ) : DownloadState()
    data class Completed(val file: File, val uri: String? = null) : DownloadState()
    data class Failed(val message: String) : DownloadState()
}
