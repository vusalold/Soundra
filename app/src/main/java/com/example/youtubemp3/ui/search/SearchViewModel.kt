package com.vusal.soundra.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vusal.soundra.model.search.SearchResult
import com.vusal.soundra.repository.search.SearchHistoryRepository
import com.vusal.soundra.repository.search.SearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(val results: List<SearchResult>, val isHomeFeed: Boolean = false) : SearchUiState()
    data class Error(val messageResId: Int) : SearchUiState()
    object Empty : SearchUiState()
}

class SearchViewModel(
    private val repository: SearchRepository,
    private val historyRepository: SearchHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val recentSearches: StateFlow<List<String>> = historyRepository.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _showSuggestions = MutableStateFlow(false)
    val showSuggestions: StateFlow<Boolean> = _showSuggestions.asStateFlow()

    private val _selectedCategory = MutableStateFlow("all")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private var searchJob: Job? = null
    private var suggestionJob: Job? = null

    init {
        loadHomeFeed()
    }

    fun onQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        suggestionJob?.cancel()
        
        if (query.isBlank()) {
            _suggestions.value = emptyList()
            _showSuggestions.value = false
            loadHomeFeed(_selectedCategory.value)
            return
        }

        _showSuggestions.value = true

        if (query.length < 2) {
            _suggestions.value = emptyList()
            return
        }

        suggestionJob = viewModelScope.launch {
            delay(500) // Debounce 500ms
            try {
                val results = withContext(Dispatchers.IO) { repository.getSuggestions(query) }
                _suggestions.value = results
            } catch (e: Exception) {
                _suggestions.value = emptyList()
            }
        }
    }

    fun onCategorySelected(category: String) {
        _selectedCategory.value = category
        if (_searchQuery.value.isBlank()) {
            loadHomeFeed(category)
        } else {
            performSearch("${_searchQuery.value} $category")
        }
    }

    fun loadHomeFeed(category: String = "all") {
        viewModelScope.launch {
            if (_uiState.value is SearchUiState.Loading) return@launch
            
            _uiState.value = SearchUiState.Loading
            _showSuggestions.value = false
            try {
                val results = withContext(Dispatchers.IO) {
                    if (category == "all") {
                        repository.getYouTubeHomeFeed()
                    } else {
                        repository.search("$category music")
                    }
                }
                
                if (results.isEmpty()) {
                    _uiState.value = SearchUiState.Empty
                } else {
                    _uiState.value = SearchUiState.Success(results, isHomeFeed = true)
                }
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(com.vusal.soundra.R.string.err_load_failed)
            }
        }
    }

    fun performSearch(query: String) {
        if (query.isBlank()) {
            loadHomeFeed()
            return
        }
        
        searchJob?.cancel()
        suggestionJob?.cancel()
        _searchQuery.value = query
        _showSuggestions.value = false

        searchJob = viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            try {
                val results = withContext(Dispatchers.IO) { repository.search(query) }
                if (results.isEmpty()) {
                    _uiState.value = SearchUiState.Empty
                } else {
                    _uiState.value = SearchUiState.Success(results, isHomeFeed = false)
                    withContext(Dispatchers.IO) { historyRepository.addSearch(query) }
                }
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(com.vusal.soundra.R.string.err_search_failed)
            }
        }
    }

    fun refresh() {
        val currentQuery = _searchQuery.value
        if (currentQuery.isBlank()) {
            loadHomeFeed()
        } else {
            performSearch(currentQuery)
        }
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { historyRepository.removeSearch(query) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { historyRepository.clearHistory() }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SearchViewModel(
                SearchRepository(),
                SearchHistoryRepository(context)
            ) as T
        }
    }
}
