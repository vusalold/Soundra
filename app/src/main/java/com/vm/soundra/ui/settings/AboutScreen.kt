package com.vm.soundra.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vm.soundra.R
import com.vm.soundra.ui.theme.BrandCyan
import com.vm.soundra.ui.theme.BrandViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Haqqında", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_brand_logo),
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = Color.Unspecified
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Soundra",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "2.5.0 Aesthetic Edition",
                style = MaterialTheme.typography.labelSmall,
                color = BrandCyan,
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "Premium musiqi yükləmə təcrübəsi üçün yaradılmış minimalist və güclü tətbiq. YouTube-dan ən yüksək keyfiyyətdə MP3-ləri asanlıqla əldə edin.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(64.dp))
            
            InfoCard(label = "Yaradıcı", value = "Vusal Memmedzade")
            Spacer(modifier = Modifier.height(16.dp))
            InfoCard(label = "Məqsəd", value = "Keyfiyyətli Musiqi")
            Spacer(modifier = Modifier.height(16.dp))
            InfoCard(label = "İl", value = "2026")
        }
    }
}

@Composable
private fun InfoCard(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.03f), CircleShape)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
