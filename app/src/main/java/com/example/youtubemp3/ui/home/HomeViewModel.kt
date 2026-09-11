package com.vusal.soundra.ui.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vusal.soundra.data.AppDatabase
import com.vusal.soundra.data.DownloadStatus
import com.vusal.soundra.data.DownloadTaskRepository
import com.vusal.soundra.logic.RecommendationEngine
import com.vusal.soundra.repository.search.RecommendationRepository
import com.vusal.soundra.repository.search.SearchHistoryRepository
import com.vusal.soundra.repository.search.SearchRepository
import com.vusal.soundra.ui.search.SearchUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@kotlinx.coroutines.FlowPreview
class HomeViewModel(
    private val searchHistoryRepository: SearchHistoryRepository,
    private val downloadTaskRepository: DownloadTaskRepository,
    private val recommendationRepository: RecommendationRepository
) : ViewModel() {

    private val engine = RecommendationEngine()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val recentSearches: StateFlow<List<String>> = searchHistoryRepository.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var isRefreshing = false

    init {
        // Observe history changes to refresh recommendations automatically (throttled)
        combine(
            recentSearches,
            downloadTaskRepository.allTasks.map { tasks -> 
                tasks.filter { it.state == DownloadStatus.COMPLETED } 
            }
        ) { searches, downloads ->
            searches to downloads
        }
        .debounce(3000) // Avoid excessive refreshing
        .onEach {
            if (!isRefreshing) loadRecommendations()
        }
        .launchIn(viewModelScope)
    }

    fun refresh() {
        Log.d("PERF_REFRESH", "Manual refresh requested")
        loadRecommendations(force = true)
    }

    fun loadRecommendations(force: Boolean = false) {
        viewModelScope.launch {
            if (_uiState.value is SearchUiState.Loading && !force) return@launch
            
            isRefreshing = true
            _uiState.value = SearchUiState.Loading
            val startTime = System.currentTimeMillis()
            
            try {
                // 1. Get History Snapshots (IO)
                val searches = recentSearches.value
                val downloads = withContext(Dispatchers.IO) {
                    downloadTaskRepository.allTasks.first().filter { 
                        it.state == DownloadStatus.COMPLETED 
                    }
                }

                // 2. Generate Queries (Default/CPU)
                val queries = withContext(Dispatchers.Default) {
                    engine.generateRecommendationQueries(searches, downloads)
                }
                
                // 3. Fetch Results (IO)
                var results = withContext(Dispatchers.IO) {
                    recommendationRepository.getRecommendations(queries)
                }
                
                // 4. Filter and Deduplicate (Default/CPU)
                results = withContext(Dispatchers.Default) {
                    val downloadedIds = downloads.map { it.videoId }.toSet()
                    results.filter { it.id !in downloadedIds }.distinctBy { it.id }.take(30)
                }

                if (results.isEmpty()) {
                    _uiState.value = SearchUiState.Empty
                } else {
                    _uiState.value = SearchUiState.Success(results, isHomeFeed = true)
                }
                Log.d("PERF_STARTUP", "Home: Recommendation loading complete in ${System.currentTimeMillis() - startTime}ms")
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(com.vusal.soundra.R.string.err_load_failed)
                Log.e("PERF_STARTUP", "Home: Recommendation loading failed", e)
            } finally {
                isRefreshing = false
            }
        }
    }

    fun removeSearch(query: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { searchHistoryRepository.removeSearch(query) }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = AppDatabase.getDatabase(context)
            val searchHistoryRepo = SearchHistoryRepository(context)
            val downloadTaskRepo = DownloadTaskRepository(db.downloadTaskDao())
            val searchRepo = SearchRepository()
            val recommendationRepo = RecommendationRepository(searchRepo)
            
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(searchHistoryRepo, downloadTaskRepo, recommendationRepo) as T
        }
    }
}
