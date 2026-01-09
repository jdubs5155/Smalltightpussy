package com.zim.jackettprowler.video

/**
 * Result model for video search
 */
data class VideoResult(
    val title: String,
    val videoUrl: String,
    val thumbnailUrl: String = "",
    val duration: String = "",
    val views: String = "",
    val channel: String = "",
    val uploadDate: String = "",
    val description: String = "",
    val source: String = "",  // Which site/provider it came from
    val siteType: VideoSiteType = VideoSiteType.GENERIC,
    val embedUrl: String = "",
    val directUrl: String = ""
) {
    fun getFormattedViews(): String {
        return try {
            val count = views.replace(Regex("[^0-9]"), "").toLongOrNull() ?: return views
            when {
                count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
                count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
                count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
                else -> count.toString()
            }
        } catch (e: Exception) {
            views
        }
    }
    
    fun getFormattedDuration(): String {
        // If already formatted (like "10:30"), return as-is
        if (duration.contains(":")) return duration
        
        // Try to parse seconds
        return try {
            val seconds = duration.toLongOrNull() ?: return duration
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            
            if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, secs)
            } else {
                String.format("%d:%02d", minutes, secs)
            }
        } catch (e: Exception) {
            duration
        }
    }
}
