package com.aggregatorx.app.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// AggregatorX - DARK RED NIGHT THEME
// Deep blacks with vibrant red accents
// ==========================================

// Primary Colors - DARK RED & CRIMSON palette
val CyberCyan = Color(0xFFFF1744)        // Vibrant red (accent red)
val CyberCyanDark = Color(0xFFD50000)    // Deep red
val CyberBlue = Color(0xFFFF5252)        // Bright red
val CyberBlueDark = Color(0xFFB71C1C)    // Dark red
val CyberPurple = Color(0xFFFF4081)      // Pink-red accent
val CyberPink = Color(0xFFFF1744)        // Crimson

// Background Colors - PURE BLACKS & DEEP DARK
val DarkBackground = Color(0xFF0A0A0A)   // Pure black
val DarkSurface = Color(0xFF121212)      // Almost black
val DarkSurfaceVariant = Color(0xFF1A1A1A) // Dark charcoal
val DarkCard = Color(0xFF1F1F1F)         // Card background - dark gray
val DarkCardHover = Color(0xFF2A2A2A)    // Hover state

// Accent Colors - RED SPECTRUM
val AccentGreen = Color(0xFF4CAF50)      // Success green (kept for status)
val AccentOrange = Color(0xFFFF6D00)     // Vivid orange accent
val AccentRed = Color(0xFFFF1744)        // Primary red accent
val AccentYellow = Color(0xFFFFAB00)     // Amber gold

// Text Colors - HIGH CONTRAST for dark theme
val TextPrimary = Color(0xFFFAFAFA)      // Pure white
val TextSecondary = Color(0xFFBDBDBD)    // Light gray
val TextTertiary = Color(0xFF757575)     // Medium gray
val TextMuted = Color(0xFF616161)        // Muted gray

// Gradient Colors - RED gradients
val GradientStart = Color(0xFFFF1744)    // Crimson red
val GradientMid = Color(0xFFD50000)      // Deep red
val GradientEnd = Color(0xFFFF4081)      // Pink-red

// Status Colors - Clear indicators
val StatusSuccess = Color(0xFF4CAF50)    // Green for success
val StatusWarning = Color(0xFFFFAB00)    // Amber for warning
val StatusError = Color(0xFFFF1744)      // Red for error
val StatusInfo = Color(0xFFFF5252)       // Bright red for info

// Provider Category Colors - RED THEME variations
val CategoryStreaming = Color(0xFFFF1744) // Crimson
val CategoryTorrent = Color(0xFFFF4081)   // Pink-red
val CategoryNews = Color(0xFF4CAF50)      // Green (contrast)
val CategoryMedia = Color(0xFFFF5252)     // Bright red
val CategoryGeneral = Color(0xFFFF1744)   // Crimson
val CategoryAPI = Color(0xFFFFAB00)       // Amber gold

// AI/Smart Feature Colors - GLOWING RED
val AIAccent = Color(0xFFFF1744)          // AI red glow
val SmartFeature = Color(0xFFFF4081)      // Smart feature pink-red

// Download/Media Colors - RED THEMED
val DownloadActive = Color(0xFFFF1744)    // Active download - red
val DownloadComplete = Color(0xFF4CAF50)  // Complete - green
val DownloadPaused = Color(0xFFFFAB00)    // Paused - amber
val DownloadError = Color(0xFFD50000)     // Error - deep red

// Score Colors - Progressive with red focus
fun getScoreColor(score: Float): Color {
    return when {
        score >= 80f -> AccentGreen        // High scores - green
        score >= 60f -> Color(0xFFFF5252)  // Good - bright red
        score >= 40f -> AccentOrange       // Medium - orange
        score >= 20f -> AccentYellow       // Low - amber
        else -> Color(0xFFD50000)          // Very low - deep red
    }
}

// Security Score Colors
fun getSecurityColor(score: Float): Color {
    return when {
        score >= 80f -> AccentGreen
        score >= 60f -> Color(0xFFFF5252)  // Bright red
        score >= 40f -> AccentYellow
        score >= 20f -> AccentOrange
        else -> Color(0xFFD50000)          // Deep red
    }
}

// Quality Badge Colors
fun getQualityColor(quality: String): Color {
    return when {
        quality.contains("4k", ignoreCase = true) || quality.contains("2160") -> AccentGreen
        quality.contains("1080") || quality.contains("full hd", ignoreCase = true) -> Color(0xFFFF1744) // Crimson
        quality.contains("720") -> Color(0xFFFF5252)  // Bright red
        quality.contains("480") -> AccentOrange
        else -> TextTertiary
    }
}
