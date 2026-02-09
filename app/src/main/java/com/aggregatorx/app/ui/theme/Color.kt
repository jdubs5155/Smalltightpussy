package com.aggregatorx.app.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// AggregatorX - VIBRANT Dark Theme (Bright & Modern)
// ==========================================

// Primary Colors - VIVID & BRIGHT Neon palette
val CyberCyan = Color(0xFF00F5FF)       // Bright electric cyan
val CyberCyanDark = Color(0xFF00D4E5)   // Vibrant cyan
val CyberBlue = Color(0xFF00BFFF)       // Deep sky blue
val CyberBlueDark = Color(0xFF1E90FF)   // Dodger blue
val CyberPurple = Color(0xFFDA70D6)     // Orchid purple
val CyberPink = Color(0xFFFF69B4)       // Hot pink

// Background Colors - Deep & Rich
val DarkBackground = Color(0xFF0A0E14)   // Deep space black
val DarkSurface = Color(0xFF141A22)      // Rich dark blue
val DarkSurfaceVariant = Color(0xFF1C242E) // Dark slate blue
val DarkCard = Color(0xFF242D3A)         // Card background
val DarkCardHover = Color(0xFF2E3A48)    // Hover state with glow

// Accent Colors - VIVID & SATURATED
val AccentGreen = Color(0xFF00FF88)      // Bright mint green
val AccentOrange = Color(0xFFFF9F43)     // Vivid orange
val AccentRed = Color(0xFFFF6B6B)        // Bright coral red
val AccentYellow = Color(0xFFFFD93D)     // Bright gold

// Text Colors - BRIGHT & CRISP for high readability
val TextPrimary = Color(0xFFFFFEFF)      // Pure bright white
val TextSecondary = Color(0xFFD0D8E8)    // Light gray-blue (brighter)
val TextTertiary = Color(0xFF9EABC0)     // Clear medium gray
val TextMuted = Color(0xFF6E7A90)        // Muted but visible

// Gradient Colors - VIVID Neon transitions
val GradientStart = Color(0xFF00F5FF)    // Electric cyan
val GradientMid = Color(0xFF7B68EE)      // Medium slate blue
val GradientEnd = Color(0xFFDA70D6)      // Orchid purple

// Status Colors - BRIGHT & Clear
val StatusSuccess = Color(0xFF00FF88)    // Bright mint
val StatusWarning = Color(0xFFFFD93D)    // Bright gold
val StatusError = Color(0xFFFF6B6B)      // Vivid coral
val StatusInfo = Color(0xFF00BFFF)       // Deep sky blue

// Provider Category Colors - VIBRANT
val CategoryStreaming = Color(0xFFFF69B4) // Hot pink
val CategoryTorrent = Color(0xFFDA70D6)   // Orchid purple
val CategoryNews = Color(0xFF00FF88)      // Bright mint
val CategoryMedia = Color(0xFF00BFFF)     // Sky blue
val CategoryGeneral = Color(0xFF00F5FF)   // Electric cyan
val CategoryAPI = Color(0xFFFFD93D)       // Bright gold

// AI/Smart Feature Colors - GLOWING
val AIAccent = Color(0xFF00E5FF)          // Bright AI cyan
val SmartFeature = Color(0xFF7B68EE)      // Medium slate blue (glowing)

// Download/Media Colors - VIVID
val DownloadActive = Color(0xFF00F5FF)    // Active download - electric
val DownloadComplete = Color(0xFF00FF88)  // Complete - bright mint
val DownloadPaused = Color(0xFFFFD93D)    // Paused - gold
val DownloadError = Color(0xFFFF6B6B)     // Error - coral

// Score Colors - Progressive pastels
fun getScoreColor(score: Float): Color {
    return when {
        score >= 80f -> AccentGreen
        score >= 60f -> CyberCyan
        score >= 40f -> AccentOrange
        score >= 20f -> AccentYellow
        else -> AccentRed
    }
}

// Security Score Colors
fun getSecurityColor(score: Float): Color {
    return when {
        score >= 80f -> AccentGreen
        score >= 60f -> CyberCyan
        score >= 40f -> AccentYellow
        score >= 20f -> AccentOrange
        else -> AccentRed
    }
}

// Quality Badge Colors
fun getQualityColor(quality: String): Color {
    return when {
        quality.contains("4k", ignoreCase = true) || quality.contains("2160") -> AccentGreen
        quality.contains("1080") || quality.contains("full hd", ignoreCase = true) -> CyberCyan
        quality.contains("720") -> CyberBlue
        quality.contains("480") -> AccentOrange
        else -> TextTertiary
    }
}
