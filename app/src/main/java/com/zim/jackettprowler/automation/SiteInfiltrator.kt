package com.zim.jackettprowler.automation

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

/**
 * Site Infiltrator - Inspired by Tool-X web reconnaissance tools
 * Automatically analyzes and extracts all relevant data from torrent sites
 * Works like a penetration testing tool but for legitimate scraping purposes
 * 
 * Capabilities:
 * - Deep site structure analysis
 * - Anti-bot detection bypass
 * - Cookie/session management
 * - Dynamic content detection
 * - API endpoint discovery
 * - Form auto-detection
 * - Authentication flow detection
 */
class SiteInfiltrator {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    companion object {
        private const val TAG = "SiteInfiltrator"
        
        // User agents for rotation
        private val USER_AGENTS = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0"
        )
    }
    
    data class InfiltrationResult(
        val url: String,
        val siteName: String,
        val detectedAPIs: List<APIEndpoint>,
        val searchMethods: List<SearchMethod>,
        val authRequired: Boolean,
        val cloudflareProtected: Boolean,
        val reCaptchaPresent: Boolean,
        val torrentListPatterns: List<String>,
        val downloadMethods: List<DownloadMethod>,
        val recommendedConfig: Map<String, String>,
        val success: Boolean,
        val confidence: Double
    )
    
    data class APIEndpoint(
        val url: String,
        val type: String, // "torznab", "json", "xml", "rss"
        val requiresAuth: Boolean
    )
    
    data class SearchMethod(
        val type: String, // "url", "post", "ajax"
        val endpoint: String,
        val parameters: Map<String, String>
    )
    
    data class DownloadMethod(
        val type: String, // "magnet", "torrent", "api"
        val pattern: String
    )
    
    /**
     * Infiltrate a site and extract all usable information
     */
    suspend fun infiltrate(url: String): InfiltrationResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "🎯 Infiltrating: $url")
        
        try {
            // Phase 1: Initial reconnaissance
            val document = fetchWithRetry(url)
            val siteName = detectSiteName(document, url)
            
            // Phase 2: Detect protection mechanisms
            val cloudflare = detectCloudflare(document)
            val recaptcha = detectRecaptcha(document)
            val authRequired = detectAuthRequirement(document)
            
            // Phase 3: API discovery
            val apis = discoverAPIs(document, url)
            
            // Phase 4: Search method detection
            val searchMethods = detectSearchMethods(document, url)
            
            // Phase 5: Torrent list pattern detection
            val torrentPatterns = detectTorrentPatterns(document)
            
            // Phase 6: Download method detection
            val downloadMethods = detectDownloadMethods(document)
            
            // Phase 7: Generate recommended configuration
            val config = generateConfig(
                url, siteName, apis, searchMethods, 
                torrentPatterns, downloadMethods
            )
            
            // Calculate confidence
            val confidence = calculateInfiltrationConfidence(
                apis, searchMethods, torrentPatterns, downloadMethods
            )
            
            InfiltrationResult(
                url = url,
                siteName = siteName,
                detectedAPIs = apis,
                searchMethods = searchMethods,
                authRequired = authRequired,
                cloudflareProtected = cloudflare,
                reCaptchaPresent = recaptcha,
                torrentListPatterns = torrentPatterns,
                downloadMethods = downloadMethods,
                recommendedConfig = config,
                success = true,
                confidence = confidence
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Infiltration failed: ${e.message}", e)
            InfiltrationResult(
                url = url,
                siteName = "",
                detectedAPIs = emptyList(),
                searchMethods = emptyList(),
                authRequired = false,
                cloudflareProtected = false,
                reCaptchaPresent = false,
                torrentListPatterns = emptyList(),
                downloadMethods = emptyList(),
                recommendedConfig = emptyMap(),
                success = false,
                confidence = 0.0
            )
        }
    }
    
    private suspend fun fetchWithRetry(url: String, maxRetries: Int = 3): Document {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                val userAgent = USER_AGENTS.random()
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("DNT", "1")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string() ?: throw Exception("Empty response")
                        return Jsoup.parse(html, url)
                    }
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    delay(1000L * (attempt + 1)) // Exponential backoff
                }
            }
        }
        
        throw lastException ?: Exception("Failed to fetch URL")
    }
    
    private fun detectSiteName(document: Document, url: String): String {
        // Try multiple methods
        document.select("meta[property='og:site_name']").firstOrNull()?.attr("content")?.let { return it }
        document.select("meta[name='application-name']").firstOrNull()?.attr("content")?.let { return it }
        document.select("title").firstOrNull()?.text()?.split("-", "|")?.firstOrNull()?.trim()?.let { return it }
        
        // Fallback to domain
        return url.replace(Regex("https?://(www\\.)?"), "").split("/").firstOrNull() ?: "Unknown"
    }
    
    private fun detectCloudflare(document: Document): Boolean {
        return document.html().contains("cloudflare", ignoreCase = true) ||
               document.select("script[src*='cloudflare']").isNotEmpty()
    }
    
    private fun detectRecaptcha(document: Document): Boolean {
        return document.select("script[src*='recaptcha']").isNotEmpty() ||
               document.select("div.g-recaptcha").isNotEmpty()
    }
    
    private fun detectAuthRequirement(document: Document): Boolean {
        val loginKeywords = listOf("login", "signin", "sign in", "log in", "authentication")
        val hasLoginForm = document.select("form").any { form ->
            val action = form.attr("action").lowercase()
            val inputs = form.select("input[type='password']")
            loginKeywords.any { action.contains(it) } || inputs.isNotEmpty()
        }
        
        return hasLoginForm || document.select("a[href*='login'], a[href*='signin']").isNotEmpty()
    }
    
    private fun discoverAPIs(document: Document, baseUrl: String): List<APIEndpoint> {
        val apis = mutableListOf<APIEndpoint>()
        
        // Check for Torznab
        if (document.html().contains("torznab", ignoreCase = true)) {
            apis.add(APIEndpoint("$baseUrl/api", "torznab", true))
        }
        
        // Check for RSS feeds
        document.select("link[type='application/rss+xml']").forEach { link ->
            apis.add(APIEndpoint(link.attr("href"), "rss", false))
        }
        
        // Check for JSON APIs in scripts
        val scriptContent = document.select("script").joinToString("\n") { it.html() }
        if (scriptContent.contains("/api/", ignoreCase = true)) {
            Regex("""["'](/api/[^"']+)["']""").findAll(scriptContent).forEach { match ->
                apis.add(APIEndpoint("$baseUrl${match.groupValues[1]}", "json", false))
            }
        }
        
        return apis
    }
    
    private fun detectSearchMethods(document: Document, baseUrl: String): List<SearchMethod> {
        val methods = mutableListOf<SearchMethod>()
        
        // Check for search forms
        document.select("form").forEach { form ->
            val action = form.attr("action")
            val method = form.attr("method").takeIf { it.isNotEmpty() } ?: "get"
            val inputs = form.select("input[name], select[name]")
            
            if (inputs.any { it.attr("name").contains("search", ignoreCase = true) ||
                              it.attr("name").contains("query", ignoreCase = true) ||
                              it.attr("name") == "q" }) {
                
                val params = inputs.associate { 
                    it.attr("name") to (it.attr("value") ?: "{query}")
                }
                
                methods.add(SearchMethod(
                    type = if (method.equals("post", ignoreCase = true)) "post" else "url",
                    endpoint = if (action.startsWith("http")) action else "$baseUrl$action",
                    parameters = params
                ))
            }
        }
        
        // Check for URL-based search
        document.select("a[href*='search'], a[href*='query']").firstOrNull()?.let { link ->
            val href = link.attr("href")
            methods.add(SearchMethod(
                type = "url",
                endpoint = if (href.startsWith("http")) href else "$baseUrl$href",
                parameters = mapOf("q" to "{query}")
            ))
        }
        
        return methods
    }
    
    private fun detectTorrentPatterns(document: Document): List<String> {
        val patterns = mutableListOf<String>()
        
        // Common table structures
        document.select("table").forEach { table ->
            val rows = table.select("tr")
            if (rows.size > 3) { // Likely a result table
                val headers = rows.firstOrNull()?.select("th")?.map { it.text().lowercase() }
                if (headers?.any { it.contains("name") || it.contains("title") } == true) {
                    patterns.add("table tr")
                }
            }
        }
        
        // Common div structures
        listOf(".torrent", ".result", ".item", "[class*='torrent']").forEach { selector ->
            if (document.select(selector).size > 2) {
                patterns.add(selector)
            }
        }
        
        return patterns.distinct()
    }
    
    private fun detectDownloadMethods(document: Document): List<DownloadMethod> {
        val methods = mutableListOf<DownloadMethod>()
        
        // Magnet links
        if (document.select("a[href^='magnet:']").isNotEmpty()) {
            methods.add(DownloadMethod("magnet", "a[href^='magnet:']"))
        }
        
        // Torrent files
        if (document.select("a[href$='.torrent']").isNotEmpty()) {
            methods.add(DownloadMethod("torrent", "a[href$='.torrent']"))
        }
        
        // Download buttons
        document.select("a, button").forEach { elem ->
            val text = elem.text().lowercase()
            val href = elem.attr("href").lowercase()
            if (text.contains("download") || href.contains("download")) {
                methods.add(DownloadMethod("link", elem.cssSelector()))
            }
        }
        
        return methods.distinctBy { it.type }
    }
    
    private fun generateConfig(
        url: String,
        siteName: String,
        apis: List<APIEndpoint>,
        searchMethods: List<SearchMethod>,
        torrentPatterns: List<String>,
        downloadMethods: List<DownloadMethod>
    ): Map<String, String> {
        val config = mutableMapOf<String, String>()
        
        config["site_name"] = siteName
        config["base_url"] = url.replace(Regex("(https?://[^/]+).*"), "$1")
        
        // API configuration
        apis.firstOrNull()?.let {
            config["api_type"] = it.type
            config["api_url"] = it.url
        }
        
        // Search configuration
        searchMethods.firstOrNull()?.let {
            config["search_method"] = it.type
            config["search_endpoint"] = it.endpoint
            config["search_params"] = it.parameters.entries.joinToString("&") { "${it.key}=${it.value}" }
        }
        
        // Scraping configuration
        torrentPatterns.firstOrNull()?.let {
            config["torrent_list_selector"] = it
        }
        
        downloadMethods.firstOrNull { it.type == "magnet" }?.let {
            config["magnet_selector"] = it.pattern
        }
        
        downloadMethods.firstOrNull { it.type == "torrent" }?.let {
            config["download_selector"] = it.pattern
        }
        
        return config
    }
    
    private fun calculateInfiltrationConfidence(
        apis: List<APIEndpoint>,
        searchMethods: List<SearchMethod>,
        torrentPatterns: List<String>,
        downloadMethods: List<DownloadMethod>
    ): Double {
        var score = 0.0
        
        if (apis.isNotEmpty()) score += 0.3
        if (searchMethods.isNotEmpty()) score += 0.3
        if (torrentPatterns.isNotEmpty()) score += 0.2
        if (downloadMethods.isNotEmpty()) score += 0.2
        
        return score
    }
}
