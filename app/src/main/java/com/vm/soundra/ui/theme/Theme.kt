package com.vm.soundra.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Single Authoritative Premium Identity (Reverted to Dark)
private val PremiumColorScheme = darkColorScheme(
    primary = BrandCyan,
    onPrimary = Color.Black,
    secondary = BrandViolet,
    onSecondary = Color.White,
    background = BrandPureBlack,
    onBackground = BrandWhite,
    surface = BrandDeepBlack,
    onSurface = BrandWhite,
    surfaceVariant = BrandSoftBlack,
    onSurfaceVariant = BrandGrey,
    error = BrandError,
    onError = Color.White
)

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

@Composable
fun SoundraTheme(
    @Suppress("UNUSED_PARAMETER") themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            if (activity != null) {
                val window = activity.window
                window.statusBarColor = BrandPureBlack.toArgb()
                window.navigationBarColor = BrandPureBlack.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = PremiumColorScheme,
        typography = Typography,
        content = content
    )
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
