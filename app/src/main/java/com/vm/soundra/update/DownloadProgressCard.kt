package com.vm.soundra.update

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.soundra.R
import java.io.File
import java.util.Locale

@Composable
fun DownloadProgressCard(
    state: DownloadState,
    onInstallClick: (File) -> Unit,
    modifier: Modifier = Modifier,
    titleOverride: String? = null,
    subtitleOverride: String? = null,
    showAction: Boolean = true
) {
    Log.d("DOWNLOAD_UI", "Current State = $state")
    
    AnimatedVisibility(
        visible = state !is DownloadState.Idle,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (state) {
                            is DownloadState.Completed -> Icons.Default.CheckCircle
                            is DownloadState.Failed -> Icons.Default.Error
                            else -> Icons.Default.Download
                        },
                        contentDescription = null,
                        tint = when (state) {
                            is DownloadState.Completed -> Color(0xFF34C759)
                            is DownloadState.Failed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(28.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = titleOverride ?: when (state) {
                                is DownloadState.Preparing -> stringResource(R.string.status_preparing_download)
                                is DownloadState.Downloading -> stringResource(R.string.update_downloading)
                                is DownloadState.Completed -> stringResource(R.string.status_download_completed)
                                is DownloadState.Failed -> stringResource(R.string.state_failed)
                                else -> ""
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        if (state is DownloadState.Completed || subtitleOverride != null) {
                            Text(
                                text = subtitleOverride ?: stringResource(R.string.update_ready),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    if (state is DownloadState.Downloading) {
                        Text(
                            text = "${state.progress}%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (state is DownloadState.Downloading || state is DownloadState.Preparing) {
                    val progressValue = if (state is DownloadState.Downloading) state.progress / 100f else 0f
                    LinearProgressIndicator(
                        progress = { progressValue },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    
                    if (state is DownloadState.Downloading && state.totalBytes > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.speed.isNotEmpty()) {
                                Text(
                                    text = state.speed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (state.eta.isNotEmpty() && state.eta != "--:--") {
                            Text(
                                text = stringResource(R.string.eta_label, state.eta),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                if (state is DownloadState.Completed && showAction) {
                    Log.d("DOWNLOAD_UI", "Showing Install Button")
                    Button(
                        onClick = { onInstallClick(state.file) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                    ) {
                        Text(stringResource(R.string.btn_install), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                if (state is DownloadState.Failed) {
                    Text(
                        text = state.message,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return String.format(Locale.US, "%.1f MB", mb)
}
