package com.zim.jackettprowler

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Web scraping service for extracting torrent data from HTML pages
 */
class ScraperService(
    private val torProxyManager: TorProxyManager? = null
) {
    private val lastRequestTime = mutableMapOf<String, Long>()
    
    /**
     * Search a custom site and extract torrent results
     */
    suspend fun search(
        siteConfig: CustomSiteConfig,
        query: String,
        limit: Int = 50
    ): List<TorrentResult> = withContext(Dispatchers.IO) {
        try {
            // Rate limiting
            enforceRateLimit(siteConfig)
            
            // Build search URL
            val searchUrl = buildSearchUrl(siteConfig, query)
            
            // Fetch HTML
            val html = fetchHtml(searchUrl, siteConfig)
            
            // Parse HTML
            val document = Jsoup.parse(html, searchUrl)
            
            // Extract results
            extractResults(document, siteConfig, limit)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Fetch detailed information from a torrent page
     */
    suspend fun fetchTorrentDetails(
        url: String,
        siteConfig: CustomSiteConfig
    ): TorrentDetailInfo? = withContext(Dispatchers.IO) {
        try {
            enforceRateLimit(siteConfig)
            val html = fetchHtml(url, siteConfig)
            val document = Jsoup.parse(html, url)
            
            // Extract additional details if needed
            TorrentDetailInfo(
                description = document.select("div.torrent-description").text(),
                files = extractFileList(document),
                comments = extractComments(document)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun buildSearchUrl(siteConfig: CustomSiteConfig, query: String): String {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        return if (siteConfig.searchPath.contains("{query}")) {
            siteConfig.baseUrl + siteConfig.searchPath.replace("{query}", encodedQuery)
        } else {
            "${siteConfig.baseUrl}${siteConfig.searchPath}?${siteConfig.searchParamName}=$encodedQuery"
        }
    }
    
    private suspend fun fetchHtml(url: String, siteConfig: CustomSiteConfig): String {
        return if (siteConfig.requiresTor || siteConfig.isOnionSite) {
            // Use Tor proxy
            torProxyManager?.fetchViaProxy(url, siteConfig.headers) 
                ?: throw IOException("Tor proxy not available for onion site")
        } else {
            // Normal HTTP request
            val connection = Jsoup.connect(url)
                .timeout(30000)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .followRedirects(true)
            
            // Add custom headers
            siteConfig.headers.forEach { (key, value) ->
                connection.header(key, value)
            }
            
            connection.get().html()
        }
    }
    
    private fun extractResults(
        document: Document,
        siteConfig: CustomSiteConfig,
        limit: Int
    ): List<TorrentResult> {
        val results = mutableListOf<TorrentResult>()
        val selectors = siteConfig.selectors
        
        try {
            val containers = document.select(selectors.resultContainer)
            
            for ((index, container) in containers.withIndex()) {
                if (index >= limit) break
                
                try {
                    val result = extractSingleResult(container, siteConfig)
                    if (result != null) {
                        results.add(result)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Continue with other results
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return results
    }
    
    private fun extractSingleResult(
        element: Element,
        siteConfig: CustomSiteConfig
    ): TorrentResult? {
        val selectors = siteConfig.selectors
        
        // Title is required
        val title = selectText(element, selectors.title)?.trim() ?: return null
        
        // Extract other fields
        val downloadUrl = selectAttribute(element, selectors.downloadUrl, "href")
        val magnetUrl = selectAttribute(element, selectors.magnetUrl, "href")
        val size = selectText(element, selectors.size)
        val seeders = selectText(element, selectors.seeders)?.toIntOrNull() ?: 0
        val leechers = selectText(element, selectors.leechers)?.toIntOrNull() ?: 0
        val publishDate = selectText(element, selectors.publishDate)
        val category = selectText(element, selectors.category)
        val torrentPageUrl = selectAttribute(element, selectors.torrentPageUrl, "href")
        
        // Make URLs absolute
        val absoluteDownloadUrl = downloadUrl?.let { makeAbsolute(it, siteConfig.baseUrl) }
        val absoluteMagnetUrl = magnetUrl ?: ""
        val absoluteTorrentPageUrl = torrentPageUrl?.let { makeAbsolute(it, siteConfig.baseUrl) }
        
        return TorrentResult(
            title = title,
            link = absoluteDownloadUrl ?: absoluteTorrentPageUrl ?: "",
            sizeBytes = parseSize(size),
            seeders = seeders ?: 0,
            indexer = siteConfig.name,
            leechers = leechers ?: 0,
            magnetUrl = absoluteMagnetUrl,
            category = category ?: "",
            pubDate = publishDate ?: "",
            guid = absoluteTorrentPageUrl ?: absoluteDownloadUrl ?: title,
            description = ""
        )
    }
    
    private fun selectText(element: Element, selector: String?): String? {
        if (selector.isNullOrBlank()) return null
        return try {
            element.select(selector).firstOrNull()?.text()
        } catch (e: Exception) {
            null
        }
    }
    
    private fun selectAttribute(element: Element, selector: String?, attribute: String): String? {
        if (selector.isNullOrBlank()) return null
        return try {
            element.select(selector).firstOrNull()?.attr(attribute)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun makeAbsolute(url: String, baseUrl: String): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") || url.startsWith("magnet:") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> baseUrl + url
            else -> "$baseUrl/$url"
        }
    }
    
    private fun parseSize(sizeStr: String?): Long {
        if (sizeStr.isNullOrBlank()) return 0L
        
        try {
            // Try to extract size from various formats
            val regex = """(\d+(?:\.\d+)?)\s*(GB|MB|KB|GiB|MiB|KiB|TB|TiB|B)""".toRegex(RegexOption.IGNORE_CASE)
            val match = regex.find(sizeStr) ?: return 0L
            
            val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
            val unit = match.groupValues[2].uppercase()
            
            return when (unit) {
                "TB", "TIB" -> (value * 1024 * 1024 * 1024 * 1024).toLong()
                "GB", "GIB" -> (value * 1024 * 1024 * 1024).toLong()
                "MB", "MIB" -> (value * 1024 * 1024).toLong()
                "KB", "KIB" -> (value * 1024).toLong()
                "B" -> value.toLong()
                else -> 0L
            }
        } catch (e: Exception) {
            return 0L
        }
    }
    
    private fun enforceRateLimit(siteConfig: CustomSiteConfig) {
        val lastTime = lastRequestTime[siteConfig.id] ?: 0
        val elapsed = System.currentTimeMillis() - lastTime
        
        if (elapsed < siteConfig.rateLimit) {
            Thread.sleep(siteConfig.rateLimit - elapsed)
        }
        
        lastRequestTime[siteConfig.id] = System.currentTimeMillis()
    }
    
    private fun extractFileList(document: Document): List<String> {
        return try {
            document.select(".file-list li, .filelist li, table.file-list tr")
                .map { it.text() }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun extractComments(document: Document): List<String> {
        return try {
            document.select(".comment, .torrent-comment, div[class*='comment']")
                .map { it.text() }
                .filter { it.isNotBlank() }
                .take(10)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Detailed information from torrent page
 */
data class TorrentDetailInfo(
    val description: String = "",
    val files: List<String> = emptyList(),
    val comments: List<String> = emptyList()
)
