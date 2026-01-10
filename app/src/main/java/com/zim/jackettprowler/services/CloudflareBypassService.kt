package com.zim.jackettprowler.services

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.*
import okhttp3.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.HttpCookie
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Cloudflare Bypass Service
 * 
 * Multiple strategies to bypass Cloudflare protection:
 * 1. Cookie persistence - reuse cf_clearance cookies
 * 2. Header spoofing - mimic real browser fingerprints
 * 3. WebView solver - use Android WebView to solve JS challenges
 * 4. Request timing - avoid triggering rate limits
 * 5. Retry with backoff - handle temporary blocks
 * 
 * Works with:
 * - Cloudflare Under Attack Mode
 * - Cloudflare JS Challenge
 * - Cloudflare Browser Check
 * - Basic bot detection
 */
class CloudflareBypassService(private val context: Context) {
    
    companion object {
        private const val TAG = "CloudflareBypass"
        
        // Cloudflare challenge markers
        private val CF_CHALLENGE_MARKERS = listOf(
            "cf-browser-verification",
            "cf_clearance",
            "checking your browser",
            "Just a moment...",
            "Checking your browser before accessing",
            "Please Wait... | Cloudflare",
            "ray id",
            "cf-challenge",
            "__cf_bm",
            "Attention Required! | Cloudflare",
            "cf-mitigated"
        )
        
        // Realistic browser fingerprints
        private val BROWSER_FINGERPRINTS = listOf(
            BrowserFingerprint(
                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                acceptLanguage = "en-US,en;q=0.9",
                platform = "Win32",
                secChUa = "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"",
                secChUaMobile = "?0",
                secChUaPlatform = "\"Windows\""
            ),
            BrowserFingerprint(
                userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                acceptLanguage = "en-US,en;q=0.9",
                platform = "MacIntel",
                secChUa = "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"",
                secChUaMobile = "?0",
                secChUaPlatform = "\"macOS\""
            ),
            BrowserFingerprint(
                userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                acceptLanguage = "en-US,en;q=0.9",
                platform = "Linux x86_64",
                secChUa = "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"",
                secChUaMobile = "?0",
                secChUaPlatform = "\"Linux\""
            ),
            BrowserFingerprint(
                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
                acceptLanguage = "en-US,en;q=0.5",
                platform = "Win32",
                secChUa = null, // Firefox doesn't send Sec-CH-UA
                secChUaMobile = null,
                secChUaPlatform = null
            ),
            BrowserFingerprint(
                userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
                acceptLanguage = "en-US,en;q=0.9",
                platform = "MacIntel",
                secChUa = null, // Safari doesn't send Sec-CH-UA
                secChUaMobile = null,
                secChUaPlatform = null
            )
        )
        
        // Cookie storage per domain
        private val cookieStorage = ConcurrentHashMap<String, MutableMap<String, String>>()
        
        // Last request time per domain for rate limiting
        private val lastRequestTime = ConcurrentHashMap<String, Long>()
        
        // Minimum delay between requests per domain (ms)
        private const val MIN_REQUEST_DELAY = 2000L
        
        // Fingerprint to use per domain (for consistency)
        private val domainFingerprints = ConcurrentHashMap<String, BrowserFingerprint>()
    }
    
    data class BrowserFingerprint(
        val userAgent: String,
        val acceptLanguage: String,
        val platform: String,
        val secChUa: String?,
        val secChUaMobile: String?,
        val secChUaPlatform: String?
    )
    
    data class BypassResult(
        val success: Boolean,
        val html: String?,
        val document: Document?,
        val cookies: Map<String, String>,
        val wasBlocked: Boolean,
        val bypassMethod: String?,
        val error: String?
    )
    
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val domain = url.host
            val domainCookies = cookieStorage.getOrPut(domain) { mutableMapOf() }
            cookies.forEach { cookie ->
                domainCookies[cookie.name] = cookie.value
                Log.d(TAG, "Saved cookie for $domain: ${cookie.name}")
            }
        }
        
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val domain = url.host
            val cookies = mutableListOf<Cookie>()
            
            // Check exact domain and parent domains
            val domainsToCheck = listOf(domain) + getParentDomains(domain)
            
            domainsToCheck.forEach { d ->
                cookieStorage[d]?.forEach { (name, value) ->
                    cookies.add(Cookie.Builder()
                        .domain(domain)
                        .name(name)
                        .value(value)
                        .build())
                }
            }
            
            return cookies
        }
        
        private fun getParentDomains(domain: String): List<String> {
            val parts = domain.split(".")
            val parents = mutableListOf<String>()
            for (i in 1 until parts.size - 1) {
                parents.add(parts.subList(i, parts.size).joinToString("."))
            }
            return parents
        }
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(cookieJar)
        .build()
    
    /**
     * Fetch a URL with automatic Cloudflare bypass
     */
    suspend fun fetch(url: String, additionalHeaders: Map<String, String> = emptyMap()): BypassResult {
        return withContext(Dispatchers.IO) {
            val domain = getDomain(url)
            
            // Rate limiting
            enforceRateLimit(domain)
            
            // Get consistent fingerprint for this domain
            val fingerprint = domainFingerprints.getOrPut(domain) {
                BROWSER_FINGERPRINTS.random()
            }
            
            // Try direct request first
            var result = tryDirectRequest(url, fingerprint, additionalHeaders)
            
            if (result.success && !result.wasBlocked) {
                Log.d(TAG, "✅ Direct request successful for $url")
                return@withContext result
            }
            
            // If blocked, try with stored cookies
            if (cookieStorage.containsKey(domain)) {
                Log.d(TAG, "🔄 Retrying with stored cookies for $domain")
                result = tryDirectRequest(url, fingerprint, additionalHeaders)
                
                if (result.success && !result.wasBlocked) {
                    Log.d(TAG, "✅ Request with cookies successful")
                    return@withContext result
                }
            }
            
            // If still blocked, try WebView challenge solver
            Log.d(TAG, "🌐 Attempting WebView challenge solver for $url")
            result = solveWithWebView(url, fingerprint)
            
            if (result.success) {
                Log.d(TAG, "✅ WebView solver successful")
                return@withContext result
            }
            
            // Final fallback: retry with different fingerprints
            Log.d(TAG, "🔄 Trying alternative fingerprints")
            for (altFingerprint in BROWSER_FINGERPRINTS.shuffled().take(3)) {
                if (altFingerprint != fingerprint) {
                    delay(2000)
                    result = tryDirectRequest(url, altFingerprint, additionalHeaders)
                    if (result.success && !result.wasBlocked) {
                        // Remember this fingerprint worked
                        domainFingerprints[domain] = altFingerprint
                        Log.d(TAG, "✅ Alternative fingerprint successful")
                        return@withContext result
                    }
                }
            }
            
            Log.e(TAG, "❌ All bypass methods failed for $url")
            result
        }
    }
    
    /**
     * Fetch and parse as Jsoup Document
     */
    suspend fun fetchDocument(url: String, additionalHeaders: Map<String, String> = emptyMap()): Document? {
        val result = fetch(url, additionalHeaders)
        return result.document
    }
    
    private suspend fun tryDirectRequest(
        url: String,
        fingerprint: BrowserFingerprint,
        additionalHeaders: Map<String, String>
    ): BypassResult {
        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", fingerprint.userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", fingerprint.acceptLanguage)
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .header("DNT", "1")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Cache-Control", "max-age=0")
            
            // Add Sec-CH-UA headers if browser supports them
            fingerprint.secChUa?.let { requestBuilder.header("Sec-CH-UA", it) }
            fingerprint.secChUaMobile?.let { requestBuilder.header("Sec-CH-UA-Mobile", it) }
            fingerprint.secChUaPlatform?.let { requestBuilder.header("Sec-CH-UA-Platform", it) }
            
            // Add any additional headers
            additionalHeaders.forEach { (key, value) ->
                requestBuilder.header(key, value)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            val html = response.body?.string() ?: ""
            val responseCode = response.code
            
            // Check if we hit a Cloudflare challenge
            val wasBlocked = isCloudflareChallenge(html, responseCode)
            
            if (wasBlocked) {
                Log.w(TAG, "⚠️ Cloudflare challenge detected (code: $responseCode)")
                BypassResult(
                    success = false,
                    html = html,
                    document = null,
                    cookies = getCurrentCookies(getDomain(url)),
                    wasBlocked = true,
                    bypassMethod = null,
                    error = "Cloudflare challenge detected"
                )
            } else {
                val document = Jsoup.parse(html, url)
                BypassResult(
                    success = true,
                    html = html,
                    document = document,
                    cookies = getCurrentCookies(getDomain(url)),
                    wasBlocked = false,
                    bypassMethod = "direct",
                    error = null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request failed: ${e.message}")
            BypassResult(
                success = false,
                html = null,
                document = null,
                cookies = emptyMap(),
                wasBlocked = false,
                bypassMethod = null,
                error = e.message
            )
        }
    }
    
    /**
     * Use Android WebView to solve JavaScript challenges
     * WebView executes JS and passes the challenge automatically
     */
    private suspend fun solveWithWebView(url: String, fingerprint: BrowserFingerprint): BypassResult {
        return withContext(Dispatchers.Main) {
            suspendCoroutine { continuation ->
                try {
                    val webView = WebView(context)
                    
                    // Configure WebView to behave like a real browser
                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        userAgentString = fingerprint.userAgent
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                        allowContentAccess = true
                        allowFileAccess = false
                        javaScriptCanOpenWindowsAutomatically = false
                        mediaPlaybackRequiresUserGesture = true
                    }
                    
                    // Enable cookies
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(webView, true)
                    
                    var pageLoaded = false
                    var challengeSolved = false
                    var loadAttempts = 0
                    val maxAttempts = 3
                    
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            super.onPageFinished(view, loadedUrl)
                            loadAttempts++
                            
                            if (pageLoaded) return
                            
                            // Wait a moment for JS to execute
                            webView.postDelayed({
                                // Get page content
                                webView.evaluateJavascript(
                                    "(function() { return document.documentElement.outerHTML; })();"
                                ) { html ->
                                    val cleanHtml = html
                                        ?.trim('"')
                                        ?.replace("\\u003C", "<")
                                        ?.replace("\\u003E", ">")
                                        ?.replace("\\\"", "\"")
                                        ?.replace("\\n", "\n")
                                        ?: ""
                                    
                                    val isChallenge = isCloudflareChallenge(cleanHtml, 200)
                                    
                                    if (!isChallenge && cleanHtml.length > 1000) {
                                        // Challenge solved!
                                        pageLoaded = true
                                        challengeSolved = true
                                        
                                        // Extract cookies
                                        val domain = getDomain(url)
                                        val cookies = extractCookiesFromWebView(url)
                                        
                                        // Store cookies for future use
                                        cookies.forEach { (name, value) ->
                                            val domainCookies = cookieStorage.getOrPut(domain) { mutableMapOf() }
                                            domainCookies[name] = value
                                        }
                                        
                                        webView.destroy()
                                        
                                        val document = try {
                                            Jsoup.parse(cleanHtml, url)
                                        } catch (e: Exception) {
                                            null
                                        }
                                        
                                        continuation.resume(BypassResult(
                                            success = true,
                                            html = cleanHtml,
                                            document = document,
                                            cookies = cookies,
                                            wasBlocked = false,
                                            bypassMethod = "webview",
                                            error = null
                                        ))
                                    } else if (loadAttempts >= maxAttempts) {
                                        // Give up after max attempts
                                        pageLoaded = true
                                        webView.destroy()
                                        
                                        continuation.resume(BypassResult(
                                            success = false,
                                            html = cleanHtml,
                                            document = null,
                                            cookies = emptyMap(),
                                            wasBlocked = true,
                                            bypassMethod = "webview",
                                            error = "Challenge not solved after $maxAttempts attempts"
                                        ))
                                    }
                                    // Otherwise, wait for next page load (challenge redirect)
                                }
                            }, 3000) // Wait 3 seconds for JS challenge to process
                        }
                        
                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            if (!pageLoaded) {
                                pageLoaded = true
                                webView.destroy()
                                continuation.resume(BypassResult(
                                    success = false,
                                    html = null,
                                    document = null,
                                    cookies = emptyMap(),
                                    wasBlocked = false,
                                    bypassMethod = "webview",
                                    error = "WebView error: $description"
                                ))
                            }
                        }
                    }
                    
                    // Set timeout
                    webView.postDelayed({
                        if (!pageLoaded) {
                            pageLoaded = true
                            webView.destroy()
                            continuation.resume(BypassResult(
                                success = false,
                                html = null,
                                document = null,
                                cookies = emptyMap(),
                                wasBlocked = true,
                                bypassMethod = "webview",
                                error = "WebView timeout"
                            ))
                        }
                    }, 30000) // 30 second timeout
                    
                    // Load the URL
                    webView.loadUrl(url)
                    
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }
    
    private fun extractCookiesFromWebView(url: String): Map<String, String> {
        val cookies = mutableMapOf<String, String>()
        try {
            val cookieManager = CookieManager.getInstance()
            val cookieString = cookieManager.getCookie(url) ?: return cookies
            
            cookieString.split(";").forEach { cookie ->
                val parts = cookie.trim().split("=", limit = 2)
                if (parts.size == 2) {
                    cookies[parts[0].trim()] = parts[1].trim()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting cookies: ${e.message}")
        }
        return cookies
    }
    
    private fun isCloudflareChallenge(html: String, responseCode: Int): Boolean {
        // Check response code
        if (responseCode == 403 || responseCode == 503 || responseCode == 429) {
            val htmlLower = html.lowercase()
            for (marker in CF_CHALLENGE_MARKERS) {
                if (htmlLower.contains(marker.lowercase())) {
                    return true
                }
            }
        }
        
        // Check for challenge page markers even with 200 response
        val htmlLower = html.lowercase()
        val challengeIndicators = listOf(
            "checking your browser",
            "just a moment",
            "cf-browser-verification",
            "please wait",
            "_cf_chl_opt",
            "cdn-cgi/challenge-platform"
        )
        
        for (indicator in challengeIndicators) {
            if (htmlLower.contains(indicator)) {
                return true
            }
        }
        
        // Check if page content is suspiciously short (challenge pages are usually small)
        if (html.length < 500 && (htmlLower.contains("cloudflare") || htmlLower.contains("ray id"))) {
            return true
        }
        
        return false
    }
    
    private fun getDomain(url: String): String {
        return try {
            URI(url).host ?: url
        } catch (e: Exception) {
            url
        }
    }
    
    private fun getCurrentCookies(domain: String): Map<String, String> {
        return cookieStorage[domain]?.toMap() ?: emptyMap()
    }
    
    private suspend fun enforceRateLimit(domain: String) {
        val lastTime = lastRequestTime[domain] ?: 0L
        val now = System.currentTimeMillis()
        val elapsed = now - lastTime
        
        if (elapsed < MIN_REQUEST_DELAY) {
            delay(MIN_REQUEST_DELAY - elapsed)
        }
        
        lastRequestTime[domain] = System.currentTimeMillis()
    }
    
    /**
     * Pre-warm a domain by solving any challenges ahead of time
     */
    suspend fun prewarm(url: String): Boolean {
        Log.d(TAG, "Pre-warming: $url")
        val result = fetch(url)
        return result.success && !result.wasBlocked
    }
    
    /**
     * Clear stored cookies for a domain
     */
    fun clearCookies(domain: String) {
        cookieStorage.remove(domain)
        domainFingerprints.remove(domain)
        Log.d(TAG, "Cleared cookies for $domain")
    }
    
    /**
     * Clear all stored data
     */
    fun clearAll() {
        cookieStorage.clear()
        domainFingerprints.clear()
        lastRequestTime.clear()
        Log.d(TAG, "Cleared all stored data")
    }
    
    /**
     * Get stored cookies for debugging
     */
    fun getStoredCookies(): Map<String, Map<String, String>> {
        return cookieStorage.toMap()
    }
    
    /**
     * Check if a domain has stored Cloudflare cookies
     */
    fun hasCloudflareCookies(domain: String): Boolean {
        val cookies = cookieStorage[domain] ?: return false
        return cookies.keys.any { it.startsWith("cf_") || it == "__cf_bm" }
    }
}
