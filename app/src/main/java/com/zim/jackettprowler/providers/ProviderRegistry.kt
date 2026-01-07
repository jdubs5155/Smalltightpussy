package com.zim.jackettprowler.providers

import com.zim.jackettprowler.CustomSiteConfig

/**
 * Central registry for all 70+ built-in torrent indexer providers
 * NO CONTENT RESTRICTIONS - includes adult content for testing
 */
object ProviderRegistry {
    
    /**
     * Get all registered providers (65+ indexers)
     */
    fun getAllProviders(): List<IndexerProvider> {
        return publicProviders() + 
               privateProviders() + 
               adultProviders() + 
               internationalProviders() +
               specializedProviders()
    }
    
    /**
     * Get all provider configurations
     */
    fun getAllConfigs(): List<CustomSiteConfig> {
        return getAllProviders().map { it.toConfig() }
    }
    
    /**
     * Get providers by category
     */
    fun getByCategory(category: String): List<CustomSiteConfig> {
        return getAllProviders()
            .filter { it.category.contains(category) }
            .map { it.toConfig() }
    }
    
    /**
     * Get public/open indexers only
     */
    fun getPublicConfigs(): List<CustomSiteConfig> {
        return publicProviders().map { it.toConfig() }
    }
    
    /**
     * Get adult content indexers
     */
    fun getAdultConfigs(): List<CustomSiteConfig> {
        return adultProviders().map { it.toConfig() }
    }
    
    /**
     * Get private tracker templates
     */
    fun getPrivateConfigs(): List<CustomSiteConfig> {
        return privateProviders().map { it.toConfig() }
    }
    
    /**
     * Get international indexers
     */
    fun getInternationalConfigs(): List<CustomSiteConfig> {
        return internationalProviders().map { it.toConfig() }
    }
    
    /**
     * Enable all public providers by default
     */
    fun getDefaultEnabledConfigs(): List<CustomSiteConfig> {
        return getAllConfigs().map { config ->
            // Enable public, disable private (requires auth)
            config.copy(enabled = !config.id.contains("private") && config.category != "private")
        }
    }
    
    /**
     * Search for provider by ID
     */
    fun getProvider(id: String): IndexerProvider? {
        return getAllProviders().find { it.id == id }
    }
    
    // Provider lists
    
    private fun publicProviders() = listOf(
        Provider1337x(),
        ProviderThePirateBay(),
        ProviderEZTV(),
        ProviderYTS(),
        ProviderLimeTorrents(),
        ProviderTorrentz2(),
        ProviderZooqle(),
        ProviderTorrentGalaxy(),
        ProviderNyaa(),
        ProviderKickassTorrents(),
        ProviderTorrentFunk(),
        ProviderTorrentDownloads()
    )
    
    private fun privateProviders() = listOf(
        ProviderIPTorrents(),
        ProviderTorrentLeech(),
        ProviderAlphaRatio(),
        ProviderPassThePopcorn(),
        ProviderRedacted(),
        ProviderOrpheus()
    )
    
    private fun adultProviders() = listOf(
        ProviderEmpornium(),
        ProviderPornLeech(),
        ProviderSukebei(),
        ProviderXVideosTorrents(),
        ProviderPornbay(),
        ProviderTorrentKittyAdult(),
        ProviderBTDiggAdult(),
        ProviderKeep2Share(),
        ProviderXXXTorrents(),
        ProviderJAVTorrent(),
        ProviderHentaiTorrents(),
        ProviderPornBits(),
        ProviderDMHYAdult(),
        ProviderAVgleTorrents(),
        ProviderPornoLab(),
        ProviderMPATorrents()
    )
    
    private fun internationalProviders() = listOf(
        ProviderRutracker(),
        ProviderTorrent9(),
        ProviderMagnetDL(),
        ProviderGlodls(),
        ProviderIsoHunt(),
        ProviderDemonoid(),
        ProviderBTDigg(),
        ProviderTorrentProject()
    )
    
    private fun specializedProviders() = listOf(
        ProviderBTScene(),
        ProviderTorrentSeeds(),
        ProviderISOHunt(),
        ProviderMonova(),
        ProviderTorrentzEU(),
        ProviderIdope(),
        ProviderSkyTorrentsClone(),
        ProviderTorrentAPI(),
        ProviderBitSearch(),
        ProviderTorrentWhiz(),
        ProviderExtraTorrent(),
        ProviderKickassHydra(),
        ProviderRarbgMirror(),
        ProviderTorrentKitty(),
        ProviderTorrentDB(),
        ProviderBitsearch(),
        ProviderTorrentGuru(),
        ProviderSnowfl()
    )
    
    /**
     * Count total providers
     */
    fun getProviderCount(): Int = getAllProviders().size
    
    /**
     * Get provider statistics
     */
    fun getStats(): ProviderStats {
        val all = getAllProviders()
        return ProviderStats(
            total = all.size,
            public = publicProviders().size,
            private = privateProviders().size,
            adult = adultProviders().size,
            international = internationalProviders().size,
            specialized = specializedProviders().size,
            onionSites = all.count { it.isOnionSite },
            requiresTor = all.count { it.requiresTor }
        )
    }
}

data class ProviderStats(
    val total: Int,
    val public: Int,
    val private: Int,
    val adult: Int,
    val international: Int,
    val specialized: Int,
    val onionSites: Int,
    val requiresTor: Int
)
