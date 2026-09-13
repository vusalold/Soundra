package com.vm.soundra.ui.downloads.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vm.soundra.R
import com.vm.soundra.data.DownloadStatus
import com.vm.soundra.data.DownloadTaskEntity
import java.util.*

@Composable
fun DownloadTaskCard(
    task: DownloadTaskEntity,
    onDelete: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_title), fontWeight = FontWeight.Black) },
            text = { Text(stringResource(R.string.dialog_delete_message, task.title)) },
            confirmButton = {
                Button(onClick = { onDelete(true); showDeleteDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.btn_delete_confirm), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .animateContentSize(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 1. Extra Large Modern Thumbnail
                Box(modifier = Modifier.size(120.dp).clip(RoundedCornerShape(24.dp))) {
                    AsyncImage(
                        model = task.thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (task.state == DownloadStatus.COMPLETED) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(28.dp)
                                .background(Color(0xFF34C759), androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                // 2. High-end Metadata
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 24.sp,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = task.artist,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${task.bitrate} • ${formatBytes(task.totalBytes)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    StatusBadgeRebuild(task.state)
                }

                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                
                DownloadTaskMenu(
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    task = task,
                    onPause = onPause,
                    onCancel = onCancel,
                    onResume = onResume,
                    onRetry = onRetry,
                    onOpen = onOpen,
                    onShare = onShare,
                    onRename = onRename,
                    onProperties = onProperties,
                    onDeleteClick = { showDeleteDialog = true }
                )
            }

            // 3. Premium Progress Section
            if (task.state in listOf(DownloadStatus.DOWNLOADING, DownloadStatus.PREPARING, DownloadStatus.CONVERTING, DownloadStatus.WRITING_METADATA)) {
                Spacer(modifier = Modifier.height(20.dp))
                Column {
                    LinearProgressIndicator(
                        progress = { task.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (task.speed.isNotEmpty()) {
                            Text(task.speed, fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else if (task.state == DownloadStatus.COMPLETED) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.btn_listen_now_caps), fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
fun StatusBadgeRebuild(status: DownloadStatus) {
    val color = when (status) {
        DownloadStatus.COMPLETED -> Color(0xFF34C759)
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = status.name,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}

@Composable
fun DownloadTaskMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    task: DownloadTaskEntity,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    onDeleteClick: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        val isActive = task.state in listOf(
            DownloadStatus.DOWNLOADING,
            DownloadStatus.PREPARING,
            DownloadStatus.CONVERTING,
            DownloadStatus.WRITING_METADATA,
            DownloadStatus.RESUMING
        )

        if (isActive) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.btn_pause)) },
                onClick = { onPause(); onDismiss() },
                leadingIcon = { Icon(Icons.Default.Pause, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.btn_cancel)) },
                onClick = { onCancel(); onDismiss() },
                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) }
            )
        }

        when (task.state) {
            DownloadStatus.PAUSED -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.btn_resume)) },
                    onClick = { onResume(); onDismiss() },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                )
            }
            DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.btn_retry)) },
                    onClick = { onRetry(); onDismiss() },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                )
            }
            DownloadStatus.COMPLETED -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.btn_open)) },
                    onClick = { onOpen(); onDismiss() },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.btn_share)) },
                    onClick = { onShare(); onDismiss() },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.btn_rename)) },
                    onClick = { onRename(); onDismiss() },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.btn_properties)) },
                    onClick = { onProperties(); onDismiss() },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }
            DownloadStatus.QUEUED -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.btn_start_now)) },
                    onClick = { onResume(); onDismiss() },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                )
            }
            else -> {}
        }
        
        HorizontalDivider()
        
        DropdownMenuItem(
            text = { Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error) },
            onClick = { onDeleteClick(); onDismiss() },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return String.format(Locale.US, "%.1f MB", mb)
}
