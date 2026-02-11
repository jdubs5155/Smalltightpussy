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
 * AggregatorX - DARK RED NIGHT THEME
 * Pure black backgrounds with vibrant red accents
 * Perfect for night/dark viewing
 */
private val DarkRedNightColorScheme = darkColorScheme(
    primary = CyberCyan, // Now crimson red
    onPrimary = DarkBackground,
    primaryContainer = CyberCyanDark,
    onPrimaryContainer = TextPrimary,
    secondary = CyberBlue, // Bright red
    onSecondary = DarkBackground,
    secondaryContainer = CyberBlueDark,
    onSecondaryContainer = TextPrimary,
    tertiary = CyberPurple, // Pink-red
    onTertiary = DarkBackground,
    tertiaryContainer = CyberPurple.copy(alpha = 0.3f),
    onTertiaryContainer = TextPrimary,
    error = AccentRed,
    onError = TextPrimary,
    errorContainer = AccentRed.copy(alpha = 0.2f),
    onErrorContainer = AccentRed,
    background = DarkBackground, // Pure black
    onBackground = TextPrimary,
    surface = DarkSurface, // Almost black
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = TextTertiary,
    outlineVariant = DarkCardHover,
    inverseSurface = TextPrimary,
    inverseOnSurface = DarkBackground,
    inversePrimary = CyberCyanDark,
    surfaceTint = CyberCyan.copy(alpha = 0.15f) // Red tint
)

@Composable
fun AggregatorXTheme(
    darkTheme: Boolean = true, // Always dark - night red theme
    content: @Composable () -> Unit
) {
    val colorScheme = DarkRedNightColorScheme
    
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
