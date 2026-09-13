package com.vm.soundra.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.soundra.R
import com.vm.soundra.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        delay(800)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandPureBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scale.value)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_brand_logo),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = Color.Unspecified
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Soundra",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black
            )
            
            Text(
                text = "AUTHENTIC MUSIC EXPERIENCE",
                style = MaterialTheme.typography.labelSmall,
                color = BrandCyan,
                letterSpacing = 4.sp
            )
        }
    }
}
