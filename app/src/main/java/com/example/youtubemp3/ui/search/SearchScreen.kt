package com.vusal.soundra.ui.search

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vusal.soundra.R
import com.vusal.soundra.logic.Mp3DownloadViewModel
import com.vusal.soundra.ui.home.DiscoveryMusicItem
import com.vusal.soundra.ui.home.DiscoverySkeletonItem
import com.vusal.soundra.ui.theme.BrandCyan
import com.vusal.soundra.model.search.SearchResult

@Composable
fun SearchContent(
    viewModel: SearchViewModel,
    @Suppress("UNUSED_PARAMETER") mp3DownloadViewModel: Mp3DownloadViewModel,
    onDownloadClick: (SearchResult) -> Unit,
    onAddToPlaylist: (SearchResult) -> Unit,
    initialQuery: String? = null,
    onInitialQueryUsed: () -> Unit = {}
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val showSuggestions by viewModel.showSuggestions.collectAsState()
    
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // Autofocus on entry
        focusRequester.requestFocus()
    }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            viewModel.performSearch(initialQuery)
            onInitialQueryUsed()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Premium Search Header
        SearchHeader(
            query = searchQuery,
            focusRequester = focusRequester,
            onQueryChange = { viewModel.onQueryChange(it) },
            onSearch = { 
                if (searchQuery.isNotBlank()) {
                    viewModel.performSearch(searchQuery)
                    focusManager.clearFocus()
                }
            },
            onClear = { viewModel.onQueryChange("") }
        )

        // 2. Dynamic Content
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                when {
                    // State: Real-time Suggestions
                    showSuggestions && searchQuery.isNotBlank() && uiState !is SearchUiState.Success -> {
                        item { SuggestionTitle() }
                        items(suggestions, key = { it }) { suggestion ->
                            SuggestionRow(suggestion) {
                                viewModel.performSearch(suggestion)
                                focusManager.clearFocus()
                            }
                        }
                    }
                    
                    // State: Empty Input -> Show History
                    searchQuery.isBlank() -> {
                        if (recentSearches.isNotEmpty()) {
                            item { HistoryHeader { viewModel.clearHistory() } }
                            items(recentSearches, key = { it }) { search ->
                                HistoryRow(
                                    query = search,
                                    onClick = { 
                                        viewModel.performSearch(search)
                                        focusManager.clearFocus()
                                    },
                                    onDelete = { viewModel.removeRecentSearch(search) }
                                )
                            }
                        } else {
                            item { HistoryEmptyState() }
                        }
                    }

                    // State: Results or Loading
                    else -> {
                        when (val state = uiState) {
                            is SearchUiState.Loading -> {
                                items(8) { DiscoverySkeletonItem() }
                            }
                            is SearchUiState.Success -> {
                                item { ResultsTitle() }
                                items(state.results, key = { it.id }) { result ->
                                    DiscoveryMusicItem(
                                        song = result,
                                        onClick = onDownloadClick,
                                        onAddToPlaylist = { onAddToPlaylist(result) }
                                    )
                                }
                            }
                            is SearchUiState.Error -> {
                                item { SearchErrorState(stringResource(state.messageResId)) }
                            }
                            is SearchUiState.Empty -> {
                                item { SearchEmptyResultsState() }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(
    query: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(24.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.05f),
        border = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = BrandCyan, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_hint_youtube),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.2f)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    cursorBrush = SolidColor(BrandCyan),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun SuggestionTitle() {
    Text(
        text = stringResource(R.string.search_suggestions),
        style = MaterialTheme.typography.labelSmall,
        color = BrandCyan,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun SuggestionRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.NorthWest, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(20.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun HistoryHeader(onClearAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.search_recent),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.4f)
        )
        TextButton(onClick = onClearAll) {
            Text(stringResource(R.string.search_clear_all), style = MaterialTheme.typography.labelSmall, color = BrandCyan)
        }
    }
}

@Composable
private fun HistoryRow(query: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.History, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(20.dp))
        Text(text = query, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ResultsTitle() {
    Text(
        text = stringResource(R.string.results_title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(24.dp)
    )
}

@Composable
private fun HistoryEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_brand_logo),
            contentDescription = null,
            modifier = Modifier.size(80.dp).graphicsLayer { alpha = 0.15f },
            tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.search_no_history), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.15f))
    }
}

@Composable
private fun SearchEmptyResultsState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_brand_logo),
            contentDescription = null,
            modifier = Modifier.size(80.dp).graphicsLayer { alpha = 0.15f },
            tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.no_results), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.15f))
    }
}

@Composable
private fun SearchErrorState(msg: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text = msg, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f), textAlign = TextAlign.Center)
    }
}
