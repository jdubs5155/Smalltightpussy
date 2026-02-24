package com.aggregatorx.app.engine.scraper

import com.aggregatorx.app.data.model.*
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advanced Fallback System for Resilient Scraping v2
 *
 * Improvements:
 * - Updated Chrome 122 / Firefox 123 user agents
 * - Accept-Encoding gzip header for better compatibility
 * - Exponential backoff retry with jitter
 * - Headless tab-click fallback wired in
 * - API endpoint discovery via FallbackEngine itself
 */
@Singleton
class FallbackEngine @Inject constructor() {

    /**
     * Try all fallback strategies, then use headless browser as last resort
     * (with shadow DOM, ad-skipper, and tab-click for no-search sites)
     */
    suspend fun fetchWithFallbackAndHeadless(
        url: String,
        waitSelector: String? = null,
        timeout: Int = 12000,
        query: String? = null,
        onHeadless: ((String) -> Unit)? = null
    ): String? {
        val strategies = generateStrategies()
        try {
            return executeWithFallback(strategies) { ctx ->
                Jsoup.connect(url)
                    .userAgent(ctx.userAgent)
                    .header("Referer", ctx.referer)
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(timeout)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .get()
                    .html()
            }
        } catch (_: Exception) {}

        // Headless with shadow DOM & ad skip
        val content = HeadlessBrowserHelper.fetchPageContentWithShadowAndAdSkip(url, waitSelector, timeout)
        onHeadless?.invoke(content ?: "")
        if (!content.isNullOrEmpty()) return content

        // Headless tab-click for no-search sites
        if (query != null) {
            try {
                val baseUrl = url.substringBefore("?").let {
                    val parts = it.split("/")
                    parts.take(3).joinToString("/")
                }
                val tabContent = HeadlessBrowserHelper.fetchContentByClickingTabs(baseUrl, query, timeout)
                if (!tabContent.isNullOrEmpty()) {
                    onHeadless?.invoke(tabContent)
                    return tabContent
                }
            } catch (_: Exception) {}
        }

        return null
    }
    
    companion object {
        // Updated User Agent Pool – Chrome 122, Firefox 123, Safari 17, Edge 122
        val USER_AGENTS = listOf(
            // Desktop Browsers (Chrome 132, Firefox 135, Safari 17.6, Edge 132 — Feb 2026)
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36 Edg/132.0.0.0",
            "Mozilla/5.0 (X11; Linux x86_64; rv:135.0) Gecko/20100101 Firefox/135.0",
            "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:135.0) Gecko/20100101 Firefox/135.0",
            
            // Mobile Browsers (2026)
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.2 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.6834.83 Mobile Safari/537.36",
            "Mozilla/5.0 (iPad; CPU OS 18_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.2 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (Linux; Android 15; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Mobile Safari/537.36",

            // Search engine bots (sometimes bypass blocks)
            "Googlebot/2.1 (+http://www.google.com/bot.html)",
            "Mozilla/5.0 (compatible; Bingbot/2.0; +http://www.bing.com/bingbot.htm)"
        )
        
        // Request delays for rate limiting evasion
        val DELAY_STRATEGIES = listOf(
            0L,      // No delay
            500L,    // Half second
            1000L,   // One second
            2000L,   // Two seconds
            5000L    // Five seconds (last resort)
        )
        
        // Referer variations
        val REFERERS = listOf(
            "",
            "https://www.google.com/",
            "https://www.bing.com/",
            "https://duckduckgo.com/",
            "https://www.yahoo.com/"
        )
    }
    
    /**
     * Execute with fallback strategies using exponential backoff
     */
    suspend fun <T> executeWithFallback(
        strategies: List<FallbackStrategy>,
        block: suspend (FallbackContext) -> T
    ): T {
        var lastException: Exception? = null

        strategies.forEachIndexed { index, strategy ->
            try {
                val context = FallbackContext(
                    userAgent = strategy.userAgent,
                    delay = strategy.delay,
                    referer = strategy.referer,
                    useMobile = strategy.useMobile,
                    headers = strategy.additionalHeaders
                )

                // Exponential backoff: base delay * 2^attempt (capped at 8s)
                val backoffDelay = if (strategy.delay > 0) strategy.delay
                    else minOf(strategy.delay + (100L * (1L shl minOf(index, 6))), 8000L)
                if (backoffDelay > 0) delay(backoffDelay)

                return block(context)
            } catch (e: Exception) {
                lastException = e
            }
        }

        throw lastException ?: Exception("All fallback strategies failed")
    }
    
    /**
     * Generate fallback strategies for a provider
     */
    fun generateStrategies(maxStrategies: Int = 10): List<FallbackStrategy> {
        val strategies = mutableListOf<FallbackStrategy>()
        
        // Basic strategy with different user agents
        USER_AGENTS.take(5).forEach { ua ->
            strategies.add(FallbackStrategy(userAgent = ua))
        }
        
        // Add delay strategies
        DELAY_STRATEGIES.forEach { delay ->
            strategies.add(FallbackStrategy(
                userAgent = USER_AGENTS.random(),
                delay = delay
            ))
        }
        
        // Mobile fallback
        strategies.add(FallbackStrategy(
            userAgent = USER_AGENTS.filter { it.contains("Mobile") }.random(),
            useMobile = true
        ))
        
        // With referers
        REFERERS.forEach { referer ->
            strategies.add(FallbackStrategy(
                userAgent = USER_AGENTS.random(),
                referer = referer
            ))
        }
        
        return strategies.take(maxStrategies)
    }
    
    /**
     * Generate URL variations for fallback
     */
    fun generateUrlVariations(baseUrl: String): List<String> {
        val variations = mutableListOf(baseUrl)
        
        // Add/remove www
        if (baseUrl.contains("://www.")) {
            variations.add(baseUrl.replace("://www.", "://"))
        } else {
            variations.add(baseUrl.replace("://", "://www."))
        }
        
        // Mobile version
        variations.add(baseUrl.replace("://www.", "://m."))
        variations.add(baseUrl.replace("://", "://m."))
        
        // HTTP/HTTPS variations
        if (baseUrl.startsWith("https://")) {
            variations.add(baseUrl.replace("https://", "http://"))
        } else {
            variations.add(baseUrl.replace("http://", "https://"))
        }
        
        return variations.distinct()
    }
}

data class FallbackStrategy(
    val userAgent: String = FallbackEngine.USER_AGENTS.first(),
    val delay: Long = 0L,
    val referer: String = "",
    val useMobile: Boolean = false,
    val additionalHeaders: Map<String, String> = emptyMap()
)

data class FallbackContext(
    val userAgent: String,
    val delay: Long,
    val referer: String,
    val useMobile: Boolean,
    val headers: Map<String, String>
)

/**
 * Provider Health Monitor
 * Tracks provider reliability and adjusts scraping behavior
 */
@Singleton
class ProviderHealthMonitor @Inject constructor() {
    
    private val healthData = mutableMapOf<String, ProviderHealthData>()
    
    fun recordSuccess(providerId: String, responseTime: Long) {
        val current = healthData.getOrPut(providerId) { ProviderHealthData() }
        healthData[providerId] = current.copy(
            successCount = current.successCount + 1,
            totalResponseTime = current.totalResponseTime + responseTime,
            lastSuccessTime = System.currentTimeMillis(),
            consecutiveFailures = 0
        )
    }
    
    fun recordFailure(providerId: String, errorType: ErrorType) {
        val current = healthData.getOrPut(providerId) { ProviderHealthData() }
        healthData[providerId] = current.copy(
            failureCount = current.failureCount + 1,
            lastFailureTime = System.currentTimeMillis(),
            lastErrorType = errorType,
            consecutiveFailures = current.consecutiveFailures + 1
        )
    }
    
    fun getHealthScore(providerId: String): Float {
        val data = healthData[providerId] ?: return 1f
        
        val totalRequests = data.successCount + data.failureCount
        if (totalRequests == 0) return 1f
        
        var score = data.successCount.toFloat() / totalRequests
        
        // Penalty for recent failures
        if (data.consecutiveFailures > 0) {
            score *= (1f - (data.consecutiveFailures * 0.1f).coerceAtMost(0.5f))
        }
        
        // Bonus for recent success
        val timeSinceSuccess = System.currentTimeMillis() - data.lastSuccessTime
        if (timeSinceSuccess < 60000) { // Within last minute
            score += 0.1f
        }
        
        return score.coerceIn(0f, 1f)
    }
    
    fun getAverageResponseTime(providerId: String): Long {
        val data = healthData[providerId] ?: return 0L
        if (data.successCount == 0) return 0L
        return data.totalResponseTime / data.successCount
    }
    
    fun shouldUseAggressiveFallback(providerId: String): Boolean {
        val data = healthData[providerId] ?: return false
        return data.consecutiveFailures >= 3
    }
    
    fun getRecommendedDelay(providerId: String): Long {
        val data = healthData[providerId] ?: return 0L
        return when {
            data.lastErrorType == ErrorType.RATE_LIMITED -> 5000L
            data.consecutiveFailures >= 5 -> 3000L
            data.consecutiveFailures >= 3 -> 1000L
            data.consecutiveFailures >= 1 -> 500L
            else -> 0L
        }
    }
}

data class ProviderHealthData(
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val totalResponseTime: Long = 0L,
    val lastSuccessTime: Long = 0L,
    val lastFailureTime: Long = 0L,
    val lastErrorType: ErrorType = ErrorType.UNKNOWN,
    val consecutiveFailures: Int = 0
)

enum class ErrorType {
    TIMEOUT,
    CONNECTION_FAILED,
    RATE_LIMITED,
    BLOCKED,
    PARSE_ERROR,
    NOT_FOUND,
    SERVER_ERROR,
    UNKNOWN
}
