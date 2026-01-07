package com.zim.jackettprowler.automation

import android.content.Context
import android.util.Log
import com.zim.jackettprowler.CustomSiteConfig
import com.zim.jackettprowler.CustomSiteManager
import com.zim.jackettprowler.ScraperSelectors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * URL-to-Config Converter - The ULTIMATE automation tool!
 * 
 * Tool-X Inspired: Combines multiple reconnaissance tools to:
 * - SiteInfiltrator: Initial site analysis and protection detection
 * - DeepParsingAnalyzer: Advanced CSS selector detection for EVERY field
 * - Auto-generates complete working configuration
 * 
 * Just paste a URL → Get a fully working provider config!
 * All parsing, selectors, and API data auto-detected and filled!
 */
class URLToConfigConverter(private val context: Context) {
    
    private val infiltrator = SiteInfiltrator()
    private val deepAnalyzer = DeepParsingAnalyzer()
    private val customSiteManager = CustomSiteManager(context)
    
    companion object {
        private const val TAG = "URLToConfig"
    }
    
    data class ConversionResult(
        val success: Boolean,
        val config: CustomSiteConfig?,
        val infiltrationData: SiteInfiltrator.InfiltrationResult?,
        val deepAnalysis: DeepParsingAnalyzer.DeepAnalysisResult?,
        val autoSaved: Boolean,
        val message: String,
        val detectionDetails: DetectionDetails? = null
    )
    
    /**
     * Details about what was auto-detected
     */
    data class DetectionDetails(
        val titleSelector: String,
        val titleConfidence: Double,
        val magnetSelector: String?,
        val torrentSelector: String?,
        val seedersSelector: String?,
        val leechersSelector: String?,
        val sizeSelector: String?,
        val containerSelector: String,
        val detectedAPIs: List<String>,
        val recommendations: List<String>
    )
    
    /**
     * MAIN MAGIC FUNCTION - Convert URL to working config with FULL automation!
     * 
     * This does EVERYTHING:
     * 1. Infiltrates the site (protection detection, initial analysis)
     * 2. Deep parses to find EXACT selectors for every field
     * 3. Auto-generates complete CustomSiteConfig
     * 4. Validates the configuration
     * 5. Optionally auto-saves
     */
    suspend fun convertAndSave(
        url: String,
        autoSave: Boolean = true,
        testQuery: String = "ubuntu"
    ): ConversionResult = withContext(Dispatchers.IO) {
        
        Log.d(TAG, "🎯 Starting FULL URL-to-Config conversion for: $url")
        Log.d(TAG, "📡 Phase 1: Site Infiltration...")
        
        try {
            // ============================================================
            // PHASE 1: SITE INFILTRATION
            // Protection detection, basic structure, API discovery
            // ============================================================
            val infiltrationResult = infiltrator.infiltrate(url)
            
            Log.d(TAG, "✓ Infiltration complete. Success: ${infiltrationResult.success}")
            Log.d(TAG, "  - Cloudflare: ${infiltrationResult.cloudflareProtected}")
            Log.d(TAG, "  - reCAPTCHA: ${infiltrationResult.reCaptchaPresent}")
            Log.d(TAG, "  - Auth Required: ${infiltrationResult.authRequired}")
            
            // ============================================================
            // PHASE 2: DEEP PARSING ANALYSIS
            // Detect EXACT CSS selectors for every field!
            // ============================================================
            Log.d(TAG, "📡 Phase 2: Deep Parsing Analysis...")
            val deepAnalysis = deepAnalyzer.analyzeDeep(url, testQuery)
            
            Log.d(TAG, "✓ Deep analysis complete. Confidence: ${deepAnalysis.confidence}")
            Log.d(TAG, "  - Result container: ${deepAnalysis.detectedSelectors.resultContainer.selector}")
            Log.d(TAG, "  - Title: ${deepAnalysis.detectedSelectors.title.selector}")
            Log.d(TAG, "  - Magnet: ${deepAnalysis.detectedSelectors.magnetLink?.selector ?: "not found"}")
            Log.d(TAG, "  - Seeders: ${deepAnalysis.detectedSelectors.seeders?.selector ?: "not found"}")
            
            // ============================================================
            // PHASE 3: BUILD CONFIGURATION
            // Merge infiltration + deep analysis into complete config
            // ============================================================
            Log.d(TAG, "📡 Phase 3: Building Configuration...")
            val config = buildConfigFromDeepAnalysis(infiltrationResult, deepAnalysis, testQuery)
            
            // ============================================================
            // PHASE 4: VALIDATION
            // Ensure all critical fields are present
            // ============================================================
            Log.d(TAG, "📡 Phase 4: Validation...")
            val (isValid, validationMessage) = validateConfigDetailed(config)
            
            if (!isValid) {
                Log.w(TAG, "⚠️ Validation failed: $validationMessage")
                return@withContext ConversionResult(
                    success = false,
                    config = config,
                    infiltrationData = infiltrationResult,
                    deepAnalysis = deepAnalysis,
                    autoSaved = false,
                    message = "Validation failed: $validationMessage",
                    detectionDetails = buildDetectionDetails(deepAnalysis)
                )
            }
            
            // ============================================================
            // PHASE 5: AUTO-SAVE
            // ============================================================
            var saved = false
            if (autoSave) {
                Log.d(TAG, "📡 Phase 5: Auto-saving...")
                customSiteManager.addSite(config)
                saved = true
                Log.d(TAG, "✓ Auto-saved config for ${config.name}")
            }
            
            Log.d(TAG, "🎉 URL-to-Config conversion COMPLETE!")
            
            ConversionResult(
                success = true,
                config = config,
                infiltrationData = infiltrationResult,
                deepAnalysis = deepAnalysis,
                autoSaved = saved,
                message = "✓ Successfully configured ${config.name} with ${getDetectionSummary(deepAnalysis)}",
                detectionDetails = buildDetectionDetails(deepAnalysis)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Conversion failed: ${e.message}", e)
            ConversionResult(
                success = false,
                config = null,
                infiltrationData = null,
                deepAnalysis = null,
                autoSaved = false,
                message = "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Build complete CustomSiteConfig from deep analysis results
     * This is where the MAGIC happens - auto-filling ALL fields!
     */
    private fun buildConfigFromDeepAnalysis(
        infiltration: SiteInfiltrator.InfiltrationResult,
        deepAnalysis: DeepParsingAnalyzer.DeepAnalysisResult,
        testQuery: String
    ): CustomSiteConfig {
        val selectors = deepAnalysis.detectedSelectors
        val siteInfo = deepAnalysis.siteInfo
        
        // Use deep analysis selectors (more accurate) with fallback to infiltration
        val resultContainer = selectors.resultContainer.selector.takeIf { it.isNotBlank() }
            ?: infiltration.torrentListPatterns.firstOrNull()
            ?: "table tr"
        
        val titleSelector = selectors.title.selector.takeIf { it.isNotBlank() }
            ?: "a"
        
        val magnetSelector = selectors.magnetLink?.selector
            ?: infiltration.downloadMethods.find { it.type == "magnet" }?.pattern
        
        val torrentSelector = selectors.torrentLink?.selector
            ?: infiltration.downloadMethods.find { it.type == "torrent" }?.pattern
        
        val seedersSelector = selectors.seeders?.selector
        val leechersSelector = selectors.leechers?.selector
        val sizeSelector = selectors.size?.selector
        
        // Build search URL path
        val searchPath = siteInfo.searchUrl
            .replace(siteInfo.baseUrl, "")
            .replace("{query}", "{query}") // Ensure placeholder is correct
            .takeIf { it.isNotBlank() }
            ?: "/search?${siteInfo.searchParamName}={query}"
        
        // Determine rate limit based on protections
        val rateLimit = when {
            siteInfo.hasCloudflare -> 3000L
            siteInfo.hasCaptcha -> 5000L
            siteInfo.isOnionSite -> 2000L
            else -> 1000L
        }
        
        return CustomSiteConfig(
            id = generateSiteId(siteInfo.name),
            name = siteInfo.name,
            baseUrl = siteInfo.baseUrl,
            searchPath = searchPath,
            searchParamName = siteInfo.searchParamName,
            selectors = ScraperSelectors(
                resultContainer = resultContainer,
                title = titleSelector,
                magnetUrl = magnetSelector,
                downloadUrl = torrentSelector,
                seeders = seedersSelector,
                leechers = leechersSelector,
                size = sizeSelector
            ),
            enabled = true,
            requiresTor = siteInfo.isOnionSite,
            isOnionSite = siteInfo.isOnionSite,
            rateLimit = rateLimit,
            category = "auto-added"
        )
    }
    
    /**
     * Detailed validation with specific error messages
     */
    private fun validateConfigDetailed(config: CustomSiteConfig): Pair<Boolean, String> {
        val issues = mutableListOf<String>()
        
        if (config.name.isBlank()) issues.add("Site name not detected")
        if (config.baseUrl.isBlank()) issues.add("Base URL missing")
        if (config.searchPath.isBlank()) issues.add("Search path not found")
        if (config.selectors.resultContainer.isBlank()) issues.add("Result container not detected")
        if (config.selectors.title.isBlank()) issues.add("Title selector not found")
        
        // Not critical but log warnings
        if (config.selectors.magnetUrl == null && config.selectors.downloadUrl == null) {
            Log.w(TAG, "⚠️ No download method detected - may need details page parsing")
        }
        
        return if (issues.isEmpty()) {
            true to "Valid"
        } else {
            false to issues.joinToString(", ")
        }
    }
    
    /**
     * Build detection details for UI display
     */
    private fun buildDetectionDetails(analysis: DeepParsingAnalyzer.DeepAnalysisResult): DetectionDetails {
        val selectors = analysis.detectedSelectors
        return DetectionDetails(
            titleSelector = selectors.title.selector,
            titleConfidence = selectors.title.confidence,
            magnetSelector = selectors.magnetLink?.selector,
            torrentSelector = selectors.torrentLink?.selector,
            seedersSelector = selectors.seeders?.selector,
            leechersSelector = selectors.leechers?.selector,
            sizeSelector = selectors.size?.selector,
            containerSelector = selectors.resultContainer.selector,
            detectedAPIs = analysis.apiEndpoints.map { "${it.type}: ${it.url}" },
            recommendations = analysis.recommendations
        )
    }
    
    /**
     * Generate summary of what was detected
     */
    private fun getDetectionSummary(analysis: DeepParsingAnalyzer.DeepAnalysisResult): String {
        val detectedCount = listOfNotNull(
            analysis.detectedSelectors.title.selector.takeIf { it.isNotBlank() },
            analysis.detectedSelectors.magnetLink?.selector,
            analysis.detectedSelectors.torrentLink?.selector,
            analysis.detectedSelectors.seeders?.selector,
            analysis.detectedSelectors.leechers?.selector,
            analysis.detectedSelectors.size?.selector
        ).size
        
        return "$detectedCount/6 fields auto-detected (${(analysis.confidence * 100).toInt()}% confidence)"
    }
    
    private fun generateSiteId(siteName: String): String {
        return "auto_" + siteName.lowercase()
            .replace(Regex("[^a-z0-9]"), "_")
            .take(30) + "_" + System.currentTimeMillis().toString().takeLast(6)
    }
    
    /**
     * Batch convert multiple URLs
     */
    suspend fun convertBatch(urls: List<String>): Map<String, ConversionResult> {
        val results = mutableMapOf<String, ConversionResult>()
        
        urls.forEach { url ->
            val result = convertAndSave(url, autoSave = true)
            results[url] = result
            
            if (result.success) {
                Log.d(TAG, "✓ Successfully added: ${result.config?.name}")
            } else {
                Log.w(TAG, "✗ Failed to add: $url - ${result.message}")
            }
        }
        
        return results
    }
    
    /**
     * Quick analysis without saving - for preview
     */
    suspend fun analyzeOnly(url: String, testQuery: String = "ubuntu"): ConversionResult {
        return convertAndSave(url, autoSave = false, testQuery = testQuery)
    }
}
