package com.zim.jackettprowler.video

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Advanced Site Infiltration Engine
 * 
 * Automatically analyzes ANY website URL and configures it for video/torrent extraction.
 * Uses ethical penetration testing techniques to:
 * - Discover API endpoints
 * - Learn site structure
 * - Extract CSS/XPath selectors
 * - Bypass basic protections (rate limiting, user-agent checks)
 * - Auto-detect content types
 * 
 * Works like a combination of:
 * - yt-dlp extractors
 * - Scrapy spider discovery
 * - Burp Suite passive scanning
 */
class SiteInfiltrationEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "SiteInfiltrationEngine"
        
        // Common user agents for rotation
        private val USER_AGENTS = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
            "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (X11; Linux x86_64; rv:121.0) Gecko/20100101 Firefox/121.0"
        )
        
        // Known platform fingerprints for quick detection
        private val PLATFORM_FINGERPRINTS = mapOf(
            // Video platforms
            "youtube" to SiteType.VIDEO,
            "vimeo" to SiteType.VIDEO,
            "dailymotion" to SiteType.VIDEO,
            "twitch" to SiteType.VIDEO,
            "pornhub" to SiteType.ADULT_VIDEO,
            "xvideos" to SiteType.ADULT_VIDEO,
            "xnxx" to SiteType.ADULT_VIDEO,
            "xhamster" to SiteType.ADULT_VIDEO,
            "redtube" to SiteType.ADULT_VIDEO,
            "youporn" to SiteType.ADULT_VIDEO,
            "spankbang" to SiteType.ADULT_VIDEO,
            "tube8" to SiteType.ADULT_VIDEO,
            // Torrent sites
            "1337x" to SiteType.TORRENT,
            "piratebay" to SiteType.TORRENT,
            "rarbg" to SiteType.TORRENT,
            "nyaa" to SiteType.TORRENT,
            "torrentgalaxy" to SiteType.TORRENT,
            "yts" to SiteType.TORRENT,
            "eztv" to SiteType.TORRENT,
            "limetorrents" to SiteType.TORRENT,
            // Instance-based platforms
            "invidious" to SiteType.INVIDIOUS_INSTANCE,
            "piped" to SiteType.PIPED_INSTANCE,
            "peertube" to SiteType.PEERTUBE_INSTANCE
        )
        
        // API endpoint patterns to probe
        private val API_PROBE_PATHS = listOf(
            "/api/v1/search",
            "/api/v1/videos",
            "/api/v1/trending",
            "/api/v1/config",
            "/api/v2/search",
            "/api/search",
            "/api/videos",
            "/search.json",
            "/videos.json",
            "/api.php",
            "/ajax/search",
            "/graphql"
        )
        
        // Search form patterns
        private val SEARCH_INPUT_NAMES = listOf(
            "q", "query", "search", "s", "search_query", "keyword", "k", "term", "text"
        )
    }
    
    enum class SiteType {
        VIDEO,
        ADULT_VIDEO,
        TORRENT,
        INVIDIOUS_INSTANCE,
        PIPED_INSTANCE,
        PEERTUBE_INSTANCE,
        GENERIC_VIDEO,
        GENERIC_TORRENT,
        UNKNOWN
    }
    
    data class InfiltrationResult(
        val success: Boolean,
        val siteType: SiteType,
        val config: Any?, // VideoSiteConfig or CustomSiteConfig
        val confidence: Float,
        val features: List<String>,
        val apiEndpoints: List<String>,
        val warnings: List<String>,
        val error: String? = null
    )
    
    data class SiteAnalysis(
        val url: String,
        val baseUrl: String,
        val host: String,
        val title: String,
        val siteType: SiteType,
        val hasSearch: Boolean,
        val searchPath: String,
        val searchMethod: String,
        val searchParamName: String,
        val apiEndpoints: List<String>,
        val contentSelectors: ContentSelectors,
        val headers: Map<String, String>,
        val cookies: Map<String, String>,
        val isAdult: Boolean,
        val requiresJS: Boolean,
        val hasCloudflare: Boolean,
        val hasCaptcha: Boolean
    )
    
    data class ContentSelectors(
        val container: String,
        val title: String,
        val link: String,
        val thumbnail: String,
        val duration: String,
        val views: String,
        val date: String,
        val channel: String,
        val size: String,
        val seeders: String,
        val leechers: String,
        val magnet: String
    )
    
    private val cookieJar = object : CookieJar {
        private val cookies = mutableMapOf<String, List<Cookie>>()
        
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies[url.host] = cookies
        }
        
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookies[url.host] ?: emptyList()
        }
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(cookieJar)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", USER_AGENTS.random())
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip, deflate")
                .header("DNT", "1")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Cache-Control", "max-age=0")
                .build()
            chain.proceed(request)
        }
        .build()
    
    /**
     * Main infiltration method - analyzes any URL and creates configuration
     */
    suspend fun infiltrate(url: String): InfiltrationResult = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<String>()
        val features = mutableListOf<String>()
        
        try {
            Log.d(TAG, "Starting infiltration of: $url")
            
            // Step 1: Parse and validate URL
            val uri = try {
                URI(url)
            } catch (e: Exception) {
                return@withContext InfiltrationResult(
                    success = false,
                    siteType = SiteType.UNKNOWN,
                    config = null,
                    confidence = 0f,
                    features = emptyList(),
                    apiEndpoints = emptyList(),
                    warnings = emptyList(),
                    error = "Invalid URL: ${e.message}"
                )
            }
            
            val host = uri.host?.lowercase() ?: ""
            val baseUrl = "${uri.scheme}://${uri.host}${if (uri.port > 0 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""}"
            
            // Step 2: Quick fingerprint check
            val quickType = detectSiteTypeFromHost(host)
            if (quickType != SiteType.UNKNOWN) {
                features.add("Known platform detected")
            }
            
            // Step 3: Fetch main page
            val mainPageAnalysis = analyzeMainPage(url, baseUrl)
            if (mainPageAnalysis == null) {
                return@withContext InfiltrationResult(
                    success = false,
                    siteType = quickType,
                    config = null,
                    confidence = 0.1f,
                    features = features,
                    apiEndpoints = emptyList(),
                    warnings = listOf("Could not fetch main page"),
                    error = "Failed to connect to site"
                )
            }
            
            // Step 4: Check for protections
            if (mainPageAnalysis.hasCloudflare) {
                warnings.add("Cloudflare protection detected - may require browser")
            }
            if (mainPageAnalysis.hasCaptcha) {
                warnings.add("CAPTCHA detected - automated access limited")
            }
            if (mainPageAnalysis.requiresJS) {
                warnings.add("JavaScript required - some features may not work")
            }
            
            // Step 5: Probe for API endpoints
            val apiEndpoints = probeApiEndpoints(baseUrl)
            if (apiEndpoints.isNotEmpty()) {
                features.add("API endpoints found: ${apiEndpoints.size}")
            }
            
            // Step 6: Determine final site type
            val finalType = when {
                quickType != SiteType.UNKNOWN -> quickType
                apiEndpoints.any { it.contains("peertube") || it.contains("/api/v1/videos") } -> SiteType.PEERTUBE_INSTANCE
                apiEndpoints.any { it.contains("invidious") } -> SiteType.INVIDIOUS_INSTANCE
                mainPageAnalysis.isAdult -> SiteType.ADULT_VIDEO
                mainPageAnalysis.siteType != SiteType.UNKNOWN -> mainPageAnalysis.siteType
                else -> detectTypeFromContent(mainPageAnalysis)
            }
            
            // Step 7: Create configuration based on type
            val config = createConfiguration(mainPageAnalysis, finalType, apiEndpoints)
            
            // Calculate confidence
            val confidence = calculateConfidence(mainPageAnalysis, apiEndpoints, finalType)
            
            if (mainPageAnalysis.hasSearch) features.add("Search functionality")
            if (mainPageAnalysis.isAdult) features.add("Adult content")
            if (apiEndpoints.isNotEmpty()) features.add("API access")
            
            InfiltrationResult(
                success = true,
                siteType = finalType,
                config = config,
                confidence = confidence,
                features = features,
                apiEndpoints = apiEndpoints,
                warnings = warnings
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Infiltration failed: ${e.message}", e)
            InfiltrationResult(
                success = false,
                siteType = SiteType.UNKNOWN,
                config = null,
                confidence = 0f,
                features = features,
                apiEndpoints = emptyList(),
                warnings = warnings,
                error = e.message
            )
        }
    }
    
    private fun detectSiteTypeFromHost(host: String): SiteType {
        for ((pattern, type) in PLATFORM_FINGERPRINTS) {
            if (host.contains(pattern, ignoreCase = true)) {
                return type
            }
        }
        return SiteType.UNKNOWN
    }
    
    private suspend fun analyzeMainPage(url: String, baseUrl: String): SiteAnalysis? {
        return try {
            val request = Request.Builder()
                .url(url)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 403) {
                    return null
                }
                
                val html = response.body?.string() ?: return null
                val doc = Jsoup.parse(html, baseUrl)
                
                val host = URI(url).host?.lowercase() ?: ""
                val title = doc.title()
                
                // Detect protections
                val hasCloudflare = html.contains("cloudflare", ignoreCase = true) ||
                        html.contains("cf-browser-verification") ||
                        response.header("Server")?.contains("cloudflare", ignoreCase = true) == true
                
                val hasCaptcha = html.contains("captcha", ignoreCase = true) ||
                        html.contains("recaptcha", ignoreCase = true) ||
                        html.contains("hcaptcha", ignoreCase = true)
                
                val requiresJS = html.contains("enable javascript", ignoreCase = true) ||
                        html.contains("javascript is required", ignoreCase = true) ||
                        (doc.select("noscript").isNotEmpty() && doc.body().text().length < 500)
                
                // Detect adult content
                val isAdult = html.contains("18+", ignoreCase = true) ||
                        html.contains("adult", ignoreCase = true) ||
                        html.contains("xxx", ignoreCase = true) ||
                        html.contains("porn", ignoreCase = true) ||
                        doc.select("meta[name*='rating']").attr("content").contains("adult", ignoreCase = true)
                
                // Find search functionality
                val (hasSearch, searchPath, searchMethod, searchParamName) = detectSearchForm(doc, baseUrl)
                
                // Detect content selectors
                val contentSelectors = detectContentSelectors(doc)
                
                // Determine site type from content
                val siteType = when {
                    doc.select("[class*='torrent'], [class*='magnet'], a[href*='magnet:']").isNotEmpty() -> SiteType.TORRENT
                    doc.select("[class*='video'], video, [class*='player']").isNotEmpty() -> {
                        if (isAdult) SiteType.ADULT_VIDEO else SiteType.VIDEO
                    }
                    else -> SiteType.UNKNOWN
                }
                
                SiteAnalysis(
                    url = url,
                    baseUrl = baseUrl,
                    host = host,
                    title = title,
                    siteType = siteType,
                    hasSearch = hasSearch,
                    searchPath = searchPath,
                    searchMethod = searchMethod,
                    searchParamName = searchParamName,
                    apiEndpoints = emptyList(),
                    contentSelectors = contentSelectors,
                    headers = mapOf("User-Agent" to USER_AGENTS.first()),
                    cookies = emptyMap(),
                    isAdult = isAdult,
                    requiresJS = requiresJS,
                    hasCloudflare = hasCloudflare,
                    hasCaptcha = hasCaptcha
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze main page: ${e.message}")
            null
        }
    }
    
    private fun detectSearchForm(doc: Document, baseUrl: String): SearchFormResult {
        // Try to find search form
        val searchForms = doc.select(
            "form[action*='search'], form[role='search'], form#search, " +
            "form[class*='search'], form[id*='search'], form:has(input[type='search'])"
        )
        
        if (searchForms.isNotEmpty()) {
            val form = searchForms.first()!!
            val action = form.attr("action").let {
                when {
                    it.isBlank() -> "/search"
                    it.startsWith("http") -> URI(it).path
                    it.startsWith("/") -> it
                    else -> "/$it"
                }
            }
            val method = form.attr("method").uppercase().ifEmpty { "GET" }
            
            // Find input name
            val inputName = form.select("input[type='search'], input[type='text'], input[name]")
                .firstOrNull { input ->
                    val name = input.attr("name").lowercase()
                    SEARCH_INPUT_NAMES.any { name.contains(it) } || name.isNotEmpty()
                }?.attr("name") ?: "q"
            
            return SearchFormResult(true, action, method, inputName)
        }
        
        // Fallback: look for search input anywhere
        val searchInput = doc.select("input[type='search'], input[placeholder*='search' i], input[name='q']")
        if (searchInput.isNotEmpty()) {
            return SearchFormResult(true, "/search", "GET", searchInput.first()?.attr("name") ?: "q")
        }
        
        return SearchFormResult(false, "/search", "GET", "q")
    }
    
    data class SearchFormResult(
        val hasSearch: Boolean,
        val searchPath: String,
        val method: String,
        val paramName: String
    )
    
    private fun detectContentSelectors(doc: Document): ContentSelectors {
        // Video/content container detection
        val containerPatterns = listOf(
            // Specific patterns
            "div.video-item", "div.video-box", "div.video-card", "article.video",
            "li.video", "div.thumb-block", "div.thumb", "div.item",
            // Torrent patterns
            "tr.torrent", "div.torrent-item", "li.torrent", "table.torrents tbody tr",
            // Generic patterns
            "div[class*='video']", "div[class*='item']", "article", "li[class*='video']"
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
        
        // Now detect selectors within container
        val sampleContainer = if (container.isNotEmpty()) doc.select(container).firstOrNull() else doc.body()
        
        val titleSelector = detectSelector(sampleContainer, listOf(
            "h3 a", "h2 a", ".title a", "a.title", ".name a", "a.name",
            "[class*='title'] a", "a[title]", "h4 a", "span.title"
        ), "title")
        
        val linkSelector = detectSelector(sampleContainer, listOf(
            "a[href*='video']", "a[href*='watch']", "a[href*='view']",
            "a[href*='torrent']", "a[href*='download']", "a.thumb", "a"
        ), "link")
        
        val thumbnailSelector = detectSelector(sampleContainer, listOf(
            "img.thumb", "img[class*='thumb']", "img[data-src]", "img[src*='thumb']",
            "picture img", "img.lazy", "img"
        ), "thumbnail")
        
        val durationSelector = detectSelector(sampleContainer, listOf(
            ".duration", "span.duration", "[class*='duration']", ".time", ".length"
        ), "duration")
        
        val viewsSelector = detectSelector(sampleContainer, listOf(
            ".views", "span.views", "[class*='views']", ".view-count"
        ), "views")
        
        val dateSelector = detectSelector(sampleContainer, listOf(
            ".date", "time", "[class*='date']", ".uploaded", ".added"
        ), "date")
        
        val channelSelector = detectSelector(sampleContainer, listOf(
            ".channel", ".author", ".uploader", "a[href*='channel']", "a[href*='user']"
        ), "channel")
        
        // Torrent-specific
        val sizeSelector = detectSelector(sampleContainer, listOf(
            ".size", "td.size", "[class*='size']", ".filesize"
        ), "size")
        
        val seedersSelector = detectSelector(sampleContainer, listOf(
            ".seeders", ".seeds", "td.seeds", "[class*='seed']", "font[color='green']"
        ), "seeders")
        
        val leechersSelector = detectSelector(sampleContainer, listOf(
            ".leechers", ".leeches", "td.leeches", "[class*='leech']", "font[color='red']"
        ), "leechers")
        
        val magnetSelector = detectSelector(sampleContainer, listOf(
            "a[href^='magnet:']", "a[href*='magnet']", ".magnet a"
        ), "magnet")
        
        return ContentSelectors(
            container = container,
            title = titleSelector,
            link = linkSelector,
            thumbnail = thumbnailSelector,
            duration = durationSelector,
            views = viewsSelector,
            date = dateSelector,
            channel = channelSelector,
            size = sizeSelector,
            seeders = seedersSelector,
            leechers = leechersSelector,
            magnet = magnetSelector
        )
    }
    
    private fun detectSelector(element: Element?, patterns: List<String>, fieldName: String): String {
        if (element == null) return ""
        
        for (pattern in patterns) {
            try {
                val matches = element.select(pattern)
                if (matches.isNotEmpty()) {
                    // Verify it actually has useful content
                    val text = matches.first()?.text() ?: matches.first()?.attr("href") ?: matches.first()?.attr("src")
                    if (!text.isNullOrBlank()) {
                        return pattern
                    }
                }
            } catch (e: Exception) {
                // Invalid selector, continue
            }
        }
        return ""
    }
    
    private suspend fun probeApiEndpoints(baseUrl: String): List<String> = withContext(Dispatchers.IO) {
        val foundEndpoints = mutableListOf<String>()
        
        for (path in API_PROBE_PATHS) {
            try {
                val apiUrl = "$baseUrl$path"
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("Accept", "application/json")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val contentType = response.header("Content-Type") ?: ""
                        val body = response.body?.string() ?: ""
                        
                        // Check if it's actually JSON
                        if (contentType.contains("json") || 
                            body.trim().startsWith("{") || 
                            body.trim().startsWith("[")) {
                            foundEndpoints.add(apiUrl)
                            Log.d(TAG, "Found API endpoint: $apiUrl")
                        }
                    }
                }
            } catch (e: Exception) {
                // Endpoint doesn't exist, continue
            }
        }
        
        foundEndpoints
    }
    
    private fun detectTypeFromContent(analysis: SiteAnalysis): SiteType {
        return when {
            analysis.contentSelectors.magnet.isNotEmpty() -> SiteType.TORRENT
            analysis.contentSelectors.seeders.isNotEmpty() -> SiteType.TORRENT
            analysis.isAdult -> SiteType.ADULT_VIDEO
            analysis.contentSelectors.duration.isNotEmpty() -> SiteType.VIDEO
            analysis.contentSelectors.thumbnail.isNotEmpty() -> SiteType.GENERIC_VIDEO
            else -> SiteType.UNKNOWN
        }
    }
    
    private fun createConfiguration(
        analysis: SiteAnalysis,
        siteType: SiteType,
        apiEndpoints: List<String>
    ): Any {
        return when (siteType) {
            SiteType.VIDEO, SiteType.ADULT_VIDEO, SiteType.GENERIC_VIDEO,
            SiteType.INVIDIOUS_INSTANCE, SiteType.PIPED_INSTANCE, SiteType.PEERTUBE_INSTANCE -> {
                createVideoConfig(analysis, siteType, apiEndpoints)
            }
            SiteType.TORRENT, SiteType.GENERIC_TORRENT -> {
                createTorrentConfig(analysis)
            }
            else -> createVideoConfig(analysis, siteType, apiEndpoints)
        }
    }
    
    private fun createVideoConfig(
        analysis: SiteAnalysis,
        siteType: SiteType,
        apiEndpoints: List<String>
    ): VideoSiteConfig {
        val videoSiteType = when (siteType) {
            SiteType.INVIDIOUS_INSTANCE -> VideoSiteType.YOUTUBE
            SiteType.PIPED_INSTANCE -> VideoSiteType.YOUTUBE
            SiteType.PEERTUBE_INSTANCE -> VideoSiteType.PEERTUBE
            else -> VideoSiteType.GENERIC
        }
        
        val searchPath = if (analysis.searchPath.contains("{query}")) {
            analysis.searchPath
        } else {
            "${analysis.searchPath}?${analysis.searchParamName}={query}"
        }
        
        return VideoSiteConfig(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = analysis.title.take(30).ifEmpty { analysis.host },
            baseUrl = analysis.baseUrl,
            siteType = videoSiteType,
            instanceUrl = if (siteType in listOf(SiteType.INVIDIOUS_INSTANCE, SiteType.PIPED_INSTANCE, SiteType.PEERTUBE_INSTANCE)) analysis.baseUrl else "",
            apiEndpoint = apiEndpoints.firstOrNull() ?: "",
            searchPath = searchPath,
            selectors = VideoSelectors(
                container = analysis.contentSelectors.container,
                videoContainer = analysis.contentSelectors.container,
                title = analysis.contentSelectors.title,
                videoTitle = analysis.contentSelectors.title,
                thumbnailUrl = analysis.contentSelectors.thumbnail,
                thumbnail = analysis.contentSelectors.thumbnail,
                videoUrl = analysis.contentSelectors.link,
                duration = analysis.contentSelectors.duration,
                views = analysis.contentSelectors.views,
                channel = analysis.contentSelectors.channel,
                uploadDate = analysis.contentSelectors.date
            ),
            isEnabled = true,
            isAdult = analysis.isAdult
        )
    }
    
    private fun createTorrentConfig(analysis: SiteAnalysis): com.zim.jackettprowler.CustomSiteConfig {
        val searchPath = if (analysis.searchPath.contains("{query}")) {
            analysis.searchPath
        } else {
            "${analysis.searchPath}?${analysis.searchParamName}={query}"
        }
        
        return com.zim.jackettprowler.CustomSiteConfig(
            id = "custom_torrent_${UUID.randomUUID().toString().take(8)}",
            name = analysis.title.take(30).ifEmpty { analysis.host },
            baseUrl = analysis.baseUrl,
            searchPath = searchPath,
            searchParamName = analysis.searchParamName,
            selectors = com.zim.jackettprowler.ScraperSelectors(
                resultContainer = analysis.contentSelectors.container,
                title = analysis.contentSelectors.title,
                downloadUrl = analysis.contentSelectors.link,
                magnetUrl = analysis.contentSelectors.magnet,
                size = analysis.contentSelectors.size,
                seeders = analysis.contentSelectors.seeders,
                leechers = analysis.contentSelectors.leechers,
                publishDate = analysis.contentSelectors.date,
                torrentPageUrl = analysis.contentSelectors.link
            ),
            headers = analysis.headers,
            enabled = true
        )
    }
    
    private fun calculateConfidence(
        analysis: SiteAnalysis,
        apiEndpoints: List<String>,
        siteType: SiteType
    ): Float {
        var score = 0f
        
        // Base scores
        if (siteType != SiteType.UNKNOWN) score += 0.2f
        if (analysis.hasSearch) score += 0.2f
        if (apiEndpoints.isNotEmpty()) score += 0.2f
        
        // Selector scores
        if (analysis.contentSelectors.container.isNotEmpty()) score += 0.15f
        if (analysis.contentSelectors.title.isNotEmpty()) score += 0.1f
        if (analysis.contentSelectors.thumbnail.isNotEmpty()) score += 0.05f
        if (analysis.contentSelectors.link.isNotEmpty()) score += 0.05f
        
        // Penalties
        if (analysis.hasCloudflare) score -= 0.1f
        if (analysis.hasCaptcha) score -= 0.15f
        if (analysis.requiresJS) score -= 0.1f
        
        return score.coerceIn(0f, 1f)
    }
    
    /**
     * Test if a configured site works by performing a test search
     */
    suspend fun testConfiguration(config: Any, testQuery: String = "test"): TestResult = withContext(Dispatchers.IO) {
        try {
            when (config) {
                is VideoSiteConfig -> {
                    val videoService = VideoSearchService(context)
                    val results = videoService.searchSite(config, testQuery, 5)
                    TestResult(
                        success = results.isNotEmpty(),
                        resultCount = results.size,
                        sampleTitle = results.firstOrNull()?.title,
                        error = if (results.isEmpty()) "No results found" else null
                    )
                }
                is com.zim.jackettprowler.CustomSiteConfig -> {
                    val scraperService = com.zim.jackettprowler.ScraperService(null, context)
                    val results = scraperService.search(config, testQuery, 5)
                    TestResult(
                        success = results.isNotEmpty(),
                        resultCount = results.size,
                        sampleTitle = results.firstOrNull()?.title,
                        error = if (results.isEmpty()) "No results found" else null
                    )
                }
                else -> TestResult(false, 0, null, "Unknown config type")
            }
        } catch (e: Exception) {
            TestResult(false, 0, null, e.message)
        }
    }
    
    data class TestResult(
        val success: Boolean,
        val resultCount: Int,
        val sampleTitle: String?,
        val error: String?
    )
}
