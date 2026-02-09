package com.aggregatorx.app.data.database

import androidx.room.*
import com.aggregatorx.app.data.model.Provider
import com.aggregatorx.app.data.model.ScrapingConfig
import com.aggregatorx.app.data.model.SearchHistoryEntry
import com.aggregatorx.app.data.model.SiteAnalysis
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY name ASC")
    fun getAllProviders(): Flow<List<Provider>>
    
    @Query("SELECT * FROM providers WHERE isEnabled = 1 ORDER BY name ASC")
    fun getEnabledProviders(): Flow<List<Provider>>
    
    @Query("SELECT * FROM providers WHERE isEnabled = 1")
    suspend fun getEnabledProvidersSync(): List<Provider>
    
    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun getProviderById(id: String): Provider?
    
    @Query("SELECT * FROM providers WHERE url = :url")
    suspend fun getProviderByUrl(url: String): Provider?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvider(provider: Provider)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviders(providers: List<Provider>)
    
    @Update
    suspend fun updateProvider(provider: Provider)
    
    @Delete
    suspend fun deleteProvider(provider: Provider)
    
    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun deleteProviderById(id: String)
    
    @Query("UPDATE providers SET isEnabled = :enabled WHERE id = :id")
    suspend fun setProviderEnabled(id: String, enabled: Boolean)
    
    @Query("UPDATE providers SET healthScore = :score, avgResponseTime = :responseTime WHERE id = :id")
    suspend fun updateProviderStats(id: String, score: Float, responseTime: Long)
    
    @Query("UPDATE providers SET totalSearches = totalSearches + 1 WHERE id = :id")
    suspend fun incrementSearchCount(id: String)
    
    @Query("UPDATE providers SET failedSearches = failedSearches + 1 WHERE id = :id")
    suspend fun incrementFailedCount(id: String)
    
    @Query("UPDATE providers SET lastAnalyzed = :timestamp WHERE id = :id")
    suspend fun updateLastAnalyzed(id: String, timestamp: Long)
}

@Dao
interface SiteAnalysisDao {
    @Query("SELECT * FROM site_analysis ORDER BY analyzedAt DESC")
    fun getAllAnalyses(): Flow<List<SiteAnalysis>>
    
    @Query("SELECT * FROM site_analysis WHERE providerId = :providerId ORDER BY analyzedAt DESC LIMIT 1")
    suspend fun getLatestAnalysis(providerId: String): SiteAnalysis?
    
    @Query("SELECT * FROM site_analysis WHERE id = :id")
    suspend fun getAnalysisById(id: String): SiteAnalysis?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(analysis: SiteAnalysis)
    
    @Update
    suspend fun updateAnalysis(analysis: SiteAnalysis)
    
    @Delete
    suspend fun deleteAnalysis(analysis: SiteAnalysis)
    
    @Query("DELETE FROM site_analysis WHERE providerId = :providerId")
    suspend fun deleteAnalysesForProvider(providerId: String)
}

@Dao
interface ScrapingConfigDao {
    @Query("SELECT * FROM scraping_configs")
    fun getAllConfigs(): Flow<List<ScrapingConfig>>
    
    @Query("SELECT * FROM scraping_configs WHERE providerId = :providerId")
    suspend fun getConfigForProvider(providerId: String): ScrapingConfig?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ScrapingConfig)
    
    @Update
    suspend fun updateConfig(config: ScrapingConfig)
    
    @Delete
    suspend fun deleteConfig(config: ScrapingConfig)
    
    @Query("DELETE FROM scraping_configs WHERE providerId = :providerId")
    suspend fun deleteConfigForProvider(providerId: String)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 50")
    fun getRecentSearches(): Flow<List<SearchHistoryEntry>>
    
    @Query("SELECT * FROM search_history WHERE query LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 10")
    suspend fun searchHistory(query: String): List<SearchHistoryEntry>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(entry: SearchHistoryEntry)
    
    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearch(id: String)
    
    @Query("DELETE FROM search_history")
    suspend fun clearHistory()
}
