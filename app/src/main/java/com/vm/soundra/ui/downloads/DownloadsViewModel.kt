package com.vm.soundra.ui.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vm.soundra.data.AppDatabase
import com.vm.soundra.data.DownloadStatus
import com.vm.soundra.data.DownloadTaskEntity
import com.vm.soundra.data.DownloadTaskRepository
import com.vm.soundra.logic.DownloadManager
import com.vm.soundra.logic.FileDetails
import com.vm.soundra.R
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

enum class DownloadFilter {
    ALL, DOWNLOADING, COMPLETED, FAILED, QUEUED
}

enum class DownloadSort {
    NEWEST, OLDEST, NAME_AZ, SIZE
}

data class DownloadsUiState(
    val tasks: List<DownloadTaskEntity> = emptyList(),
    val filteredTasks: List<DownloadTaskEntity> = emptyList(),
    val searchQuery: String = "",
    val activeFilter: DownloadFilter = DownloadFilter.ALL,
    val activeSort: DownloadSort = DownloadSort.NEWEST,
    val totalSize: Long = 0L,
    val completedCount: Int = 0,
    val downloadingCount: Int = 0,
    val failedCount: Int = 0,
    val queuedCount: Int = 0,
    val pausedCount: Int = 0,
    val snackbarMessageResId: Int? = null
)

class DownloadsViewModel(
    private val repository: DownloadTaskRepository,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _filter = MutableStateFlow(DownloadFilter.ALL)
    private val _sort = MutableStateFlow(DownloadSort.NEWEST)
    private val _snackbarMessageResId = MutableStateFlow<Int?>(null)
    
    val uiState: StateFlow<DownloadsUiState> = combine(
        repository.allTasks,
        _searchQuery,
        _filter,
        _sort,
        _snackbarMessageResId
    ) { tasks, query, filter, sort, snackbarResId ->
        val filtered = tasks.filter { task ->
            val matchesSearch = task.title.contains(query, ignoreCase = true) || 
                              task.videoId.contains(query, ignoreCase = true)
            
            val matchesFilter = when (filter) {
                DownloadFilter.ALL -> true
                DownloadFilter.DOWNLOADING -> task.state in listOf(DownloadStatus.DOWNLOADING, DownloadStatus.PREPARING, DownloadStatus.CONVERTING, DownloadStatus.WRITING_METADATA)
                DownloadFilter.COMPLETED -> task.state == DownloadStatus.COMPLETED
                DownloadFilter.FAILED -> task.state == DownloadStatus.FAILED
                DownloadFilter.QUEUED -> task.state == DownloadStatus.QUEUED
            }
            
            matchesSearch && matchesFilter
        }.sortedWith(when (sort) {
            DownloadSort.NEWEST -> compareByDescending { it.createdAt }
            DownloadSort.OLDEST -> compareBy { it.createdAt }
            DownloadSort.NAME_AZ -> compareBy { it.title.lowercase() }
            DownloadSort.SIZE -> compareByDescending { it.totalBytes }
        })

        val completed = tasks.count { it.state == DownloadStatus.COMPLETED }
        val downloading = tasks.count { it.state in listOf(DownloadStatus.DOWNLOADING, DownloadStatus.PREPARING, DownloadStatus.CONVERTING, DownloadStatus.WRITING_METADATA, DownloadStatus.RESUMING) }
        val failed = tasks.count { it.state == DownloadStatus.FAILED }
        val queued = tasks.count { it.state == DownloadStatus.QUEUED }
        val paused = tasks.count { it.state == DownloadStatus.PAUSED }
        val totalBytes = tasks.filter { it.state == DownloadStatus.COMPLETED }.sumOf { it.totalBytes }

        DownloadsUiState(
            tasks = tasks,
            filteredTasks = filtered,
            searchQuery = query,
            activeFilter = filter,
            activeSort = sort,
            totalSize = totalBytes,
            completedCount = completed,
            downloadingCount = downloading,
            failedCount = failed,
            queuedCount = queued,
            pausedCount = paused,
            snackbarMessageResId = snackbarResId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadsUiState())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onFilterChange(filter: DownloadFilter) {
        _filter.value = filter
    }

    fun onSortChange(sort: DownloadSort) {
        _sort.value = sort
    }

    fun clearSnackbar() {
        _snackbarMessageResId.value = null
    }

    // Actions Implemented in Phase 4 & Fix
    fun renameTask(taskId: Int, newName: String) {
        viewModelScope.launch {
            val success = downloadManager.renameTask(taskId, newName)
            _snackbarMessageResId.value = if (success) R.string.err_rename_success else R.string.err_rename_failed
        }
    }

    fun getProperties(taskId: Int, callback: (FileDetails) -> Unit) {
        downloadManager.getTaskDetails(taskId, callback)
    }

    fun retryTask(taskId: Int) { 
        viewModelScope.launch { downloadManager.retry(taskId) }
    }
    
    fun deleteTask(taskId: Int, deletePhysicalFile: Boolean = false) { 
        viewModelScope.launch {
            downloadManager.deleteTask(taskId, deletePhysicalFile)
        }
    }
    
    fun pauseTask(taskId: Int) { 
        viewModelScope.launch { downloadManager.pause(taskId) }
    }
    
    fun resumeTask(taskId: Int) { 
        viewModelScope.launch { downloadManager.resume(taskId) }
    }

    fun cancelTask(taskId: Int) {
        viewModelScope.launch { downloadManager.cancel(taskId) }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val database = AppDatabase.getDatabase(context)
            val repository = DownloadTaskRepository(database.downloadTaskDao())
            val downloadManager = DownloadManager.getInstance(context)
            @Suppress("UNCHECKED_CAST")
            return DownloadsViewModel(repository, downloadManager) as T
        }
    }
}
