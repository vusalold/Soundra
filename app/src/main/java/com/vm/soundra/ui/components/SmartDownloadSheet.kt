package com.vm.soundra.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vm.soundra.R
import com.vm.soundra.logic.MetadataState
import com.vm.soundra.logic.PreviewState
import com.vm.soundra.logic.VideoMetadata
import com.vm.soundra.logic.AudioStreamMetadata
import com.vm.soundra.logic.PartialVideoMetadata
import com.vm.soundra.logic.PlaybackRepeatMode
import com.vm.soundra.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartDownloadSheet(
    metadataState: MetadataState,
    onRetry: () -> Unit,
    selectedBitrate: String,
    onBitrateChange: (String) -> Unit,
    estimateSize: (Long, Int, Long) -> String,
    previewState: PreviewState,
    playbackPosition: Long,
    playbackDuration: Long,
    bufferPosition: Long,
    onTogglePreview: (String) -> Unit,
    onSeek: (Long) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    hasNext: Boolean,
    hasPrevious: Boolean,
    shuffleEnabled: Boolean,
    onToggleShuffle: () -> Unit,
    repeatMode: PlaybackRepeatMode,
    onToggleRepeat: () -> Unit,
    onDownload: (VideoMetadata) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BrandPureBlack,
        dragHandle = null
    ) {
        Box(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            // Immersive Background
            val currentThumbnail = remember(metadataState) {
                when(metadataState) {
                    is MetadataState.Success -> metadataState.metadata.thumbnailUrl
                    is MetadataState.Loading -> metadataState.partial?.thumbnailUrl
                    else -> null
                }
            }

            if (currentThumbnail != null) {
                AsyncImage(
                    model = currentThumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(100.dp)
                        .scale(1.5f),
                    contentScale = ContentScale.Crop,
                    alpha = 0.2f
                )
            }

            // Entrance slide animation for sheet content
            val entranceOffset = remember { androidx.compose.animation.core.Animatable(220f) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(80)
                try {
                    entranceOffset.animateTo(0f, animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing))
                } catch (_: Exception) { }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = entranceOffset.value.dp)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(48.dp)) // Spacer to center drag indicator
                    
                    // Drag Indicator + animated chevron hint
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(40.dp, 4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val chevronTransition = rememberInfiniteTransition()
                        val chevronOffset by chevronTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 8f,
                            animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse)
                        )
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(20.dp)
                                .offset(y = chevronOffset.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(48.dp))
                }

                Spacer(modifier = Modifier.height(48.dp))

                AnimatedContent(
                    targetState = metadataState,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "content"
                ) { state ->
                    when (state) {
                        is MetadataState.Loading -> LoadingViewRebuild(state.partial)
                        is MetadataState.Success -> PremiumSheetContent(
                            metadata = state.metadata,
                            selectedBitrate = selectedBitrate,
                            onBitrateChange = onBitrateChange,
                            estimateSize = estimateSize,
                            previewState = previewState,
                            playbackPosition = playbackPosition,
                            playbackDuration = playbackDuration,
                            onTogglePreview = { onTogglePreview(state.metadata.videoUrl) },
                            onSeek = onSeek,
                            onPlayNext = onPlayNext,
                            onPlayPrevious = onPlayPrevious,
                            hasNext = hasNext,
                            hasPrevious = hasPrevious,
                            shuffleEnabled = shuffleEnabled,
                            onToggleShuffle = onToggleShuffle,
                            repeatMode = repeatMode,
                            onToggleRepeat = onToggleRepeat,
                            onDownload = { onDownload(state.metadata) }
                        )
                        is MetadataState.Error -> ErrorViewRebuild(state.messageResId, onRetry)
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumSheetContent(
    metadata: VideoMetadata,
    selectedBitrate: String,
    onBitrateChange: (String) -> Unit,
    estimateSize: (Long, Int, Long) -> String,
    previewState: PreviewState,
    playbackPosition: Long,
    playbackDuration: Long,
    onTogglePreview: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    hasNext: Boolean,
    hasPrevious: Boolean,
    shuffleEnabled: Boolean,
    onToggleShuffle: () -> Unit,
    repeatMode: PlaybackRepeatMode,
    onToggleRepeat: () -> Unit,
    onDownload: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 1. Hero Artwork
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(40.dp))
                .padding(12.dp)
        ) {
            AsyncImage(
                model = metadata.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(32.dp)),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // 2. Info Typography
        Text(
            text = metadata.title,
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = metadata.artist,
            style = MaterialTheme.typography.titleLarge,
            color = BrandCyan,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(56.dp))

        // 3. Immersive Player
        Column {
            Slider(
                value = playbackPosition.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..maxOf(playbackDuration.toFloat(), 1f),
                colors = SliderDefaults.colors(
                    thumbColor = BrandWhite,
                    activeTrackColor = BrandWhite,
                    inactiveTrackColor = BrandWhite.copy(alpha = 0.1f)
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(playbackPosition / 1000), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                Text(formatDuration(playbackDuration / 1000), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = null,
                        tint = if (shuffleEnabled) BrandCyan else Color.White.copy(alpha = 0.4f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPlayPrevious,
                        enabled = hasPrevious || repeatMode == PlaybackRepeatMode.ALL,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = if (hasPrevious || repeatMode == PlaybackRepeatMode.ALL) Color.White else Color.White.copy(alpha = 0.2f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = if (pulsePreviewPlaying(previewState)) 1.1f else 1f,
                        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "pulse"
                    )

                    FloatingActionButton(
                        onClick = onTogglePreview,
                        shape = CircleShape,
                        containerColor = BrandWhite,
                        contentColor = BrandPureBlack,
                        modifier = Modifier.size(80.dp).scale(pulseScale)
                    ) {
                        if (previewState is PreviewState.Buffering) {
                            CircularProgressIndicator(color = BrandPureBlack, modifier = Modifier.size(32.dp))
                        } else {
                            Icon(
                                imageVector = if (previewState is PreviewState.Playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))
                    
                    IconButton(
                        onClick = onPlayNext,
                        enabled = hasNext || repeatMode == PlaybackRepeatMode.ALL,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = if (hasNext || repeatMode == PlaybackRepeatMode.ALL) Color.White else Color.White.copy(alpha = 0.2f)
                        )
                    }
                }

                // Repeat Button
                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector = when (repeatMode) {
                            PlaybackRepeatMode.ONE -> Icons.Rounded.RepeatOne
                            else -> Icons.Rounded.Repeat
                        },
                        contentDescription = null,
                        tint = if (repeatMode != PlaybackRepeatMode.OFF) BrandCyan else Color.White.copy(alpha = 0.4f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // 4. Premium Dynamic Quality Selector
        DynamicQualitySelector(
            streams = metadata.audioStreams,
            selectedBitrate = selectedBitrate,
            onBitrateChange = onBitrateChange
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 5. Hero Download Button
        Button(
            onClick = onDownload,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandCyan,
                contentColor = BrandPureBlack
            )
        ) {
            Text(
                text = stringResource(R.string.btn_download_mp3_large),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Estimate size based on selected bitrate
        val currentBitrateVal = try { selectedBitrate.filter { it.isDigit() }.toInt() } catch(e: Exception) { 128 }
        val size = estimateSize(metadata.durationSeconds, currentBitrateVal, -1)
        Text(
            text = stringResource(R.string.estimated_size, size),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.3f)
        )
        
        Spacer(modifier = Modifier.height(64.dp))
    }
}

@Composable
private fun DynamicQualitySelector(
    streams: List<AudioStreamMetadata>,
    selectedBitrate: String,
    onBitrateChange: (String) -> Unit
) {
    val sortedStreams = remember(streams) {
        streams.distinctBy { it.bitrateKbps }.sortedBy { it.bitrateKbps }
    }
    
    if (sortedStreams.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(28.dp))
            .padding(8.dp)
    ) {
        Text(
            text = stringResource(R.string.quality_label).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            letterSpacing = 2.sp
        )
        
        sortedStreams.forEach { stream ->
            val bitrateStr = "${stream.bitrateKbps}k"
            val isSelected = selectedBitrate == bitrateStr
            val isBest = stream == sortedStreams.last()

            QualityRow(
                bitrate = stream.bitrateKbps,
                isSelected = isSelected,
                isBest = isBest,
                onClick = { onBitrateChange(bitrateStr) }
            )
        }
    }
}

@Composable
private fun QualityRow(
    bitrate: Int,
    isSelected: Boolean,
    isBest: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) BrandCyan else Color.Transparent,
        label = "bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) BrandPureBlack else BrandWhite,
        label = "content"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = if (!isSelected) BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isSelected) BrandPureBlack else BrandCyan, CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.bitrate_kbps, bitrate),
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (isBest) {
                Text(
                    text = stringResource(R.string.quality_best_available).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) BrandPureBlack.copy(alpha = 0.6f) else BrandCyan,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun LoadingViewRebuild(partial: PartialVideoMetadata? = null) {
    Column(
        modifier = Modifier.padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (partial != null) {
            // Show partial info immediately
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(40.dp))
                    .padding(12.dp)
            ) {
                AsyncImage(
                    model = partial.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(32.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = partial.title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = partial.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = BrandCyan,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(48.dp))
        }

        CircularProgressIndicator(color = BrandCyan, modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.status_analyzing_dots), style = MaterialTheme.typography.titleMedium, color = BrandCyan)
        
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
private fun ErrorViewRebuild(resId: Int, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(64.dp), tint = BrandError.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(resId), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = BrandError)) {
            Text(stringResource(R.string.btn_retry))
        }
    }
}

private fun pulsePreviewPlaying(state: PreviewState): Boolean = state is PreviewState.Playing

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
