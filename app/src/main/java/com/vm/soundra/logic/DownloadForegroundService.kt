package com.vm.soundra.logic

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.vm.soundra.R
import com.vm.soundra.data.DownloadStatus
import com.vm.soundra.update.DownloadState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: DownloadNotificationManager
    private lateinit var downloadManager: DownloadManager
    
    // Track active jobs and downloaders within this service instance
    private val activeJobs = ConcurrentHashMap<Int, Job>()
    private val activeDownloaders = ConcurrentHashMap<Int, YoutubeDownloader>()
    
    override fun onCreate() {
        super.onCreate()
        
        // Use runBlocking for one-time authoritative language initialization to avoid race conditions in Service
        val languageManager = LanguageManager(this)
        val language = kotlinx.coroutines.runBlocking { languageManager.selectedLanguage.first() }
        val localizedContext = languageManager.getLocalizedContext(this, language.tag)
        
        notificationManager = DownloadNotificationManager(localizedContext)
        downloadManager = DownloadManager.getInstance(localizedContext)
        Log.d("FG_SERVICE", "Service Created with language: ${language.tag}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("FG_SERVICE", "onStartCommand: ${intent?.action}")
        val action = intent?.action
        val taskId = intent?.getIntExtra(EXTRA_TASK_ID, -1) ?: -1

        when (action) {
            ACTION_CANCEL -> {
                Log.d("DOWNLOAD_MANAGER", "ActionCancel: $taskId")
                serviceScope.launch { downloadManager.cancel(taskId) }
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                Log.d("DOWNLOAD_MANAGER", "ActionPause: $taskId")
                serviceScope.launch { downloadManager.pause(taskId) }
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                Log.d("DOWNLOAD_MANAGER", "ActionResume: $taskId")
                serviceScope.launch { downloadManager.resume(taskId) }
                return START_NOT_STICKY
            }
            ACTION_RETRY -> {
                Log.d("DOWNLOAD_MANAGER", "ActionRetry: $taskId")
                serviceScope.launch { downloadManager.retry(taskId) }
                return START_NOT_STICKY
            }
            ACTION_DELETE -> {
                Log.d("DOWNLOAD_MANAGER", "ActionDelete: $taskId")
                serviceScope.launch { downloadManager.deleteTask(taskId) }
                return START_NOT_STICKY
            }
            ACTION_PAUSE_JOB -> {
                Log.d("DOWNLOAD_MANAGER", "ActionPauseJob: $taskId")
                activeDownloaders[taskId]?.pause()
                activeJobs[taskId]?.cancel("User paused task")
                return START_NOT_STICKY
            }
            ACTION_STOP_JOB -> {
                Log.d("DOWNLOAD_MANAGER", "ActionStopJob: $taskId")
                activeDownloaders[taskId]?.cancel("User cancelled")
                activeJobs[taskId]?.cancel("User cancelled via notification")
                notificationManager.cancelNotification(taskId)
                return START_NOT_STICKY
            }
        }

        val url = intent?.getStringExtra(EXTRA_URL)
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: getString(R.string.prop_title)
        val bitrate = intent?.getStringExtra(EXTRA_BITRATE) ?: "192k"

        if (url != null && taskId != -1) {
            startDownload(url, title, bitrate, taskId)
        }

        return START_NOT_STICKY
    }

    private fun startDownload(url: String, title: String, bitrate: String, taskId: Int) {
        if (activeJobs.containsKey(taskId)) {
            Log.d("FG_SERVICE", "Task $taskId already running, ignoring start request")
            return
        }

        _downloadState.value = DownloadState.Preparing
        
        // Android 12+ requires immediate notification for foreground service
        // We use a fixed ID for the service lifecycle, and separate IDs for tasks
        val initialNotification = notificationManager.getInitialNotification(title, getString(R.string.status_preparing_download), "", taskId)
        startForeground(DownloadNotificationManager.NOTIFICATION_ID, initialNotification)
        
        Log.d("FG_SERVICE", "startForeground() called for task $taskId")

        val taskDownloader = YoutubeDownloader(this)
        activeDownloaders[taskId] = taskDownloader

        val job = serviceScope.launch {
            try {
                // Update details from DB if available
                val task = downloadManager.allTasks.value.find { it.id == taskId }
                val artist = task?.artist ?: getString(R.string.unknown)
                val thumbnail = task?.thumbnail ?: ""
                notificationManager.updateProgress(title, artist, thumbnail, 0, 0, 0, "", "", taskId, activeJobs.size + 1)

                // Register this job in the manager so it can be controlled
                downloadManager.registerActiveJob(taskId, this.coroutineContext[Job]!!)

                val result = taskDownloader.downloadAndConvert(
                    url = url,
                    bitrate = bitrate,
                    onStatus = { status ->
                        Log.d("DOWNLOAD_MANAGER", "Status($taskId): $status")
                    },
                    onProgress = { progress, downloaded, total, speed, eta ->
                        // Only update global state if this is the "primary" task or first one
                        if (activeJobs.keys.firstOrNull() == taskId) {
                            _downloadState.value = DownloadState.Downloading(progress, downloaded, total, speed, eta)
                        }
                        
                        val currentTask = downloadManager.allTasks.value.find { it.id == taskId }
                        val artistName = currentTask?.artist ?: getString(R.string.unknown)
                        val thumbUrl = currentTask?.thumbnail ?: ""
                        notificationManager.updateProgress(title, artistName, thumbUrl, progress, downloaded, total, speed, eta, taskId, activeJobs.size)
                        
                        serviceScope.launch {
                            downloadManager.updateProgress(taskId, progress, downloaded, total, speed, eta)
                        }
                    },
                    onStateChange = { state ->
                        serviceScope.launch {
                            downloadManager.updateStatus(taskId, state)
                        }
                    },
                    existingTempFile = downloadManager.getActiveTempFile(taskId)
                )

                when (result) {
                    is DownloadResult.Success -> {
                        Log.d("DOWNLOAD_MANAGER", "QueueFinished: $taskId")
                        val file = File(result.entity.localFilePath)
                        if (activeJobs.size == 1) {
                            _downloadState.value = DownloadState.Completed(file, result.entity.localFilePath)
                        }
                        
                        val currentTask = downloadManager.allTasks.value.find { it.id == taskId }
                        Log.d("DOWNLOAD_NOTIFICATION", "Completion notification triggered for task: $taskId")
                        notificationManager.showCompleted(title, currentTask?.artist ?: getString(R.string.unknown), currentTask?.thumbnail ?: "", result.entity.localFilePath, taskId, activeJobs.size - 1)
                        
                        downloadManager.markAsCompleted(taskId, result.entity.localFilePath)
                    }
                    is DownloadResult.Error -> {
                        Log.d("DOWNLOAD_NOTIFICATION", "Completion notification skipped: task $taskId not successful")
                        if (result.message == "Cancelled") {
                            Log.d("DOWNLOAD_MANAGER", "Task $taskId cancelled internally")
                        } else {
                            Log.e("DOWNLOAD_MANAGER", "Task $taskId failed: ${result.message}")
                            
                            val currentTask = downloadManager.allTasks.value.find { it.id == taskId }
                            notificationManager.showFailed(title, currentTask?.artist ?: getString(R.string.unknown), currentTask?.thumbnail ?: "", result.message, taskId, activeJobs.size - 1)
                            
                            downloadManager.updateStatus(taskId, DownloadStatus.FAILED)
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d("DOWNLOAD_MANAGER", "Job $taskId cancelled: ${e.message}")
                if (e.message != "User paused task") {
                    downloadManager.updateStatus(taskId, DownloadStatus.CANCELLED)
                }
            } catch (e: Exception) {
                Log.e("DOWNLOAD_MANAGER", "Task $taskId crashed", e)
                
                val currentTask = downloadManager.allTasks.value.find { it.id == taskId }
                notificationManager.showFailed(title, currentTask?.artist ?: getString(R.string.unknown), currentTask?.thumbnail ?: "", e.localizedMessage ?: getString(R.string.err_analysis_failed), taskId, activeJobs.size - 1)
                
                downloadManager.updateStatus(taskId, DownloadStatus.FAILED)
            } finally {
                activeJobs.remove(taskId)
                activeDownloaders.remove(taskId)
                downloadManager.unregisterActiveJob(taskId)
                downloadManager.unregisterActiveTempFile(taskId)
                
                if (activeJobs.isEmpty()) {
                    Log.d("FG_SERVICE", "No more active tasks. Stopping service.")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        activeJobs[taskId] = job
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d("FG_SERVICE", "Service Destroyed")
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "com.vm.soundra.ACTION_CANCEL"
        const val ACTION_PAUSE = "com.vm.soundra.ACTION_PAUSE"
        const val ACTION_RESUME = "com.vm.soundra.ACTION_RESUME"
        const val ACTION_RETRY = "com.vm.soundra.ACTION_RETRY"
        const val ACTION_DELETE = "com.vm.soundra.ACTION_DELETE"
        
        // Internal actions from Manager to Service
        const val ACTION_STOP_JOB = "com.vm.soundra.INTERNAL_STOP_JOB"
        const val ACTION_PAUSE_JOB = "com.vm.soundra.INTERNAL_PAUSE_JOB"

        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BITRATE = "extra_bitrate"
        const val EXTRA_TASK_ID = "extra_task_id"

        private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
        val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

        fun resetState() {
            _downloadState.value = DownloadState.Idle
        }

        fun start(context: Context, url: String, title: String, bitrate: String = "192k", taskId: Int = -1) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BITRATE, bitrate)
                putExtra(EXTRA_TASK_ID, taskId)
            }
            try {
                Log.d("FG_SERVICE", "Starting foreground service via ContextCompat")
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                    Log.e("FG_SERVICE", "Foreground service start denied", e)
                } else {
                    Log.e("FG_SERVICE", "Failed to start service", e)
                }
            }
        }
    }
}
