package com.vusal.soundra.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vusal.soundra.R
import com.vusal.soundra.logic.AppLanguage
import com.vusal.soundra.logic.LanguageManager
import com.vusal.soundra.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") themeMode: ThemeMode,
    @Suppress("UNUSED_PARAMETER") onToggleTheme: () -> Unit,
    onAboutClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val languageManager = remember { LanguageManager(context) }
    val currentLanguage by languageManager.selectedLanguage.collectAsState(initial = AppLanguage.AZERBAIJANI)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(24.dp)
            )
        }

        // Language Section
        item {
            SettingsTitle(stringResource(R.string.settings_section_language))
            AppLanguage.entries.forEach { lang ->
                LanguageSelectionRow(
                    language = lang,
                    isSelected = currentLanguage == lang,
                    onClick = { scope.launch { languageManager.setLanguage(lang) } }
                )
            }
        }

        // Downloads Section (REMOVED)

        // About Section
        item {
            Spacer(modifier = Modifier.height(32.dp))
            SettingsTitle(stringResource(R.string.settings_section_about))
            SettingsActionRow(
                title = stringResource(R.string.settings_version),
                value = "2.5.0 Premium",
                icon = Icons.Rounded.Info,
                onClick = onAboutClick
            )
            SettingsActionRow(
                title = stringResource(R.string.settings_developer),
                value = "Vusal Memmedzade",
                icon = Icons.Rounded.Favorite
            )
        }
    }
}

@Composable
private fun SettingsTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.3f),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun LanguageSelectionRow(language: AppLanguage, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val flag = when (language) {
            AppLanguage.AZERBAIJANI -> "🇦🇿"
            AppLanguage.TURKISH -> "🇹🇷"
            AppLanguage.RUSSIAN -> "🇷🇺"
            else -> "🇬🇧"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = flag, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                text = stringResource(language.labelResId),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) BrandCyan else BrandWhite
            )
        }
        if (isSelected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = BrandCyan, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SettingsActionRow(title: String, value: String, icon: ImageVector, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.05f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.4f))
        }
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.2f))
        }
    }
}
