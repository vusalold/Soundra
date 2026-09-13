package com.vm.soundra.ui.playlists

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vm.soundra.R
import com.vm.soundra.data.PlaylistSongEntity
import com.vm.soundra.data.PlaylistWithSongs
import com.vm.soundra.logic.Mp3DownloadViewModel
import com.vm.soundra.logic.PreviewState
import com.vm.soundra.model.search.SearchResult
import com.vm.soundra.ui.components.PlaylistCollageCover
import com.vm.soundra.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    onPlayAll: (List<SearchResult>) -> Unit,
    onShuffle: (List<SearchResult>) -> Unit,
    onPlaySong: (SearchResult, List<SearchResult>) -> Unit,
    onChangeCover: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: PlaylistsViewModel = viewModel(factory = PlaylistsViewModel.Factory(context))
    val mp3DownloadViewModel: Mp3DownloadViewModel = viewModel(factory = Mp3DownloadViewModel.Factory(context))
    
    val uiState by viewModel.detailState.collectAsState()
    val currentTrackId by mp3DownloadViewModel.currentTrackId.collectAsState()
    val previewTrackId by mp3DownloadViewModel.previewTrackId.collectAsState()
    
    val previewState by mp3DownloadViewModel.previewState.collectAsState()
    val previewOnlyState by mp3DownloadViewModel.previewOnlyState.collectAsState()

    var songToAddToOther by remember { mutableStateOf<SearchResult?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    
    // Track file availability
    val availabilityMap = remember { mutableStateMapOf<String, Boolean>() }

    // Ensure detail is loaded when ID changes
    LaunchedEffect(playlistId) {
        Log.d("PLAYLIST_DETAIL", "PlaylistDetailScreen composed with ID: $playlistId")
        if (playlistId > 0) {
            viewModel.loadPlaylistDetail(playlistId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier.background(BrandSoftBlack)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_change_cover)) },
                                onClick = { 
                                    onChangeCover()
                                    showMoreMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Default.Image, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_remove_cover)) },
                                onClick = { 
                                    viewModel.updatePlaylistCover(playlistId, null)
                                    showMoreMenu = false 
                                },
                                leadingIcon = { Icon(Icons.Default.NoPhotography, null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = BrandPureBlack
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(bottom = padding.calculateBottomPadding())) {
            
            when (val state = uiState) {
                is PlaylistDetailState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = BrandCyan
                    )
                }
                is PlaylistDetailState.Success -> {
                    val data = state.data
                    val songs = data.songs
                    val searchResults = remember(songs) { songs.map { it.toSearchResult() } }

                    // Check availability for all songs
                    LaunchedEffect(songs) {
                        songs.forEach { song ->
                            availabilityMap[song.videoId] = mp3DownloadViewModel.isFileAvailable(song.videoId)
                        }
                    }

                    // Background Blur
                    val firstThumbnail = remember(songs) { songs.firstOrNull()?.thumbnail }
                    if (firstThumbnail != null) {
                        AsyncImage(
                            model = firstThumbnail,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().blur(100.dp).alpha(0.2f),
                            contentScale = ContentScale.Crop
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        item {
                            PlaylistHeroHeader(
                                name = data.playlist.name,
                                description = data.playlist.description,
                                count = songs.size,
                                thumbnails = songs.map { it.thumbnail },
                                coverUri = data.playlist.coverUri,
                                createdAt = data.playlist.createdAt,
                                onPlayAll = { if (searchResults.isNotEmpty()) onPlayAll(searchResults) },
                                onShuffle = { if (searchResults.isNotEmpty()) onShuffle(searchResults) },
                                onMore = { onChangeCover() }
                            )
                        }

                        if (songs.isNotEmpty()) {
                            itemsIndexed(songs, key = { _, song -> song.videoId }) { _, song ->
                                val isMainPlaying = currentTrackId == song.videoId && previewState !is PreviewState.Idle
                                val isPreviewPlaying = previewTrackId == song.videoId && previewOnlyState !is PreviewState.Idle
                                val isCurrentlyPlaying = isMainPlaying || isPreviewPlaying
                                
                                val isAvailable = availabilityMap[song.videoId] ?: true

                                SongItem(
                                    song = song,
                                    isPlaying = isCurrentlyPlaying,
                                    isAvailable = isAvailable,
                                    onClick = { 
                                        if (isAvailable) {
                                            if (isCurrentlyPlaying) {
                                                mp3DownloadViewModel.showFullScreenPlayer()
                                            } else {
                                                onPlaySong(song.toSearchResult(), searchResults) 
                                            }
                                        }
                                    },
                                    onRemove = { viewModel.removeSongFromPlaylist(playlistId, song.videoId) },
                                    onAddToOther = { songToAddToOther = song.toSearchResult() }
                                )
                            }
                        } else {
                            item { EmptyPlaylistSongsView() }
                        }
                    }
                }
                is PlaylistDetailState.NotFound -> {
                    ErrorStateView(
                        message = stringResource(R.string.no_results),
                        onBack = onBack
                    )
                }
                is PlaylistDetailState.Error -> {
                    ErrorStateView(
                        message = state.message,
                        onBack = onBack
                    )
                }
            }
        }
    }

    if (songToAddToOther != null) {
        AddToPlaylistDialog(
            song = songToAddToOther!!,
            onDismiss = { songToAddToOther = null }
        )
    }
}

@Composable
private fun PlaylistHeroHeader(
    name: String,
    description: String?,
    count: Int,
    thumbnails: List<String>,
    coverUri: String?,
    @Suppress("UNUSED_PARAMETER") createdAt: Long,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onMore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Large Cover (Collage or Custom)
        if (coverUri != null) {
            Surface(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .clickable(onClick = onMore),
                color = Color.White.copy(alpha = 0.05f),
                shadowElevation = 8.dp
            ) {
                AsyncImage(
                    model = coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            PlaylistCollageCover(
                thumbnails = thumbnails,
                size = 240.dp,
                modifier = Modifier.clickable(onClick = onMore)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text(
                text = name,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Text(
                text = stringResource(R.string.songs_count, count),
                style = MaterialTheme.typography.labelMedium,
                color = BrandCyan,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play Button
            Button(
                onClick = onPlayAll,
                modifier = Modifier
                    .height(56.dp)
                    .weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = BrandCyan, contentColor = BrandPureBlack),
                shape = RoundedCornerShape(28.dp),
                enabled = count > 0
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_play_all), fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.width(16.dp))

            // Shuffle Button
            Surface(
                onClick = onShuffle,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                enabled = count > 0
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, tint = if (count > 0) BrandCyan else Color.White.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongItem(
    song: PlaylistSongEntity,
    isPlaying: Boolean,
    isAvailable: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onAddToOther: () -> Unit
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .alpha(if (isAvailable) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            AsyncImage(
                model = song.thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                alpha = if (isPlaying) 0.5f else 1f
            )
            if (isPlaying) {
                Icon(
                    Icons.Rounded.Equalizer,
                    contentDescription = null,
                    tint = BrandCyan,
                    modifier = Modifier.align(Alignment.Center).size(24.dp)
                )
            } else if (!isAvailable) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = BrandError,
                    modifier = Modifier.align(Alignment.Center).size(24.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isPlaying) FontWeight.Black else FontWeight.Bold,
                color = if (isPlaying) BrandCyan else if (!isAvailable) Color.White.copy(alpha = 0.5f) else Color.White
            )
            Text(
                text = if (isAvailable) "${song.artist} • ${song.duration}" else stringResource(R.string.err_file_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = if (!isAvailable) BrandError.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f),
                maxLines = 1
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White.copy(alpha = 0.3f))
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(BrandSoftBlack)
            ) {
                if (isAvailable) {
                    DropdownMenuItem(
                        text = { Text(if (isPlaying) "Pause" else "Play") },
                        onClick = { onClick(); showMenu = false },
                        leadingIcon = { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_add_to_playlist)) },
                    onClick = { onAddToOther(); showMenu = false },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_remove_from_playlist)) },
                    onClick = { onRemove(); showMenu = false },
                    leadingIcon = { Icon(Icons.Rounded.PlaylistRemove, null, tint = BrandError) },
                    colors = MenuDefaults.itemColors(textColor = BrandError)
                )
            }
        }
    }
}

@Composable
private fun EmptyPlaylistSongsView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.White.copy(alpha = 0.05f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Playlist is empty",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add downloaded songs to this playlist",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorStateView(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = message, color = Color.White.copy(alpha = 0.4f), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = BrandCyan, contentColor = BrandPureBlack)) {
            Text("Go Back")
        }
    }
}

private fun PlaylistSongEntity.toSearchResult(): SearchResult {
    return SearchResult(
        id = videoId,
        title = title,
        uploaderName = artist,
        duration = duration,
        thumbnailUrl = thumbnail,
        url = url
    )
}
