package com.vm.soundra.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vm.soundra.R
import com.vm.soundra.logic.Mp3DownloadViewModel
import com.vm.soundra.model.search.SearchResult
import com.vm.soundra.ui.search.SearchUiState
import com.vm.soundra.ui.theme.*
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    @Suppress("UNUSED_PARAMETER") mp3DownloadViewModel: Mp3DownloadViewModel,
    homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(LocalContext.current)),
    initialSearchQuery: String?,
    onInitialQueryUsed: () -> Unit,
    onDownloadClick: (SearchResult) -> Unit,
    onAddToPlaylist: (SearchResult) -> Unit,
    onSearchTriggered: (String) -> Unit
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val recentSearches by homeViewModel.recentSearches.collectAsState()

    val pullRefreshState = rememberPullToRefreshState()
    
    LaunchedEffect(initialSearchQuery) {
        if (!initialSearchQuery.isNullOrBlank()) {
            onInitialQueryUsed()
        }
    }

    Scaffold(
        topBar = { HomeTopBar() },
        containerColor = Color.Transparent
    ) { padding ->
        PullToRefreshBox(
            state = pullRefreshState,
            isRefreshing = uiState is SearchUiState.Loading,
            onRefresh = { homeViewModel.loadRecommendations(force = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // 1. Hero Experience
                item {
                    HeroExperience(onSearchClick = { onSearchTriggered("") })
                }

                // 2. Recent Quick Actions
                if (recentSearches.isNotEmpty()) {
                    item {
                        RecentChipsSection(recentSearches, onSearchTriggered)
                    }
                }

                // 3. Immersive Feed Header
                item {
                    Text(
                        text = stringResource(R.string.home_for_you),
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
                    )
                }

                // 4. Personalized Results
                when (val state = uiState) {
                    is SearchUiState.Success -> {
                        items(state.results, key = { it.id }) { song ->
                            DiscoveryMusicItem(
                                song = song,
                                onClick = onDownloadClick,
                                onAddToPlaylist = { onAddToPlaylist(song) }
                            )
                        }
                    }
                    is SearchUiState.Loading -> {
                        items(6) { DiscoverySkeletonItem() }
                    }
                    is SearchUiState.Empty -> {
                        item { DiscoveryEmptyView() }
                    }
                    is SearchUiState.Error -> {
                        item {
                            DiscoveryErrorView(stringResource(state.messageResId)) { homeViewModel.loadRecommendations() }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar() {
    Spacer(modifier = Modifier.statusBarsPadding().height(24.dp))
}

@Composable
private fun HeroExperience(onSearchClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSearchClick
            )
    ) {
        Text(
            text = stringResource(R.string.home_hero_greeting),
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        // Premium Minimal Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(Color.White.copy(alpha = 0.05f), CircleShape)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = BrandCyan, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.home_search_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
private fun RecentChipsSection(searches: List<String>, onSearch: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        items(searches.take(6), key = { it }) { search ->
            Surface(
                onClick = { onSearch(search) },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.03f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Text(
                    text = search,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscoveryMusicItem(
    song: SearchResult,
    onClick: (SearchResult) -> Unit,
    onAddToPlaylist: () -> Unit = {}
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick(song) },
                onLongClick = { showMenu = true }
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // High Contrast Image
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(22.dp))
        ) {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Visual Tag
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.8f), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = song.duration, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = song.uploaderName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.4f),
                maxLines = 1
            )
        }

        Box {
            IconButton(
                onClick = { 
                    showMenu = true 
                },
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert, 
                    contentDescription = null, 
                    tint = Color.White.copy(alpha = 0.3f), 
                    modifier = Modifier.size(20.dp)
                )
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(BrandSoftBlack)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_add_to_playlist)) },
                    onClick = { 
                        onAddToPlaylist()
                        showMenu = false 
                    },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
fun DiscoverySkeletonItem() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(88.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(22.dp)))
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth(0.5f).height(20.dp).background(Color.White.copy(alpha = 0.05f), CircleShape))
            Spacer(modifier = Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth(0.3f).height(14.dp).background(Color.White.copy(alpha = 0.03f), CircleShape))
        }
    }
}

@Composable
private fun DiscoveryEmptyView() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_brand_logo),
            contentDescription = null,
            modifier = Modifier.size(100.dp).graphicsLayer { alpha = 0.2f },
            tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.home_empty_history_msg),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.2f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DiscoveryErrorView(msg: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = msg, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = BrandError.copy(alpha = 0.6f))
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.btn_retry), color = BrandCyan)
        }
    }
}
