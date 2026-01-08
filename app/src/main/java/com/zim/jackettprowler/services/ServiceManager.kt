package com.zim.jackettprowler.services

import android.content.Context
import android.util.Log
import com.zim.jackettprowler.automation.ConnectionStabilityManager
import com.zim.jackettprowler.automation.ProviderHealthMonitor

/**
 * Service Manager - Centralized control for all background services
 * 
 * Provides single point of initialization and management for:
 * - BackgroundServiceDaemon
 * - SearchResultCache
 * - NetworkQualityMonitor
 * - ProviderAnalytics
 * - ProviderHealthMonitor
 * - ConnectionStabilityManager
 */
object ServiceManager {
    
    private const val TAG = "ServiceManager"
    
    private var isInitialized = false
    
    // Service instances
    private var searchCache: SearchResultCache? = null
    private var networkMonitor: NetworkQualityMonitor? = null
    private var providerAnalytics: ProviderAnalytics? = null
    private var healthMonitor: ProviderHealthMonitor? = null
    private var stabilityManager: ConnectionStabilityManager? = null
    
    /**
     * Initialize all services
     */
    @Synchronized
    fun initialize(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "Services already initialized")
            return
        }
        
        Log.i(TAG, "Initializing all services...")
        
        val appContext = context.applicationContext
        
        // Initialize services
        searchCache = SearchResultCache(appContext)
        networkMonitor = NetworkQualityMonitor(appContext).apply {
            startMonitoring()
        }
        providerAnalytics = ProviderAnalytics(appContext)
        healthMonitor = ProviderHealthMonitor(appContext)
        stabilityManager = ConnectionStabilityManager(appContext)
        
        // Start background daemon
        DaemonController.initialize(appContext)
        
        isInitialized = true
        Log.i(TAG, "All services initialized successfully")
    }
    
    /**
     * Shutdown all services
     */
    @Synchronized
    fun shutdown() {
        if (!isInitialized) return
        
        Log.i(TAG, "Shutting down all services...")
        
        networkMonitor?.destroy()
        // Note: BackgroundServiceDaemon stops when isEnabled is set to false
        
        searchCache = null
        networkMonitor = null
        providerAnalytics = null
        healthMonitor = null
        stabilityManager = null
        
        isInitialized = false
        Log.i(TAG, "All services shut down")
    }
    
    // Service accessors
    
    fun getSearchCache(): SearchResultCache? = searchCache
    
    fun getNetworkMonitor(): NetworkQualityMonitor? = networkMonitor
    
    fun getProviderAnalytics(): ProviderAnalytics? = providerAnalytics
    
    fun getHealthMonitor(): ProviderHealthMonitor? = healthMonitor
    
    fun getStabilityManager(): ConnectionStabilityManager? = stabilityManager
    
    /**
     * Check if all services are running properly
     */
    fun isHealthy(): Boolean {
        if (!isInitialized) return false
        
        // Check network availability
        val networkAvailable = networkMonitor?.isNetworkAvailable() ?: false
        
        return networkAvailable || isInitialized
    }
    
    /**
     * Get comprehensive status report
     */
    fun getStatusReport(): Map<String, Any> {
        val status = mutableMapOf<String, Any>()
        
        status["initialized"] = isInitialized
        
        networkMonitor?.let {
            val netStatus = it.getCurrentStatus()
            status["network_type"] = netStatus.connectionType.name
            status["network_quality"] = netStatus.quality.name
            status["network_latency_ms"] = netStatus.latencyMs
        }
        
        searchCache?.let {
            val cacheStats = it.getStats()
            status["cache_memory_entries"] = cacheStats.memoryEntries
            status["cache_disk_entries"] = cacheStats.diskEntries
            status["cache_hit_rate"] = "%.1f%%".format(cacheStats.hitRate * 100)
        }
        
        providerAnalytics?.let {
            val summary = it.getSummary()
            status["total_searches"] = summary.totalSearches
            status["provider_count"] = summary.totalProviders
            status["success_rate"] = "%.1f%%".format(summary.overallSuccessRate * 100)
        }
        
        return status
    }
    
    /**
     * Run maintenance tasks
     */
    fun runMaintenance() {
        Log.i(TAG, "Running maintenance tasks...")
        
        // Cleanup expired cache entries
        searchCache?.cleanup()
        
        // Cleanup old analytics data
        providerAnalytics?.cleanup()
        
        Log.i(TAG, "Maintenance complete")
    }
}
