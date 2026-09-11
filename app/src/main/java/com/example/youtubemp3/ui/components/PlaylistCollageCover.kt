package com.vusal.soundra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vusal.soundra.ui.theme.BrandCyan

@Composable
fun PlaylistCollageCover(
    thumbnails: List<String>,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp
) {
    Surface(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.15f)),
        color = Color.White.copy(alpha = 0.05f),
        shadowElevation = 8.dp
    ) {
        when {
            thumbnails.isEmpty() -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = BrandCyan,
                        modifier = Modifier.size(size * 0.5f)
                    )
                }
            }
            thumbnails.size < 4 -> {
                // Show only the first one
                AsyncImage(
                    model = thumbnails[0],
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                // Collage of first 4
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        AsyncImage(model = thumbnails[0], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                        AsyncImage(model = thumbnails[1], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        AsyncImage(model = thumbnails[2], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                        AsyncImage(model = thumbnails[3], contentDescription = null, modifier = Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
    }
}
