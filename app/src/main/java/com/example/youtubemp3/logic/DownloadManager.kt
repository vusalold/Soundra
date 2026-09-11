package com.vusal.soundra.logic

import android.content.Context
import android.content.Intent
import android.util.Log
import com.vusal.soundra.data.AppDatabase
import com.vusal.soundra.R
import com.vusal.soundra.data.DownloadStatus
import com.vusal.soundra.data.DownloadTaskEntity
import com.vusal.soundra.data.DownloadTaskRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

const val MAX_ACTIVE_DOWNLOADS = 2

/**
 * Professional Download Manager Singleton.
 * Responsible for Queue management, Concurrency control, and persistence.
 */
class DownloadManager private constructor(private val context: Context) {
    private val TAG = "DOWNLOAD_MANAGER"
    private val repository: DownloadTaskRepository
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val mutex = Mutex()
    
    // Track active jobs to allow cancellation/pause (Fixes "Zombie Download" bug)
    private val activeJobs = ConcurrentHashMap<Int, Job>()
    // Track temp files for active tasks so we can cleanup or resume
    private val activeTempFiles = ConcurrentHashMap<Int, File>()

    init {
        val database = AppDatabase.getDatabase(context)
        repository = DownloadTaskRepository(database.downloadTaskDao())
        Log.d(TAG, "DownloadManager initialized. Max concurrent: $MAX_ACTIVE_DOWNLOADS")
        
        // One-time migration from legacy history to unified downloads
        scope.launch {
            migrateLegacyHistory(database)
            restoreQueue()
        }
    }

    private suspend fun restoreQueue() {
        val restoredCount = repository.restoreQueue()
        if (restoredCount > 0) {
            Log.d(TAG, "QueueRestored: $restoredCount tasks moved back to QUEUED")
        }
        startNext()
    }

    private suspend fun migrateLegacyHistory(db: AppDatabase) {
        try {
            val historyDao = db.downloadHistoryDao()
            val legacyItems = historyDao.getAllHistory().first()
            if (legacyItems.isNotEmpty()) {
                Log.d(TAG, "Migrating ${legacyItems.size} legacy history items...")
                legacyItems.forEach { item ->
                    val videoId = item.title.hashCode().toString()
                    repository.insertTask(DownloadTaskEntity(
                        videoId = videoId,
                        title = item.title,
                        artist = item.artist,
                        thumbnail = item.thumbnailUrl,
                        url = "",
                        bitrate = item.bitrate,
                        state = DownloadStatus.COMPLETED,
                        progress = 100,
                        totalBytes = 0,
                        localFilePath = item.localFilePath,
                        createdAt = item.downloadDate,
                        updatedAt = item.downloadDate
                    ))
                    historyDao.deleteDownload(item)
                }
                Log.d(TAG, "Migration finished.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Migration error", e)
        }
    }

    /**
     * Adds a new task to the queue and manages execution based on concurrency limits.
     */
    suspend fun enqueue(videoId: String, title: String, artist: String, thumbnail: String, url: String, bitrate: String) {
        Log.d("QUEUE DEBUG", "enqueue called: $title (videoId: $videoId)")
        
        mutex.withLock {
            val exists = repository.isTaskExists(videoId)
            Log.d("QUEUE DEBUG", "duplicate check: $exists")
            if (exists) {
                Log.d(TAG, "QueueDuplicate: $title already exists")
                return
            }

            val task = DownloadTaskEntity(
                videoId = videoId,
                title = title,
                artist = artist,
                thumbnail = thumbnail,
                url = url,
                bitrate = bitrate,
                state = DownloadStatus.QUEUED
            )
            val id = repository.insertTask(task).toInt()
            Log.d("QUEUE DEBUG", "insert success, ID: $id")
            Log.d(TAG, "QueueInserted: $title (ID: $id)")
        }
        
        startNext()
    }

    /**
     * Internal method to register an active job from the service.
     */
    fun registerActiveJob(taskId: Int, job: Job) {
        activeJobs[taskId] = job
        Log.d(TAG, "Registered active job for task: $taskId")
    }

    /**
     * Register a temporary file for a task so it can be cleaned up or used to resume.
     */
    fun registerActiveTempFile(taskId: Int, file: File) {
        activeTempFiles[taskId] = file
        Log.d(TAG, "Registered temp file for task $taskId: ${file.absolutePath}")
    }

    fun unregisterActiveTempFile(taskId: Int) {
        activeTempFiles.remove(taskId)
        Log.d(TAG, "Unregistered temp file for task: $taskId")
    }

    fun getActiveTempFile(taskId: Int): File? = activeTempFiles[taskId]

    /**
     * Internal method to unregister an active job.
     */
    fun unregisterActiveJob(taskId: Int) {
        activeJobs.remove(taskId)
        Log.d(TAG, "Unregistered active job for task: $taskId")
        // Try to start next task in queue
        scope.launch { startNext() }
    }

    /**
     * Pauses a running or queued task.
     */
    suspend fun pause(taskId: Int) {
        Log.d(TAG, "QueuePaused requested for task: $taskId")
        
        mutex.withLock {
            // If it's active, we need to stop the job in the service
            if (activeJobs.containsKey(taskId)) {
                val intent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = DownloadForegroundService.ACTION_PAUSE_JOB
                    putExtra(DownloadForegroundService.EXTRA_TASK_ID, taskId)
                }
                context.startService(intent)
                activeJobs.remove(taskId)
            }
            
            repository.updateStatus(taskId, DownloadStatus.PAUSED)
        }
        
        startNext()
    }

    /**
     * Resumes a paused or failed task by putting it back in the queue.
     */
    suspend fun resume(taskId: Int) {
        Log.d(TAG, "QueueResumed requested for task: $taskId")
        repository.updateStatus(taskId, DownloadStatus.QUEUED)
        startNext()
    }

    /**
     * Retries a failed task.
     */
    suspend fun retry(taskId: Int) {
        Log.d(TAG, "Retry requested for task: $taskId")
        repository.updateStatus(taskId, DownloadStatus.QUEUED)
        startNext()
    }

    /**
     * Cancels a task, stops any active job, and deletes temporary files.
     */
    suspend fun cancel(taskId: Int) {
        Log.d(TAG, "QueueCancelled requested for task: $taskId")
        
        mutex.withLock {
            // Signal service to stop this task
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = DownloadForegroundService.ACTION_STOP_JOB
                putExtra(DownloadForegroundService.EXTRA_TASK_ID, taskId)
            }
            context.startService(intent)
            
            activeJobs.remove(taskId)
            repository.updateStatus(taskId, DownloadStatus.CANCELLED)
            cleanupTaskFiles(taskId)
        }
        
        startNext()
    }

    /**
     * Completely removes a task from the database and deletes associated files.
     */
    suspend fun deleteTask(taskId: Int, deletePhysicalFile: Boolean = false) {
        Log.d(TAG, "Delete requested for task: $taskId (Delete file: $deletePhysicalFile)")
        
        mutex.withLock {
            // Signal service to stop this task if active
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = DownloadForegroundService.ACTION_STOP_JOB
                putExtra(DownloadForegroundService.EXTRA_TASK_ID, taskId)
            }
            context.startService(intent)
            
            activeJobs.remove(taskId)

            val task = repository.getTaskById(taskId)
            if (task != null) {
                cleanupTaskFiles(taskId)
                if (deletePhysicalFile && task.state == DownloadStatus.COMPLETED) {
                    task.localFilePath?.let { StorageUtils.deletePhysicalFile(context, it) }
                }
                repository.deleteTask(task)
            }
        }
        
        startNext()
    }

    fun cleanupTaskFiles(taskId: Int) {
        try {
            // First try temp file we explicitly registered
            val tmp = activeTempFiles[taskId]
            if (tmp != null && tmp.exists()) {
                tmp.delete()
                activeTempFiles.remove(taskId)
                Log.d(TAG, "Deleted temp file for task $taskId: ${tmp.absolutePath}")
            } else {
                // Fallback: scan cache dir for matching temp files
                val cacheDir = context.cacheDir
                cacheDir.listFiles()?.filter { it.name.startsWith("yt_download") }?.forEach { f ->
                    try { f.delete() } catch (_: Exception) {}
                }
                Log.d(TAG, "Scanned cache and removed orphan temp files for task $taskId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup temp files for $taskId", e)
        }
        Log.d(TAG, "Cleanup finished for task: $taskId")
    }

    /**
     * Renames a completed task and the physical file.
     */
    suspend fun renameTask(taskId: Int, newName: String): Boolean {
        Log.d(TAG, "Rename requested: $taskId -> $newName")
        val task = repository.getTaskById(taskId)
        if (task != null && task.state == DownloadStatus.COMPLETED && task.localFilePath != null) {
            val finalName = StorageUtils.renameMediaStoreFile(context, task.localFilePath, newName)
            if (finalName != null) {
                repository.updateTask(task.copy(title = newName.replace(".mp3", "", ignoreCase = true)))
                return true
            }
        }
        return false
    }

    /**
     * Gets technical details of a task's file.
     */
    fun getTaskDetails(taskId: Int, callback: (FileDetails) -> Unit) {
        scope.launch {
            val task = repository.getTaskById(taskId)
            if (task?.localFilePath != null) {
                val details = StorageUtils.getFileDetails(context, task.localFilePath)
                withContext(Dispatchers.Main) {
                    callback(details)
                }
            } else {
                // Return empty details if file missing
                val unknown = context.getString(R.string.unknown)
                val fileMissing = context.getString(R.string.file_missing)
                val empty = FileDetails(unknown, fileMissing, unknown, unknown, unknown, unknown, "MP3", unknown, unknown, unknown, unknown, "")
                withContext(Dispatchers.Main) {
                    callback(empty)
                }
            }
        }
    }

    /**
     * Updates the status of a task in the database.
     */
    suspend fun updateStatus(taskId: Int, state: DownloadStatus) {
        repository.updateStatus(taskId, state)
        Log.d(TAG, "Task status updated: $taskId -> $state")
    }

    /**
     * Updates completion details.
     */
    suspend fun markAsCompleted(taskId: Int, localPath: String) {
        val task = repository.getTaskById(taskId)
        if (task != null) {
            repository.updateTask(task.copy(
                state = DownloadStatus.COMPLETED,
                localFilePath = localPath,
                progress = 100,
                updatedAt = System.currentTimeMillis()
            ))
            Log.d(TAG, "QueueCompleted: $taskId")
        }
        startNext()
    }

    /**
     * Updates the progress of a task in the database.
     */
    suspend fun updateProgress(taskId: Int, progress: Int, downloaded: Long, total: Long, speed: String, eta: String) {
        repository.updateProgress(taskId, progress, downloaded, total, speed, eta)
    }

    /**
     * Observes all tasks as a StateFlow.
     */
    val allTasks: StateFlow<List<DownloadTaskEntity>> = repository.allTasks
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Triggers the next task(s) in queue if concurrency limit allows.
     */
    private suspend fun startNext() {
        mutex.withLock {
            val activeCount = repository.getActiveTaskCount()
            val queuedTasks = repository.getQueuedTasks()
            
            Log.d("QUEUE DEBUG", "active count: $activeCount")
            Log.d("QUEUE DEBUG", "queued count: ${queuedTasks.size}")
            
            Log.d(TAG, "ActiveCount: $activeCount, MAX: $MAX_ACTIVE_DOWNLOADS")
            
            if (activeCount < MAX_ACTIVE_DOWNLOADS) {
                val slotsAvailable = MAX_ACTIVE_DOWNLOADS - activeCount
                Log.d("QUEUE DEBUG", "available slots: $slotsAvailable")
                
                queuedTasks.take(slotsAvailable).forEach { task ->
                    Log.d("QUEUE DEBUG", "starting download: ${task.title}")
                    Log.d(TAG, "QueueStarted: ${task.title} (ID: ${task.id})")
                    // Mark as PREPARING immediately to prevent double starting before service starts
                    repository.updateStatus(task.id, DownloadStatus.PREPARING)
                    DownloadForegroundService.start(context, task.url, task.title, task.bitrate, task.id)
                }
            } else {
                Log.d("QUEUE DEBUG", "no available slots")
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                val instance = DownloadManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
