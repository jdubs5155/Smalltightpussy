package com.zim.jackettprowler.providers

import com.zim.jackettprowler.TorrentResult
import com.zim.jackettprowler.CustomSiteConfig
import com.zim.jackettprowler.ScraperSelectors
import com.zim.jackettprowler.SelectorType

/**
 * Base interface for all indexer providers
 */
interface IndexerProvider {
    val id: String
    val name: String
    val baseUrl: String
    val language: String
    val description: String
    val category: List<String>
    val isOnionSite: Boolean
    val requiresTor: Boolean
    val isPrivate: Boolean
    
    /**
     * Convert this provider to a CustomSiteConfig for use with the scraper
     */
    fun toConfig(): CustomSiteConfig
    
    /**
     * Check if the provider is currently accessible
     */
    suspend fun checkAvailability(): Boolean
    
    /**
     * Get alternative URLs/mirrors for this provider
     */
    fun getMirrors(): List<String>
}

/**
 * Abstract base implementation with common functionality
 */
abstract class BaseIndexerProvider : IndexerProvider {
    override val language: String = "en-US"
    override val category: List<String> = listOf("Movies", "TV", "Music", "Games", "Software", "Books")
    override val isOnionSite: Boolean = false
    override val requiresTor: Boolean = false
    override val isPrivate: Boolean = false
    
    override suspend fun checkAvailability(): Boolean {
        // Default implementation - can be overridden
        return try {
            // Simplified check - real implementation would ping the site
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getMirrors(): List<String> = emptyList()
    
    /**
     * Helper to create common search path patterns
     */
    protected fun searchPath(pattern: String): String = pattern
    
    /**
     * Helper to create common selector patterns
     */
    protected fun selectors(
        container: String,
        title: String,
        downloadUrl: String? = null,
        magnetUrl: String? = null,
        size: String? = null,
        seeders: String? = null,
        leechers: String? = null,
        publishDate: String? = null,
        category: String? = null,
        infoHash: String? = null,
        torrentPageUrl: String? = null,
        type: SelectorType = SelectorType.CSS
    ) = ScraperSelectors(
        resultContainer = container,
        title = title,
        downloadUrl = downloadUrl,
        magnetUrl = magnetUrl,
        size = size,
        seeders = seeders,
        leechers = leechers,
        publishDate = publishDate,
        category = category,
        infoHash = infoHash,
        torrentPageUrl = torrentPageUrl,
        selectorType = type
    )
}
