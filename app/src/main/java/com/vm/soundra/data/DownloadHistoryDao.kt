package com.vm.soundra.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadHistoryDao {
    @Query("SELECT * FROM download_history ORDER BY downloadDate DESC")
    fun getAllHistory(): Flow<List<DownloadHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDownload(download: DownloadHistoryEntity)

    @Delete
    fun deleteDownload(download: DownloadHistoryEntity)

    @Query("UPDATE download_history SET isFavorite = :isFavorite WHERE id = :id")
    fun updateFavorite(id: Int, isFavorite: Boolean)
}
