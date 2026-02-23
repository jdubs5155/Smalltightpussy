package com.aggregatorx.app.data.repository

import com.aggregatorx.app.data.database.*
import com.aggregatorx.app.data.model.*
import com.aggregatorx.app.engine.analyzer.SiteAnalyzerEngine
import com.aggregatorx.app.engine.ranking.RankingEngine
import com.aggregatorx.app.engine.scraper.ScrapingEngine
import kotlinx.coroutines.flow.*
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AggregatorRepository @Inject constructor(
    private val providerDao: ProviderDao,
    private val siteAnalysisDao: SiteAnalysisDao,
    private val scrapingConfigDao: ScrapingConfigDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val siteAnalyzerEngine: SiteAnalyzerEngine,
    private val scrapingEngine: ScrapingEngine,
    private val rankingEngine: RankingEngine
) {
    fun clearSearchCache() {
        scrapingEngine.clearCache()
    }
    // Providers
    fun getAllProviders(): Flow<List<Provider>> = providerDao.getAllProviders()
    fun getEnabledProviders(): Flow<List<Provider>> = providerDao.getEnabledProviders()
    
    suspend fun getProviderById(id: String): Provider? = providerDao.getProviderById(id)
    
    suspend fun addProvider(url: String, name: String? = null): Provider {
        val normalizedUrl = normalizeUrl(url)
        val baseUrl = extractBaseUrl(normalizedUrl)
        
        val existingProvider = providerDao.getProviderByUrl(normalizedUrl)
        if (existingProvider != null) {
            return existingProvider
        }
        
        val provider = Provider(
            id = UUID.randomUUID().toString(),
            name = name ?: extractSiteName(normalizedUrl),
            url = normalizedUrl,
            baseUrl = baseUrl,
            isEnabled = true,
            category = detectCategory(normalizedUrl)
        )
        
        providerDao.insertProvider(provider)
        return provider
    }
    
    suspend fun updateProvider(provider: Provider) {
        providerDao.updateProvider(provider)
    }
    
    suspend fun deleteProvider(providerId: String) {
        providerDao.deleteProviderById(providerId)
        siteAnalysisDao.deleteAnalysesForProvider(providerId)
        scrapingConfigDao.deleteConfigForProvider(providerId)
    }
    
    suspend fun setProviderEnabled(providerId: String, enabled: Boolean) {
        providerDao.setProviderEnabled(providerId, enabled)
    }
    
    // Site Analysis
    suspend fun analyzeProvider(providerId: String): SiteAnalysis {
        val provider = providerDao.getProviderById(providerId)
            ?: throw IllegalArgumentException("Provider not found")
        
        val analysis = siteAnalyzerEngine.analyzeSite(provider.url, providerId)
        siteAnalysisDao.insertAnalysis(analysis)
        
        // Generate scraping config from analysis
        generateScrapingConfig(provider, analysis)
        
        // Update provider last analyzed timestamp
        providerDao.updateLastAnalyzed(providerId, System.currentTimeMillis())
        
        return analysis
    }
    
    suspend fun analyzeNewUrl(url: String): Pair<Provider, SiteAnalysis> {
        val provider = addProvider(url)
        val analysis = analyzeProvider(provider.id)
        return Pair(provider, analysis)
    }
    
    suspend fun refreshAllProviders(): List<Pair<Provider, Result<SiteAnalysis>>> {
        val providers = providerDao.getEnabledProvidersSync()
        return providers.map { provider ->
            try {
                val analysis = analyzeProvider(provider.id)
                Pair(provider, Result.success(analysis))
            } catch (e: Exception) {
                Pair(provider, Result.failure(e))
            }
        }
    }
    
    suspend fun getLatestAnalysis(providerId: String): SiteAnalysis? {
        return siteAnalysisDao.getLatestAnalysis(providerId)
    }
    
    fun searchAllProviders(query: String): Flow<ProviderSearchResults> {
        // Always pass false for cache to ensure fresh results for each unique query
        // The cache is cleared before each search anyway, so this ensures no stale results
        return scrapingEngine.searchAllProviders(query, false)
    }
    
    suspend fun aggregateSearchResults(
        query: String,
        providerResults: List<ProviderSearchResults>
    ): AggregatedSearchResults {
        // Save to search history
        searchHistoryDao.insertSearch(SearchHistoryEntry(
            query = query,
            resultCount = providerResults.sumOf { it.results.size },
            providersSearched = providerResults.size,
            successfulProviders = providerResults.count { it.success }
        ))
        
        return rankingEngine.rankAndAggregate(query, providerResults)
    }
    
    // Search History
    fun getRecentSearches(): Flow<List<SearchHistoryEntry>> = searchHistoryDao.getRecentSearches()
    
    suspend fun clearSearchHistory() {
        searchHistoryDao.clearHistory()
    }
    
    // Helper methods
    private suspend fun generateScrapingConfig(provider: Provider, analysis: SiteAnalysis) {
        // Build search URL template
        val searchUrlTemplate = buildSearchUrlTemplate(provider.baseUrl, analysis)
        
        val config = ScrapingConfig(
            providerId = provider.id,
            searchUrlTemplate = searchUrlTemplate,
            resultSelector = analysis.resultItemSelector ?: ".item, .result, article",
            titleSelector = analysis.titleSelector ?: "h2, .title, a",
            urlSelector = "a[href]",
            descriptionSelector = analysis.descriptionSelector,
            thumbnailSelector = analysis.thumbnailSelector,
            dateSelector = analysis.dateSelector,
            ratingSelector = analysis.ratingSelector
        )
        
        scrapingConfigDao.insertConfig(config)
    }
    
    private fun buildSearchUrlTemplate(baseUrl: String, analysis: SiteAnalysis): String {
        // Try to detect search pattern from analysis
        return when {
            analysis.searchFormSelector != null -> "$baseUrl/search?q={query}&page={page}"
            analysis.hasAPI -> "$baseUrl/api/search?query={query}&page={page}"
            else -> "$baseUrl/search?q={query}"
        }
    }
    
    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        return normalized.trimEnd('/')
    }
    
    private fun extractBaseUrl(url: String): String {
        return try {
            val u = URL(url)
            "${u.protocol}://${u.host}"
        } catch (e: Exception) {
            url
        }
    }
    
    private fun extractSiteName(url: String): String {
        return try {
            val u = URL(url)
            val host = u.host.removePrefix("www.")
            host.split(".").first().replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    private fun detectCategory(url: String): ProviderCategory {
        val urlLower = url.lowercase()
        return when {
            urlLower.contains("torrent") || urlLower.contains("1337") || 
            urlLower.contains("rarbg") || urlLower.contains("pirate") -> ProviderCategory.TORRENT
            
            urlLower.contains("stream") || urlLower.contains("movie") || 
            urlLower.contains("watch") || urlLower.contains("video") -> ProviderCategory.STREAMING
            
            urlLower.contains("news") || urlLower.contains("blog") -> ProviderCategory.NEWS
            
            urlLower.contains("api") -> ProviderCategory.API_BASED
            
            else -> ProviderCategory.GENERAL
        }
    }
}
