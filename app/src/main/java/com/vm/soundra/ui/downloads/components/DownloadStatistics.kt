package com.vm.soundra.ui.downloads.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.soundra.R
import java.util.Locale

@Composable
fun DownloadStatistics(
    totalSize: Long,
    completedCount: Int,
    downloadingCount: Int,
    queuedCount: Int,
    pausedCount: Int
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.stats_storage),
                value = formatBytes(totalSize),
                icon = Icons.Default.Storage,
                color = Color(0xFF007AFF)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.stats_completed),
                value = completedCount.toString(),
                icon = Icons.Default.CheckCircle,
                color = Color(0xFF34C759)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.stats_active),
                value = downloadingCount.toString(),
                icon = Icons.Default.Download,
                color = Color(0xFFFF9500)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.stats_queued),
                value = queuedCount.toString(),
                icon = Icons.Default.Schedule,
                color = Color(0xFF8E8E93)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.stats_paused),
                value = pausedCount.toString(),
                icon = Icons.Default.Pause,
                color = Color(0xFFFFCC00)
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1024) {
        String.format(Locale.US, "%.2f GB", mb / 1024)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}
