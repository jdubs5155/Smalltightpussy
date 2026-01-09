package com.zim.jackettprowler.video

import com.google.gson.Gson

/**
 * Configuration for clearnet video sites
 */
data class VideoSiteConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val searchPath: String = "",
    val apiEndpoint: String = "",
    val siteType: VideoSiteType = VideoSiteType.GENERIC,
    val instanceUrl: String = "",  // For sites like Invidious/Piped that have instances
    val isEnabled: Boolean = true,
    val selectors: VideoSelectors = VideoSelectors(),
    val addedTimestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = Gson().toJson(this)
    
    companion object {
        fun fromJson(json: String): VideoSiteConfig = Gson().fromJson(json, VideoSiteConfig::class.java)
    }
}

enum class VideoSiteType {
    YOUTUBE,          // YouTube (uses Invidious/Piped instances)
    DAILYMOTION,      // Dailymotion
    VIMEO,            // Vimeo
    RUMBLE,           // Rumble
    ODYSEE,           // Odysee/LBRY
    BITCHUTE,         // BitChute
    PEERTUBE,         // PeerTube instances
    TWITCH,           // Twitch VODs
    ARCHIVE_ORG,      // Internet Archive
    GENERIC           // Generic video site with scraping
}

data class VideoSelectors(
    val container: String = "",
    val title: String = "",
    val thumbnailUrl: String = "",
    val videoUrl: String = "",
    val duration: String = "",
    val views: String = "",
    val channel: String = "",
    val uploadDate: String = ""
)
