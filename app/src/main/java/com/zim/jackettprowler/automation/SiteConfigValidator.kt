package com.zim.jackettprowler.automation

import android.content.Context
import android.util.Log
import com.zim.jackettprowler.CustomSiteConfig
import com.zim.jackettprowler.ScraperService
import com.zim.jackettprowler.TorProxyManager
import com.zim.jackettprowler.TorrentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Site Config Validator - Tool-X Inspired Validation System
 * 
 * Validates auto-generated configurations BEFORE saving by:
 * - Testing the search URL
 * - Verifying selectors actually extract data
 * - Checking download links work
 * - Calculating reliability score
 * 
 * Ensures configurations WORK before user commits to saving!
 */
class SiteConfigValidator(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    companion object {
        private const val TAG = "SiteConfigValidator"
        private const val VALIDATION_TIMEOUT = 60000L // 60 seconds
    }
    
    /**
     * Complete validation result with detailed scoring
     */
    data class ValidationResult(
        val isValid: Boolean,
        val overallScore: Double, // 0.0 to 1.0
        val searchUrlValid: Boolean,
        val containerValid: Boolean,
        val titleSelectorValid: Boolean,
        val downloadMethodValid: Boolean,
        val seedersValid: Boolean,
        val sizeValid: Boolean,
        val sampleResults: List<TorrentResult>,
        val issues: List<String>,
        val recommendations: List<String>,
        val responseTime: Long
    )
    
    // Internal state holder to avoid expression-context issues
    private class ValidationState {
        var searchUrlValid = false
        var containerValid = false
        var titleSelectorValid = false
        var downloadMethodValid = false
        var seedersValid = false
        var sizeValid = false
        var sampleResults = emptyList<TorrentResult>()
        val issues = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
    }
    
    /**
     * Validate a CustomSiteConfig by actually testing it
     */
    suspend fun validate(
        config: CustomSiteConfig,
        testQuery: String = "ubuntu"
    ): ValidationResult {
        Log.d(TAG, "🧪 Validating config for: ${config.name}")
        
        val state = ValidationState()
        val startTime = System.currentTimeMillis()
        
        try {
            withTimeout(VALIDATION_TIMEOUT) {
                runValidation(config, testQuery, state)
            }
        } catch (e: Exception) {
            state.issues.add("Validation timeout or error: ${e.message}")
        }
        
        val responseTime = System.currentTimeMillis() - startTime
        
        // Calculate overall score
        val score = calculateScore(state, state.sampleResults.size)
        
        // Generate final recommendations
        addFinalRecommendations(state, score)
        
        return ValidationResult(
            isValid = score >= 0.5 && state.containerValid && state.titleSelectorValid,
            overallScore = score,
            searchUrlValid = state.searchUrlValid,
            containerValid = state.containerValid,
            titleSelectorValid = state.titleSelectorValid,
            downloadMethodValid = state.downloadMethodValid,
            seedersValid = state.seedersValid,
            sizeValid = state.sizeValid,
            sampleResults = state.sampleResults,
            issues = state.issues,
            recommendations = state.recommendations,
            responseTime = responseTime
        )
    }
    
    private suspend fun runValidation(
        config: CustomSiteConfig,
        testQuery: String,
        state: ValidationState
    ) {
        // TEST 1: Search URL Accessibility
        Log.d(TAG, "  Test 1: Search URL...")
        val searchUrl = buildSearchUrl(config, testQuery)
        val searchResponse = testUrl(searchUrl)
        
        if (searchResponse.first) {
            state.searchUrlValid = true
            Log.d(TAG, "    ✓ Search URL accessible")
        } else {
            state.issues.add("Search URL not accessible: ${searchResponse.second}")
            Log.d(TAG, "    ✗ Search URL failed: ${searchResponse.second}")
        }
        
        // TEST 2: Parse with Selectors
        if (state.searchUrlValid) {
            runSelectorTests(config, searchUrl, searchResponse.second ?: "", state)
        }
        
        // TEST 3: Full Scrape Test
        runScrapeTest(config, testQuery, state)
    }
    
    private fun runSelectorTests(
        config: CustomSiteConfig,
        searchUrl: String,
        htmlContent: String,
        state: ValidationState
    ) {
        Log.d(TAG, "  Test 2: Selector validation...")
        val document = Jsoup.parse(htmlContent, searchUrl)
        
        // Test container selector
        val containers = document.select(config.selectors.resultContainer)
        if (containers.size > 0) {
            state.containerValid = true
            Log.d(TAG, "    ✓ Container found: ${containers.size} items")
        } else {
            state.issues.add("Result container selector found 0 items")
            state.recommendations.add("Try different container selector: ${suggestContainerSelector(document)}")
            return
        }
        
        // Test selectors on first container
        val firstItem = containers.firstOrNull() ?: return
        
        // Test title selector
        val titles = firstItem.select(config.selectors.title)
        val titleLength = titles.firstOrNull()?.text()?.length ?: 0
        if (titles.isNotEmpty() && titleLength > 3) {
            state.titleSelectorValid = true
            Log.d(TAG, "    ✓ Title selector works: ${titles.first()?.text()?.take(50)}")
        } else {
            state.issues.add("Title selector found no valid text")
            state.recommendations.add("Try: a[href*='torrent'], .name a, td:nth-child(2) a")
        }
        
        // Test download method
        val hasMagnet = config.selectors.magnetUrl?.let { 
            firstItem.select(it).isNotEmpty() 
        } ?: false
        val hasTorrent = config.selectors.downloadUrl?.let {
            firstItem.select(it).isNotEmpty()
        } ?: false
        
        if (hasMagnet || hasTorrent) {
            state.downloadMethodValid = true
            Log.d(TAG, "    ✓ Download method found")
        } else {
            state.issues.add("No download links found with current selectors")
            
            // Try to find magnet links in the item
            val magnetInItem = firstItem.select("a[href^='magnet:']").isNotEmpty()
            val torrentInItem = firstItem.select("a[href$='.torrent']").isNotEmpty()
            
            if (magnetInItem) {
                state.recommendations.add("Use magnetUrl: a[href^='magnet:']")
            }
            if (torrentInItem) {
                state.recommendations.add("Use downloadUrl: a[href\$='.torrent']")
            }
        }
        
        // Test seeders
        config.selectors.seeders?.let { selector ->
            val seeders = firstItem.select(selector)
            val seedText = seeders.firstOrNull()?.text() ?: ""
            if (seedText.matches(Regex(".*\\d+.*"))) {
                state.seedersValid = true
                Log.d(TAG, "    ✓ Seeders selector works: $seedText")
            }
        }
        
        // Test size
        config.selectors.size?.let { selector ->
            val sizes = firstItem.select(selector)
            val sizeText = sizes.firstOrNull()?.text() ?: ""
            if (sizeText.contains(Regex("\\d+.*[KMGT]?B", RegexOption.IGNORE_CASE))) {
                state.sizeValid = true
                Log.d(TAG, "    ✓ Size selector works: $sizeText")
            }
        }
    }
    
    private suspend fun runScrapeTest(
        config: CustomSiteConfig,
        testQuery: String,
        state: ValidationState
    ) {
        Log.d(TAG, "  Test 3: Full scrape test...")
        try {
            val torProxyManager = TorProxyManager(context)
            val scraperService = ScraperService(torProxyManager, context)
            state.sampleResults = scraperService.search(config, testQuery, limit = 5)
            
            if (state.sampleResults.isNotEmpty()) {
                Log.d(TAG, "    ✓ Scrape returned ${state.sampleResults.size} results")
                validateResultQuality(state)
            } else {
                state.issues.add("Full scrape returned no results")
            }
        } catch (e: Exception) {
            state.issues.add("Scrape test failed: ${e.message}")
            Log.e(TAG, "    ✗ Scrape failed: ${e.message}")
        }
    }
    
    private fun validateResultQuality(state: ValidationState) {
        val validTitles = state.sampleResults.count { it.title.length > 5 }
        val validDownloads = state.sampleResults.count { 
            it.magnetUrl.isNotBlank() || it.link.isNotBlank() 
        }
        
        val totalResults = state.sampleResults.size
        if (validTitles < totalResults) {
            state.issues.add("${totalResults - validTitles} results have invalid titles")
        }
        if (validDownloads < totalResults) {
            state.issues.add("${totalResults - validDownloads} results missing download links")
        }
    }
    
    private fun addFinalRecommendations(state: ValidationState, score: Double) {
        if (score < 0.5) {
            state.recommendations.add("⚠️ Low validation score - manual review recommended")
        }
        if (state.sampleResults.isEmpty() && state.issues.isEmpty()) {
            state.recommendations.add("No results found - the site may have anti-bot protection")
        }
    }
    
    private fun buildSearchUrl(config: CustomSiteConfig, query: String): String {
        val searchPath = config.searchPath.replace("{query}", query)
        return "${config.baseUrl}$searchPath"
    }
    
    private suspend fun testUrl(url: String): Pair<Boolean, String?> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Pair(true, response.body?.string())
                    } else {
                        Pair(false, "HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Pair(false, e.message)
            }
        }
    }
    
    private fun suggestContainerSelector(document: org.jsoup.nodes.Document): String {
        // Try to find the best container
        val candidates = listOf(
            "table tbody tr" to document.select("table tbody tr").size,
            "table tr" to document.select("table tr").size,
            ".torrent" to document.select(".torrent").size,
            ".result" to document.select(".result").size,
            "div[class*='torrent']" to document.select("div[class*='torrent']").size
        )
        
        return candidates.filter { it.second > 2 }
            .maxByOrNull { it.second }?.first ?: "table tr"
    }
    
    private fun calculateScore(state: ValidationState, resultCount: Int): Double {
        var score = 0.0
        
        // Critical fields (weighted higher)
        if (state.searchUrlValid) score += 0.2
        if (state.containerValid) score += 0.2
        if (state.titleSelectorValid) score += 0.2
        if (state.downloadMethodValid) score += 0.2
        
        // Optional fields
        if (state.seedersValid) score += 0.1
        if (state.sizeValid) score += 0.1
        
        // Bonus for actual results
        if (resultCount > 0) score = minOf(score + 0.1, 1.0)
        
        return score
    }
    
    /**
     * Quick validation - just checks if URL is accessible
     */
    suspend fun quickValidate(config: CustomSiteConfig): Boolean {
        val searchUrl = buildSearchUrl(config, "test")
        return testUrl(searchUrl).first
    }
    
    /**
     * Format validation result for display
     */
    fun formatResult(result: ValidationResult): String {
        return buildString {
            appendLine("═".repeat(40))
            appendLine("VALIDATION REPORT")
            appendLine("═".repeat(40))
            appendLine()
            appendLine("Overall Score: ${(result.overallScore * 100).toInt()}%")
            appendLine("Status: ${if (result.isValid) "✅ VALID" else "❌ INVALID"}")
            appendLine("Response Time: ${result.responseTime}ms")
            appendLine()
            
            appendLine("─".repeat(35))
            appendLine("Test Results:")
            appendLine("  ${if (result.searchUrlValid) "✓" else "✗"} Search URL Accessible")
            appendLine("  ${if (result.containerValid) "✓" else "✗"} Container Selector Valid")
            appendLine("  ${if (result.titleSelectorValid) "✓" else "✗"} Title Selector Valid")
            appendLine("  ${if (result.downloadMethodValid) "✓" else "✗"} Download Method Valid")
            appendLine("  ${if (result.seedersValid) "✓" else "✗"} Seeders Selector Valid")
            appendLine("  ${if (result.sizeValid) "✓" else "✗"} Size Selector Valid")
            appendLine()
            
            if (result.sampleResults.isNotEmpty()) {
                appendLine("─".repeat(35))
                appendLine("Sample Results (${result.sampleResults.size}):")
                result.sampleResults.take(3).forEach { torrent ->
                    appendLine("  • ${torrent.title.take(50)}...")
                    appendLine("    Seeds: ${torrent.seeders} | Size: ${torrent.sizeBytes}")
                }
                appendLine()
            }
            
            if (result.issues.isNotEmpty()) {
                appendLine("─".repeat(35))
                appendLine("Issues Found:")
                result.issues.forEach { issue ->
                    appendLine("  ⚠️ $issue")
                }
                appendLine()
            }
            
            if (result.recommendations.isNotEmpty()) {
                appendLine("─".repeat(35))
                appendLine("Recommendations:")
                result.recommendations.forEach { rec ->
                    appendLine("  💡 $rec")
                }
            }
            
            appendLine("═".repeat(40))
        }
    }
}
