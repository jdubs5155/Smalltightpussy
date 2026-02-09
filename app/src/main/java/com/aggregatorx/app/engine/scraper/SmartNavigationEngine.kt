package com.aggregatorx.app.engine.scraper

import com.aggregatorx.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smart Navigation Engine
 * 
 * Intelligently navigates websites to:
 * - Bypass category screens
 * - Handle popups and overlays
 * - Find and use search functionality
 * - Navigate pagination
 * - Handle dynamic content loading
 */
@Singleton
class SmartNavigationEngine @Inject constructor() {
    
    companion object {
        private const val DEFAULT_TIMEOUT = 10000
        private const val QUICK_TIMEOUT = 5000
        private const val DEFAULT_USER_AGENT = 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        
        // Common search URL patterns to try (ordered by likelihood)
        private val SEARCH_URL_PATTERNS = listOf(
            "{base}/search?q={query}",
            "{base}/?s={query}",
            "{base}/search/{query}",
            "{base}/search?query={query}",
            "{base}/?q={query}",
            "{base}/search?s={query}",
            "{base}/videos?search={query}",
            "{base}/results?search_query={query}",
            "{base}/search.php?q={query}",
            "{base}/find?q={query}",
            "{base}/movies?search={query}",
            "{base}/search/videos/{query}"
        )
        
        // Category page indicators to detect and bypass
        private val CATEGORY_INDICATORS = listOf(
            "/category/", "/categories/", "/cat/",
            "/genre/", "/genres/", "/tag/", "/tags/",
            "/type/", "/types/", "/browse/", "/explore/",
            "/popular", "/trending", "/latest", "/new",
            "/top-rated", "/featured", "/recommended"
        )
        
        // Popup/overlay selectors to dismiss
        private val POPUP_SELECTORS = listOf(
            ".popup-close", ".modal-close", ".close-button",
            "[class*='popup'] .close", "[class*='modal'] .close",
            ".overlay-close", "#close-popup", "#close-modal",
            "[data-dismiss='modal']", ".cookie-close",
            ".ad-close", ".notification-close", ".banner-close",
            "button[aria-label='Close']", "button[aria-label='Dismiss']",
            ".adblock-close", ".adblocker-close", ".skip-ad", ".skipAds", ".ad-skip", ".ad_skip_btn", ".ad-overlay-close", ".ad-popup-close",
            ".interstitial-close", ".promo-close", ".splash-close", ".fullscreen-ad-close", ".ad-modal-close", ".ad-dismiss", ".ad-exit",
            "button.skip", "button[title*='Skip']", "button[title*='Dismiss']", "button[title*='Close']"
        )

    /**
     * Attempt to close popups/ads with retries and escalation
     */
    suspend fun closePopupsWithRetries(page: org.jsoup.nodes.Document, maxRetries: Int = 3): Boolean {
        var closedAny = false
        repeat(maxRetries) { attempt ->
            var closedThisRound = false
            for (selector in POPUP_SELECTORS) {
                val elements = page.select(selector)
                if (elements.isNotEmpty()) {
                    elements.forEach { it.remove() }
                    closedThisRound = true
                }
            }
            if (closedThisRound) closedAny = true
            if (!closedThisRound) return@repeat
        }
        return closedAny
    }
    }
    
    /**
     * Find the best search URL for a site
     */
    suspend fun findSearchUrl(baseUrl: String, query: String): String? = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        
        // Try each pattern with quick timeout
        for (pattern in SEARCH_URL_PATTERNS) {
            val searchUrl = pattern
                .replace("{base}", baseUrl.trimEnd('/'))
                .replace("{query}", encodedQuery)
            
            try {
                val response = Jsoup.connect(searchUrl)
                    .userAgent(DEFAULT_USER_AGENT)
                    .timeout(QUICK_TIMEOUT)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .execute()
                
                // Check if we got a valid search results page
                if (response.statusCode() == 200) {
                    val doc = response.parse()
                    if (isSearchResultsPage(doc)) {
                        return@withContext searchUrl
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }
        
        // Fallback: try to find search form on homepage (quick)
        try {
            val homepage = Jsoup.connect(baseUrl)
                .userAgent(DEFAULT_USER_AGENT)
                .timeout(QUICK_TIMEOUT)
                .get()
            
            val searchForm = findSearchForm(homepage)
            searchForm?.let { form ->
                return@withContext buildSearchUrlFromForm(baseUrl, form, query)
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        null
    }
    
    /**
     * Check if current page is a category page that needs bypassing
     */
    fun isCategoryPage(url: String, document: Document): Boolean {
        // Check URL patterns
        val urlLower = url.lowercase()
        if (CATEGORY_INDICATORS.any { urlLower.contains(it) }) {
            return true
        }
        
        // Check page content
        val hasSearchResults = document.select(
            ".results, .search-results, #results, [class*='result'], " +
            "[class*='item'], .video-list, .movie-list"
        ).isNotEmpty()
        
        val hasCategoryList = document.select(
            ".categories, .category-list, .genre-list, .tags, " +
            "nav.categories, .browse-categories"
        ).size > 5
        
        // If has many categories but few results, it's a category page
        return hasCategoryList && !hasSearchResults
    }
    
    /**
     * Check if page appears to be search results
     */
    fun isSearchResultsPage(document: Document): Boolean {
        // Look for result indicators
        val resultIndicators = listOf(
            ".results", ".search-results", "#search-results",
            "[class*='result-item']", "[class*='search-item']",
            ".video-item", ".movie-item", ".torrent-item",
            "article.item", ".card", ".entry"
        )
        
        for (selector in resultIndicators) {
            val elements = document.select(selector)
            if (elements.size >= 3) {
                return true
            }
        }
        
        // Check for "no results" indicators (still a search page)
        val noResultsText = document.text().lowercase()
        if (noResultsText.contains("no results") || 
            noResultsText.contains("nothing found") ||
            noResultsText.contains("0 results")) {
            return true
        }
        
        return false
    }
    
    /**
     * Find search form on page
     */
    fun findSearchForm(document: Document): Element? {
        val formSelectors = listOf(
            "form[action*='search']",
            "form[role='search']",
            "form#search",
            "form.search",
            "form#searchForm",
            "form.search-form",
            "form:has(input[type='search'])",
            "form:has(input[name='q'])",
            "form:has(input[name='query'])",
            "form:has(input[name='search'])",
            "form:has(input[name='s'])"
        )
        
        for (selector in formSelectors) {
            val form = document.select(selector).firstOrNull()
            if (form != null) return form
        }
        
        return null
    }
    
    /**
     * Build search URL from form element
     */
    fun buildSearchUrlFromForm(baseUrl: String, form: Element, query: String): String {
        val action = form.attr("action")
        val method = form.attr("method").lowercase()
        
        // Find input name
        val inputNames = listOf("q", "query", "search", "s", "keyword", "term")
        var inputName = "q"
        
        for (name in inputNames) {
            if (form.select("input[name='$name']").isNotEmpty()) {
                inputName = name
                break
            }
        }
        
        // Build URL
        val searchBase = when {
            action.startsWith("http") -> action
            action.startsWith("/") -> "${baseUrl.trimEnd('/')}$action"
            action.isEmpty() -> baseUrl
            else -> "${baseUrl.trimEnd('/')}/$action"
        }
        
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        
        return if (searchBase.contains("?")) {
            "$searchBase&$inputName=$encodedQuery"
        } else {
            "$searchBase?$inputName=$encodedQuery"
        }
    }
    
    /**
     * Navigate past category page to search
     */
    suspend fun navigatePastCategory(
        baseUrl: String,
        document: Document,
        query: String
    ): Pair<String, Document>? = withContext(Dispatchers.IO) {
        // First try to find search on current page
        val searchForm = findSearchForm(document)
        if (searchForm != null) {
            val searchUrl = buildSearchUrlFromForm(baseUrl, searchForm, query)
            try {
                val newDoc = Jsoup.connect(searchUrl)
                    .userAgent(DEFAULT_USER_AGENT)
                    .timeout(DEFAULT_TIMEOUT)
                    .get()
                return@withContext Pair(searchUrl, newDoc)
            } catch (e: Exception) {
                // Continue trying other methods
            }
        }
        
        // Try common search URL patterns
        val workingUrl = findSearchUrl(baseUrl, query)
        if (workingUrl != null) {
            try {
                val newDoc = Jsoup.connect(workingUrl)
                    .userAgent(DEFAULT_USER_AGENT)
                    .timeout(DEFAULT_TIMEOUT)
                    .get()
                return@withContext Pair(workingUrl, newDoc)
            } catch (e: Exception) {
                // Continue
            }
        }
        
        null
    }
    
    /**
     * Extract all possible result links from a page
     * (handles both search results and category listings)
     */
    fun extractContentLinks(document: Document, baseUrl: String): List<ContentLink> {
        val links = mutableListOf<ContentLink>()
        
        // Common content item selectors
        val itemSelectors = listOf(
            ".video-item", ".movie-item", ".item", ".card",
            ".result", ".entry", "article", ".post",
            ".torrent", "[class*='video']", "[class*='movie']",
            "[data-id]", "[data-video-id]", ".thumb"
        )
        
        for (selector in itemSelectors) {
            document.select(selector).forEach { item ->
                val link = item.select("a[href]").firstOrNull()
                val title = extractItemTitle(item)
                val thumbnail = extractItemThumbnail(item, baseUrl)
                val duration = extractItemDuration(item)
                
                if (link != null && title.isNotEmpty()) {
                    val href = link.attr("href")
                    val fullUrl = normalizeUrl(href, baseUrl)
                    
                    if (isContentUrl(fullUrl)) {
                        links.add(ContentLink(
                            url = fullUrl,
                            title = title,
                            thumbnail = thumbnail,
                            duration = duration
                        ))
                    }
                }
            }
        }
        
        return links.distinctBy { it.url }
    }
    
    /**
     * Extract title from item
     */
    private fun extractItemTitle(item: Element): String {
        val titleSelectors = listOf(
            "h1", "h2", "h3", "h4", ".title", ".name",
            "[class*='title']", "a[title]", ".video-title"
        )
        
        for (selector in titleSelectors) {
            val text = item.select(selector).firstOrNull()?.text()?.trim()
            if (!text.isNullOrEmpty() && text.length > 2) {
                return text
            }
        }
        
        // Fallback to link text
        return item.select("a").firstOrNull()?.text()?.trim() ?: ""
    }
    
    /**
     * Extract thumbnail from item
     */
    private fun extractItemThumbnail(item: Element, baseUrl: String): String? {
        val img = item.select("img").firstOrNull()
        
        val src = img?.attr("src")?.takeIf { it.isNotEmpty() }
            ?: img?.attr("data-src")?.takeIf { it.isNotEmpty() }
            ?: img?.attr("data-lazy-src")?.takeIf { it.isNotEmpty() }
            ?: img?.attr("data-original")?.takeIf { it.isNotEmpty() }
            ?: item.attr("data-thumb")?.takeIf { it.isNotEmpty() }
            ?: item.attr("data-poster")?.takeIf { it.isNotEmpty() }
        
        return src?.let { normalizeUrl(it, baseUrl) }
    }
    
    /**
     * Extract duration from item
     */
    private fun extractItemDuration(item: Element): String? {
        val durationSelectors = listOf(
            ".duration", ".time", ".length", "[class*='duration']",
            "[class*='time']", ".runtime"
        )
        
        for (selector in durationSelectors) {
            val text = item.select(selector).firstOrNull()?.text()?.trim()
            if (!text.isNullOrEmpty() && text.matches(Regex(".*\\d+.*"))) {
                return text
            }
        }
        
        return null
    }
    
    /**
     * Check if URL is likely content (not navigation)
     */
    private fun isContentUrl(url: String): Boolean {
        val excludePatterns = listOf(
            "/category/", "/categories/", "/tag/", "/tags/",
            "/page/", "/user/", "/login", "/register",
            "/about", "/contact", "/privacy", "/terms",
            "javascript:", "#", "mailto:", "tel:"
        )
        
        val urlLower = url.lowercase()
        return excludePatterns.none { urlLower.contains(it) }
    }
    
    /**
     * Normalize URL
     */
    private fun normalizeUrl(url: String, baseUrl: String): String {
        return when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "${baseUrl.trimEnd('/')}$url"
            else -> "${baseUrl.trimEnd('/')}/$url"
        }
    }
    
    /**
     * Get pagination links for more results
     */
    fun getPaginationLinks(document: Document, baseUrl: String, maxPages: Int = 3): List<String> {
        val links = mutableListOf<String>()
        
        val paginationSelectors = listOf(
            ".pagination a", ".pager a", ".pages a",
            "nav.pagination a", "[class*='pagination'] a",
            ".page-numbers a", ".page-link"
        )
        
        for (selector in paginationSelectors) {
            document.select(selector).forEach { link ->
                val href = link.attr("href")
                if (href.isNotEmpty() && !href.startsWith("#")) {
                    links.add(normalizeUrl(href, baseUrl))
                }
            }
            
            if (links.isNotEmpty()) break
        }
        
        return links.distinct().take(maxPages)
    }
}

data class ContentLink(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val duration: String?
)
