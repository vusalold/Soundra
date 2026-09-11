package com.vusal.soundra.ui.search.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vusal.soundra.R
import com.vusal.soundra.model.search.SearchResult
import com.vusal.soundra.update.DownloadState
import java.util.*

@Composable
fun SearchCard(
    result: SearchResult,
    downloadState: DownloadState,
    onDownloadClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.94f else 1f, label = "scale")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(32.dp))
            .clickable { isPressed = !isPressed }
            .animateContentSize(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = null
    ) {
        Column {
            // 1. Premium Large Artwork
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                AsyncImage(
                    model = result.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Glass Duration Overlay
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = result.duration,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }

                // Quick Play/Preview Button
                FloatingActionButton(
                    onClick = { /* In-place preview handled by SmartDownloadSheet */ },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .scale(if (isPressed) 0.8f else 1f),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(32.dp))
                }
            }

            // 2. Modern Info Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.title,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = result.uploaderName,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = formatViews(result.viewCount),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))

                // Modern Download Trigger
                DownloadActionIconRebuild(state = downloadState, onClick = onDownloadClick)
            }
        }
    }
}

@Composable
fun DownloadActionIconRebuild(state: DownloadState, onClick: () -> Unit) {
    val isIdle = state is DownloadState.Idle || state is DownloadState.Failed
    
    Surface(
        modifier = Modifier
            .size(52.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(enabled = isIdle, onClick = onClick),
        color = if (state is DownloadState.Completed) Color(0xFF34C759).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (state) {
                is DownloadState.Downloading -> {
                    CircularProgressIndicator(
                        progress = { state.progress / 100f },
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Text("${state.progress}", fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
                is DownloadState.Completed -> Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF34C759))
                is DownloadState.Preparing -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                else -> Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun formatViews(views: Long): String {
    return when {
        views >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", views / 1_000_000_000.0)
        views >= 1_000_000 -> String.format(Locale.US, "%.1fM", views / 1_000_000.0)
        views >= 1_000 -> String.format(Locale.US, "%.1fK", views / 1_000.0)
        else -> views.toString()
    } + " Görüntüleme"
}
