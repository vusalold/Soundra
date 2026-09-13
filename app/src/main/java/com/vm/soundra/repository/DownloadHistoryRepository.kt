package com.vm.soundra.repository

import com.vm.soundra.data.DownloadHistoryDao
import com.vm.soundra.data.DownloadHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DownloadHistoryRepository(private val downloadHistoryDao: DownloadHistoryDao) {
    val allHistory: Flow<List<DownloadHistoryEntity>> = downloadHistoryDao.getAllHistory()

    suspend fun insert(download: DownloadHistoryEntity) = withContext(Dispatchers.IO) {
        downloadHistoryDao.insertDownload(download)
    }

    suspend fun delete(download: DownloadHistoryEntity) = withContext(Dispatchers.IO) {
        downloadHistoryDao.deleteDownload(download)
    }

    suspend fun toggleFavorite(id: Int, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        downloadHistoryDao.updateFavorite(id, isFavorite)
    }
}
