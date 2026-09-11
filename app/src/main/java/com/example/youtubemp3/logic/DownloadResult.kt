package com.vusal.soundra.logic

import com.vusal.soundra.data.DownloadHistoryEntity

sealed class DownloadResult {
    data class Success(val entity: DownloadHistoryEntity) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}
