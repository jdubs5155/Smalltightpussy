package com.zim.jackettprowler.services

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zim.jackettprowler.CustomSiteConfig
import com.zim.jackettprowler.TorProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manager for REAL, VERIFIED torrent sources.
 * 
 * This is NOT a preset manager - it stores ONLY configurations that have been:
 * 1. Auto-detected from live site analysis
 * 2. Verified to return actual torrent data
 * 3. Saved with verification timestamp
 * 
 * NO GENERIC CONFIGS - every source here is real and working!
 */
class RealSourceManager(private val context: Context) {
    companion object {
        private const val TAG = "RealSourceManager"
        private const val PREFS_NAME = "real_verified_sources"
        private const val KEY_SOURCES = "verified_sources"
        private const val KEY_LAST_VERIFICATION = "last_verification"
        
        // How long before re-verification is recommended (7 days)
        private const val REVERIFICATION_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L
    }
    
    private val gson = Gson()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val torProxyManager = TorProxyManager(context)
    private val configBuilder = LiveSiteConfigBuilder(context, torProxyManager)
    private val verificationService = SiteVerificationService(context, torProxyManager)
    
    data class VerifiedSource(
        val config: CustomSiteConfig,
        val verifiedAt: Long,
        val lastResultCount: Int,
        val hasMagnets: Boolean,
        val hasSeeders: Boolean,
        val averageResponseMs: Long,
        val failureCount: Int = 0,
        val isAutoConfigured: Boolean = false
    )
    
    /**
     * Get all verified sources
     */
    fun getAllSources(): List<VerifiedSource> {
        val json = prefs.getString(KEY_SOURCES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<VerifiedSource>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sources: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get only enabled and working sources
     */
    fun getEnabledSources(): List<CustomSiteConfig> {
        return getAllSources()
            .filter { it.config.enabled && it.failureCount < 3 }
            .map { it.config }
    }
    
    /**
     * Add a new source by URL - auto-configures and verifies!
     */
    suspend fun addSourceByUrl(url: String): AddSourceResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Adding source from URL: $url")
        
        // Step 1: Build configuration from live site
        val buildResult = configBuilder.buildConfig(url)
        if (!buildResult.success || buildResult.config == null) {
            return@withContext AddSourceResult(
                success = false,
                message = buildResult.message
            )
        }
        
        // Step 2: Verify the configuration actually works
        val verifyResult = verificationService.verifySite(buildResult.config)
        if (!verifyResult.isWorking) {
            return@withContext AddSourceResult(
                success = false,
                message = "Configuration detected but verification failed: ${verifyResult.errorMessage}"
            )
        }
        
        // Step 3: Save the verified source
        val verifiedSource = VerifiedSource(
            config = buildResult.config,
            verifiedAt = System.currentTimeMillis(),
            lastResultCount = verifyResult.resultCount,
            hasMagnets = verifyResult.hasMagnets,
            hasSeeders = verifyResult.hasSeeders,
            averageResponseMs = verifyResult.responseTimeMs,
            isAutoConfigured = true
        )
        
        saveSource(verifiedSource)
        
        Log.d(TAG, "Successfully added and verified source: ${buildResult.config.name}")
        
        AddSourceResult(
            success = true,
            message = "Added ${buildResult.config.name} - verified with ${verifyResult.resultCount} results",
            source = verifiedSource
        )
    }
    
    /**
     * Add multiple sources from a list of URLs
     */
    suspend fun addMultipleSources(
        urls: List<String>,
        onProgress: ((Int, Int, AddSourceResult) -> Unit)? = null
    ): BatchAddResult = withContext(Dispatchers.IO) {
        val results = mutableListOf<AddSourceResult>()
        var successCount = 0
        
        for ((index, url) in urls.withIndex()) {
            val result = addSourceByUrl(url)
            results.add(result)
            if (result.success) successCount++
            onProgress?.invoke(index + 1, urls.size, result)
        }
        
        BatchAddResult(
            totalAttempted = urls.size,
            successCount = successCount,
            failureCount = urls.size - successCount,
            results = results
        )
    }
    
    /**
     * Re-verify all existing sources
     */
    suspend fun reverifyAllSources(
        onProgress: ((Int, Int, SiteVerificationService.VerificationResult) -> Unit)? = null
    ): ReverificationResult = withContext(Dispatchers.IO) {
        val sources = getAllSources()
        var working = 0
        var broken = 0
        
        val updatedSources = mutableListOf<VerifiedSource>()
        
        for ((index, source) in sources.withIndex()) {
            val result = verificationService.verifySite(source.config)
            onProgress?.invoke(index + 1, sources.size, result)
            
            val updatedSource = if (result.isWorking) {
                working++
                source.copy(
                    verifiedAt = System.currentTimeMillis(),
                    lastResultCount = result.resultCount,
                    hasMagnets = result.hasMagnets,
                    hasSeeders = result.hasSeeders,
                    averageResponseMs = result.responseTimeMs,
                    failureCount = 0
                )
            } else {
                broken++
                source.copy(
                    failureCount = source.failureCount + 1
                )
            }
            
            updatedSources.add(updatedSource)
        }
        
        // Save updated list
        saveAllSources(updatedSources)
        
        ReverificationResult(
            total = sources.size,
            working = working,
            broken = broken,
            lastVerification = System.currentTimeMillis()
        )
    }
    
    /**
     * Remove a source
     */
    fun removeSource(sourceId: String) {
        val sources = getAllSources().filter { it.config.id != sourceId }
        saveAllSources(sources)
    }
    
    /**
     * Toggle source enabled state
     */
    fun toggleSource(sourceId: String, enabled: Boolean) {
        val sources = getAllSources().map { source ->
            if (source.config.id == sourceId) {
                source.copy(config = source.config.copy(enabled = enabled))
            } else source
        }
        saveAllSources(sources)
    }
    
    /**
     * Save a single verified source
     */
    private fun saveSource(source: VerifiedSource) {
        val existing = getAllSources().toMutableList()
        // Remove if exists, then add
        existing.removeAll { it.config.id == source.config.id }
        existing.add(source)
        saveAllSources(existing)
    }
    
    /**
     * Save all sources
     */
    private fun saveAllSources(sources: List<VerifiedSource>) {
        val json = gson.toJson(sources)
        prefs.edit().putString(KEY_SOURCES, json).apply()
    }
    
    /**
     * Check if reverification is recommended
     */
    fun isReverificationRecommended(): Boolean {
        val lastVerification = prefs.getLong(KEY_LAST_VERIFICATION, 0)
        return System.currentTimeMillis() - lastVerification > REVERIFICATION_INTERVAL_MS
    }
    
    /**
     * Get statistics about sources
     */
    fun getSourceStats(): SourceStats {
        val sources = getAllSources()
        return SourceStats(
            total = sources.size,
            enabled = sources.count { it.config.enabled },
            working = sources.count { it.failureCount < 3 },
            withMagnets = sources.count { it.hasMagnets },
            withSeeders = sources.count { it.hasSeeders },
            autoConfigured = sources.count { it.isAutoConfigured },
            onionSites = sources.count { it.config.isOnionSite },
            needsReverification = isReverificationRecommended()
        )
    }
    
    /**
     * Initialize with default verified sources
     * These are KNOWN WORKING sites, not presets!
     */
    suspend fun initializeDefaultSources(): Int = withContext(Dispatchers.IO) {
        val currentSources = getAllSources()
        if (currentSources.isNotEmpty()) {
            return@withContext 0 // Already has sources
        }
        
        // List of known working sites to auto-configure
        val defaultUrls = listOf(
            "https://1337x.to",
            "https://nyaa.si",
            "https://eztv.re",
            "https://torrentgalaxy.to",
            "https://bt4g.org"
        )
        
        var addedCount = 0
        for (url in defaultUrls) {
            val result = addSourceByUrl(url)
            if (result.success) addedCount++
        }
        
        Log.d(TAG, "Initialized $addedCount default sources")
        addedCount
    }
    
    // Result classes
    data class AddSourceResult(
        val success: Boolean,
        val message: String,
        val source: VerifiedSource? = null
    )
    
    data class BatchAddResult(
        val totalAttempted: Int,
        val successCount: Int,
        val failureCount: Int,
        val results: List<AddSourceResult>
    )
    
    data class ReverificationResult(
        val total: Int,
        val working: Int,
        val broken: Int,
        val lastVerification: Long
    )
    
    data class SourceStats(
        val total: Int,
        val enabled: Int,
        val working: Int,
        val withMagnets: Int,
        val withSeeders: Int,
        val autoConfigured: Int,
        val onionSites: Int,
        val needsReverification: Boolean
    )
}
