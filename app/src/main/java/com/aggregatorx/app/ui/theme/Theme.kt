package com.aggregatorx.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * AggregatorX - VIBRANT Neon Dark Theme
 * Bright, modern, eye-catching colors for maximum visibility
 */
private val VibrantDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = DarkBackground,
    primaryContainer = CyberCyanDark,
    onPrimaryContainer = TextPrimary,
    secondary = CyberBlue,
    onSecondary = DarkBackground,
    secondaryContainer = CyberBlueDark,
    onSecondaryContainer = TextPrimary,
    tertiary = CyberPurple,
    onTertiary = DarkBackground,
    tertiaryContainer = CyberPurple.copy(alpha = 0.3f),
    onTertiaryContainer = TextPrimary,
    error = AccentRed,
    onError = TextPrimary,
    errorContainer = AccentRed.copy(alpha = 0.2f),
    onErrorContainer = AccentRed,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = TextTertiary,
    outlineVariant = DarkCardHover,
    inverseSurface = TextPrimary,
    inverseOnSurface = DarkBackground,
    inversePrimary = CyberCyanDark,
    surfaceTint = CyberCyan.copy(alpha = 0.15f)
)

@Composable
fun AggregatorXTheme(
    darkTheme: Boolean = true, // Always dark - vibrant neon theme
    content: @Composable () -> Unit
) {
    val colorScheme = VibrantDarkColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkBackground.toArgb()
            window.navigationBarColor = DarkBackground.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
