package com.zim.jackettprowler.video

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Automatically analyzes and configures video sites
 * Similar to SiteInfiltrator but for video sites
 */
class VideoSiteInfiltrator(private val context: Context) {
    
    companion object {
        private const val TAG = "VideoSiteInfiltrator"
        
        // Known video site patterns
        private val SITE_PATTERNS = mapOf(
            "youtube.com" to VideoSiteType.YOUTUBE,
            "youtu.be" to VideoSiteType.YOUTUBE,
            "dailymotion.com" to VideoSiteType.DAILYMOTION,
            "vimeo.com" to VideoSiteType.VIMEO,
            "rumble.com" to VideoSiteType.RUMBLE,
            "odysee.com" to VideoSiteType.ODYSEE,
            "lbry.tv" to VideoSiteType.ODYSEE,
            "bitchute.com" to VideoSiteType.BITCHUTE,
            "twitch.tv" to VideoSiteType.TWITCH,
            "archive.org" to VideoSiteType.ARCHIVE_ORG,
            // Invidious instances
            "invidious" to VideoSiteType.YOUTUBE,
            "yewtu.be" to VideoSiteType.YOUTUBE,
            // Piped instances
            "piped" to VideoSiteType.YOUTUBE,
            // PeerTube instances often have these in URL
            "peertube" to VideoSiteType.PEERTUBE,
            "tube" to VideoSiteType.PEERTUBE
        )
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    /**
     * Analyze a URL and create a VideoSiteConfig
     */
    suspend fun analyzeAndConfigure(url: String): VideoSiteConfig? = withContext(Dispatchers.IO) {
        try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return@withContext null
            val baseUrl = "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}"
            
            // Check known patterns
            val siteType = detectSiteType(host, url)
            
            // Generate config based on detected type
            val config = when (siteType) {
                VideoSiteType.YOUTUBE -> createYouTubeConfig(baseUrl, host)
                VideoSiteType.DAILYMOTION -> createDailymotionConfig()
                VideoSiteType.VIMEO -> createVimeoConfig()
                VideoSiteType.RUMBLE -> createRumbleConfig()
                VideoSiteType.ODYSEE -> createOdyseeConfig()
                VideoSiteType.BITCHUTE -> createBitChuteConfig()
                VideoSiteType.PEERTUBE -> createPeerTubeConfig(baseUrl)
                VideoSiteType.ARCHIVE_ORG -> createArchiveOrgConfig()
                VideoSiteType.TWITCH -> createTwitchConfig()
                VideoSiteType.GENERIC -> analyzeGenericSite(baseUrl, url)
            }
            
            config
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing URL: ${e.message}")
            null
        }
    }
    
    private fun detectSiteType(host: String, url: String): VideoSiteType {
        // Check known patterns
        for ((pattern, type) in SITE_PATTERNS) {
            if (host.contains(pattern)) {
                return type
            }
        }
        
        // Check if it's a PeerTube instance by API
        if (isPeerTubeInstance(url)) {
            return VideoSiteType.PEERTUBE
        }
        
        return VideoSiteType.GENERIC
    }
    
    private fun isPeerTubeInstance(url: String): Boolean {
        return try {
            val uri = URI(url)
            val apiUrl = "${uri.scheme}://${uri.host}/api/v1/config"
            
            val request = Request.Builder()
                .url(apiUrl)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    body.contains("peertube") || body.contains("instance") && body.contains("name")
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun createYouTubeConfig(instanceUrl: String, host: String): VideoSiteConfig {
        return VideoSiteConfig(
            id = "youtube_${UUID.randomUUID().toString().take(8)}",
            name = if (host.contains("youtube")) "YouTube" else "YouTube (${host.take(15)})",
            baseUrl = "https://www.youtube.com",
            siteType = VideoSiteType.YOUTUBE,
            instanceUrl = if (host.contains("youtube")) "" else instanceUrl,
            searchPath = "/results?search_query={query}"
        )
    }
    
    private fun createDailymotionConfig(): VideoSiteConfig {
        return VideoSiteConfig(
            id = "dailymotion",
            name = "Dailymotion",
            baseUrl = "https://www.dailymotion.com",
            siteType = VideoSiteType.DAILYMOTION,
            apiEndpoint = "https://api.dailymotion.com/videos",
            searchPath = "/search/{query}"
        )
    }
    
    private fun createVimeoConfig(): VideoSiteConfig {
        return VideoSiteConfig(
            id = "vimeo",
            name = "Vimeo",
            baseUrl = "https://vimeo.com",
            siteType = VideoSiteType.VIMEO,
            searchPath = "/search?q={query}",
            selectors = VideoSelectors(
                container = "div.iris_video-vital",
                title = "a.iris_link-header",
                thumbnailUrl = "img",
                duration = "time",
                channel = "span.vimeo-author"
            )
        )
    }
    
    private fun createRumbleConfig(): VideoSiteConfig {
        return VideoSiteConfig(
            id = "rumble",
            name = "Rumble",
            baseUrl = "https://rumble.com",
            siteType = VideoSiteType.RUMBLE,
            searchPath = "/search/video?q={query}",
            selectors = VideoSelectors(
                container = "li.video-listing-entry",
                title = "h3.video-item--title",
                thumbnailUrl = "img.video-item--img",
                duration = "span.video-item--duration",
                views = "span.video-item--views",
                channel = "span.video-item--by-a"
            )
        )
    }
    
    private fun createOdyseeConfig(): VideoSiteConfig {
        return VideoSiteConfig(
            id = "odysee",
            name = "Odysee",
            baseUrl = "https://odysee.com",
            siteType = VideoSiteType.ODYSEE,
            apiEndpoint = "https://lighthouse.odysee.com/search",
            searchPath = "/$/search?q={query}"
        )
    }
    
    private fun createBitChuteConfig(): VideoSiteConfig {
        return VideoSiteConfig(
            id = "bitchute",
            name = "BitChute",
            baseUrl = "https://www.bitchute.com",
            siteType = VideoSiteType.BITCHUTE,
            searchPath = "/search/?query={query}&kind=video",
            selectors = VideoSelectors(
                container = "div.video-result-container",
                title = "div.video-result-title a",
                thumbnailUrl = "img.img-responsive",
                duration = "span.video-duration",
                views = "span.video-views",
                channel = "p.video-result-channel a"
            )
        )
    }
    
    private fun createPeerTubeConfig(instanceUrl: String): VideoSiteConfig {
        val host = try {
            URI(instanceUrl).host?.replace("www.", "") ?: "peertube"
        } catch (e: Exception) {
            "peertube"
        }
        
        return VideoSiteConfig(
            id = "peertube_${UUID.randomUUID().toString().take(8)}",
            name = "PeerTube ($host)",
            baseUrl = instanceUrl,
            siteType = VideoSiteType.PEERTUBE,
            instanceUrl = instanceUrl,
            apiEndpoint = "$instanceUrl/api/v1/search/videos",
            searchPath = "/search?search={query}"
        )
    }
    
    private fun createArchiveOrgConfig(): VideoSiteConfig {
        return VideoSiteConfig(
            id = "archive_org",
            name = "Internet Archive",
            baseUrl = "https://archive.org",
            siteType = VideoSiteType.ARCHIVE_ORG,
            apiEndpoint = "https://archive.org/advancedsearch.php",
            searchPath = "/search.php?query={query}&mediatype=movies"
        )
    }
    
    private fun createTwitchConfig(): VideoSiteConfig {
        return VideoSiteConfig(
            id = "twitch",
            name = "Twitch VODs",
            baseUrl = "https://www.twitch.tv",
            siteType = VideoSiteType.TWITCH,
            searchPath = "/search?term={query}&type=video",
            selectors = VideoSelectors(
                container = "div[data-a-target='search-result-video']",
                title = "h3",
                thumbnailUrl = "img",
                duration = "div.tw-media-card-stat",
                channel = "a.tw-link"
            )
        )
    }
    
    /**
     * Analyze a generic video site and detect selectors
     */
    private suspend fun analyzeGenericSite(baseUrl: String, url: String): VideoSiteConfig? {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                
                val html = response.body?.string() ?: return null
                val doc = Jsoup.parse(html, baseUrl)
                
                val host = try {
                    URI(baseUrl).host?.replace("www.", "") ?: "video"
                } catch (e: Exception) {
                    "video"
                }
                
                // Detect search path
                val searchPath = detectSearchPath(doc)
                
                // Detect video selectors
                val selectors = detectVideoSelectors(doc)
                
                return VideoSiteConfig(
                    id = "generic_${UUID.randomUUID().toString().take(8)}",
                    name = doc.title().take(30).ifEmpty { host },
                    baseUrl = baseUrl,
                    siteType = VideoSiteType.GENERIC,
                    searchPath = searchPath,
                    selectors = selectors
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing generic site: ${e.message}")
            return null
        }
    }
    
    private fun detectSearchPath(doc: org.jsoup.nodes.Document): String {
        // Look for search forms
        val searchForms = doc.select("form[action*=search], form[role=search], form#search")
        if (searchForms.isNotEmpty()) {
            val action = searchForms.first()?.attr("action") ?: ""
            return if (action.isNotEmpty()) "$action?q={query}" else "/search?q={query}"
        }
        
        // Look for search input
        val searchInputs = doc.select("input[type=search], input[name*=search], input[name=q]")
        if (searchInputs.isNotEmpty()) {
            return "/search?q={query}"
        }
        
        return "/search?q={query}"
    }
    
    private fun detectVideoSelectors(doc: org.jsoup.nodes.Document): VideoSelectors {
        // Common video container patterns
        val containerCandidates = listOf(
            "div.video-item", "div.video-card", "article.video",
            "div[class*='video']", "li[class*='video']",
            "div.media-item", "div.result", "div.item"
        )
        
        var container = ""
        for (candidate in containerCandidates) {
            if (doc.select(candidate).isNotEmpty()) {
                container = candidate
                break
            }
        }
        
        // Title selectors
        val titleCandidates = listOf(
            "h3 a", "h2 a", ".title a", ".video-title",
            "a[href*=video]", "a[href*=watch]"
        )
        
        var title = ""
        for (candidate in titleCandidates) {
            if (doc.select(candidate).isNotEmpty()) {
                title = candidate
                break
            }
        }
        
        return VideoSelectors(
            container = container,
            title = title,
            thumbnailUrl = "img",
            duration = ".duration, span[class*='time'], time",
            views = ".views, span[class*='view']",
            channel = ".channel, .author, .uploader"
        )
    }
}
