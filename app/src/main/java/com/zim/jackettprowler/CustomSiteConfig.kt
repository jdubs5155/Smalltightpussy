package com.zim.jackettprowler

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Configuration for a custom torrent site with scraper definitions
 */
data class CustomSiteConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val isOnionSite: Boolean = false,
    val enabled: Boolean = true,
    val searchPath: String,
    val searchParamName: String = "q",
    val selectors: ScraperSelectors,
    val headers: Map<String, String> = emptyMap(),
    val requiresTor: Boolean = false,
    val useJavaScript: Boolean = false,
    val rateLimit: Long = 1000, // milliseconds between requests
    val category: String = "general"
)

/**
 * CSS/XPath selectors for extracting torrent data from HTML
 */
data class ScraperSelectors(
    val resultContainer: String, // Container for each torrent result
    val title: String,
    val downloadUrl: String? = null,
    val magnetUrl: String? = null,
    val size: String? = null,
    val seeders: String? = null,
    val leechers: String? = null,
    val publishDate: String? = null,
    val category: String? = null,
    val infoHash: String? = null,
    val torrentPageUrl: String? = null, // Link to detail page if needed
    val selectorType: SelectorType = SelectorType.CSS
)

enum class SelectorType {
    CSS,    // CSS selectors (default)
    XPATH   // XPath expressions
}

/**
 * Manager for custom site configurations with persistence
 */
class CustomSiteManager(private val context: android.content.Context) {
    private val gson = Gson()
    private val configFile = File(context.filesDir, "custom_sites.json")
    
    fun getSites(): List<CustomSiteConfig> {
        if (!configFile.exists()) {
            // Initialize with default templates
            val defaults = getDefaultTemplates()
            saveSites(defaults)
            return defaults
        }
        
        return try {
            val json = configFile.readText()
            val type = object : TypeToken<List<CustomSiteConfig>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    fun saveSites(sites: List<CustomSiteConfig>) {
        try {
            val json = gson.toJson(sites)
            configFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun addSite(site: CustomSiteConfig) {
        val sites = getSites().toMutableList()
        sites.add(site)
        saveSites(sites)
    }
    
    fun updateSite(siteId: String, updatedSite: CustomSiteConfig) {
        val sites = getSites().toMutableList()
        val index = sites.indexOfFirst { it.id == siteId }
        if (index != -1) {
            sites[index] = updatedSite
            saveSites(sites)
        }
    }
    
    fun removeSite(siteId: String) {
        val sites = getSites().filter { it.id != siteId }
        saveSites(sites)
    }
    
    fun getEnabledSites(): List<CustomSiteConfig> {
        return getSites().filter { it.enabled }
    }
    
    fun getOnionSites(): List<CustomSiteConfig> {
        return getEnabledSites().filter { it.isOnionSite || it.requiresTor }
    }
    
    fun getClearnetSites(): List<CustomSiteConfig> {
        return getEnabledSites().filter { !it.isOnionSite && !it.requiresTor }
    }
    
    /**
     * Pre-configured templates for popular torrent sites
     */
    private fun getDefaultTemplates(): List<CustomSiteConfig> {
        return listOf(
            // 1337x template
            CustomSiteConfig(
                id = "1337x",
                name = "1337x",
                baseUrl = "https://1337x.to",
                searchPath = "/search/{query}/1/",
                searchParamName = "query",
                selectors = ScraperSelectors(
                    resultContainer = "table.table-list tbody tr",
                    title = "td.coll-1 a:nth-child(2)",
                    torrentPageUrl = "td.coll-1 a:nth-child(2)",
                    seeders = "td.coll-2",
                    leechers = "td.coll-3",
                    size = "td.coll-4"
                ),
                enabled = false, // Disabled by default
                category = "general"
            ),
            
            // ThePirateBay template (onion)
            CustomSiteConfig(
                id = "tpb-onion",
                name = "The Pirate Bay (Onion)",
                baseUrl = "http://piratebayztemzmv.onion",
                isOnionSite = true,
                requiresTor = true,
                searchPath = "/search/{query}/1/99/0",
                searchParamName = "query",
                selectors = ScraperSelectors(
                    resultContainer = "#searchResult tbody tr",
                    title = "td.vertTh div.detName a",
                    torrentPageUrl = "td.vertTh div.detName a",
                    magnetUrl = "td:nth-child(2) a[href^='magnet:']",
                    seeders = "td:nth-child(3)",
                    leechers = "td:nth-child(4)",
                    size = "font.detDesc"
                ),
                enabled = false,
                category = "general"
            ),
            
            // RARBG-style template
            CustomSiteConfig(
                id = "rarbg-style",
                name = "RARBG Style Site",
                baseUrl = "https://example.com", // User must configure
                searchPath = "/torrents.php?search={query}",
                searchParamName = "query",
                selectors = ScraperSelectors(
                    resultContainer = "table.lista2t tr.lista2",
                    title = "td:nth-child(2) a[href^='/torrent/']",
                    torrentPageUrl = "td:nth-child(2) a[href^='/torrent/']",
                    category = "td:nth-child(1) img",
                    seeders = "td:nth-child(5)",
                    leechers = "td:nth-child(6)",
                    size = "td:nth-child(4)"
                ),
                enabled = false,
                category = "template"
            ),
            
            // Nyaa template (for anime)
            CustomSiteConfig(
                id = "nyaa",
                name = "Nyaa",
                baseUrl = "https://nyaa.si",
                searchPath = "/?q={query}",
                searchParamName = "query",
                selectors = ScraperSelectors(
                    resultContainer = "table.torrent-list tbody tr",
                    title = "td:nth-child(2) a:not(.comments)",
                    torrentPageUrl = "td:nth-child(2) a:not(.comments)",
                    downloadUrl = "td:nth-child(3) a[href$='.torrent']",
                    magnetUrl = "td:nth-child(3) a[href^='magnet:']",
                    size = "td:nth-child(4)",
                    seeders = "td:nth-child(6)",
                    leechers = "td:nth-child(7)",
                    publishDate = "td:nth-child(5)"
                ),
                enabled = false,
                category = "anime"
            )
        )
    }
}
