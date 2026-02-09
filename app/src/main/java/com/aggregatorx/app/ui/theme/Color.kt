package com.aggregatorx.app.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// AggravatedX - Pastel Dark Theme (Easy on Eyes)
// ==========================================

// Primary Colors - Soft Pastel palette (muted, easy on eyes)
val CyberCyan = Color(0xFF7EC8E3)       // Soft pastel cyan
val CyberCyanDark = Color(0xFF5BA3C0)   // Muted cyan
val CyberBlue = Color(0xFFA8D8E5)       // Soft pastel blue
val CyberBlueDark = Color(0xFF82B6C4)   // Muted blue
val CyberPurple = Color(0xFFB8A9C9)     // Soft lavender
val CyberPink = Color(0xFFD4A5A5)       // Muted rose

// Background Colors - Deep & Restful
val DarkBackground = Color(0xFF121418)   // Very dark charcoal
val DarkSurface = Color(0xFF1A1E24)      // Dark slate
val DarkSurfaceVariant = Color(0xFF232930) // Slate variant
val DarkCard = Color(0xFF2A3038)         // Card background
val DarkCardHover = Color(0xFF333B44)    // Hover state

// Accent Colors - Muted Pastels
val AccentGreen = Color(0xFF98C9A3)      // Soft sage green
val AccentOrange = Color(0xFFE5B88C)     // Muted peach
val AccentRed = Color(0xFFD88888)        // Soft coral red
val AccentYellow = Color(0xFFE5D88C)     // Muted gold

// Text Colors - Comfortable Contrast
val TextPrimary = Color(0xFFE8EAF0)      // Soft white (not pure white)
val TextSecondary = Color(0xFFB0B8C8)    // Muted gray-blue
val TextTertiary = Color(0xFF7A8595)     // Subtle gray
val TextMuted = Color(0xFF5A6575)        // Very muted

// Gradient Colors - Soft transitions
val GradientStart = Color(0xFF7EC8E3)    // Pastel cyan
val GradientMid = Color(0xFFA8B8D8)      // Pastel blue-gray
val GradientEnd = Color(0xFFB8A9C9)      // Pastel lavender

// Status Colors - Muted for comfort
val StatusSuccess = Color(0xFF98C9A3)    // Soft sage
val StatusWarning = Color(0xFFE5D88C)    // Muted gold
val StatusError = Color(0xFFD88888)      // Soft coral
val StatusInfo = Color(0xFF7EC8E3)       // Pastel cyan

// Provider Category Colors - Soft pastels
val CategoryStreaming = Color(0xFFD4A5A5) // Muted rose
val CategoryTorrent = Color(0xFFB8A9C9)   // Soft lavender
val CategoryNews = Color(0xFF98C9A3)      // Sage green
val CategoryMedia = Color(0xFFA8B8D8)     // Pastel blue
val CategoryGeneral = Color(0xFF7EC8E3)   // Pastel cyan
val CategoryAPI = Color(0xFFE5D88C)       // Muted gold

// AI/Smart Feature Colors
val AIAccent = Color(0xFFA8D0E8)          // AI indicator blue
val SmartFeature = Color(0xFFB8C9D4)      // Smart feature highlight

// Download/Media Colors
val DownloadActive = Color(0xFF7EC8E3)    // Active download
val DownloadComplete = Color(0xFF98C9A3)  // Complete
val DownloadPaused = Color(0xFFE5D88C)    // Paused
val DownloadError = Color(0xFFD88888)     // Error

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
