package com.aggregatorx.app.engine.analyzer

import com.aggregatorx.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.net.URL
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Advanced Site Analyzer Engine
 * 
 * Performs deep analysis of websites including:
 * - Security analysis (SSL, headers, CSP, etc.)
 * - DOM structure analysis
 * - Pattern detection (search forms, result lists, video players, etc.)
 * - API endpoint detection
 * - Content mapping for streaming and media sites
 * - Performance metrics
 */
@Singleton
class SiteAnalyzerEngine @Inject constructor() {
    
    private val json = Json { 
        prettyPrint = true 
        ignoreUnknownKeys = true
    }
    
    companion object {
        private const val DEFAULT_TIMEOUT = 30000
        private const val DEFAULT_USER_AGENT = 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        
        // Common selectors for pattern detection
        private val SEARCH_FORM_SELECTORS = listOf(
            "form[action*='search']", "form[role='search']", "form#search", 
            "form.search", ".search-form", "#searchForm", "form[method='get']"
        )
        private val SEARCH_INPUT_SELECTORS = listOf(
            "input[type='search']", "input[name*='search']", "input[name='q']",
            "input[name='query']", "input[placeholder*='search' i]", "#search-input"
        )
        private val RESULT_CONTAINER_SELECTORS = listOf(
            ".results", "#results", ".search-results", "#search-results",
            ".result-list", ".content-list", ".items", ".videos", ".movies"
        )
        private val RESULT_ITEM_SELECTORS = listOf(
            ".result", ".item", ".card", ".video-item", ".movie-item",
            ".torrent", ".row", "article", ".entry", ".post"
        )
        private val PAGINATION_SELECTORS = listOf(
            ".pagination", ".pager", ".page-numbers", ".pages",
            "nav.pagination", ".paginate", "[class*='pagination']"
        )
        private val VIDEO_PLAYER_SELECTORS = listOf(
            "video", "iframe[src*='player']", ".video-player", "#player",
            "iframe[src*='youtube']", "iframe[src*='vimeo']", ".jwplayer", ".plyr"
        )
        private val NAVIGATION_SELECTORS = listOf(
            "nav", ".navigation", ".menu", "#menu", ".navbar", "header nav"
        )
    }
    
    /**
     * Perform comprehensive site analysis
     */
    suspend fun analyzeSite(url: String, providerId: String): SiteAnalysis = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            // Normalize URL
            val normalizedUrl = normalizeUrl(url)
            val baseUrl = extractBaseUrl(normalizedUrl)
            
            // Fetch the page
            val connection = Jsoup.connect(normalizedUrl)
                .userAgent(DEFAULT_USER_AGENT)
                .timeout(DEFAULT_TIMEOUT)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .ignoreContentType(false)
            
            val response = connection.execute()
            val document = response.parse()
            val loadTime = System.currentTimeMillis() - startTime
            
            // Perform all analyses
            val securityAnalysis = analyzeSecurityHeaders(normalizedUrl, response.headers())
            val domAnalysis = analyzeDOMStructure(document)
            val patterns = detectPatterns(document)
            val mediaAnalysis = analyzeMediaContent(document)
            val apiAnalysis = detectAPIEndpoints(document, response.body())
            val navigationStructure = analyzeNavigation(document)
            val scrapingStrategy = determineScrapingStrategy(document, patterns)
            
            SiteAnalysis(
                providerId = providerId,
                url = normalizedUrl,
                analyzedAt = System.currentTimeMillis(),
                
                // Security
                securityScore = securityAnalysis.score,
                hasSSL = normalizedUrl.startsWith("https"),
                sslVersion = securityAnalysis.sslVersion,
                hasCSP = securityAnalysis.hasCSP,
                hasXFrameOptions = securityAnalysis.hasXFrameOptions,
                hasHSTS = securityAnalysis.hasHSTS,
                cookieFlags = securityAnalysis.cookieFlags,
                
                // DOM Structure
                domDepth = domAnalysis.maxDepth,
                totalElements = domAnalysis.totalElements,
                uniqueTags = domAnalysis.uniqueTags,
                formCount = domAnalysis.formCount,
                linkCount = domAnalysis.linkCount,
                scriptCount = domAnalysis.scriptCount,
                iframeCount = domAnalysis.iframeCount,
                imageCount = domAnalysis.imageCount,
                videoCount = domAnalysis.videoCount,
                
                // Patterns
                detectedPatterns = json.encodeToString(patterns),
                navigationStructure = json.encodeToString(navigationStructure),
                contentAreas = json.encodeToString(domAnalysis.contentAreas),
                searchFormSelector = patterns.find { it.type == PatternType.SEARCH_FORM }?.selector,
                searchInputSelector = findSearchInput(document),
                resultContainerSelector = patterns.find { it.type == PatternType.RESULT_LIST }?.selector,
                resultItemSelector = patterns.find { it.type == PatternType.RESULT_ITEM }?.selector,
                paginationSelector = patterns.find { it.type == PatternType.PAGINATION }?.selector,
                
                // Media
                videoPlayerType = mediaAnalysis.playerType,
                videoSourcePattern = mediaAnalysis.sourcePattern,
                thumbnailSelector = mediaAnalysis.thumbnailSelector,
                titleSelector = findTitleSelector(document, patterns),
                descriptionSelector = findDescriptionSelector(document),
                dateSelector = findDateSelector(document),
                ratingSelector = findRatingSelector(document),
                
                // API
                hasAPI = apiAnalysis.hasAPI,
                apiEndpoints = json.encodeToString(apiAnalysis.endpoints),
                apiType = apiAnalysis.type,
                
                // Performance
                loadTime = loadTime,
                resourceCount = document.select("script, link, img, video").size,
                totalSize = response.body().length.toLong(),
                
                // Scraping Config
                scrapingStrategy = scrapingStrategy,
                requiresJavaScript = detectJavaScriptRequirement(document),
                requiresAuth = detectAuthRequirement(document),
                
                // Raw data
                rawHtml = document.html().take(50000), // Limit storage
                headers = json.encodeToString(response.headers()),
                cookies = json.encodeToString(response.cookies())
            )
        } catch (e: Exception) {
            // Return minimal analysis on failure
            SiteAnalysis(
                providerId = providerId,
                url = url,
                securityScore = 0f
            )
        }
    }
    
    /**
     * Security Header Analysis
     */
    private fun analyzeSecurityHeaders(url: String, headers: Map<String, String>): SecurityAnalysisResult {
        var score = 0f
        var sslVersion: String? = null
        
        // Check SSL
        if (url.startsWith("https")) {
            score += 20f
            sslVersion = getSSLVersion(url)
        }
        
        // Check security headers
        val hasCSP = headers.keys.any { it.equals("Content-Security-Policy", ignoreCase = true) }
        if (hasCSP) score += 20f
        
        val hasXFrameOptions = headers.keys.any { it.equals("X-Frame-Options", ignoreCase = true) }
        if (hasXFrameOptions) score += 15f
        
        val hasHSTS = headers.keys.any { it.equals("Strict-Transport-Security", ignoreCase = true) }
        if (hasHSTS) score += 20f
        
        val hasXContentType = headers.keys.any { it.equals("X-Content-Type-Options", ignoreCase = true) }
        if (hasXContentType) score += 10f
        
        val hasXXSS = headers.keys.any { it.equals("X-XSS-Protection", ignoreCase = true) }
        if (hasXXSS) score += 10f
        
        val hasReferrerPolicy = headers.keys.any { it.equals("Referrer-Policy", ignoreCase = true) }
        if (hasReferrerPolicy) score += 5f
        
        // Check cookie flags
        val setCookie = headers.entries.find { it.key.equals("Set-Cookie", ignoreCase = true) }?.value
        val cookieFlags = analyzeCookieFlags(setCookie)
        
        return SecurityAnalysisResult(
            score = score,
            sslVersion = sslVersion,
            hasCSP = hasCSP,
            hasXFrameOptions = hasXFrameOptions,
            hasHSTS = hasHSTS,
            cookieFlags = cookieFlags
        )
    }
    
    private fun getSSLVersion(url: String): String? {
        return try {
            val connection = URL(url).openConnection() as? HttpsURLConnection
            connection?.connect()
            // Get cipher suite which indicates TLS version
            val cipherSuite = connection?.cipherSuite
            connection?.disconnect()
            when {
                cipherSuite?.contains("TLS13") == true -> "TLSv1.3"
                cipherSuite?.contains("TLS12") == true -> "TLSv1.2"
                cipherSuite?.contains("TLS11") == true -> "TLSv1.1"
                cipherSuite?.contains("TLS") == true -> "TLSv1.0"
                cipherSuite?.contains("SSL") == true -> "SSL"
                else -> "TLSv1.2" // Default assumption for modern servers
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun analyzeCookieFlags(cookie: String?): String {
        if (cookie == null) return "No cookies"
        val flags = mutableListOf<String>()
        if (cookie.contains("Secure", ignoreCase = true)) flags.add("Secure")
        if (cookie.contains("HttpOnly", ignoreCase = true)) flags.add("HttpOnly")
        if (cookie.contains("SameSite", ignoreCase = true)) flags.add("SameSite")
        return flags.joinToString(", ").ifEmpty { "No security flags" }
    }
    
    /**
     * DOM Structure Analysis
     */
    private fun analyzeDOMStructure(document: Document): DOMAnalysisResult {
        val allElements = document.allElements
        val uniqueTags = allElements.map { it.tagName() }.distinct().size
        
        // Calculate max depth
        var maxDepth = 0
        fun calculateDepth(element: Element, depth: Int) {
            if (depth > maxDepth) maxDepth = depth
            element.children().forEach { calculateDepth(it, depth + 1) }
        }
        document.body()?.let { calculateDepth(it, 0) }
        
        // Find content areas
        val contentAreas = findContentAreas(document)
        
        return DOMAnalysisResult(
            totalElements = allElements.size,
            uniqueTags = uniqueTags,
            maxDepth = maxDepth,
            formCount = document.select("form").size,
            linkCount = document.select("a").size,
            scriptCount = document.select("script").size,
            iframeCount = document.select("iframe").size,
            imageCount = document.select("img").size,
            videoCount = document.select("video").size,
            contentAreas = contentAreas
        )
    }
    
    private fun findContentAreas(document: Document): List<ContentArea> {
        val areas = mutableListOf<ContentArea>()
        
        // Look for main content areas
        val mainSelectors = listOf(
            "main", "#main", ".main", "#content", ".content",
            "article", ".articles", "#articles", ".container"
        )
        
        mainSelectors.forEach { selector ->
            document.select(selector).firstOrNull()?.let { element ->
                areas.add(ContentArea(
                    selector = selector,
                    tagName = element.tagName(),
                    childCount = element.children().size,
                    textLength = element.text().length,
                    confidence = calculateContentConfidence(element)
                ))
            }
        }
        
        return areas.sortedByDescending { it.confidence }
    }
    
    private fun calculateContentConfidence(element: Element): Float {
        var confidence = 0f
        
        // More text = higher confidence it's a content area
        val textLength = element.text().length
        if (textLength > 500) confidence += 0.3f
        if (textLength > 2000) confidence += 0.2f
        
        // Has links and images = likely content
        if (element.select("a").isNotEmpty()) confidence += 0.2f
        if (element.select("img").isNotEmpty()) confidence += 0.15f
        
        // Has articles or items
        if (element.select("article, .item, .card").isNotEmpty()) confidence += 0.15f
        
        return confidence.coerceAtMost(1f)
    }
    
    /**
     * Pattern Detection
     */
    private fun detectPatterns(document: Document): List<DetectedPattern> {
        val patterns = mutableListOf<DetectedPattern>()
        
        // Search Form
        SEARCH_FORM_SELECTORS.forEach { selector ->
            val elements = document.select(selector)
            if (elements.isNotEmpty()) {
                patterns.add(DetectedPattern(
                    type = PatternType.SEARCH_FORM,
                    selector = selector,
                    confidence = calculateSelectorConfidence(elements, selector),
                    sampleContent = elements.first()?.outerHtml()?.take(200),
                    occurrences = elements.size
                ))
            }
        }
        
        // Result Lists
        RESULT_CONTAINER_SELECTORS.forEach { selector ->
            val elements = document.select(selector)
            if (elements.isNotEmpty()) {
                patterns.add(DetectedPattern(
                    type = PatternType.RESULT_LIST,
                    selector = selector,
                    confidence = calculateSelectorConfidence(elements, selector),
                    sampleContent = elements.first()?.outerHtml()?.take(200),
                    occurrences = elements.size
                ))
            }
        }
        
        // Result Items
        detectResultItems(document)?.let { patterns.add(it) }
        
        // Pagination
        PAGINATION_SELECTORS.forEach { selector ->
            val elements = document.select(selector)
            if (elements.isNotEmpty()) {
                patterns.add(DetectedPattern(
                    type = PatternType.PAGINATION,
                    selector = selector,
                    confidence = calculateSelectorConfidence(elements, selector),
                    sampleContent = elements.first()?.outerHtml()?.take(200),
                    occurrences = elements.size
                ))
            }
        }
        
        // Video Players
        detectVideoPlayer(document)?.let { patterns.add(it) }
        
        // Navigation
        NAVIGATION_SELECTORS.forEach { selector ->
            val elements = document.select(selector)
            if (elements.isNotEmpty()) {
                patterns.add(DetectedPattern(
                    type = PatternType.NAVIGATION,
                    selector = selector,
                    confidence = calculateSelectorConfidence(elements, selector),
                    occurrences = elements.size
                ))
            }
        }
        
        // Additional patterns
        detectAdditionalPatterns(document, patterns)
        
        return patterns.sortedByDescending { it.confidence }
    }
    
    private fun detectResultItems(document: Document): DetectedPattern? {
        // Look for repeating structures
        val candidates = mutableMapOf<String, Int>()
        
        // Check common item selectors
        RESULT_ITEM_SELECTORS.forEach { selector ->
            val count = document.select(selector).size
            if (count >= 3) { // At least 3 items suggests a list
                candidates[selector] = count
            }
        }
        
        // Also look for data attributes
        document.select("[data-id], [data-item], [data-result]").let {
            if (it.isNotEmpty() && it.size >= 3) {
                val selector = it.first()?.let { el ->
                    when {
                        el.hasAttr("data-id") -> "[data-id]"
                        el.hasAttr("data-item") -> "[data-item]"
                        else -> "[data-result]"
                    }
                }
                selector?.let { candidates[it] = it.length }
            }
        }
        
        // Return the best candidate
        return candidates.maxByOrNull { it.value }?.let { (selector, count) ->
            DetectedPattern(
                type = PatternType.RESULT_ITEM,
                selector = selector,
                confidence = (count.toFloat() / 20).coerceAtMost(1f),
                occurrences = count
            )
        }
    }
    
    private fun detectVideoPlayer(document: Document): DetectedPattern? {
        VIDEO_PLAYER_SELECTORS.forEach { selector ->
            val elements = document.select(selector)
            if (elements.isNotEmpty()) {
                return DetectedPattern(
                    type = PatternType.VIDEO_PLAYER,
                    selector = selector,
                    confidence = 0.9f,
                    sampleContent = elements.first()?.outerHtml()?.take(300),
                    occurrences = elements.size
                )
            }
        }
        return null
    }
    
    private fun detectAdditionalPatterns(document: Document, patterns: MutableList<DetectedPattern>) {
        // Infinite scroll detection
        if (document.select("[data-infinite-scroll], .infinite-scroll, [class*='infinite']").isNotEmpty() ||
            document.html().contains("IntersectionObserver") ||
            document.html().contains("infinite")) {
            patterns.add(DetectedPattern(
                type = PatternType.INFINITE_SCROLL,
                selector = "[data-infinite-scroll]",
                confidence = 0.7f
            ))
        }
        
        // Load more button
        document.select("button:contains(Load More), a:contains(Load More), .load-more, #load-more").firstOrNull()?.let {
            patterns.add(DetectedPattern(
                type = PatternType.LOAD_MORE_BUTTON,
                selector = it.cssSelector(),
                confidence = 0.9f
            ))
        }
        
        // Thumbnail grid
        document.select(".thumbnails, .thumb-grid, .video-grid, .image-grid").firstOrNull()?.let {
            patterns.add(DetectedPattern(
                type = PatternType.THUMBNAIL_GRID,
                selector = it.cssSelector(),
                confidence = 0.85f
            ))
        }
        
        // Card layout
        document.select(".cards, .card-container, .card-grid").firstOrNull()?.let {
            patterns.add(DetectedPattern(
                type = PatternType.CARD_LAYOUT,
                selector = it.cssSelector(),
                confidence = 0.85f
            ))
        }
        
        // Filter panel
        document.select(".filters, .filter-panel, #filters, [class*='filter']").firstOrNull()?.let {
            patterns.add(DetectedPattern(
                type = PatternType.FILTER_PANEL,
                selector = it.cssSelector(),
                confidence = 0.8f
            ))
        }
        
        // Category list
        document.select(".categories, .category-list, #categories").firstOrNull()?.let {
            patterns.add(DetectedPattern(
                type = PatternType.CATEGORY_LIST,
                selector = it.cssSelector(),
                confidence = 0.85f
            ))
        }
        
        // Rating system
        document.select(".rating, .stars, [class*='rating'], [data-rating]").firstOrNull()?.let {
            patterns.add(DetectedPattern(
                type = PatternType.RATING_SYSTEM,
                selector = it.cssSelector(),
                confidence = 0.8f
            ))
        }
        
        // Login form
        document.select("form[action*='login'], form#login, .login-form, form:has(input[type='password'])").firstOrNull()?.let {
            patterns.add(DetectedPattern(
                type = PatternType.LOGIN_FORM,
                selector = it.cssSelector(),
                confidence = 0.9f
            ))
        }
    }
    
    /**
     * Media Content Analysis
     */
    private fun analyzeMediaContent(document: Document): MediaAnalysisResult {
        // Dismiss overlays/popups/ads before extracting media
        val cleanedDoc = dismissOverlaysAndAds(document)

        var playerType: String? = null
        var sourcePattern: String? = null
        var thumbnailSelector: String? = null

        // Detect video player type
        when {
            cleanedDoc.select(".jwplayer, #jwplayer").isNotEmpty() -> playerType = "JWPlayer"
            cleanedDoc.select(".video-js, .vjs-tech").isNotEmpty() -> playerType = "VideoJS"
            cleanedDoc.select(".plyr").isNotEmpty() -> playerType = "Plyr"
            cleanedDoc.select("iframe[src*='youtube']").isNotEmpty() -> playerType = "YouTube"
            cleanedDoc.select("iframe[src*='vimeo']").isNotEmpty() -> playerType = "Vimeo"
            cleanedDoc.select("video").isNotEmpty() -> playerType = "HTML5"
        }

        // Detect video source patterns
        cleanedDoc.select("video source, video[src]").firstOrNull()?.let {
            val src = it.attr("src").ifEmpty { it.attr("data-src") }
            if (src.isNotEmpty()) {
                sourcePattern = extractUrlPattern(src)
            }
        }

        // Also check for m3u8 or streaming patterns in scripts
        val scripts = cleanedDoc.select("script").html()
        when {
            scripts.contains(".m3u8") -> sourcePattern = "HLS (m3u8)"
            scripts.contains(".mpd") -> sourcePattern = "DASH (mpd)"
            scripts.contains("rtmp://") -> sourcePattern = "RTMP"
        }

        // Find thumbnail selectors
        thumbnailSelector = listOf(
            ".thumbnail img", ".thumb img", "img.thumbnail",
            ".poster", "img.poster", "[data-poster]"
        ).firstOrNull { cleanedDoc.select(it).isNotEmpty() }

        return MediaAnalysisResult(
            playerType = playerType,
            sourcePattern = sourcePattern,
            thumbnailSelector = thumbnailSelector
        )
    }

    /**
     * Remove overlays/popups/ads and auto-click close/dismiss buttons
     */
    private fun dismissOverlaysAndAds(document: Document): Document {
        val popupSelectors = listOf(
            ".popup, .modal, .overlay, .ad, .banner, .cookie, .notification, .interstitial",
            "[class*='popup']", "[class*='modal']", "[class*='overlay']", "[class*='ad']",
            "[id*='popup']", "[id*='modal']", "[id*='overlay']", "[id*='ad']"
        )
        val closeButtonSelectors = listOf(
            ".close, .dismiss, .exit, .btn-close, .close-btn, .close-button, .modal-close, .popup-close",
            "button[aria-label='Close']", "button[aria-label='Dismiss']", "[data-dismiss]", "[data-close]"
        )

        // Remove overlays/popups/ads
        popupSelectors.forEach { selector ->
            document.select(selector).forEach { it.remove() }
        }

        // Simulate auto-clicking close/dismiss buttons
        closeButtonSelectors.forEach { selector ->
            document.select(selector).forEach { it.remove() }
        }

        return document
    }
    
    /**
     * API Endpoint Detection
     */
    private fun detectAPIEndpoints(document: Document, html: String): APIAnalysisResult {
        val endpoints = mutableListOf<String>()
        var apiType: String? = null
        
        // Look for API calls in scripts
        val scripts = document.select("script").html()
        
        // REST API patterns
        val restPattern = Regex("""(?:fetch|axios|ajax|get|post)\s*\(\s*['"](\/api\/[^'"]+)['"]""", RegexOption.IGNORE_CASE)
        restPattern.findAll(scripts).forEach { match ->
            endpoints.add(match.groupValues[1])
            apiType = "REST"
        }
        
        // GraphQL patterns
        if (scripts.contains("graphql", ignoreCase = true) || scripts.contains("query {")) {
            apiType = "GraphQL"
            val graphqlPattern = Regex("""['"](\/graphql[^'"]*)['""]""")
            graphqlPattern.findAll(scripts).forEach { match ->
                endpoints.add(match.groupValues[1])
            }
        }
        
        // JSON endpoints in HTML
        val jsonPattern = Regex("""['"](https?:\/\/[^'"]+\.json)['""]""")
        jsonPattern.findAll(html).forEach { match ->
            endpoints.add(match.groupValues[1])
        }
        
        // Data attributes with URLs
        document.select("[data-api], [data-url], [data-endpoint]").forEach { el ->
            el.attr("data-api").takeIf { it.isNotEmpty() }?.let { endpoints.add(it) }
            el.attr("data-url").takeIf { it.isNotEmpty() }?.let { endpoints.add(it) }
            el.attr("data-endpoint").takeIf { it.isNotEmpty() }?.let { endpoints.add(it) }
        }
        
        return APIAnalysisResult(
            hasAPI = endpoints.isNotEmpty(),
            endpoints = endpoints.distinct(),
            type = apiType
        )
    }
    
    /**
     * Navigation Structure Analysis
     */
    private fun analyzeNavigation(document: Document): NavigationStructure {
        val menuItems = mutableListOf<NavigationItem>()
        
        // Find main navigation
        val nav = document.select("nav, .navigation, #nav, .menu, #menu").first()
        
        nav?.select("a")?.forEach { link ->
            menuItems.add(NavigationItem(
                text = link.text(),
                url = link.attr("href"),
                hasSubmenu = link.parent()?.select("ul, .submenu, .dropdown")?.isNotEmpty() == true
            ))
        }
        
        // Find categories
        val categories = document.select(".categories a, .category-list a, nav.categories a")
            .map { it.text() to it.attr("href") }
            .filter { it.first.isNotEmpty() }
        
        return NavigationStructure(
            mainMenu = menuItems,
            categories = categories.map { NavigationItem(it.first, it.second, false) }
        )
    }
    
    /**
     * Determine optimal scraping strategy
     */
    private fun determineScrapingStrategy(document: Document, patterns: List<DetectedPattern>): ScrapingStrategy {
        // Check for JavaScript dependency
        val requiresJS = detectJavaScriptRequirement(document)
        val hasAPI = patterns.any { it.type == PatternType.API_ENDPOINT }
        val hasInfiniteScroll = patterns.any { it.type == PatternType.INFINITE_SCROLL }
        
        return when {
            hasAPI -> ScrapingStrategy.API_BASED
            hasInfiniteScroll -> ScrapingStrategy.DYNAMIC_CONTENT
            requiresJS && hasAPI -> ScrapingStrategy.HYBRID
            requiresJS -> ScrapingStrategy.HEADLESS_BROWSER
            else -> ScrapingStrategy.HTML_PARSING
        }
    }
    
    private fun detectJavaScriptRequirement(document: Document): Boolean {
        // Check for SPA frameworks
        val html = document.html()
        val indicators = listOf(
            "ng-app", "data-ng", // Angular
            "__NEXT_DATA__", "_next", // Next.js
            "__NUXT__", // Nuxt.js
            "data-reactroot", "data-react", // React
            "data-v-", // Vue
            "window.__INITIAL_STATE__",
            "application/json\">{"
        )
        
        if (indicators.any { html.contains(it) }) return true
        
        // Check if content is mostly empty (JS-rendered)
        val bodyText = document.body()?.text() ?: ""
        if (bodyText.length < 100 && document.select("script").size > 3) return true
        
        return false
    }
    
    private fun detectAuthRequirement(document: Document): Boolean {
        val loginIndicators = listOf(
            "form[action*='login']", "form#login", ".login-form",
            "input[name='password']", "input[type='password']",
            ".auth-required", "#login-required"
        )
        
        return loginIndicators.any { document.select(it).isNotEmpty() }
    }
    
    // Helper functions
    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        return normalized.trimEnd('/')
    }
    
    private fun extractBaseUrl(url: String): String {
        return try {
            val u = URL(url)
            "${u.protocol}://${u.host}"
        } catch (e: Exception) {
            url
        }
    }
    
    private fun calculateSelectorConfidence(elements: Elements, selector: String): Float {
        var confidence = 0.5f
        
        // ID selectors are very specific
        if (selector.startsWith("#")) confidence += 0.3f
        
        // Class selectors with meaningful names
        if (selector.contains("search") || selector.contains("result") || 
            selector.contains("item") || selector.contains("content")) {
            confidence += 0.2f
        }
        
        // Multiple matches reduce confidence slightly
        if (elements.size > 5) confidence -= 0.1f
        
        return confidence.coerceIn(0f, 1f)
    }
    
    private fun findSearchInput(document: Document): String? {
        SEARCH_INPUT_SELECTORS.forEach { selector ->
            if (document.select(selector).isNotEmpty()) {
                return selector
            }
        }
        return null
    }
    
    private fun findTitleSelector(document: Document, patterns: List<DetectedPattern>): String? {
        val candidates = listOf(
            "h1", "h2.title", ".title", "#title", "[class*='title']",
            ".name", ".item-title", ".video-title", ".movie-title"
        )
        return candidates.firstOrNull { document.select(it).isNotEmpty() }
    }
    
    private fun findDescriptionSelector(document: Document): String? {
        val candidates = listOf(
            ".description", "#description", ".desc", ".synopsis",
            ".summary", "[class*='description']", "p.info"
        )
        return candidates.firstOrNull { document.select(it).isNotEmpty() }
    }
    
    private fun findDateSelector(document: Document): String? {
        val candidates = listOf(
            ".date", ".time", ".timestamp", "[datetime]",
            ".posted", ".published", "[class*='date']"
        )
        return candidates.firstOrNull { document.select(it).isNotEmpty() }
    }
    
    private fun findRatingSelector(document: Document): String? {
        val candidates = listOf(
            ".rating", ".stars", ".score", "[data-rating]",
            ".imdb-rating", "[class*='rating']"
        )
        return candidates.firstOrNull { document.select(it).isNotEmpty() }
    }
    
    private fun extractUrlPattern(url: String): String {
        return when {
            url.contains(".m3u8") -> "HLS"
            url.contains(".mpd") -> "DASH"
            url.contains(".mp4") -> "MP4"
            url.contains(".webm") -> "WebM"
            else -> "Unknown"
        }
    }
    
    // Data classes for internal use
    data class SecurityAnalysisResult(
        val score: Float,
        val sslVersion: String?,
        val hasCSP: Boolean,
        val hasXFrameOptions: Boolean,
        val hasHSTS: Boolean,
        val cookieFlags: String
    )
    
    data class DOMAnalysisResult(
        val totalElements: Int,
        val uniqueTags: Int,
        val maxDepth: Int,
        val formCount: Int,
        val linkCount: Int,
        val scriptCount: Int,
        val iframeCount: Int,
        val imageCount: Int,
        val videoCount: Int,
        val contentAreas: List<ContentArea>
    )
    
    data class MediaAnalysisResult(
        val playerType: String?,
        val sourcePattern: String?,
        val thumbnailSelector: String?
    )
    
    data class APIAnalysisResult(
        val hasAPI: Boolean,
        val endpoints: List<String>,
        val type: String?
    )
}

@kotlinx.serialization.Serializable
data class ContentArea(
    val selector: String,
    val tagName: String,
    val childCount: Int,
    val textLength: Int,
    val confidence: Float
)

@kotlinx.serialization.Serializable
data class NavigationStructure(
    val mainMenu: List<NavigationItem>,
    val categories: List<NavigationItem>
)

@kotlinx.serialization.Serializable
data class NavigationItem(
    val text: String,
    val url: String,
    val hasSubmenu: Boolean
)
