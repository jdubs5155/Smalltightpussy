package com.aggregatorx.app.engine.scraper

import com.aggregatorx.app.data.model.*
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advanced Fallback System for Resilient Scraping
 * 
 * Provides multiple fallback strategies when primary scraping fails:
 * - User agent rotation
 * - Proxy rotation
 * - Request timing variations  
 * - Mobile/Desktop switching
 * - API endpoint discovery
 * - Cache utilization
 */
@Singleton
class FallbackEngine @Inject constructor() {
        /**
         * Try all fallback strategies, then use headless browser as last resort (with shadow DOM and ad-skipper)
         */
        suspend fun fetchWithFallbackAndHeadless(
            url: String,
            waitSelector: String? = null,
            timeout: Int = 10000,
            onHeadless: ((String) -> Unit)? = null
        ): String? {
            // Try normal fallback strategies first
            val strategies = generateStrategies()
            try {
                return executeWithFallback(strategies) { ctx ->
                    // Use Jsoup with fallback context (user agent, referer, etc)
                    org.jsoup.Jsoup.connect(url)
                        .userAgent(ctx.userAgent)
                        .header("Referer", ctx.referer)
                        .timeout(timeout)
                        .get()
                        .html()
                }
            } catch (_: Exception) {}
            // If all else fails, use headless browser (with shadow DOM and ad-skipper)
            val content = HeadlessBrowserHelper.fetchPageContentWithShadowAndAdSkip(url, waitSelector, timeout)
            onHeadless?.invoke(content ?: "")
            return content
        }
    
    companion object {
        // User Agent Pool
        val USER_AGENTS = listOf(
            // Desktop Browsers
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Edge/120.0.0.0 Safari/537.36",
            
            // Mobile Browsers
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36",
            "Mozilla/5.0 (iPad; CPU OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1",
            
            // Bots (sometimes work when others don't)
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
     * Execute with fallback strategies
     */
    suspend fun <T> executeWithFallback(
        strategies: List<FallbackStrategy>,
        block: suspend (FallbackContext) -> T
    ): T {
        var lastException: Exception? = null
        
        for (strategy in strategies) {
            try {
                val context = FallbackContext(
                    userAgent = strategy.userAgent,
                    delay = strategy.delay,
                    referer = strategy.referer,
                    useMobile = strategy.useMobile,
                    headers = strategy.additionalHeaders
                )
                
                // Apply delay if specified
                if (strategy.delay > 0) {
                    delay(strategy.delay)
                }
                
                return block(context)
            } catch (e: Exception) {
                lastException = e
                // Continue to next strategy
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
