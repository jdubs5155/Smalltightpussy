package com.zim.jackettprowler

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

/**
 * Intelligently analyzes torrent sites to automatically detect:
 * - Torrent list containers
 * - Download links and magnet links
 * - Seeders/leechers/size information
 * - Search result patterns
 * 
 * This allows users to add custom sites by just providing a URL!
 */
class SmartSiteAnalyzer(private val torProxyManager: TorProxyManager) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val TAG = "SmartSiteAnalyzer"
        
        // Common patterns for torrent site elements
        private val TORRENT_LIST_SELECTORS = listOf(
            "table.torrents tr",
            "table#torrents tr",
            "table.table tr",
            ".torrent-row",
            ".torrent-list .torrent",
            "tbody tr",
            ".search-results tr",
            ".results-table tr",
            ".torrent-item",
            "div[class*='torrent']",
            "tr[class*='torrent']"
        )
        
        private val TITLE_SELECTORS = listOf(
            "a.torrent-name",
            "a.title",
            ".torrent-title a",
            "td.name a",
            "a[href*='torrent']",
            "a[href*='details']",
            ".title a",
            "td:nth-child(2) a",
            ".name a"
        )
        
        private val MAGNET_SELECTORS = listOf(
            "a[href^='magnet:']",
            "a[href*='magnet']",
            ".magnet",
            "[title*='magnet']"
        )
        
        private val DOWNLOAD_SELECTORS = listOf(
            "a[href$='.torrent']",
            "a[href*='download']",
            "a[href*='.torrent']",
            ".download-link",
            "[title*='download']"
        )
        
        private val SEEDERS_SELECTORS = listOf(
            "td.seeds",
            "td.seeders",
            ".seeds",
            ".seeders",
            "td:has([title*='seed'])",
            "span.seeds",
            "[class*='seed']"
        )
        
        private val LEECHERS_SELECTORS = listOf(
            "td.leeches",
            "td.leechers",
            ".leeches",
            ".leechers",
            "td:has([title*='leech'])",
            "span.leechers",
            "[class*='leech']"
        )
        
        private val SIZE_SELECTORS = listOf(
            "td.size",
            ".size",
            "td:has([title*='size'])",
            "span.size"
        )
    }
    
    data class SiteAnalysisResult(
        val success: Boolean,
        val siteName: String,
        val baseUrl: String,
        val searchUrlTemplate: String,
        val detectedConfig: CustomSiteConfig?,
        val confidence: Double,
        val issues: List<String> = emptyList()
    )
    
    /**
     * Analyze a torrent site by URL and automatically detect its structure
     */
    suspend fun analyzeSite(url: String, testQuery: String = "ubuntu"): SiteAnalysisResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Analyzing site: $url")
            
            // Extract base URL and build search URL
            val baseUrl = extractBaseUrl(url)
            val searchUrl = buildSearchUrl(url, testQuery)
            
            Log.d(TAG, "Search URL: $searchUrl")
            
            // Fetch and parse the page
            val document = fetchPage(searchUrl)
            val siteName = detectSiteName(document, baseUrl)
            
            Log.d(TAG, "Site name: $siteName")
            
            // Detect torrent list structure
            val listSelector = detectTorrentListSelector(document)
            if (listSelector == null) {
                return@withContext SiteAnalysisResult(
                    success = false,
                    siteName = siteName,
                    baseUrl = baseUrl,
                    searchUrlTemplate = searchUrl.replace(testQuery, "{query}"),
                    detectedConfig = null,
                    confidence = 0.0,
                    issues = listOf("Could not detect torrent list structure")
                )
            }
            
            Log.d(TAG, "List selector: $listSelector")
            
            // Detect element selectors
            val titleSelector = detectElementSelector(document, listSelector, TITLE_SELECTORS)
            val magnetSelector = detectElementSelector(document, listSelector, MAGNET_SELECTORS)
            val downloadSelector = detectElementSelector(document, listSelector, DOWNLOAD_SELECTORS)
            val seedersSelector = detectElementSelector(document, listSelector, SEEDERS_SELECTORS)
            val leechersSelector = detectElementSelector(document, listSelector, LEECHERS_SELECTORS)
            val sizeSelector = detectElementSelector(document, listSelector, SIZE_SELECTORS)
            
            Log.d(TAG, "Detected selectors - Title: $titleSelector, Magnet: $magnetSelector, Download: $downloadSelector")
            
            // Calculate confidence score
            val confidence = calculateConfidence(
                titleSelector, magnetSelector, downloadSelector, 
                seedersSelector, leechersSelector, sizeSelector
            )
            
            val issues = mutableListOf<String>()
            if (titleSelector == null) issues.add("Could not detect title selector")
            if (magnetSelector == null && downloadSelector == null) issues.add("Could not detect download method")
            if (seedersSelector == null) issues.add("Could not detect seeders")
            
            // Create config
            val config = CustomSiteConfig(
                id = generateId(siteName),
                name = siteName,
                baseUrl = baseUrl,
                searchPath = searchUrl.replace(testQuery, "{query}").replace(baseUrl, ""),
                selectors = ScraperSelectors(
                    resultContainer = listSelector,
                    title = titleSelector ?: "",
                    downloadUrl = downloadSelector,
                    magnetUrl = magnetSelector,
                    size = sizeSelector,
                    seeders = seedersSelector,
                    leechers = leechersSelector
                ),
                enabled = true,
                requiresTor = url.contains(".onion"),
                isOnionSite = url.contains(".onion"),
                rateLimit = 1000,
                category = "auto-detected"
            )
            
            SiteAnalysisResult(
                success = confidence > 0.5,
                siteName = siteName,
                baseUrl = baseUrl,
                searchUrlTemplate = searchUrl.replace(testQuery, "{query}"),
                detectedConfig = config,
                confidence = confidence,
                issues = issues
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing site: ${e.message}", e)
            SiteAnalysisResult(
                success = false,
                siteName = "",
                baseUrl = url,
                searchUrlTemplate = "",
                detectedConfig = null,
                confidence = 0.0,
                issues = listOf(e.message ?: "Unknown error")
            )
        }
    }
    
    private suspend fun fetchPage(url: String): Document = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            val html = response.body?.string() ?: throw Exception("Empty response")
            Jsoup.parse(html, url)
        }
    }
    
    private fun detectSiteName(document: Document, baseUrl: String): String {
        // Try to get site name from title or logo
        val title = document.title()
        if (title.isNotBlank()) {
            return title.split("-", "|", "::").firstOrNull()?.trim() ?: title
        }
        
        // Fallback to domain name
        return baseUrl.replace(Regex("https?://"), "")
            .replace(Regex("www\\."), "")
            .split("/").firstOrNull() ?: "Custom Site"
    }
    
    private fun extractBaseUrl(url: String): String {
        val regex = Regex("(https?://[^/]+)")
        return regex.find(url)?.value ?: url
    }
    
    private fun buildSearchUrl(url: String, query: String): String {
        // If URL already has search params, replace query
        if (url.contains("?") || url.contains("search")) {
            return url.replace(Regex("q=[^&]*"), "q=$query")
                .replace(Regex("search=[^&]*"), "search=$query")
                .replace(Regex("query=[^&]*"), "query=$query")
        }
        
        // Common search URL patterns
        val baseUrl = extractBaseUrl(url)
        return when {
            url.contains(".onion") -> "$baseUrl/search?q=$query"
            else -> "$baseUrl/search/$query"
        }
    }
    
    private fun detectTorrentListSelector(document: Document): String? {
        for (selector in TORRENT_LIST_SELECTORS) {
            try {
                val elements = document.select(selector)
                if (elements.size >= 3) { // Need at least 3 results to confirm
                    Log.d(TAG, "Found torrent list with selector: $selector (${elements.size} items)")
                    return selector
                }
            } catch (e: Exception) {
                // Skip invalid selectors
            }
        }
        
        // Try to find tables with multiple rows
        document.select("table").forEach { table ->
            val rows = table.select("tr")
            if (rows.size >= 5) {
                return "table tr"
            }
        }
        
        return null
    }
    
    private fun detectElementSelector(document: Document, listSelector: String, candidates: List<String>): String? {
        val firstItem = document.select(listSelector).firstOrNull() ?: return null
        
        for (selector in candidates) {
            try {
                val elements = firstItem.select(selector)
                if (elements.isNotEmpty()) {
                    val text = elements.first().text()
                    if (text.isNotBlank()) {
                        Log.d(TAG, "Detected selector '$selector' with value: ${text.take(50)}")
                        return selector
                    }
                }
            } catch (e: Exception) {
                // Skip invalid selectors
            }
        }
        
        return null
    }
    
    private fun calculateConfidence(
        title: String?,
        magnet: String?,
        download: String?,
        seeders: String?,
        leechers: String?,
        size: String?
    ): Double {
        var score = 0.0
        
        if (title != null) score += 0.4  // Title is critical
        if (magnet != null || download != null) score += 0.4  // Download method is critical
        if (seeders != null) score += 0.1
        if (leechers != null) score += 0.05
        if (size != null) score += 0.05
        
        return score
    }
    
    private fun generateId(siteName: String): String {
        return "custom_" + siteName.lowercase()
            .replace(Regex("[^a-z0-9]"), "_")
            .take(30)
    }
}
