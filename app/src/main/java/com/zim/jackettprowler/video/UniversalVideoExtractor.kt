package com.zim.jackettprowler.video

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Universal Video Extractor - Tool-X Style
 * Automatically discovers and extracts videos from ANY site
 * Works like yt-dlp extractors but Android-native
 */
class UniversalVideoExtractor(private val context: Context) {

    companion object {
        private const val TAG = "UniversalVideoExtractor"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12; SM-A325F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        
        // Known instance patterns (like Invidious, PeerTube, etc.)
        private val INSTANCE_FINGERPRINTS = listOf(
            InstanceFingerprint("invidious", listOf("/api/v1/videos/", "Invidious", "iv-"), VideoSiteType.YOUTUBE),
            InstanceFingerprint("piped", listOf("/api/videos/", "Piped", "piped.kavin"), VideoSiteType.YOUTUBE),
            InstanceFingerprint("peertube", listOf("/api/v1/config", "PeerTube", "peertube"), VideoSiteType.PEERTUBE),
            InstanceFingerprint("libretube", listOf("/api/", "LibreTube"), VideoSiteType.YOUTUBE),
            InstanceFingerprint("cloudtube", listOf("/cloudtube/", "CloudTube"), VideoSiteType.YOUTUBE),
            InstanceFingerprint("sepia", listOf("/api/v1/search/videos", "sepiasearch"), VideoSiteType.PEERTUBE),
            InstanceFingerprint("mediagoblin", listOf("/mediagoblin", "MediaGoblin"), VideoSiteType.GENERIC),
            InstanceFingerprint("funkwhale", listOf("/api/v1/tracks", "Funkwhale"), VideoSiteType.GENERIC),
        )
        
        // Common video platform signatures
        private val PLATFORM_SIGNATURES = mapOf(
            // Streaming platforms
            "embed.youtube" to VideoSiteType.YOUTUBE,
            "player.vimeo" to VideoSiteType.VIMEO,
            "dailymotion.com/embed" to VideoSiteType.DAILYMOTION,
            "rumble.com/embed" to VideoSiteType.RUMBLE,
            "odysee.com/@" to VideoSiteType.ODYSEE,
            "bitchute.com/video" to VideoSiteType.BITCHUTE,
            "archive.org/details" to VideoSiteType.ARCHIVE_ORG,
            "twitch.tv/videos" to VideoSiteType.TWITCH,
            // Social video
            "tiktok.com/@" to VideoSiteType.GENERIC,
            "instagram.com/reel" to VideoSiteType.GENERIC,
            "facebook.com/watch" to VideoSiteType.GENERIC,
            "twitter.com/i/status" to VideoSiteType.GENERIC,
            "x.com/i/status" to VideoSiteType.GENERIC,
        )
        
        // Video file patterns
        private val VIDEO_URL_PATTERNS = listOf(
            Pattern.compile("https?://[^\"'\\s]+\\.(?:mp4|webm|m3u8|mpd)[^\"'\\s]*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("https?://[^\"'\\s]+/video[^\"'\\s]*\\.(?:mp4|webm)[^\"'\\s]*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("src\\s*[=:]\\s*[\"']?(https?://[^\"'\\s]+\\.(?:mp4|webm|m3u8))[\"']?", Pattern.CASE_INSENSITIVE),
        )
        
        // JSON video data patterns
        private val JSON_VIDEO_PATTERNS = listOf(
            Pattern.compile("\"videoUrl\"\\s*:\\s*\"([^\"]+)\""),
            Pattern.compile("\"streamUrl\"\\s*:\\s*\"([^\"]+)\""),
            Pattern.compile("\"hlsUrl\"\\s*:\\s*\"([^\"]+)\""),
            Pattern.compile("\"dashUrl\"\\s*:\\s*\"([^\"]+)\""),
            Pattern.compile("\"sources\"\\s*:\\s*\\[([^\\]]+)\\]"),
            Pattern.compile("\"formats\"\\s*:\\s*\\[([^\\]]+)\\]"),
            Pattern.compile("file\\s*:\\s*\"([^\"]+\\.(?:mp4|webm|m3u8))\""),
            Pattern.compile("source\\s*:\\s*\"([^\"]+\\.(?:mp4|webm|m3u8))\""),
        )
    }
    
    data class InstanceFingerprint(
        val name: String,
        val signatures: List<String>,
        val siteType: VideoSiteType
    )
    
    data class DiscoveredSite(
        val config: VideoSiteConfig,
        val confidence: Float,  // 0.0 to 1.0
        val features: List<String>,
        val apiEndpoints: List<String>
    )
    
    data class ExtractedVideo(
        val url: String,
        val title: String,
        val quality: String,
        val format: String,
        val directUrl: String?,
        val streamUrls: List<StreamInfo> = emptyList()
    )
    
    data class StreamInfo(
        val url: String,
        val quality: String,
        val format: String,
        val size: Long = 0
    )
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    /**
     * Deep analyze any URL to discover site capabilities
     */
    suspend fun deepAnalyze(url: String): DiscoveredSite? = withContext(Dispatchers.IO) {
        try {
            val uri = URI(url)
            val baseUrl = "${uri.scheme}://${uri.host}${if (uri.port > 0 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""}"
            val host = uri.host?.lowercase() ?: return@withContext null
            
            Log.d(TAG, "Deep analyzing: $baseUrl")
            
            // Step 1: Fetch and analyze the page
            val pageAnalysis = analyzePage(url)
            
            // Step 2: Detect instance type (Invidious, PeerTube, etc.)
            val instanceType = detectInstanceType(baseUrl, pageAnalysis)
            
            // Step 3: Discover API endpoints
            val apiEndpoints = discoverApiEndpoints(baseUrl, pageAnalysis)
            
            // Step 4: Detect video structure and selectors
            val selectors = detectAdvancedSelectors(pageAnalysis)
            
            // Step 5: Detect search capabilities
            val searchConfig = detectSearchCapabilities(baseUrl, pageAnalysis)
            
            // Calculate confidence score
            val confidence = calculateConfidence(instanceType, apiEndpoints, selectors)
            
            val features = mutableListOf<String>()
            if (apiEndpoints.isNotEmpty()) features.add("API Support")
            if (selectors.container.isNotEmpty()) features.add("HTML Parsing")
            if (searchConfig.first.isNotEmpty()) features.add("Search")
            if (instanceType != VideoSiteType.GENERIC) features.add("Known Platform")
            
            val config = VideoSiteConfig(
                id = "discovered_${UUID.randomUUID().toString().take(8)}",
                name = pageAnalysis.siteName.ifEmpty { host.replace("www.", "").take(20) },
                baseUrl = baseUrl,
                siteType = instanceType,
                instanceUrl = if (instanceType in listOf(VideoSiteType.YOUTUBE, VideoSiteType.PEERTUBE)) baseUrl else "",
                apiEndpoint = apiEndpoints.firstOrNull() ?: "",
                searchPath = searchConfig.first,
                selectors = selectors,
                isEnabled = true
            )
            
            DiscoveredSite(config, confidence, features, apiEndpoints)
        } catch (e: Exception) {
            Log.e(TAG, "Deep analysis failed: ${e.message}")
            null
        }
    }
    
    data class PageAnalysis(
        val html: String,
        val doc: Document,
        val siteName: String,
        val hasVideoElements: Boolean,
        val hasSearchForm: Boolean,
        val hasApi: Boolean,
        val metaTags: Map<String, String>,
        val scripts: List<String>,
        val links: List<String>
    )
    
    private suspend fun analyzePage(url: String): PageAnalysis = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml")
            .build()
        
        client.newCall(request).execute().use { response ->
            val html = response.body?.string() ?: ""
            val doc = Jsoup.parse(html, url)
            
            val siteName = doc.select("meta[property='og:site_name']").attr("content")
                .ifEmpty { doc.select("meta[name='application-name']").attr("content") }
                .ifEmpty { doc.title().split(" - ", " | ").firstOrNull() ?: "" }
            
            val metaTags = mutableMapOf<String, String>()
            doc.select("meta[property], meta[name]").forEach { meta ->
                val key = meta.attr("property").ifEmpty { meta.attr("name") }
                val value = meta.attr("content")
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    metaTags[key] = value
                }
            }
            
            val scripts = doc.select("script[src]").map { it.attr("src") }
            val links = doc.select("link[href]").map { it.attr("href") }
            
            PageAnalysis(
                html = html,
                doc = doc,
                siteName = siteName,
                hasVideoElements = doc.select("video, iframe[src*=video], iframe[src*=embed], div[class*=video], div[class*=player]").isNotEmpty(),
                hasSearchForm = doc.select("form[action*=search], input[type=search], input[name=q], input[name=query]").isNotEmpty(),
                hasApi = html.contains("/api/") || scripts.any { it.contains("api") },
                metaTags = metaTags,
                scripts = scripts,
                links = links
            )
        }
    }
    
    private suspend fun detectInstanceType(baseUrl: String, analysis: PageAnalysis): VideoSiteType {
        // Check fingerprints
        for (fingerprint in INSTANCE_FINGERPRINTS) {
            for (signature in fingerprint.signatures) {
                if (analysis.html.contains(signature, ignoreCase = true) ||
                    analysis.scripts.any { it.contains(signature, ignoreCase = true) }) {
                    Log.d(TAG, "Detected ${fingerprint.name} instance")
                    return fingerprint.siteType
                }
            }
        }
        
        // Check platform signatures in URL or content
        for ((pattern, type) in PLATFORM_SIGNATURES) {
            if (baseUrl.contains(pattern) || analysis.html.contains(pattern)) {
                return type
            }
        }
        
        // Try API endpoint detection
        val apiTypes = listOf(
            Pair("$baseUrl/api/v1/videos", VideoSiteType.PEERTUBE),
            Pair("$baseUrl/api/v1/search/videos", VideoSiteType.PEERTUBE),
            Pair("$baseUrl/api/v1/trending", VideoSiteType.YOUTUBE), // Invidious
        )
        
        for ((apiUrl, type) in apiTypes) {
            try {
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "API detected at $apiUrl")
                        return type
                    }
                }
            } catch (e: Exception) {
                // Continue checking
            }
        }
        
        return VideoSiteType.GENERIC
    }
    
    private suspend fun discoverApiEndpoints(baseUrl: String, analysis: PageAnalysis): List<String> {
        val endpoints = mutableListOf<String>()
        
        // Common API paths to probe
        val apiPaths = listOf(
            "/api/v1/videos",
            "/api/v1/search/videos",
            "/api/v1/trending",
            "/api/v1/config",
            "/api/videos",
            "/api/search",
            "/videos.json",
            "/search.json",
            "/feed/videos",
        )
        
        for (path in apiPaths) {
            try {
                val apiUrl = "$baseUrl$path"
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val contentType = response.header("Content-Type") ?: ""
                        if (contentType.contains("json") || response.body?.string()?.startsWith("{") == true) {
                            endpoints.add(apiUrl)
                            Log.d(TAG, "Found API endpoint: $apiUrl")
                        }
                    }
                }
            } catch (e: Exception) {
                // Continue probing
            }
        }
        
        // Also check links in page for API references
        analysis.scripts.forEach { script ->
            if (script.contains("api", ignoreCase = true)) {
                val fullUrl = if (script.startsWith("http")) script else "$baseUrl$script"
                if (fullUrl !in endpoints) {
                    endpoints.add(fullUrl)
                }
            }
        }
        
        return endpoints
    }
    
    private fun detectAdvancedSelectors(analysis: PageAnalysis): VideoSelectors {
        val doc = analysis.doc
        
        // Advanced container detection
        val containerPatterns = listOf(
            // Specific video containers
            "div.video-card", "div.video-item", "article.video",
            "div.media-item", "li.video-entry", "div.result-item",
            // Grid layouts
            "div.video-grid > div", "ul.videos > li", "div.grid-item",
            // Card layouts  
            "div[class*='card'][class*='video']", "div[class*='video'][class*='thumb']",
            // Generic but specific
            "div[data-video-id]", "div[data-id]", "a[href*='/video/']",
            "a[href*='/watch']", "a[href*='/v/']"
        )
        
        var container = ""
        var maxMatches = 0
        for (pattern in containerPatterns) {
            val matches = doc.select(pattern).size
            if (matches > maxMatches && matches > 1) {
                container = pattern
                maxMatches = matches
            }
        }
        
        // Title detection within container
        val titlePatterns = listOf(
            "h3 a", "h2 a", ".title a", ".video-title", ".media-title",
            "a.title", "span.title", "[class*='title'] a", "a[href*='watch']"
        )
        
        var title = ""
        val containerElement = if (container.isNotEmpty()) doc.select(container).first() else doc
        for (pattern in titlePatterns) {
            if (containerElement?.select(pattern)?.isNotEmpty() == true) {
                title = pattern
                break
            }
        }
        
        // Thumbnail detection
        val thumbPatterns = listOf(
            "img.thumbnail", "img.thumb", "img[class*='thumb']",
            "img[src*='thumb']", "img[data-src]", "img"
        )
        
        var thumbnail = "img"
        for (pattern in thumbPatterns) {
            if (containerElement?.select(pattern)?.isNotEmpty() == true) {
                thumbnail = pattern
                break
            }
        }
        
        // Duration patterns
        val durationPatterns = listOf(
            ".duration", ".time", "span[class*='duration']", "span[class*='time']",
            "time", "[class*='length']"
        )
        
        var duration = ""
        for (pattern in durationPatterns) {
            if (doc.select(pattern).isNotEmpty()) {
                duration = pattern
                break
            }
        }
        
        // Views patterns
        val viewsPatterns = listOf(
            ".views", ".view-count", "span[class*='views']", 
            "[class*='view']", ".stats"
        )
        
        var views = ""
        for (pattern in viewsPatterns) {
            if (doc.select(pattern).isNotEmpty()) {
                views = pattern
                break
            }
        }
        
        // Channel/author patterns
        val channelPatterns = listOf(
            ".channel", ".author", ".uploader", ".creator",
            "a[href*='channel']", "a[href*='user']", "a[href*='@']"
        )
        
        var channel = ""
        for (pattern in channelPatterns) {
            if (doc.select(pattern).isNotEmpty()) {
                channel = pattern
                break
            }
        }
        
        // Video URL pattern from links
        var videoUrl = "a[href]"
        val linkPatterns = listOf(
            "a[href*='/watch']", "a[href*='/video/']", "a[href*='/v/']",
            "a[href*='/embed/']", "a[data-video-id]"
        )
        for (pattern in linkPatterns) {
            if (doc.select(pattern).isNotEmpty()) {
                videoUrl = pattern
                break
            }
        }
        
        return VideoSelectors(
            container = container,
            title = title.ifEmpty { "a" },
            thumbnailUrl = thumbnail,
            duration = duration,
            views = views,
            channel = channel,
            videoUrl = videoUrl
        )
    }
    
    private fun detectSearchCapabilities(baseUrl: String, analysis: PageAnalysis): Pair<String, String> {
        val doc = analysis.doc
        
        // Find search form
        val searchForms = doc.select("form[action*=search], form[role=search], form#search-form")
        if (searchForms.isNotEmpty()) {
            val form = searchForms.first()
            val action = form?.attr("action") ?: ""
            val inputName = form?.select("input[type=search], input[type=text], input[name=q], input[name=query], input[name=search_query]")
                ?.first()?.attr("name") ?: "q"
            
            val searchPath = if (action.startsWith("/")) action else "/$action"
            return Pair("$searchPath?$inputName={query}", "GET")
        }
        
        // Check for AJAX search
        if (analysis.scripts.any { it.contains("search", ignoreCase = true) }) {
            return Pair("/search?q={query}", "GET")
        }
        
        // Default search path patterns based on site type
        return Pair("/search?q={query}", "GET")
    }
    
    private fun calculateConfidence(type: VideoSiteType, apis: List<String>, selectors: VideoSelectors): Float {
        var score = 0f
        
        // Known type is high confidence
        if (type != VideoSiteType.GENERIC) score += 0.4f
        
        // API endpoints boost confidence
        if (apis.isNotEmpty()) score += 0.3f
        
        // Good selectors
        if (selectors.container.isNotEmpty()) score += 0.15f
        if (selectors.title.isNotEmpty()) score += 0.1f
        if (selectors.thumbnailUrl.isNotEmpty()) score += 0.05f
        
        return score.coerceAtMost(1.0f)
    }
    
    /**
     * Extract video streams directly from any video page URL
     * Works like yt-dlp -F
     */
    suspend fun extractVideoStreams(videoUrl: String): List<StreamInfo> = withContext(Dispatchers.IO) {
        val streams = mutableListOf<StreamInfo>()
        
        try {
            val request = Request.Builder()
                .url(videoUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            
            client.newCall(request).execute().use { response ->
                val html = response.body?.string() ?: return@withContext streams
                
                // Method 1: Find direct video URLs in HTML
                for (pattern in VIDEO_URL_PATTERNS) {
                    val matcher = pattern.matcher(html)
                    while (matcher.find()) {
                        val url = matcher.group(1) ?: matcher.group(0)
                        if (url != null && !streams.any { it.url == url }) {
                            val quality = detectQualityFromUrl(url)
                            val format = detectFormatFromUrl(url)
                            streams.add(StreamInfo(url, quality, format))
                        }
                    }
                }
                
                // Method 2: Parse JSON video data
                for (pattern in JSON_VIDEO_PATTERNS) {
                    val matcher = pattern.matcher(html)
                    while (matcher.find()) {
                        val data = matcher.group(1)
                        if (data != null) {
                            parseJsonVideoData(data, streams)
                        }
                    }
                }
                
                // Method 3: Check HTML5 video elements
                val doc = Jsoup.parse(html)
                doc.select("video source, video[src]").forEach { video ->
                    val src = video.attr("src").ifEmpty { video.attr("data-src") }
                    if (src.isNotEmpty() && !streams.any { it.url == src }) {
                        val quality = video.attr("label").ifEmpty { "unknown" }
                        val format = detectFormatFromUrl(src)
                        streams.add(StreamInfo(src, quality, format))
                    }
                }
                
                // Method 4: Check og:video meta tag
                doc.select("meta[property='og:video'], meta[property='og:video:url']").forEach { meta ->
                    val content = meta.attr("content")
                    if (content.isNotEmpty() && !streams.any { it.url == content }) {
                        streams.add(StreamInfo(content, "og:video", detectFormatFromUrl(content)))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stream extraction failed: ${e.message}")
        }
        
        // Sort by quality
        streams.sortedByDescending { qualityToNumber(it.quality) }
    }
    
    private fun detectQualityFromUrl(url: String): String {
        val qualityPatterns = listOf(
            Pattern.compile("(\\d{3,4})p"),
            Pattern.compile("quality[=_](\\d+)"),
            Pattern.compile("res[=_](\\d+)"),
            Pattern.compile("(1080|720|480|360|240)"),
            Pattern.compile("(hd|sd|hq|lq)", Pattern.CASE_INSENSITIVE)
        )
        
        for (pattern in qualityPatterns) {
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                return matcher.group(1) + if (matcher.group(1).matches(Regex("\\d+"))) "p" else ""
            }
        }
        
        return "unknown"
    }
    
    private fun detectFormatFromUrl(url: String): String {
        return when {
            url.contains(".mp4") -> "mp4"
            url.contains(".webm") -> "webm"
            url.contains(".m3u8") -> "hls"
            url.contains(".mpd") -> "dash"
            url.contains(".mkv") -> "mkv"
            url.contains(".flv") -> "flv"
            else -> "unknown"
        }
    }
    
    private fun qualityToNumber(quality: String): Int {
        return when {
            quality.contains("2160") || quality.contains("4k", true) -> 2160
            quality.contains("1440") || quality.contains("2k", true) -> 1440
            quality.contains("1080") || quality.contains("hd", true) -> 1080
            quality.contains("720") -> 720
            quality.contains("480") || quality.contains("sd", true) -> 480
            quality.contains("360") -> 360
            quality.contains("240") || quality.contains("lq", true) -> 240
            else -> 0
        }
    }
    
    private fun parseJsonVideoData(data: String, streams: MutableList<StreamInfo>) {
        try {
            // Try parsing as JSON array
            if (data.trim().startsWith("[")) {
                val arr = JSONArray("[$data]")
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val url = item.optString("url").ifEmpty { 
                        item.optString("src").ifEmpty { 
                            item.optString("file") 
                        }
                    }
                    if (url.isNotEmpty()) {
                        val quality = item.optString("quality").ifEmpty { 
                            item.optString("label").ifEmpty { "unknown" }
                        }
                        val format = item.optString("type").ifEmpty { detectFormatFromUrl(url) }
                        streams.add(StreamInfo(url, quality, format))
                    }
                }
            }
        } catch (e: Exception) {
            // Not valid JSON, try regex
            val urlPattern = Pattern.compile("\"(?:url|src|file)\"\\s*:\\s*\"([^\"]+)\"")
            val matcher = urlPattern.matcher(data)
            while (matcher.find()) {
                matcher.group(1)?.let { url ->
                    if (!streams.any { it.url == url }) {
                        streams.add(StreamInfo(url, "unknown", detectFormatFromUrl(url)))
                    }
                }
            }
        }
    }
    
    /**
     * Search any discovered site using its configuration
     */
    suspend fun searchSite(config: VideoSiteConfig, query: String): List<VideoResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoResult>()
        
        try {
            // Try API first if available
            if (config.apiEndpoint.isNotEmpty()) {
                val apiResults = searchViaApi(config, query)
                if (apiResults.isNotEmpty()) {
                    return@withContext apiResults
                }
            }
            
            // Fall back to HTML scraping
            val searchUrl = buildSearchUrl(config, query)
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext results
                
                val html = response.body?.string() ?: return@withContext results
                val doc = Jsoup.parse(html, config.baseUrl)
                
                val containers = if (config.selectors.container.isNotEmpty()) {
                    doc.select(config.selectors.container)
                } else {
                    doc.select("div.video-item, div.video-card, article, li.video")
                }
                
                containers.take(50).forEach { container ->
                    try {
                        val titleEl = container.select(config.selectors.title).first()
                        val title = titleEl?.text() ?: ""
                        val videoUrl = titleEl?.attr("href")?.let { 
                            if (it.startsWith("http")) it else "${config.baseUrl}$it"
                        } ?: ""
                        
                        if (title.isNotEmpty() && videoUrl.isNotEmpty()) {
                            val thumbEl = container.select(config.selectors.thumbnailUrl).first()
                            val thumbnail = thumbEl?.attr("src")?.ifEmpty { thumbEl.attr("data-src") } ?: ""
                            
                            val duration = container.select(config.selectors.duration).first()?.text() ?: ""
                            val views = container.select(config.selectors.views).first()?.text() ?: ""
                            val channel = container.select(config.selectors.channel).first()?.text() ?: ""
                            
                            results.add(VideoResult(
                                title = title,
                                videoUrl = videoUrl,
                                thumbnailUrl = if (thumbnail.startsWith("http")) thumbnail else "${config.baseUrl}$thumbnail",
                                duration = duration,
                                views = views,
                                channel = channel,
                                uploadDate = "",
                                source = config.name,
                                siteType = config.siteType
                            ))
                        }
                    } catch (e: Exception) {
                        // Skip this result
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed: ${e.message}")
        }
        
        results
    }
    
    private suspend fun searchViaApi(config: VideoSiteConfig, query: String): List<VideoResult> {
        val results = mutableListOf<VideoResult>()
        
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val apiUrl = when (config.siteType) {
                VideoSiteType.PEERTUBE -> "${config.apiEndpoint}?search=$encodedQuery&count=50"
                VideoSiteType.YOUTUBE -> "${config.instanceUrl}/api/v1/search?q=$encodedQuery&type=video"
                else -> "${config.apiEndpoint}?q=$encodedQuery"
            }
            
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return results
                
                val json = response.body?.string() ?: return results
                
                when (config.siteType) {
                    VideoSiteType.PEERTUBE -> parsePeerTubeResults(json, config, results)
                    VideoSiteType.YOUTUBE -> parseInvidiousResults(json, config, results)
                    else -> parseGenericApiResults(json, config, results)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "API search failed: ${e.message}")
        }
        
        return results
    }
    
    private fun parsePeerTubeResults(json: String, config: VideoSiteConfig, results: MutableList<VideoResult>) {
        try {
            val obj = JSONObject(json)
            val data = obj.optJSONArray("data") ?: return
            
            for (i in 0 until data.length()) {
                val video = data.getJSONObject(i)
                results.add(VideoResult(
                    title = video.optString("name"),
                    videoUrl = "${config.baseUrl}/videos/watch/${video.optString("uuid")}",
                    thumbnailUrl = video.optString("thumbnailPath").let { 
                        if (it.startsWith("http")) it else "${config.baseUrl}$it"
                    },
                    duration = formatDuration(video.optInt("duration")),
                    views = "${video.optInt("views")} views",
                    channel = video.optJSONObject("channel")?.optString("displayName") ?: "",
                    uploadDate = video.optString("publishedAt").take(10),
                    source = config.name,
                    siteType = VideoSiteType.PEERTUBE
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "PeerTube parse error: ${e.message}")
        }
    }
    
    private fun parseInvidiousResults(json: String, config: VideoSiteConfig, results: MutableList<VideoResult>) {
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val video = arr.getJSONObject(i)
                if (video.optString("type") != "video") continue
                
                val videoId = video.optString("videoId")
                results.add(VideoResult(
                    title = video.optString("title"),
                    videoUrl = "${config.instanceUrl}/watch?v=$videoId",
                    thumbnailUrl = video.optJSONArray("videoThumbnails")?.optJSONObject(0)?.optString("url") ?: "",
                    duration = formatDuration(video.optInt("lengthSeconds")),
                    views = "${video.optLong("viewCount")} views",
                    channel = video.optString("author"),
                    uploadDate = video.optString("publishedText"),
                    source = config.name,
                    siteType = VideoSiteType.YOUTUBE
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Invidious parse error: ${e.message}")
        }
    }
    
    private fun parseGenericApiResults(json: String, config: VideoSiteConfig, results: MutableList<VideoResult>) {
        try {
            // Try parsing as array or object with data/items/results field
            val data = when {
                json.trim().startsWith("[") -> JSONArray(json)
                else -> {
                    val obj = JSONObject(json)
                    obj.optJSONArray("data") ?: obj.optJSONArray("items") ?: 
                    obj.optJSONArray("results") ?: obj.optJSONArray("videos") ?: return
                }
            }
            
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                
                val title = item.optString("title").ifEmpty { item.optString("name") }
                val url = item.optString("url").ifEmpty { 
                    item.optString("link").ifEmpty { 
                        item.optString("videoUrl") 
                    }
                }
                
                if (title.isNotEmpty() && url.isNotEmpty()) {
                    results.add(VideoResult(
                        title = title,
                        videoUrl = if (url.startsWith("http")) url else "${config.baseUrl}$url",
                        thumbnailUrl = item.optString("thumbnail").ifEmpty { item.optString("image") },
                        duration = item.optString("duration"),
                        views = item.optString("views"),
                        channel = item.optString("channel").ifEmpty { item.optString("author") },
                        uploadDate = item.optString("date").ifEmpty { item.optString("publishedAt") },
                        source = config.name,
                        siteType = config.siteType
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generic API parse error: ${e.message}")
        }
    }
    
    private fun buildSearchUrl(config: VideoSiteConfig, query: String): String {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val path = config.searchPath.replace("{query}", encodedQuery)
        return if (path.startsWith("http")) path else "${config.baseUrl}$path"
    }
    
    private fun formatDuration(seconds: Int): String {
        if (seconds <= 0) return ""
        val hours = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, mins, secs)
        } else {
            String.format("%d:%02d", mins, secs)
        }
    }
}
