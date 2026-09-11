package com.vusal.soundra.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vusal.soundra.R
import com.vusal.soundra.logic.PlaybackRepeatMode
import com.vusal.soundra.logic.PreviewState
import com.vusal.soundra.model.search.SearchResult
import com.vusal.soundra.ui.theme.*
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(
    currentTrack: SearchResult,
    previewState: PreviewState,
    playbackPosition: Long,
    playbackDuration: Long,
    onTogglePlayPause: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = BrandSoftBlack.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        tonalElevation = 8.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = currentTrack.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentTrack.uploaderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                }

                IconButton(onClick = onTogglePlayPause) {
                    if (previewState is PreviewState.Buffering) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = BrandCyan)
                    } else {
                        Icon(
                            imageVector = if (previewState is PreviewState.Playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            }
            
            // Thin progress bar at the bottom
            val isBuffering = previewState is PreviewState.Buffering
            val progress = if (playbackDuration > 0 && !isBuffering) playbackPosition.toFloat() / playbackDuration else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = if (isBuffering) BrandCyan.copy(alpha = 0.5f) else BrandCyan,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayer(
    currentTrack: SearchResult,
    previewState: PreviewState,
    playbackPosition: Long,
    playbackDuration: Long,
    shuffleEnabled: Boolean,
    repeatMode: PlaybackRepeatMode,
    currentQueue: List<SearchResult>,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    var showQueueSheet by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BrandPureBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .statusBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(32.dp))
                }
                Text(
                    text = stringResource(R.string.player_now_playing).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
                IconButton(onClick = { /* More options */ }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Artwork
            Surface(
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp)),
                color = BrandSoftBlack,
                shadowElevation = 16.dp
            ) {
                AsyncImage(
                    model = currentTrack.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(56.dp))

            // Info
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = currentTrack.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentTrack.uploaderName,
                    style = MaterialTheme.typography.titleLarge,
                    color = BrandCyan,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Controls - Progress
            val isBuffering = previewState is PreviewState.Buffering
            Column {
                Slider(
                    value = if (isBuffering) 0f else playbackPosition.toFloat(),
                    onValueChange = { if (!isBuffering) onSeek(it.toLong()) },
                    valueRange = 0f..maxOf(playbackDuration.toFloat(), 1f),
                    colors = SliderDefaults.colors(
                        thumbColor = if (isBuffering) Color.Transparent else Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isBuffering) "00:00" else formatDuration(playbackPosition / 1000),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                    Text(
                        text = if (isBuffering) "--:--" else formatDuration(playbackDuration / 1000),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Controls - Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = null,
                        tint = if (shuffleEnabled) BrandCyan else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = onPrevious) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = null, modifier = Modifier.size(48.dp))
                }

                FloatingActionButton(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    modifier = Modifier.size(72.dp)
                ) {
                    if (previewState is PreviewState.Buffering) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(32.dp))
                    } else {
                        Icon(
                            imageVector = if (previewState is PreviewState.Playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(48.dp))
                }

                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector = when (repeatMode) {
                            PlaybackRepeatMode.ONE -> Icons.Rounded.RepeatOne
                            else -> Icons.Rounded.Repeat
                        },
                        contentDescription = null,
                        tint = if (repeatMode != PlaybackRepeatMode.OFF) BrandCyan else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Queue Button
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { showQueueSheet = true }) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                }
            }
            
            Spacer(modifier = Modifier.height(64.dp))
        }
    }
    
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = BrandSoftBlack,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
        ) {
            QueueSheetContent(
                queue = currentQueue,
                currentTrack = currentTrack,
                onRemove = onRemoveFromQueue,
                onDismiss = { showQueueSheet = false }
            )
        }
    }
}

@Composable
private fun QueueSheetContent(
    queue: List<SearchResult>,
    currentTrack: SearchResult,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.fillMaxHeight(0.7f)) {
        Text(
            text = "Queue",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(24.dp),
            fontWeight = FontWeight.Bold
        )
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandCyan,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                QueueItem(currentTrack, isPlaying = true, onRemove = {})
            }
            
            item {
                Text(
                    text = "Next In Queue",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }
            
            val upNext = queue.filter { it.id != currentTrack.id }
            itemsIndexed(upNext) { index, track ->
                QueueItem(track, isPlaying = false, onRemove = { onRemove(index) })
            }
        }
    }
}

@Composable
private fun QueueItem(track: SearchResult, isPlaying: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            alpha = if (isPlaying) 1f else 0.8f
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) BrandCyan else Color.White
            )
            Text(
                text = track.uploaderName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.4f)
            )
        }
        if (!isPlaying) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = null, tint = Color.White.copy(alpha = 0.2f))
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
