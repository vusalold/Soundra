package com.vm.soundra.data

import kotlinx.coroutines.flow.Flow

class DownloadTaskRepository(private val dao: DownloadTaskDao) {
    val allTasks: Flow<List<DownloadTaskEntity>> = dao.getAllTasksFlow()
    val activeTasks: Flow<List<DownloadTaskEntity>> = dao.getActiveTasksFlow()
    val queuedTasks: Flow<List<DownloadTaskEntity>> = dao.getQueuedTasksFlow()
    val completedTasks: Flow<List<DownloadTaskEntity>> = dao.getCompletedTasksFlow()
    val failedTasks: Flow<List<DownloadTaskEntity>> = dao.getFailedTasksFlow()

    suspend fun insertTask(task: DownloadTaskEntity): Long = dao.insertTask(task)
    
    suspend fun updateTask(task: DownloadTaskEntity): Int = dao.updateTask(task)
    
    suspend fun updateStatus(id: Int, state: DownloadStatus): Int = 
        dao.updateStatus(id, state, System.currentTimeMillis())
    
    suspend fun updateProgress(id: Int, progress: Int, downloaded: Long, total: Long, speed: String, eta: String): Int = 
        dao.updateProgress(id, progress, downloaded, total, speed, eta, System.currentTimeMillis())
        
    suspend fun deleteTask(task: DownloadTaskEntity): Int = dao.deleteTask(task)
    
    suspend fun deleteTaskById(id: Int): Int = dao.deleteTaskById(id)
    
    suspend fun clearCompleted(): Int = dao.clearCompleted()
    
    suspend fun getTaskById(id: Int): DownloadTaskEntity? = dao.getTaskById(id)

    suspend fun getActiveTaskCount(): Int = dao.getActiveTaskCount()

    suspend fun isTaskExists(videoId: String): Boolean = dao.isTaskExists(videoId)

    suspend fun restoreQueue(): Int = dao.restoreQueue()

    suspend fun getQueuedTasks(): List<DownloadTaskEntity> = dao.getQueuedTasks()
}
