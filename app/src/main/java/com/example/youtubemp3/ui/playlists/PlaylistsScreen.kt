package com.vusal.soundra.ui.playlists

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.vusal.soundra.R
import com.vusal.soundra.data.PlaylistEntity
import com.vusal.soundra.ui.components.PlaylistCollageCover
import com.vusal.soundra.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onPlaylistClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val viewModel: PlaylistsViewModel = viewModel(factory = PlaylistsViewModel.Factory(context))
    val playlists by viewModel.allPlaylists.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    var showCreateSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = BrandCyan,
                contentColor = BrandPureBlack,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.playlists_title),
                        style = MaterialTheme.typography.displayLarge
                    )
                    
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = null, tint = BrandCyan)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(BrandSoftBlack)
                        ) {
                            PlaylistSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { 
                                        Text(
                                            text = getSortLabel(sort),
                                            color = if (sort == sortOrder) BrandCyan else Color.White
                                        ) 
                                    },
                                    onClick = {
                                        viewModel.setSortOrder(sort)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (playlists.isEmpty()) {
                item { EmptyPlaylistsView { showCreateSheet = true } }
            } else {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistItem(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist.id) },
                        onRename = { showRenameDialog = playlist },
                        onDelete = { showDeleteConfirm = playlist }
                    )
                }
            }
        }
    }

    if (showCreateSheet) {
        CreatePlaylistSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { name, desc ->
                viewModel.createPlaylist(name, desc)
                showCreateSheet = false
            }
        )
    }

    if (showRenameDialog != null) {
        val dialogPlaylist = showRenameDialog
        if (dialogPlaylist != null) {
            PlaylistNameDialog(
                title = stringResource(R.string.dialog_rename_playlist_title),
                initialName = dialogPlaylist.name,
                onDismiss = { showRenameDialog = null },
                onConfirm = { newName ->
                    viewModel.renamePlaylist(dialogPlaylist, newName)
                    showRenameDialog = null
                }
            )
        }
    }

    if (showDeleteConfirm != null) {
        val deletePlaylist = showDeleteConfirm
        if (deletePlaylist != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                title = { Text(stringResource(R.string.dialog_delete_playlist_title)) },
                text = { Text(stringResource(R.string.dialog_delete_playlist_msg, deletePlaylist.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deletePlaylist(deletePlaylist)
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
    }
}

@Composable
private fun getSortLabel(sort: PlaylistSort): String {
    return when (sort) {
        PlaylistSort.RECENTLY_ADDED -> stringResource(R.string.sort_recent)
        PlaylistSort.OLDEST -> stringResource(R.string.sort_oldest)
        PlaylistSort.NAME_AZ -> stringResource(R.string.sort_az)
        PlaylistSort.NAME_ZA -> stringResource(R.string.sort_za)
        PlaylistSort.CUSTOM -> "Custom"
    }
}

@Composable
private fun PlaylistItem(
    playlist: PlaylistEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover Image (Fixed logic for list)
        if (playlist.coverUri != null) {
            Surface(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = Color.White.copy(alpha = 0.05f)
            ) {
                AsyncImage(
                    model = playlist.coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // In list, we just show default icon for performance or a simpler collage?
            // User requested collage. Let's use simple icon for now to avoid relation fetch in list if not available
            Surface(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = Color.White.copy(alpha = 0.05f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null, tint = BrandCyan, modifier = Modifier.size(32.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.width(20.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(text = playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (!playlist.description.isNullOrBlank()) {
                Text(
                    text = playlist.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "Playlist",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
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
                DropdownMenuItem(
                    text = { Text("Open") },
                    onClick = { 
                        onClick()
                        showMenu = false 
                    },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.btn_rename)) },
                    onClick = { onRename(); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.btn_delete), color = BrandError) },
                    onClick = { onDelete(); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = BrandError) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePlaylistSheet(
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BrandSoftBlack,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.dialog_create_playlist_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 40) name = it },
                label = { Text("Name") },
                placeholder = { Text("My Awesome Playlist") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandCyan,
                    focusedLabelColor = BrandCyan
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 100) description = it },
                label = { Text("Description (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandCyan,
                    focusedLabelColor = BrandCyan
                )
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { onCreate(name, description.ifBlank { null }) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandCyan, contentColor = BrandPureBlack),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Create", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedLabelColor = BrandCyan
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text(if (initialName.isEmpty()) stringResource(R.string.btn_create) else stringResource(R.string.btn_change))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
        containerColor = BrandSoftBlack,
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Composable
private fun EmptyPlaylistsView(onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 100.dp),
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
            text = stringResource(R.string.no_playlists),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_playlists_msg),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onCreate,
            colors = ButtonDefaults.buttonColors(containerColor = BrandCyan, contentColor = BrandPureBlack),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(stringResource(R.string.btn_create_playlist))
        }
    }
}
