package com.vusal.soundra.ui.playlists

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vusal.soundra.R
import com.vusal.soundra.data.PlaylistEntity
import com.vusal.soundra.model.search.SearchResult
import com.vusal.soundra.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistDialog(
    song: SearchResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: PlaylistsViewModel = viewModel(factory = PlaylistsViewModel.Factory(context))
    val playlists by viewModel.allPlaylists.collectAsState()

    var showCreateSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            if (event is PlaylistsViewModel.PlaylistUiEvent.ShowSnackbar) {
                Toast.makeText(context, context.getString(event.messageResId), Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BrandSoftBlack,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.menu_add_to_playlist),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(24.dp),
                color = Color.White
            )

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCreateSheet = true }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(BrandCyan.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = BrandCyan)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(stringResource(R.string.btn_create_playlist), color = BrandCyan, fontWeight = FontWeight.Bold)
                    }
                }

                items(playlists) { playlist ->
                    PlaylistSelectionRow(
                        playlist = playlist,
                        onClick = {
                            viewModel.addSongToPlaylist(
                                playlistId = playlist.id,
                                videoId = song.id,
                                title = song.title,
                                artist = song.uploaderName,
                                thumbnail = song.thumbnailUrl,
                                url = song.url,
                                duration = song.duration
                            )
                        }
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
private fun PlaylistSelectionRow(
    playlist: PlaylistEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = Color.White.copy(alpha = 0.05f)
        ) {
            if (playlist.coverUri != null) {
                AsyncImage(
                    model = playlist.coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(text = playlist.name, style = MaterialTheme.typography.bodyLarge)
    }
}
