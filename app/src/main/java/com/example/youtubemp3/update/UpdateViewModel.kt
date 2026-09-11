package com.vusal.soundra.update

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.vusal.soundra.R
import java.io.File

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val service = GitHubUpdateService()
    private val checker = UpdateChecker(service)
    private val apkDownloader = ApkDownloader(application)
    private val installerManager = InstallerManager(application)
    private val prefs = application.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    private var downloadJob: Job? = null
    private val TAG = "APK_DOWNLOADER"

    init {
        // Check if there was an ongoing download
        val savedId = prefs.getLong("last_download_id", -1L)
        if (savedId != -1L) {
            Log.d(TAG, "Restoring download tracking for ID: $savedId")
            startPolling(savedId)
        }
    }

    fun dismissUpdateDialog() {
        _updateState.value = UpdateState.Idle
    }

    fun checkUpdate() {
        viewModelScope.launch {
            Log.d(UpdateConstants.TAG, "GitHub connection started")
            _updateState.value = UpdateState.Loading
            
            Log.d(UpdateConstants.TAG, "Downloading version.json")
            when (val result = checker.checkForUpdate()) {
                is UpdateResult.Success -> {
                    Log.d(UpdateConstants.TAG, "JSON parsed successfully")
                    Log.d(UpdateConstants.TAG, "GitHub Version: ${result.versionInfo.versionCode}")
                    Log.d(UpdateConstants.TAG, "Update Available")
                    _updateState.value = UpdateState.Success(true, result.versionInfo)
                }
                is UpdateResult.NoUpdate -> {
                    Log.d(UpdateConstants.TAG, "JSON parsed successfully")
                    Log.d(UpdateConstants.TAG, "No Update")
                    _updateState.value = UpdateState.Success(false)
                }
                is UpdateResult.Error -> {
                    val message = result.message ?: "Unknown error"
                    Log.e(UpdateConstants.TAG, "Error: $message")
                    _updateState.value = UpdateState.Error(message)
                }
            }
        }
    }

    fun startDownload(versionInfo: VersionInfo) {
        if (_downloadState.value is DownloadState.Downloading) {
            Log.d(TAG, "Download already in progress, skipping start.")
            return
        }

        _downloadState.value = DownloadState.Preparing
        viewModelScope.launch {
            try {
                val downloadId = apkDownloader.startDownload(versionInfo.apkUrl, versionInfo.versionName)
                prefs.edit().putLong("last_download_id", downloadId).apply()
                startPolling(downloadId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start download: ${e.message}")
                _downloadState.value = DownloadState.Failed(getApplication<Application>().getString(R.string.err_download_start_failed))
            }
        }
    }

    fun installApk(file: File) {
        installerManager.installApk(file)
    }

    private fun startPolling(downloadId: Long) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            Log.d(TAG, "Starting polling loop for ID: $downloadId")
            while (isActive) {
                val newState = apkDownloader.getDownloadProgress(downloadId)
                
                // Only update if state actually changed or it's a progress update
                if (_downloadState.value != newState) {
                    Log.d(TAG, "State Transition: ${_downloadState.value} -> $newState")
                    _downloadState.value = newState
                }
                
                if (newState is DownloadState.Completed || newState is DownloadState.Failed) {
                    Log.d(TAG, "Polling loop finished with terminal state: $newState")
                    prefs.edit().remove("last_download_id").apply()
                    break
                }
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        downloadJob?.cancel()
    }
}
