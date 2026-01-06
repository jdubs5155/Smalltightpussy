package com.zim.jackettprowler

import android.content.Context
import com.zim.jackettprowler.providers.ProviderRegistry
import kotlinx.coroutines.*

/**
 * Aggregates torrent results from multiple sources:
 * - Torznab APIs (Jackett, Prowlarr)
 * - Imported indexers from Jackett/Prowlarr
 * - Built-in torrent providers (60+ sites)
 * - Custom scraper sites
 * - Onion sites via Tor
 */
class TorrentAggregator(private val context: Context) {
    private val customSiteManager = CustomSiteManager(context)
    private val torProxyManager = TorProxyManager(context)
    private val scraperService = ScraperService(torProxyManager)
    private val indexerImporter = IndexerImporter(context)
    
    /**
     * Search all enabled sources and aggregate results
     */
    suspend fun searchAll(
        query: String,
        jackettService: TorznabService?,
        prowlarrService: TorznabService?,
        limit: Int = 100,
        includeCustomSites: Boolean = true,
        includeOnionSites: Boolean = false,
        includeBuiltInProviders: Boolean = true,
        includeImportedIndexers: Boolean = true
    ): AggregatedResults = withContext(Dispatchers.IO) {
        val allResults = mutableSetOf<TorrentResult>()
        val sourceStatus = mutableMapOf<String, SourceResult>()
        
        // Search Jackett (Torznab)
        if (jackettService != null && isSourceEnabled("jackett")) {
            try {
                val results = jackettService.search(query, TorznabService.SearchType.SEARCH, limit = limit)
                allResults.addAll(results)
                sourceStatus["Jackett"] = SourceResult(true, results.size, null)
            } catch (e: Exception) {
                sourceStatus["Jackett"] = SourceResult(false, 0, e.message)
            }
        }
        
        // Search Prowlarr (Torznab)
        if (prowlarrService != null && isSourceEnabled("prowlarr")) {
            try {
                val results = prowlarrService.search(query, TorznabService.SearchType.SEARCH, limit = limit)
                allResults.addAll(results)
                sourceStatus["Prowlarr"] = SourceResult(true, results.size, null)
            } catch (e: Exception) {
                sourceStatus["Prowlarr"] = SourceResult(false, 0, e.message)
            }
        }
        
        // Search imported indexers from Jackett/Prowlarr
        if (includeImportedIndexers) {
            try {
                val importedResults = indexerImporter.searchAcrossImported(query, limit)
                allResults.addAll(importedResults)
                sourceStatus["Imported Indexers"] = SourceResult(true, importedResults.size, null)
            } catch (e: Exception) {
                sourceStatus["Imported Indexers"] = SourceResult(false, 0, e.message)
            }
        }
        
        // Search built-in providers (60+ sites) as fallback
        if (includeBuiltInProviders) {
            val builtInConfigs = getEnabledBuiltInProviders()
            searchCustomSites(query, builtInConfigs, limit, allResults, sourceStatus)
        }
        
        // Search custom clearnet sites
        if (includeCustomSites) {
            val clearnetSites = customSiteManager.getClearnetSites()
            searchCustomSites(query, clearnetSites, limit, allResults, sourceStatus)
        }
        
        // Search onion sites (only if Tor is enabled and available)
        if (includeOnionSites && torProxyManager.isTorAvailable()) {
            val onionSites = customSiteManager.getOnionSites()
            searchCustomSites(query, onionSites, limit, allResults, sourceStatus)
        }
        
        // Remove duplicates and sort by seeders
        val uniqueResults = deduplicateResults(allResults.toList())
            .sortedByDescending { it.seeders }
            .take(limit)
        
        AggregatedResults(
            results = uniqueResults,
            sourceStatus = sourceStatus,
            totalSources = sourceStatus.size,
            successfulSources = sourceStatus.count { it.value.success },
            totalResults = uniqueResults.size
        )
    }
    
    /**
     * Get enabled built-in providers from settings
     */
    private fun getEnabledBuiltInProviders(): List<CustomSiteConfig> {
        val prefs = context.getSharedPreferences("builtin_providers", Context.MODE_PRIVATE)
        val enabledIds = prefs.getStringSet("enabled_providers", null)
        
        return if (enabledIds == null) {
            // Default: enable only public providers
            ProviderRegistry.getPublicConfigs()
        } else {
            ProviderRegistry.getAllConfigs().filter { it.id in enabledIds }
        }
    }
    
    /**
     * Search across multiple custom sites in parallel
     */
    private suspend fun searchCustomSites(
        query: String,
        sites: List<CustomSiteConfig>,
        limit: Int,
        allResults: MutableSet<TorrentResult>,
        sourceStatus: MutableMap<String, SourceResult>
    ) = coroutineScope {
        val jobs = sites.map { site ->
            async {
                try {
                    val results = scraperService.search(site, query, limit)
                    synchronized(allResults) {
                        allResults.addAll(results)
                        sourceStatus[site.name] = SourceResult(true, results.size, null)
                    }
                } catch (e: Exception) {
                    synchronized(sourceStatus) {
                        sourceStatus[site.name] = SourceResult(false, 0, e.message)
                    }
                }
            }
        }
        jobs.awaitAll()
    }
    
    /**
     * Remove duplicate torrents based on title similarity and info hash
     */
    private fun deduplicateResults(results: List<TorrentResult>): List<TorrentResult> {
        val seen = mutableSetOf<String>()
        val deduped = mutableListOf<TorrentResult>()
        
        for (result in results) {
            // Create a normalized key for deduplication
            val key = when {
                result.infoHash.isNotBlank() -> result.infoHash.lowercase()
                else -> normalizeTitle(result.title)
            }
            
            if (!seen.contains(key)) {
                seen.add(key)
                deduped.add(result)
            }
        }
        
        return deduped
    }
    
    /**
     * Normalize title for deduplication
     */
    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .take(50)
    }
    
    /**
     * Check if a source is enabled in preferences
     */
    private fun isSourceEnabled(source: String): Boolean {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("indexer_${source}-all_enabled", true)
    }
    
    /**
     * Get custom sites count
     */
    fun getCustomSitesCount(): Int {
        return customSiteManager.getEnabledSites().size
    }
    
    /**
     * Get onion sites count
     */
    fun getOnionSitesCount(): Int {
        return customSiteManager.getOnionSites().size
    }
    
    /**
     * Check if Tor is available
     */
    fun isTorAvailable(): Boolean {
        return torProxyManager.isTorAvailable()
    }
    
    /**
     * Get Tor connection status
     */
    fun getTorStatus(): TorConnectionStatus {
        return torProxyManager.getConnectionStatus()
    }
}

/**
 * Aggregated search results with metadata
 */
data class AggregatedResults(
    val results: List<TorrentResult>,
    val sourceStatus: Map<String, SourceResult>,
    val totalSources: Int,
    val successfulSources: Int,
    val totalResults: Int
) {
    fun getStatusSummary(): String {
        val failed = totalSources - successfulSources
        return if (failed == 0) {
            "✓ All $totalSources sources | $totalResults results"
        } else {
            "⚠ $successfulSources/$totalSources sources | $totalResults results"
        }
    }
    
    fun getDetailedStatus(): String {
        return buildString {
            appendLine("Search Results:")
            appendLine("Total Sources: $totalSources")
            appendLine("Successful: $successfulSources")
            appendLine("Failed: ${totalSources - successfulSources}")
            appendLine("Total Results: $totalResults")
            appendLine()
            appendLine("Source Breakdown:")
            sourceStatus.forEach { (name, status) ->
                val icon = if (status.success) "✓" else "✗"
                val msg = if (status.success) {
                    "$icon $name: ${status.resultCount} results"
                } else {
                    "$icon $name: ${status.error}"
                }
                appendLine(msg)
            }
        }
    }
}

/**
 * Result status for a single source
 */
data class SourceResult(
    val success: Boolean,
    val resultCount: Int,
    val error: String?
)
