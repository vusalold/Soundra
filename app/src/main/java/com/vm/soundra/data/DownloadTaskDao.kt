package com.vm.soundra.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DownloadTaskEntity): Long

    @Update
    suspend fun updateTask(task: DownloadTaskEntity): Int

    @Query("UPDATE download_tasks SET state = :state, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Int, state: DownloadStatus, updatedAt: Long): Int

    @Query("UPDATE download_tasks SET progress = :progress, downloadedBytes = :downloaded, totalBytes = :total, speed = :speed, eta = :eta, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: Int, progress: Int, downloaded: Long, total: Long, speed: String, eta: String, updatedAt: Long): Int

    @Delete
    suspend fun deleteTask(task: DownloadTaskEntity): Int

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int): Int

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAllTasksFlow(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE state IN ('DOWNLOADING', 'PREPARING', 'CONVERTING', 'WRITING_METADATA', 'RESUMING')")
    fun getActiveTasksFlow(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT COUNT(*) FROM download_tasks WHERE state IN ('DOWNLOADING', 'PREPARING', 'CONVERTING', 'WRITING_METADATA', 'RESUMING')")
    suspend fun getActiveTaskCount(): Int

    @Query("SELECT * FROM download_tasks WHERE state = 'QUEUED' ORDER BY priority DESC, createdAt ASC")
    fun getQueuedTasksFlow(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE state = 'QUEUED' ORDER BY priority DESC, createdAt ASC")
    suspend fun getQueuedTasks(): List<DownloadTaskEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM download_tasks WHERE videoId = :videoId AND state IN ('QUEUED', 'PREPARING', 'DOWNLOADING', 'CONVERTING', 'WRITING_METADATA', 'RESUMING', 'PAUSED'))")
    suspend fun isTaskExists(videoId: String): Boolean

    @Query("UPDATE download_tasks SET state = 'QUEUED' WHERE state IN ('DOWNLOADING', 'PREPARING', 'CONVERTING', 'WRITING_METADATA', 'RESUMING')")
    suspend fun restoreQueue(): Int

    @Query("SELECT * FROM download_tasks WHERE state = 'COMPLETED' ORDER BY updatedAt DESC")
    fun getCompletedTasksFlow(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE state = 'FAILED' ORDER BY updatedAt DESC")
    fun getFailedTasksFlow(): Flow<List<DownloadTaskEntity>>

    @Query("DELETE FROM download_tasks WHERE state = 'COMPLETED'")
    suspend fun clearCompleted(): Int
    
    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): DownloadTaskEntity?
}
