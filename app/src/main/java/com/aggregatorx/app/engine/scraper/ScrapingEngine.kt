package com.aggregatorx.app.engine.scraper

import com.aggregatorx.app.data.database.ProviderDao
import com.aggregatorx.app.data.database.ScrapingConfigDao
import com.aggregatorx.app.data.database.SiteAnalysisDao
import com.aggregatorx.app.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Advanced Multi-Provider Scraping Engine
 * 
 * Features:
 * - Concurrent scraping across all enabled providers
 * - Smart navigation to bypass category pages
 * - Intelligent fallback mechanisms
 * - Result scoring and ranking
 * - Rate limiting and retry logic
 * - Pattern-based content extraction
 * - Error resilience with provider isolation
 * - Thumbnail extraction for video previews
 */
@Singleton
class ScrapingEngine @Inject constructor(
    private val providerDao: ProviderDao,
    private val scrapingConfigDao: ScrapingConfigDao,
    private val siteAnalysisDao: SiteAnalysisDao,
    private val smartNavigationEngine: SmartNavigationEngine
) {
    fun clearCache() {
        synchronized(resultCache) {
            resultCache.clear()
        }
    }
    
    // Provider health tracking
    private val providerHealthMap = ConcurrentHashMap<String, ProviderHealth>()
    
    // Rate limiting
    private val lastRequestTime = ConcurrentHashMap<String, Long>()
    
    companion object {
        private const val DEFAULT_TIMEOUT = 15000
        private const val DEFAULT_RETRY_COUNT = 2
        private const val DEFAULT_RETRY_DELAY = 500L
        private const val DEFAULT_RATE_LIMIT_MS = 100L
        private const val MAX_CONCURRENT_PROVIDERS = 15
        private const val DEFAULT_USER_AGENT = 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        
        // Alternate user agents for rotation
        private val USER_AGENTS = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
    }
    
    /**
     * Search across all enabled providers
     * Returns a Flow that emits results as they come in from each provider
     * 
     * RESILIENT DESIGN:
     * - Never breaks the loop even if individual providers fail
     * - Sorts providers by success rate (best performers first)
     * - Emits results progressively as they complete
     * - Includes comprehensive error handling per-provider
     * - Auto-retries with fallback strategies
     */
    // In-memory cache for popular queries (simple LRU)
    private val resultCache = object : LinkedHashMap<String, List<ProviderSearchResults>>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<ProviderSearchResults>>?): Boolean {
            return size > 100
        }
    }

    var cacheResults: Boolean = true

    fun searchAllProviders(query: String, cache: Boolean = cacheResults): Flow<ProviderSearchResults> = flow {
        // Check cache first
        if (cache) {
            val cachedResults = synchronized(resultCache) {
                resultCache[query]
            }
            if (cachedResults != null) {
                cachedResults.forEach { emit(it) }
                return@flow
            }
        }

        var enabledProviders = providerDao.getEnabledProvidersSync()
        if (enabledProviders.isEmpty()) {
            return@flow
        }

        // Sort providers by success rate and avg response time (most successful first)
        enabledProviders = enabledProviders.sortedWith(
            compareByDescending<Provider> { it.successRate }
                .thenBy { it.avgResponseTime }
                .thenByDescending { it.totalSearches }
        )

        // Create a semaphore for rate limiting concurrent requests
        val semaphore = Semaphore(MAX_CONCURRENT_PROVIDERS)
        val results = mutableListOf<ProviderSearchResults>()

        // Search all providers concurrently - NEVER BREAK THE LOOP
        coroutineScope {
            val deferredResults = enabledProviders.map { provider ->
                async {
                    semaphore.withPermit {
                        // Wrap in comprehensive try-catch to ensure no single provider breaks the loop
                        safeSearchProvider(provider, query)
                    }
                }
            }

            // Emit results as they complete - use individual try-catch for each
            deferredResults.forEach { deferred ->
                try {
                    val result = deferred.await()
                    results.add(result)
                    emit(result)
                } catch (e: Exception) {
                    // Even if await fails somehow, don't break - create failed result
                    // This should never happen with safeSearchProvider, but extra safety
                    val failedResult = ProviderSearchResults(
                        provider = enabledProviders.firstOrNull() ?: return@forEach,
                        results = emptyList(),
                        searchTime = 0L,
                        success = false,
                        errorMessage = "Unexpected error: ${e.message}"
                    )
                    results.add(failedResult)
                    emit(failedResult)
                }
            }
        }

        // Cache successful results
        if (cache && results.any { it.success }) {
            synchronized(resultCache) {
                resultCache[query] = results
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Safe provider search that NEVER throws - always returns a result
     */
    private suspend fun safeSearchProvider(provider: Provider, query: String): ProviderSearchResults {
        return try {
            // Check if provider is in cooldown due to repeated failures
            if (provider.failedSearches > 5 && 
                provider.failedSearches.toFloat() / maxOf(provider.totalSearches, 1).toFloat() > 0.7f) {
                ProviderSearchResults(
                    provider = provider,
                    results = emptyList(),
                    searchTime = 0L,
                    success = false,
                    errorMessage = "Provider in cooldown (${provider.failedSearches} failures)"
                )
            } else {
                // Try smart search first
                searchProviderSmart(provider, query)
            }
        } catch (e: Exception) {
            // Primary search failed - try fallback
            try {
                tryFallbackScraping(provider, query, System.currentTimeMillis(), e)
            } catch (fallbackEx: Exception) {
                // All methods failed - return graceful failure
                ProviderSearchResults(
                    provider = provider,
                    results = emptyList(),
                    searchTime = 0L,
                    success = false,
                    errorMessage = "All search methods failed: ${e.message?.take(100)}"
                )
            }
        }
    }
    
    /**
     * Smart search that uses SmartNavigationEngine to bypass category pages
     */
    suspend fun searchProviderSmart(provider: Provider, query: String): ProviderSearchResults {
        val startTime = System.currentTimeMillis()
        
        return try {
            // Rate limiting
            enforceRateLimit(provider.id)
            
            // Update search count
            providerDao.incrementSearchCount(provider.id)
            
            // First, try to find the search URL using SmartNavigationEngine
            val smartSearchUrl = smartNavigationEngine.findSearchUrl(provider.baseUrl, query)
            
            if (smartSearchUrl != null) {
                // Use smart navigation to search
                val results = scrapeWithSmartNavigation(provider, query, smartSearchUrl)
                if (results.isNotEmpty()) {
                    updateProviderHealth(provider.id, true, System.currentTimeMillis() - startTime)
                    return ProviderSearchResults(
                        provider = provider,
                        results = results,
                        searchTime = System.currentTimeMillis() - startTime,
                        success = true
                    )
                }
            }
            
            // Fall back to normal search
            searchProvider(provider, query)
        } catch (e: Exception) {
            searchProvider(provider, query) // Use normal fallback
        }
    }
    
    /**
     * Scrape using smart navigation
     */
    private suspend fun scrapeWithSmartNavigation(
        provider: Provider,
        query: String,
        searchUrl: String
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val document = fetchDocument(searchUrl)
        
        // Check if we landed on a category page
        if (smartNavigationEngine.isCategoryPage(searchUrl, document)) {
            // Try to navigate past it
            val result = smartNavigationEngine.navigatePastCategory(provider.baseUrl, document, query)
            if (result != null) {
                val (newUrl, contentDoc) = result
                return@withContext extractResultsWithThumbnails(contentDoc, provider, query)
            }
        }
        
        // Extract results with thumbnail support
        extractResultsWithThumbnails(document, provider, query)
    }
    
    /**
     * Extract results with thumbnail URLs
     */
    private fun extractResultsWithThumbnails(
        document: Document,
        provider: Provider,
        query: String
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        
        // Use SmartNavigationEngine to extract content links
        val contentLinks = smartNavigationEngine.extractContentLinks(document, provider.baseUrl)
        
        for ((url, thumbnailUrl) in contentLinks) {
            // Extract title from URL or find in document
            val title = extractTitleFromUrl(url) ?: findTitleInDocument(document, url) ?: continue
            
            // Only include results that match the query
            if (matchesQuery(title, query)) {
                results.add(SearchResult(
                    title = title,
                    url = url,
                    thumbnailUrl = thumbnailUrl,
                    providerId = provider.id,
                    providerName = provider.name,
                    relevanceScore = calculateRelevance(title, query)
                ))
            }
        }
        
        // Also try generic extraction if not enough results
        if (results.size < 5) {
            results.addAll(extractResultsGeneric(document, provider, query))
        }
        
        return results.distinctBy { it.url }
    }
    
    private fun extractTitleFromUrl(url: String): String? {
        return try {
            val path = java.net.URL(url).path
            val segments = path.split("/").filter { it.isNotBlank() }
            segments.lastOrNull()
                ?.replace("-", " ")
                ?.replace("_", " ")
                ?.replace(Regex("\\.[a-z]{3,4}$"), "")
                ?.replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun findTitleInDocument(document: Document, url: String): String? {
        return document.select("a[href='$url'], a[href*='${url.substringAfterLast("/")}']")
            .firstOrNull()?.text()?.takeIf { it.isNotBlank() }
    }
    
    private fun matchesQuery(text: String, query: String): Boolean {
        val queryWords = query.lowercase().split(" ").filter { it.length > 2 }
        val textLower = text.lowercase()
        return queryWords.any { textLower.contains(it) }
    }
    
    private fun calculateRelevance(title: String, query: String): Float {
        val queryWords = query.lowercase().split(" ")
        val titleWords = title.lowercase().split(" ")
        val matchCount = queryWords.count { qw -> titleWords.any { it.contains(qw) } }
        return (matchCount.toFloat() / queryWords.size.coerceAtLeast(1)) * 100
    }
    
    /**
     * Search a single provider with full error handling and fallback
     */
    suspend fun searchProvider(provider: Provider, query: String): ProviderSearchResults {
        val startTime = System.currentTimeMillis()
        
        return try {
            // Rate limiting
            enforceRateLimit(provider.id)
            
            // Update search count
            providerDao.incrementSearchCount(provider.id)
            
            // Get scraping config or use analysis
            val config = scrapingConfigDao.getConfigForProvider(provider.id)
            val analysis = siteAnalysisDao.getLatestAnalysis(provider.id)
            
            // Try primary scraping method
            val results = when {
                config != null -> scrapeWithConfig(provider, query, config)
                analysis != null -> scrapeWithAnalysis(provider, query, analysis)
                else -> scrapeGeneric(provider, query)
            }
            
            // Update provider health on success
            updateProviderHealth(provider.id, true, System.currentTimeMillis() - startTime)
            
            ProviderSearchResults(
                provider = provider,
                results = results,
                searchTime = System.currentTimeMillis() - startTime,
                success = true
            )
        } catch (e: Exception) {
            // Try fallback methods
            tryFallbackScraping(provider, query, startTime, e)
        }
    }
    
    /**
     * Scrape using stored configuration
     */
    private suspend fun scrapeWithConfig(
        provider: Provider, 
        query: String, 
        config: ScrapingConfig
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, config.encoding)
        val searchUrl = config.searchUrlTemplate
            .replace("{baseUrl}", provider.baseUrl)
            .replace("{query}", encodedQuery)
            .replace("{page}", "1")
        
        val document = fetchDocument(searchUrl, config)
        extractResultsWithConfig(document, provider, query, config)
    }
    
    /**
     * Scrape using site analysis data
     */
    private suspend fun scrapeWithAnalysis(
        provider: Provider, 
        query: String, 
        analysis: SiteAnalysis
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        // Build search URL from analysis
        val searchUrl = buildSearchUrl(provider, query, analysis)
        
        val document = fetchDocument(searchUrl)
        extractResultsWithAnalysis(document, provider, query, analysis)
    }
    
    /**
     * Generic scraping for sites without configuration
     */
    private suspend fun scrapeGeneric(
        provider: Provider, 
        query: String
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        // Try common search URL patterns
        val searchPatterns = listOf(
            "${provider.baseUrl}/search?q=${URLEncoder.encode(query, "UTF-8")}",
            "${provider.baseUrl}/search?query=${URLEncoder.encode(query, "UTF-8")}",
            "${provider.baseUrl}/search/${URLEncoder.encode(query, "UTF-8")}",
            "${provider.baseUrl}/?s=${URLEncoder.encode(query, "UTF-8")}",
            "${provider.baseUrl}/search.php?q=${URLEncoder.encode(query, "UTF-8")}"
        )
        
        for (pattern in searchPatterns) {
            try {
                val document = fetchDocument(pattern)
                val results = extractResultsGeneric(document, provider, query)
                if (results.isNotEmpty()) {
                    return@withContext results
                }
            } catch (e: Exception) {
                continue
            }
        }
        
        emptyList()
    }
    
    /**
     * Fallback scraping when primary method fails
     */
    private suspend fun tryFallbackScraping(
        provider: Provider,
        query: String,
        startTime: Long,
        originalException: Exception
    ): ProviderSearchResults {
        // Mark initial failure
        providerDao.incrementFailedCount(provider.id)
        
        // Try alternative methods
        val fallbackMethods: List<suspend () -> List<SearchResult>> = listOf(
            { scrapeGeneric(provider, query) },
            { scrapeWithAlternateUserAgent(provider, query) },
            { scrapeWithDelay(provider, query) },
            { scrapeMobileVersion(provider, query) }
        )

        for (method in fallbackMethods) {
            try {
                val results = method()
                if (results.isNotEmpty()) {
                    updateProviderHealth(provider.id, true, System.currentTimeMillis() - startTime)
                    return ProviderSearchResults(
                        provider = provider,
                        results = results,
                        searchTime = System.currentTimeMillis() - startTime,
                        success = true
                    )
                }
            } catch (e: Exception) {
                continue
            }
        }

        // Headless browser fallback (Playwright)
        try {
            val analysis = siteAnalysisDao.getLatestAnalysis(provider.id)
            val searchUrl = analysis?.searchFormSelector?.let { selector ->
                // Use selector if available, else fallback to provider.url
                provider.url
            } ?: provider.url

            val html = HeadlessBrowserHelper.fetchPageContent(searchUrl)
            if (!html.isNullOrEmpty()) {
                val doc = org.jsoup.Jsoup.parse(html, provider.url)
                val results = extractResultsWithThumbnails(doc, provider, query)
                if (results.isNotEmpty()) {
                    updateProviderHealth(provider.id, true, System.currentTimeMillis() - startTime)
                    return ProviderSearchResults(
                        provider = provider,
                        results = results,
                        searchTime = System.currentTimeMillis() - startTime,
                        success = true,
                        errorMessage = null
                    )
                }
            }
        } catch (e: Exception) {
            // Ignore and continue to fail
        }

        // All fallbacks failed
        updateProviderHealth(provider.id, false, System.currentTimeMillis() - startTime)
        return ProviderSearchResults(
            provider = provider,
            results = emptyList(),
            searchTime = System.currentTimeMillis() - startTime,
            success = false,
            errorMessage = "All scraping methods failed (including headless browser): ${originalException.message}"
        )
    }
    
    /**
     * Fetch document with retries
     */
    private suspend fun fetchDocument(
        url: String,
        config: ScrapingConfig? = null
    ): Document = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        val retryCount = config?.retryCount ?: DEFAULT_RETRY_COUNT
        val retryDelay = config?.retryDelay ?: DEFAULT_RETRY_DELAY
        
        repeat(retryCount) { attempt ->
            try {
                val connection = Jsoup.connect(url)
                    .userAgent(config?.userAgent ?: getRandomUserAgent())
                    .timeout(config?.timeout ?: DEFAULT_TIMEOUT)
                    .followRedirects(true)
                    .ignoreHttpErrors(false)
                
                // Add custom headers
                config?.headers?.let { headersJson ->
                    try {
                        val headers = parseHeaders(headersJson)
                        headers.forEach { (key, value) -> connection.header(key, value) }
                    } catch (e: Exception) { /* ignore */ }
                }
                
                // Add cookies
                config?.cookies?.let { cookiesJson ->
                    try {
                        val cookies = parseCookies(cookiesJson)
                        cookies.forEach { (key, value) -> connection.cookie(key, value) }
                    } catch (e: Exception) { /* ignore */ }
                }
                
                return@withContext connection.get()
            } catch (e: Exception) {
                lastException = e
                if (attempt < retryCount - 1) {
                    delay(retryDelay * (attempt + 1))
                }
            }
        }
        
        throw lastException ?: Exception("Failed to fetch document")
    }
    
    /**
     * Extract results using scraping configuration
     */
    private fun extractResultsWithConfig(
        document: Document,
        provider: Provider,
        query: String,
        config: ScrapingConfig
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val items = document.select(config.resultSelector)
        
        items.forEach { item ->
            try {
                val title = extractText(item, config.titleSelector)
                val url = extractUrl(item, config.urlSelector, provider.baseUrl)
                
                if (title.isNotEmpty() && url.isNotEmpty()) {
                    results.add(SearchResult(
                        providerId = provider.id,
                        providerName = provider.name,
                        title = title,
                        url = url,
                        description = config.descriptionSelector?.let { extractText(item, it) },
                        thumbnailUrl = config.thumbnailSelector?.let { extractImageUrl(item, it, provider.baseUrl) },
                        date = config.dateSelector?.let { extractText(item, it) },
                        size = config.sizeSelector?.let { extractText(item, it) },
                        seeders = config.seedersSelector?.let { extractNumber(item, it) },
                        leechers = config.leechersSelector?.let { extractNumber(item, it) },
                        rating = config.ratingSelector?.let { extractRating(item, it) },
                        category = config.categorySelector?.let { extractText(item, it) },
                        relevanceScore = calculateRelevanceScore(title, query)
                    ))
                }
            } catch (e: Exception) {
                // Skip malformed items
            }
        }
        
        return results.sortedByDescending { it.relevanceScore }
    }
    
    /**
     * Extract results using site analysis data
     */
    private fun extractResultsWithAnalysis(
        document: Document,
        provider: Provider,
        query: String,
        analysis: SiteAnalysis
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        
        // Try to find result items
        val itemSelector = analysis.resultItemSelector 
            ?: detectResultItemSelector(document)
            ?: return emptyList()
        
        val items = document.select(itemSelector)
        
        items.forEach { item ->
            try {
                val title = extractTitleFromItem(item, analysis)
                val url = extractUrlFromItem(item, provider.baseUrl)
                
                if (title.isNotEmpty() && url.isNotEmpty()) {
                    results.add(SearchResult(
                        providerId = provider.id,
                        providerName = provider.name,
                        title = title,
                        url = url,
                        description = analysis.descriptionSelector?.let { extractText(item, it) },
                        thumbnailUrl = analysis.thumbnailSelector?.let { extractImageUrl(item, it, provider.baseUrl) },
                        date = analysis.dateSelector?.let { extractText(item, it) },
                        rating = analysis.ratingSelector?.let { extractRating(item, it) },
                        relevanceScore = calculateRelevanceScore(title, query)
                    ))
                }
            } catch (e: Exception) {
                // Skip malformed items
            }
        }
        
        return results.sortedByDescending { it.relevanceScore }
    }
    
    /**
     * Generic result extraction without configuration
     */
    private fun extractResultsGeneric(
        document: Document,
        provider: Provider,
        query: String
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        
        // Try to detect result structure
        val itemSelector = detectResultItemSelector(document) ?: return emptyList()
        val items = document.select(itemSelector)
        
        items.forEach { item ->
            try {
                val title = extractBestTitle(item)
                val url = extractUrlFromItem(item, provider.baseUrl)
                
                if (title.isNotEmpty() && url.isNotEmpty() && 
                    !url.contains("javascript:") && !url.startsWith("#")) {
                    results.add(SearchResult(
                        providerId = provider.id,
                        providerName = provider.name,
                        title = title,
                        url = url,
                        description = extractBestDescription(item),
                        thumbnailUrl = extractBestThumbnail(item, provider.baseUrl),
                        relevanceScore = calculateRelevanceScore(title, query)
                    ))
                }
            } catch (e: Exception) {
                // Skip malformed items
            }
        }
        
        return results
            .distinctBy { it.url }
            .sortedByDescending { it.relevanceScore }
            .take(50)
    }
    
    /**
     * Detect result item selector dynamically
     */
    private fun detectResultItemSelector(document: Document): String? {
        val candidates = listOf(
            ".result", ".item", ".card", ".entry", ".post",
            ".video-item", ".movie-item", ".torrent-item",
            "article", ".row", "[data-item]", "[data-id]",
            ".search-result", ".listing", ".media"
        )
        
        // Find selector with most matches (but at least 2)
        return candidates
            .map { it to document.select(it).size }
            .filter { it.second >= 2 }
            .maxByOrNull { it.second }
            ?.first
    }
    
    /**
     * Build search URL from analysis
     */
    private fun buildSearchUrl(provider: Provider, query: String, analysis: SiteAnalysis): String {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        
        // Try to find search form action
        analysis.searchFormSelector?.let { selector ->
            // Use detected search pattern
        }
        
        // Default patterns
        return "${provider.baseUrl}/search?q=$encodedQuery"
    }
    
    // Extraction helper methods
    private fun extractText(element: Element, selector: String): String {
        return element.select(selector).firstOrNull()?.text()?.trim() ?: ""
    }
    
    private fun extractUrl(element: Element, selector: String, baseUrl: String): String {
        val href = element.select(selector).firstOrNull()?.attr("href") ?: ""
        return normalizeUrl(href, baseUrl)
    }
    
    private fun extractImageUrl(element: Element, selector: String, baseUrl: String): String? {
        val img = element.select(selector).firstOrNull()
        val src = img?.attr("src") 
            ?: img?.attr("data-src") 
            ?: img?.attr("data-lazy-src")
            ?: return null
        return normalizeUrl(src, baseUrl)
    }
    
    private fun extractNumber(element: Element, selector: String): Int? {
        val text = extractText(element, selector)
        return text.replace(Regex("[^0-9]"), "").toIntOrNull()
    }
    
    private fun extractRating(element: Element, selector: String): Float? {
        val text = extractText(element, selector)
        return text.replace(Regex("[^0-9.]"), "").toFloatOrNull()
    }
    
    private fun extractTitleFromItem(item: Element, analysis: SiteAnalysis): String {
        // Try analysis selector first
        analysis.titleSelector?.let { selector ->
            val text = item.select(selector).text().trim()
            if (text.isNotEmpty()) return text
        }
        return extractBestTitle(item)
    }
    
    private fun extractBestTitle(item: Element): String {
        // Try common title patterns
        val selectors = listOf(
            "h1", "h2", "h3", "h4", ".title", ".name", 
            "[class*='title']", "[class*='name']", "a[title]"
        )
        
        for (selector in selectors) {
            val text = item.select(selector).firstOrNull()?.text()?.trim()
            if (!text.isNullOrEmpty() && text.length > 2 && text.length < 500) {
                return text
            }
        }
        
        // Fallback to first link text
        return item.select("a").firstOrNull()?.text()?.trim() ?: ""
    }
    
    private fun extractUrlFromItem(item: Element, baseUrl: String): String {
        // Look for the main link
        val link = item.select("a[href]").firstOrNull()
            ?: item.select("a").firstOrNull()
        val href = link?.attr("href") ?: ""
        return normalizeUrl(href, baseUrl)
    }
    
    private fun extractBestDescription(item: Element): String? {
        val selectors = listOf(
            ".description", ".desc", ".synopsis", ".summary",
            "[class*='description']", "p", ".info"
        )
        
        for (selector in selectors) {
            val text = item.select(selector).firstOrNull()?.text()?.trim()
            if (!text.isNullOrEmpty() && text.length > 10) {
                return text.take(500)
            }
        }
        return null
    }
    
    private fun extractBestThumbnail(item: Element, baseUrl: String): String? {
        val img = item.select("img").firstOrNull()
        val src = img?.attr("src") 
            ?: img?.attr("data-src") 
            ?: img?.attr("data-lazy-src")
            ?: return null
        
        if (src.isNotEmpty() && !src.contains("placeholder") && !src.contains("blank")) {
            return normalizeUrl(src, baseUrl)
        }
        return null
    }
    
    /**
     * Calculate relevance score for ranking results
     */
    private fun calculateRelevanceScore(title: String, query: String): Float {
        val titleLower = title.lowercase()
        val queryLower = query.lowercase()
        val queryTerms = queryLower.split(Regex("\\s+"))
        
        var score = 0f
        
        // Exact match bonus
        if (titleLower.contains(queryLower)) {
            score += 50f
        }
        
        // Individual term matching
        var matchedTerms = 0
        queryTerms.forEach { term ->
            if (titleLower.contains(term)) {
                matchedTerms++
                score += 10f
                
                // Bonus for term at start
                if (titleLower.startsWith(term)) {
                    score += 15f
                }
            }
        }
        
        // Ratio of matched terms
        val matchRatio = matchedTerms.toFloat() / queryTerms.size
        score += matchRatio * 25f
        
        // Penalize very long titles
        if (title.length > 100) {
            score -= 5f
        }
        
        // Word order bonus
        if (queryTerms.size > 1) {
            val pattern = queryTerms.joinToString(".*")
            if (titleLower.matches(Regex(".*$pattern.*"))) {
                score += 10f
            }
        }
        
        return score.coerceIn(0f, 100f)
    }
    
    // Alternative scraping methods
    private suspend fun scrapeWithAlternateUserAgent(
        provider: Provider,
        query: String
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val url = "${provider.baseUrl}/search?q=${URLEncoder.encode(query, "UTF-8")}"
        USER_AGENTS.shuffled().forEach { userAgent ->
            try {
                val document = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .timeout(DEFAULT_TIMEOUT)
                    .get()
                val results = extractResultsGeneric(document, provider, query)
                if (results.isNotEmpty()) {
                    return@withContext results
                }
            } catch (e: Exception) {
                // Try next user agent
            }
        }
        emptyList()
    }
    
    private suspend fun scrapeWithDelay(
        provider: Provider,
        query: String
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        delay(2000) // Wait before retry
        scrapeGeneric(provider, query)
    }
    
    private suspend fun scrapeMobileVersion(
        provider: Provider,
        query: String
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val mobileUrl = provider.baseUrl
            .replace("www.", "m.")
            .replace("://", "://m.")
        val searchUrl = "$mobileUrl/search?q=${URLEncoder.encode(query, "UTF-8")}"
        
        try {
            val document = Jsoup.connect(searchUrl)
                .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15")
                .timeout(DEFAULT_TIMEOUT)
                .get()
            extractResultsGeneric(document, provider, query)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Utility methods
    private fun normalizeUrl(url: String, baseUrl: String): String {
        return when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$baseUrl$url"
            else -> "$baseUrl/$url"
        }
    }
    
    private fun enforceRateLimit(providerId: String) {
        val lastTime = lastRequestTime[providerId] ?: 0L
        val elapsed = System.currentTimeMillis() - lastTime
        if (elapsed < DEFAULT_RATE_LIMIT_MS) {
            Thread.sleep(DEFAULT_RATE_LIMIT_MS - elapsed)
        }
        lastRequestTime[providerId] = System.currentTimeMillis()
    }
    
    private fun updateProviderHealth(providerId: String, success: Boolean, responseTime: Long) {
        val current = providerHealthMap.getOrPut(providerId) { ProviderHealth() }
        val newHealth = if (success) {
            current.copy(
                successCount = current.successCount + 1,
                avgResponseTime = (current.avgResponseTime + responseTime) / 2,
                lastSuccess = System.currentTimeMillis()
            )
        } else {
            current.copy(
                failureCount = current.failureCount + 1,
                lastFailure = System.currentTimeMillis()
            )
        }
        providerHealthMap[providerId] = newHealth
    }
    
    private fun getRandomUserAgent(): String = USER_AGENTS.random()
    
    private fun parseHeaders(json: String): Map<String, String> {
        // Simple JSON parsing for headers
        return try {
            val cleaned = json.trim().removeSurrounding("{", "}")
            cleaned.split(",")
                .map { it.split(":").map { p -> p.trim().removeSurrounding("\"") } }
                .filter { it.size == 2 }
                .associate { it[0] to it[1] }
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private fun parseCookies(json: String): Map<String, String> = parseHeaders(json)
    
    data class ProviderHealth(
        val successCount: Int = 0,
        val failureCount: Int = 0,
        val avgResponseTime: Long = 0,
        val lastSuccess: Long = 0,
        val lastFailure: Long = 0
    )
}
