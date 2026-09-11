package com.vusal.soundra.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vusal.soundra.ui.Screen
import com.vusal.soundra.ui.theme.BrandCyan
import com.vusal.soundra.ui.theme.BrandViolet

@Composable
fun PremiumBottomNavigation(
    currentScreen: String,
    onScreenSelected: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .height(64.dp)
                .fillMaxWidth(),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.8f),
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavAction(
                    icon = Icons.Outlined.Home,
                    selectedIcon = Icons.Default.Home,
                    isSelected = currentScreen == Screen.Home.route,
                    onClick = { onScreenSelected(Screen.Home.route) }
                )
                NavAction(
                    icon = Icons.AutoMirrored.Outlined.PlaylistPlay,
                    selectedIcon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    isSelected = currentScreen == Screen.Playlists.route || currentScreen == Screen.PlaylistDetail.route,
                    onClick = { onScreenSelected(Screen.Playlists.route) }
                )
                NavAction(
                    icon = Icons.Outlined.CloudDownload,
                    selectedIcon = Icons.Default.CloudDownload,
                    isSelected = currentScreen == Screen.Downloads.route,
                    onClick = { onScreenSelected(Screen.Downloads.route) }
                )
                NavAction(
                    icon = Icons.Outlined.Settings,
                    selectedIcon = Icons.Default.Settings,
                    isSelected = currentScreen == Screen.Settings.route,
                    onClick = { onScreenSelected(Screen.Settings.route) }
                )
            }
        }
    }
}

@Composable
private fun NavAction(
    icon: ImageVector,
    selectedIcon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSelected) selectedIcon else icon,
            contentDescription = null,
            tint = if (isSelected) BrandCyan else Color.White.copy(alpha = 0.4f),
            modifier = Modifier
                .size(24.dp)
                .scale(scale)
        )
        
        AnimatedVisibility(
            visible = isSelected,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(listOf(BrandCyan, BrandViolet))
                    )
            )
        }
    }
}
