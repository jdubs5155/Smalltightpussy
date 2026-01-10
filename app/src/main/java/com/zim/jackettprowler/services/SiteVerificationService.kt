package com.zim.jackettprowler.services

import android.content.Context
import android.util.Log
import com.zim.jackettprowler.CustomSiteConfig
import com.zim.jackettprowler.TorrentResult
import com.zim.jackettprowler.ScraperService
import com.zim.jackettprowler.TorProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Service that VERIFIES configurations by actually performing searches
 * and checking that real data is returned.
 * 
 * This is NOT a mock - it tests REAL sites with REAL queries!
 */
class SiteVerificationService(
    private val context: Context,
    private val torProxyManager: TorProxyManager
) {
    companion object {
        private const val TAG = "SiteVerificationService"
        private const val VERIFICATION_TIMEOUT_MS = 30000L
        
        // Test queries that should return results on most sites
        val TEST_QUERIES = listOf(
            "ubuntu",
            "linux",
            "open source",
            "2024"
        )
    }
    
    private val scraperService = ScraperService(torProxyManager, context)
    
    data class VerificationResult(
        val siteId: String,
        val siteName: String,
        val isWorking: Boolean,
        val resultCount: Int,
        val sampleTitles: List<String>,
        val hasMagnets: Boolean,
        val hasDownloadLinks: Boolean,
        val hasSeeders: Boolean,
        val responseTimeMs: Long,
        val errorMessage: String? = null,
        val verifiedAt: Long = System.currentTimeMillis()
    )
    
    /**
     * Verify a site configuration by actually searching it
     */
    suspend fun verifySite(config: CustomSiteConfig): VerificationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            Log.d(TAG, "Verifying site: ${config.name} (${config.baseUrl})")
            
            // Try each test query until we get results
            var results: List<TorrentResult> = emptyList()
            var usedQuery = ""
            
            for (query in TEST_QUERIES) {
                val searchResults = withTimeoutOrNull(VERIFICATION_TIMEOUT_MS) {
                    scraperService.search(config, query, limit = 20)
                }
                
                if (searchResults != null && searchResults.isNotEmpty()) {
                    results = searchResults
                    usedQuery = query
                    break
                }
            }
            
            val responseTime = System.currentTimeMillis() - startTime
            
            if (results.isEmpty()) {
                Log.w(TAG, "No results found for ${config.name}")
                return@withContext VerificationResult(
                    siteId = config.id,
                    siteName = config.name,
                    isWorking = false,
                    resultCount = 0,
                    sampleTitles = emptyList(),
                    hasMagnets = false,
                    hasDownloadLinks = false,
                    hasSeeders = false,
                    responseTimeMs = responseTime,
                    errorMessage = "No results returned for test queries"
                )
            }
            
            // Analyze results quality
            val hasMagnets = results.any { it.magnetUrl.isNotBlank() }
            val hasDownloads = results.any { it.link.isNotBlank() && !it.link.startsWith("magnet:") }
            val hasSeeders = results.any { it.seeders > 0 }
            val sampleTitles = results.take(5).map { it.title }
            
            Log.d(TAG, "Verified ${config.name}: ${results.size} results, magnets=$hasMagnets, seeders=$hasSeeders")
            
            VerificationResult(
                siteId = config.id,
                siteName = config.name,
                isWorking = true,
                resultCount = results.size,
                sampleTitles = sampleTitles,
                hasMagnets = hasMagnets,
                hasDownloadLinks = hasDownloads,
                hasSeeders = hasSeeders,
                responseTimeMs = responseTime
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying ${config.name}: ${e.message}", e)
            VerificationResult(
                siteId = config.id,
                siteName = config.name,
                isWorking = false,
                resultCount = 0,
                sampleTitles = emptyList(),
                hasMagnets = false,
                hasDownloadLinks = false,
                hasSeeders = false,
                responseTimeMs = System.currentTimeMillis() - startTime,
                errorMessage = e.message
            )
        }
    }
    
    /**
     * Batch verify multiple sites
     */
    suspend fun verifyMultipleSites(
        configs: List<CustomSiteConfig>,
        onProgress: ((Int, Int, VerificationResult) -> Unit)? = null
    ): List<VerificationResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VerificationResult>()
        
        for ((index, config) in configs.withIndex()) {
            val result = verifySite(config)
            results.add(result)
            onProgress?.invoke(index + 1, configs.size, result)
        }
        
        results
    }
    
    /**
     * Quick check if a site is accessible (doesn't verify selectors)
     * Now with Cloudflare bypass support
     */
    suspend fun quickCheck(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Use Cloudflare bypass service for better success rate
            val bypassService = CloudflareBypassService(context)
            val result = bypassService.fetch(baseUrl)
            
            if (result.success && !result.wasBlocked) {
                Log.d(TAG, "Quick check passed for $baseUrl (method: ${result.bypassMethod})")
                true
            } else if (result.wasBlocked) {
                Log.w(TAG, "Quick check: Cloudflare blocked for $baseUrl")
                // Still consider it "accessible" - we just need to solve the challenge
                true
            } else {
                Log.e(TAG, "Quick check failed for $baseUrl: ${result.error}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Quick check failed for $baseUrl: ${e.message}")
            false
        }
    }
}
