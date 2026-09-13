package com.vm.soundra.logic

import com.vm.soundra.data.DownloadHistoryEntity

sealed class DownloadResult {
    data class Success(val entity: DownloadHistoryEntity) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}
