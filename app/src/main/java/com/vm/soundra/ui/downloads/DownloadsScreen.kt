package com.vm.soundra.ui.downloads

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.vm.soundra.data.DownloadStatus
import com.vm.soundra.data.DownloadTaskEntity
import com.vm.soundra.logic.Mp3DownloadViewModel
import com.vm.soundra.model.search.SearchResult
import com.vm.soundra.ui.playlists.AddToPlaylistDialog
import com.vm.soundra.ui.theme.*

@Composable
fun DownloadsScreen(
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: DownloadsViewModel = viewModel(factory = DownloadsViewModel.Factory(context))
    val mp3DownloadViewModel: Mp3DownloadViewModel = viewModel(factory = Mp3DownloadViewModel.Factory(context))
    val uiState by viewModel.uiState.collectAsState()
    
    val allTasks = uiState.tasks
    val activeTasks = remember(allTasks) { 
        allTasks.filter { it.state in listOf(DownloadStatus.DOWNLOADING, DownloadStatus.PREPARING, DownloadStatus.CONVERTING, DownloadStatus.WRITING_METADATA) } 
    }
    val queuedTasks = remember(allTasks) { allTasks.filter { it.state == DownloadStatus.QUEUED } }
    val libraryTasks = remember(allTasks) { allTasks.filter { it.state == DownloadStatus.COMPLETED || it.state == DownloadStatus.PAUSED || it.state == DownloadStatus.FAILED } }

    var showDeleteConfirm by remember { mutableStateOf<DownloadTaskEntity?>(null) }
    var songToAddToPlaylist by remember { mutableStateOf<SearchResult?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // 1. Title
        item {
            Text(
                text = stringResource(R.string.downloads_title),
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(24.dp)
            )
        }

        // 2. Dashboard
        item {
            DownloadsDashboardGrid(
                active = uiState.downloadingCount,
                queue = uiState.queuedCount,
                completed = uiState.completedCount
            )
        }

        // 3. Active Section
        if (activeTasks.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.downloads_section_active)) }
            items(activeTasks, key = { it.id }) { task ->
                ActiveTaskItem(
                    task = task,
                    onPause = { viewModel.pauseTask(task.id) },
                    onCancel = { viewModel.cancelTask(task.id) }
                )
            }
        }

        // 4. Queue Section
        if (queuedTasks.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.downloads_section_queue)) }
            items(queuedTasks.withIndex().toList(), key = { it.value.id }) { indexedTask ->
                QueueTaskRow(indexedTask.value, indexedTask.index + 1) { 
                    viewModel.cancelTask(indexedTask.value.id) 
                }
            }
        }

        // 5. Library
        if (libraryTasks.isNotEmpty()) {
            item { SectionHeader(stringHeader = stringResource(R.string.downloads_section_completed)) }
            items(libraryTasks, key = { it.id }) { task ->
                LibraryItem(
                    task = task,
                    onAction = { action ->
                        when (action) {
                            LibraryAction.OPEN -> {
                                if (task.state == DownloadStatus.COMPLETED) {
                                    val completedOnly = libraryTasks.filter { it.state == DownloadStatus.COMPLETED }
                                    mp3DownloadViewModel.playTask(task, completedOnly)
                                }
                            }
                            LibraryAction.SHARE -> {
                                task.localFilePath?.let { uri ->
                                    try {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "audio/mpeg"
                                            putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, task.title))
                                    } catch (e: Exception) {
                                        android.util.Log.e("Downloads", "Share failed", e)
                                    }
                                }
                            }
                            LibraryAction.SHOW_LOCATION -> {
                                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                    setDataAndType(Uri.parse("content://media/external/audio/media"), "audio/*")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                            }
                            LibraryAction.ADD_TO_PLAYLIST -> {
                                songToAddToPlaylist = task.toSearchResult()
                            }
                            LibraryAction.REMOVE_FROM_LIST -> {
                                viewModel.deleteTask(task.id, deletePhysicalFile = false)
                            }
                            LibraryAction.DELETE_FILE -> {
                                showDeleteConfirm = task
                            }
                            LibraryAction.RESUME -> {
                                viewModel.resumeTask(task.id)
                            }
                            LibraryAction.RETRY -> {
                                viewModel.retryTask(task.id)
                            }
                        }
                    }
                )
            }
        }
        
        if (allTasks.isEmpty()) {
            item { EmptyLibraryView() }
        }
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.dialog_delete_file_title)) },
            text = { Text(stringResource(R.string.dialog_delete_file_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm?.let { viewModel.deleteTask(it.id, deletePhysicalFile = true) }
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = BrandError)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
            containerColor = BrandSoftBlack,
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.7f)
        )
    }

    if (songToAddToPlaylist != null) {
        AddToPlaylistDialog(
            song = songToAddToPlaylist!!,
            onDismiss = { songToAddToPlaylist = null }
        )
    }
}

enum class LibraryAction {
    OPEN, SHARE, SHOW_LOCATION, REMOVE_FROM_LIST, DELETE_FILE, RESUME, RETRY, ADD_TO_PLAYLIST
}

@Composable
private fun DownloadsDashboardGrid(active: Int, queue: Int, completed: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatPanel(
            label = stringResource(R.string.downloads_stat_active),
            value = active.toString(),
            color = BrandCyan,
            modifier = Modifier.weight(1f)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatPanelSmall(stringResource(R.string.downloads_stat_queued), queue.toString(), BrandViolet)
            StatPanelSmall(stringResource(R.string.downloads_stat_completed), completed.toString(), Color.White)
        }
    }
}

@Composable
private fun StatPanel(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.Bottom) {
            Text(text = value, style = MaterialTheme.typography.displayMedium, color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun StatPanelSmall(label: String, value: String, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.03f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
            Text(text = value, style = MaterialTheme.typography.bodyLarge, color = color)
        }
    }
}

@Composable
private fun SectionHeader(stringHeader: String) {
    Text(
        text = stringHeader.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
        color = Color.White.copy(alpha = 0.2f),
        letterSpacing = 2.sp
    )
}

@Composable
private fun ActiveTaskItem(task: DownloadTaskEntity, onPause: () -> Unit, onCancel: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.05f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))) {
                    AsyncImage(model = task.thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    CircularProgressIndicator(
                        progress = { task.progress / 100f },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 3.dp,
                        color = BrandCyan,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    
                    val statusText = when(task.state) {
                        DownloadStatus.PREPARING -> stringResource(R.string.state_preparing)
                        DownloadStatus.CONVERTING -> stringResource(R.string.state_converting)
                        DownloadStatus.WRITING_METADATA -> stringResource(R.string.state_writing)
                        else -> task.speed
                    }
                    Text(text = "${task.progress}% • $statusText", style = MaterialTheme.typography.labelSmall, color = BrandCyan)
                }
                IconButton(onClick = onPause) { Icon(Icons.Rounded.Pause, contentDescription = null, tint = Color.White) }
                IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.3f)) }
            }
            
            if (task.state == DownloadStatus.DOWNLOADING) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val downloaded = android.text.format.Formatter.formatFileSize(LocalContext.current, task.downloadedBytes.toLong())
                    val total = android.text.format.Formatter.formatFileSize(LocalContext.current, task.totalBytes.toLong())
                    Text(text = "$downloaded / $total", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                    if (task.eta.isNotEmpty()) {
                        Text(text = "ETA: ${task.eta}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueTaskRow(task: DownloadTaskEntity, pos: Int, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "#$pos", style = MaterialTheme.typography.labelSmall, color = BrandViolet, modifier = Modifier.width(32.dp))
        Text(text = task.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(18.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryItem(task: DownloadTaskEntity, onAction: (LibraryAction) -> Unit) {
    var showMenu by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { 
                    if (task.state == DownloadStatus.COMPLETED) onAction(LibraryAction.OPEN)
                    else if (task.state == DownloadStatus.PAUSED) onAction(LibraryAction.RESUME)
                    else if (task.state == DownloadStatus.FAILED) onAction(LibraryAction.RETRY)
                },
                onLongClick = { showMenu = true }
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = task.thumbnail, contentDescription = null, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            
            val subtitle = when(task.state) {
                DownloadStatus.PAUSED -> stringResource(R.string.state_paused)
                DownloadStatus.FAILED -> stringResource(R.string.state_failed)
                else -> task.artist
            }
            val subtitleColor = if (task.state == DownloadStatus.FAILED) BrandError else Color.White.copy(alpha = 0.4f)
            
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = subtitleColor)
        }
        
        Box {
            IconButton(onClick = { showMenu = true }) { 
                Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = Color.White.copy(alpha = 0.2f)) 
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(BrandSoftBlack)
            ) {
                if (task.state == DownloadStatus.COMPLETED) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_open)) },
                        onClick = { onAction(LibraryAction.OPEN); showMenu = false },
                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_share)) },
                        onClick = { onAction(LibraryAction.SHARE); showMenu = false },
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_show_location)) },
                        onClick = { onAction(LibraryAction.SHOW_LOCATION); showMenu = false },
                        leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_add_to_playlist)) },
                        onClick = { onAction(LibraryAction.ADD_TO_PLAYLIST); showMenu = false },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.1f))
                }
                
                if (task.state == DownloadStatus.PAUSED) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.btn_resume)) },
                        onClick = { onAction(LibraryAction.RESUME); showMenu = false },
                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) }
                    )
                }

                if (task.state == DownloadStatus.FAILED) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.btn_retry)) },
                        onClick = { onAction(LibraryAction.RETRY); showMenu = false },
                        leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) }
                    )
                }

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_remove_from_list)) },
                    onClick = { onAction(LibraryAction.REMOVE_FROM_LIST); showMenu = false },
                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) }
                )
                
                if (task.state == DownloadStatus.COMPLETED) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_delete_file), color = BrandError) },
                        onClick = { onAction(LibraryAction.DELETE_FILE); showMenu = false },
                        leadingIcon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = BrandError) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryView() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.LibraryMusic, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.White.copy(alpha = 0.05f))
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.downloads_empty_title), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.2f))
    }
}

private fun DownloadTaskEntity.toSearchResult(): SearchResult {
    return SearchResult(
        id = videoId,
        title = title,
        uploaderName = artist,
        duration = "",
        thumbnailUrl = thumbnail,
        url = url
    )
}
