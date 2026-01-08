package com.zim.jackettprowler.automation

import android.content.Context
import android.util.Log
import com.zim.jackettprowler.CustomSiteConfig
import com.zim.jackettprowler.CustomSiteManager
import com.zim.jackettprowler.ScraperSelectors
import com.zim.jackettprowler.TrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Tool-X Inspired Tracker Site Scanner
 * 
 * Automatically scans 200+ tracker URLs to:
 * 1. Detect if they have associated search web interfaces
 * 2. Find Torznab/RSS API endpoints
 * 3. Auto-configure CSS selectors for scraping
 * 4. Add working sites to built-in providers
 * 
 * All heavy lifting happens behind the scenes!
 */
class TrackerSiteScanner(private val context: Context) {
    
    companion object {
        private const val TAG = "TrackerSiteScanner"
        
        // Known tracker domains and their search interfaces
        private val KNOWN_SEARCH_INTERFACES = mapOf(
            "opentrackr.org" to "https://opentrackr.org/search",
            "tracker.torrent.eu.org" to "https://torrent.eu.org/search",
            "bitsearch.to" to "https://bitsearch.to/search",
            "bt4g.com" to "https://bt4g.com/search",
            "btdig.com" to "https://btdig.com/search",
            "torrentgalaxy.to" to "https://torrentgalaxy.to/search",
            "1337x.to" to "https://1337x.to/search",
            "rarbg.to" to "https://rarbg.to/torrents.php",
            "thepiratebay.org" to "https://thepiratebay.org/search",
            "nyaa.si" to "https://nyaa.si/?q=",
            "yts.mx" to "https://yts.mx/browse-movies",
            "eztv.re" to "https://eztv.re/search",
            "limetorrents.lol" to "https://limetorrents.lol/search",
            "torrentz2.eu" to "https://torrentz2.eu/search"
        )
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    private val urlConverter = URLToConfigConverter(context)
    private val customSiteManager = CustomSiteManager(context)
    
    data class ScanResult(
        val trackerUrl: String,
        val hasSearchInterface: Boolean,
        val searchUrl: String?,
        val apiType: String?, // "torznab", "rss", "html", "json"
        val autoConfigured: Boolean,
        val config: CustomSiteConfig?,
        val error: String?
    )
    
    data class BatchScanResult(
        val totalScanned: Int,
        val searchInterfacesFound: Int,
        val autoConfigured: Int,
        val results: List<ScanResult>
    )
    
    /**
     * Scan all 200+ trackers for search capability
     * This is the MAIN function that does everything automatically!
     */
    suspend fun scanAllTrackers(
        progressCallback: ((current: Int, total: Int, message: String) -> Unit)? = null
    ): BatchScanResult = withContext(Dispatchers.IO) {
        
        val allTrackers = TrackerDatabase.getAllTrackers()
        val results = mutableListOf<ScanResult>()
        var searchFound = 0
        var configured = 0
        
        Log.d(TAG, "🔍 Starting scan of ${allTrackers.size} trackers...")
        
        allTrackers.forEachIndexed { index, trackerUrl ->
            progressCallback?.invoke(index + 1, allTrackers.size, "Scanning: $trackerUrl")
            
            val result = scanSingleTracker(trackerUrl)
            results.add(result)
            
            if (result.hasSearchInterface) {
                searchFound++
                if (result.autoConfigured) {
                    configured++
                }
            }
        }
        
        Log.d(TAG, "✓ Scan complete: $searchFound search interfaces found, $configured auto-configured")
        
        BatchScanResult(
            totalScanned = allTrackers.size,
            searchInterfacesFound = searchFound,
            autoConfigured = configured,
            results = results
        )
    }
    
    /**
     * Scan a single tracker URL for search capability
     */
    private suspend fun scanSingleTracker(trackerUrl: String): ScanResult {
        try {
            // Extract domain from tracker URL
            val domain = extractDomain(trackerUrl)
            
            // Check if we have a known search interface for this domain
            val knownSearchUrl = KNOWN_SEARCH_INTERFACES.entries
                .find { domain.contains(it.key) }
                ?.value
            
            if (knownSearchUrl != null) {
                return scanKnownSite(trackerUrl, knownSearchUrl)
            }
            
            // Try to discover search interface
            return discoverSearchInterface(trackerUrl, domain)
            
        } catch (e: Exception) {
            return ScanResult(
                trackerUrl = trackerUrl,
                hasSearchInterface = false,
                searchUrl = null,
                apiType = null,
                autoConfigured = false,
                config = null,
                error = e.message
            )
        }
    }
    
    /**
     * Scan a known search site and auto-configure
     */
    private suspend fun scanKnownSite(trackerUrl: String, searchUrl: String): ScanResult {
        return try {
            // Use URLToConfigConverter for full auto-configuration
            val conversionResult = urlConverter.convertAndSave(
                url = searchUrl,
                autoSave = true,
                testQuery = "ubuntu"
            )
            
            ScanResult(
                trackerUrl = trackerUrl,
                hasSearchInterface = true,
                searchUrl = searchUrl,
                apiType = detectApiType(searchUrl),
                autoConfigured = conversionResult.success,
                config = conversionResult.config,
                error = if (!conversionResult.success) conversionResult.message else null
            )
        } catch (e: Exception) {
            ScanResult(
                trackerUrl = trackerUrl,
                hasSearchInterface = true,
                searchUrl = searchUrl,
                apiType = null,
                autoConfigured = false,
                config = null,
                error = e.message
            )
        }
    }
    
    /**
     * Try to discover search interface from tracker domain
     */
    private suspend fun discoverSearchInterface(trackerUrl: String, domain: String): ScanResult {
        // Try common search URL patterns
        val searchPatterns = listOf(
            "https://$domain/search",
            "https://$domain/torrents",
            "https://$domain/browse",
            "https://$domain/api/search",
            "https://$domain/api/v1/search",
            "https://www.$domain/search",
            "https://www.$domain"
        )
        
        for (pattern in searchPatterns) {
            try {
                val request = Request.Builder()
                    .url(pattern)
                    .head() // Just check if it exists
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful || response.code in listOf(301, 302, 307, 308)) {
                    // Found a valid URL, try to configure it
                    return scanKnownSite(trackerUrl, pattern)
                }
            } catch (_: Exception) {
                continue
            }
        }
        
        // No search interface found
        return ScanResult(
            trackerUrl = trackerUrl,
            hasSearchInterface = false,
            searchUrl = null,
            apiType = null,
            autoConfigured = false,
            config = null,
            error = null
        )
    }
    
    /**
     * Detect what type of API/interface the site uses
     */
    private fun detectApiType(url: String): String {
        return when {
            url.contains("torznab", ignoreCase = true) -> "torznab"
            url.contains("/api/", ignoreCase = true) -> "json"
            url.contains("rss", ignoreCase = true) || url.contains("feed", ignoreCase = true) -> "rss"
            else -> "html"
        }
    }
    
    /**
     * Extract domain from tracker URL
     */
    private fun extractDomain(trackerUrl: String): String {
        return try {
            val cleanUrl = trackerUrl
                .replace("udp://", "http://")
                .replace("wss://", "http://")
            val uri = URI(cleanUrl)
            uri.host ?: trackerUrl
        } catch (_: Exception) {
            trackerUrl.substringAfter("://").substringBefore("/").substringBefore(":")
        }
    }
    
    /**
     * Quick scan for fast results - only checks known interfaces
     */
    suspend fun quickScan(
        progressCallback: ((current: Int, total: Int, message: String) -> Unit)? = null
    ): BatchScanResult = withContext(Dispatchers.IO) {
        
        val results = mutableListOf<ScanResult>()
        var searchFound = 0
        var configured = 0
        
        KNOWN_SEARCH_INTERFACES.entries.forEachIndexed { index, (domain, searchUrl) ->
            progressCallback?.invoke(index + 1, KNOWN_SEARCH_INTERFACES.size, "Configuring: $domain")
            
            val result = scanKnownSite("tracker.$domain", searchUrl)
            results.add(result)
            
            if (result.hasSearchInterface) {
                searchFound++
                if (result.autoConfigured) {
                    configured++
                }
            }
        }
        
        BatchScanResult(
            totalScanned = KNOWN_SEARCH_INTERFACES.size,
            searchInterfacesFound = searchFound,
            autoConfigured = configured,
            results = results
        )
    }
}
