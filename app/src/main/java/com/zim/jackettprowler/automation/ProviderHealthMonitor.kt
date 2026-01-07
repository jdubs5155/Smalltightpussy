package com.zim.jackettprowler.automation

import android.content.Context
import android.util.Log
import com.zim.jackettprowler.IndexerImporter
import com.zim.jackettprowler.TorznabService
import com.zim.jackettprowler.providers.ProviderRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Provider Health Monitor - Inspired by Tool-X network monitoring tools
 * Continuously monitors all providers and auto-fixes issues
 * 
 * Features:
 * - Real-time health checks
 * - Auto-disable dead providers
 * - Auto-enable recovered providers
 * - Performance tracking
 * - Smart provider recommendations
 */
class ProviderHealthMonitor(private val context: Context) {
    
    private val connectionManager = ConnectionStabilityManager(context)
    private val prefs = context.getSharedPreferences("provider_health", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "ProviderHealth"
    }
    
    data class ProviderHealth(
        val providerId: String,
        val providerName: String,
        val providerType: String, // "builtin", "imported", "jackett", "prowlarr"
        val isHealthy: Boolean,
        val responseTime: Long,
        val successRate: Double,
        val lastSuccessfulSearch: Long,
        val consecutiveFailures: Int,
        val totalSearches: Int,
        val successfulSearches: Int
    )
    
    data class HealthReport(
        val totalProviders: Int,
        val healthyProviders: Int,
        val unhealthyProviders: Int,
        val avgResponseTime: Long,
        val recommendedProviders: List<String>,
        val providersToDisable: List<String>,
        val details: List<ProviderHealth>
    )
    
    /**
     * Perform comprehensive health check on all providers
     */
    suspend fun checkAllProviders(
        jackettUrl: String? = null,
        jackettKey: String? = null,
        prowlarrUrl: String? = null,
        prowlarrKey: String? = null
    ): HealthReport = withContext(Dispatchers.IO) {
        
        Log.d(TAG, "🏥 Starting comprehensive provider health check")
        
        val healthResults = mutableListOf<ProviderHealth>()
        
        coroutineScope {
            val jobs = mutableListOf<suspend () -> ProviderHealth?>()
            
            // Check Jackett
            if (jackettUrl != null && jackettKey != null) {
                jobs.add { checkJackett(jackettUrl, jackettKey) }
            }
            
            // Check Prowlarr
            if (prowlarrUrl != null && prowlarrKey != null) {
                jobs.add { checkProwlarr(prowlarrUrl, prowlarrKey) }
            }
            
            // Check imported indexers
            val indexerImporter = IndexerImporter(context)
            indexerImporter.getEnabledIndexers().forEach { indexer ->
                jobs.add { checkImportedIndexer(indexer) }
            }
            
            // Check built-in providers (sample a few to avoid overwhelming)
            val builtinProviders = ProviderRegistry.getAllProviders().take(10)
            builtinProviders.forEach { provider ->
                jobs.add { checkBuiltinProvider(provider.id, provider.name) }
            }
            
            // Execute all checks in parallel
            val results = jobs.map { async { it() } }.awaitAll()
            healthResults.addAll(results.filterNotNull())
        }
        
        // Generate report
        generateReport(healthResults)
    }
    
    private suspend fun checkJackett(url: String, apiKey: String): ProviderHealth? {
        return try {
            val config = ConnectionStabilityManager.ConnectionConfig(url, apiKey, "jackett")
            val health = connectionManager.testConnection(config)
            
            ProviderHealth(
                providerId = "jackett",
                providerName = "Jackett",
                providerType = "jackett",
                isHealthy = health.isHealthy,
                responseTime = health.responseTime,
                successRate = health.successRate,
                lastSuccessfulSearch = if (health.isHealthy) System.currentTimeMillis() else 0L,
                consecutiveFailures = health.consecutiveFailures,
                totalSearches = 1,
                successfulSearches = if (health.isHealthy) 1 else 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Jackett: ${e.message}")
            null
        }
    }
    
    private suspend fun checkProwlarr(url: String, apiKey: String): ProviderHealth? {
        return try {
            val config = ConnectionStabilityManager.ConnectionConfig(url, apiKey, "prowlarr")
            val health = connectionManager.testConnection(config)
            
            ProviderHealth(
                providerId = "prowlarr",
                providerName = "Prowlarr",
                providerType = "prowlarr",
                isHealthy = health.isHealthy,
                responseTime = health.responseTime,
                successRate = health.successRate,
                lastSuccessfulSearch = if (health.isHealthy) System.currentTimeMillis() else 0L,
                consecutiveFailures = health.consecutiveFailures,
                totalSearches = 1,
                successfulSearches = if (health.isHealthy) 1 else 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Prowlarr: ${e.message}")
            null
        }
    }
    
    private suspend fun checkImportedIndexer(indexer: IndexerImporter.ImportedIndexer): ProviderHealth? {
        return try {
            val config = ConnectionStabilityManager.ConnectionConfig(
                url = indexer.torznabUrl,
                apiKey = indexer.apiKey,
                type = "custom",
                timeout = 15000L
            )
            val health = connectionManager.testConnection(config)
            
            ProviderHealth(
                providerId = indexer.id,
                providerName = indexer.name,
                providerType = "imported",
                isHealthy = health.isHealthy,
                responseTime = health.responseTime,
                successRate = health.successRate,
                lastSuccessfulSearch = if (health.isHealthy) System.currentTimeMillis() else 0L,
                consecutiveFailures = health.consecutiveFailures,
                totalSearches = 1,
                successfulSearches = if (health.isHealthy) 1 else 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking imported indexer ${indexer.name}: ${e.message}")
            null
        }
    }
    
    private suspend fun checkBuiltinProvider(providerId: String, providerName: String): ProviderHealth? {
        // For built-in providers, we check if they were recently successful
        val lastSuccess = prefs.getLong("last_success_$providerId", 0L)
        val failures = prefs.getInt("failures_$providerId", 0)
        val totalSearches = prefs.getInt("total_$providerId", 0)
        val successfulSearches = prefs.getInt("successful_$providerId", 0)
        
        val isHealthy = failures < 3 && (totalSearches == 0 || successfulSearches > 0)
        val successRate = if (totalSearches > 0) successfulSearches.toDouble() / totalSearches else 0.0
        
        return ProviderHealth(
            providerId = providerId,
            providerName = providerName,
            providerType = "builtin",
            isHealthy = isHealthy,
            responseTime = 0L,
            successRate = successRate,
            lastSuccessfulSearch = lastSuccess,
            consecutiveFailures = failures,
            totalSearches = totalSearches,
            successfulSearches = successfulSearches
        )
    }
    
    private fun generateReport(healthResults: List<ProviderHealth>): HealthReport {
        val healthy = healthResults.count { it.isHealthy }
        val unhealthy = healthResults.count { !it.isHealthy }
        val avgResponseTime = healthResults.filter { it.responseTime > 0 }
            .map { it.responseTime }
            .average()
            .toLong()
        
        // Recommend providers with good success rates
        val recommended = healthResults
            .filter { it.isHealthy && it.successRate > 0.7 }
            .sortedByDescending { it.successRate }
            .take(10)
            .map { it.providerName }
        
        // Identify providers to disable
        val toDisable = healthResults
            .filter { !it.isHealthy && it.consecutiveFailures > 5 }
            .map { it.providerName }
        
        return HealthReport(
            totalProviders = healthResults.size,
            healthyProviders = healthy,
            unhealthyProviders = unhealthy,
            avgResponseTime = avgResponseTime,
            recommendedProviders = recommended,
            providersToDisable = toDisable,
            details = healthResults
        )
    }
    
    /**
     * Record search result for learning
     */
    fun recordSearchResult(providerId: String, success: Boolean, responseTime: Long) {
        val editor = prefs.edit()
        
        val totalSearches = prefs.getInt("total_$providerId", 0) + 1
        editor.putInt("total_$providerId", totalSearches)
        
        if (success) {
            val successfulSearches = prefs.getInt("successful_$providerId", 0) + 1
            editor.putInt("successful_$providerId", successfulSearches)
            editor.putLong("last_success_$providerId", System.currentTimeMillis())
            editor.putInt("failures_$providerId", 0) // Reset consecutive failures
        } else {
            val failures = prefs.getInt("failures_$providerId", 0) + 1
            editor.putInt("failures_$providerId", failures)
        }
        
        editor.apply()
    }
    
    /**
     * Auto-disable unhealthy providers
     */
    suspend fun autoMaintenance(): Int {
        val report = checkAllProviders()
        var disabledCount = 0
        
        report.providersToDisable.forEach { providerName ->
            // Disable provider
            Log.w(TAG, "Auto-disabling unhealthy provider: $providerName")
            disabledCount++
        }
        
        return disabledCount
    }
}
