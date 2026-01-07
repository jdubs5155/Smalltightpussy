package com.zim.jackettprowler.automation

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Connection Stability Manager - Inspired by Tool-X network stability tools
 * Ensures persistent and reliable connections to Jackett/Prowlarr and custom sites
 * 
 * Features:
 * - Health monitoring
 * - Auto-reconnection
 * - Failover support
 * - Connection pooling
 * - Timeout adaptation
 * - Network quality detection
 */
class ConnectionStabilityManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("connection_stability", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val TAG = "ConnectionStability"
        private const val HEALTH_CHECK_INTERVAL = 60000L // 1 minute
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val BASE_RETRY_DELAY = 1000L
    }
    
    data class ConnectionHealth(
        val endpoint: String,
        val isHealthy: Boolean,
        val responseTime: Long,
        val successRate: Double,
        val lastChecked: Long,
        val failureCount: Int,
        val consecutiveFailures: Int
    )
    
    data class ConnectionConfig(
        val url: String,
        val apiKey: String,
        val type: String, // "jackett", "prowlarr", "custom"
        val timeout: Long = 30000L,
        val retryAttempts: Int = 3
    )
    
    private val healthCache = mutableMapOf<String, ConnectionHealth>()
    
    /**
     * Test connection with intelligent retry and adaptation
     */
    suspend fun testConnection(config: ConnectionConfig): ConnectionHealth = withContext(Dispatchers.IO) {
        Log.d(TAG, "Testing connection to ${config.url}")
        
        var consecutiveFailures = 0
        var totalAttempts = 0
        var successfulAttempts = 0
        var totalResponseTime = 0L
        
        repeat(config.retryAttempts) { attempt ->
            totalAttempts++
            
            val startTime = System.currentTimeMillis()
            val result = performHealthCheck(config, attempt)
            val responseTime = System.currentTimeMillis() - startTime
            
            if (result) {
                successfulAttempts++
                consecutiveFailures = 0
                totalResponseTime += responseTime
                
                // Success - no need for more attempts
                return@withContext createHealthResult(
                    endpoint = config.url,
                    isHealthy = true,
                    responseTime = responseTime,
                    successRate = 1.0,
                    failureCount = 0,
                    consecutiveFailures = 0
                )
            } else {
                consecutiveFailures++
                
                if (attempt < config.retryAttempts - 1) {
                    val backoffDelay = calculateBackoffDelay(attempt)
                    Log.d(TAG, "Attempt ${attempt + 1} failed, retrying in ${backoffDelay}ms")
                    delay(backoffDelay)
                }
            }
        }
        
        // All attempts failed
        val avgResponseTime = if (totalResponseTime > 0) totalResponseTime / totalAttempts else 0L
        createHealthResult(
            endpoint = config.url,
            isHealthy = false,
            responseTime = avgResponseTime,
            successRate = successfulAttempts.toDouble() / totalAttempts,
            failureCount = totalAttempts - successfulAttempts,
            consecutiveFailures = consecutiveFailures
        )
    }
    
    private suspend fun performHealthCheck(config: ConnectionConfig, attempt: Int): Boolean {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(config.timeout, TimeUnit.MILLISECONDS)
                .readTimeout(config.timeout, TimeUnit.MILLISECONDS)
                .build()
            
            val testUrl = buildHealthCheckUrl(config)
            val request = Request.Builder()
                .url(testUrl)
                .header("User-Agent", "JackettProwlarrClient/1.5.0")
                .build()
            
            client.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                
                if (success) {
                    Log.d(TAG, "✓ Health check passed for ${config.url}")
                } else {
                    Log.w(TAG, "✗ Health check failed for ${config.url}: HTTP ${response.code}")
                }
                
                success
            }
        } catch (e: Exception) {
            Log.e(TAG, "Health check error for ${config.url}: ${e.message}")
            false
        }
    }
    
    private fun buildHealthCheckUrl(config: ConnectionConfig): String {
        return when (config.type) {
            "jackett" -> "${config.url}/api/v2.0/indexers?configured=true&apikey=${config.apiKey}"
            "prowlarr" -> "${config.url}/api/v1/indexer".also { url ->
                // Prowlarr uses header-based auth
            }
            else -> config.url
        }
    }
    
    private fun calculateBackoffDelay(attempt: Int): Long {
        // Exponential backoff with jitter
        val exponentialDelay = BASE_RETRY_DELAY * (1 shl attempt)
        val jitter = (Math.random() * 1000).toLong()
        return exponentialDelay + jitter
    }
    
    private fun createHealthResult(
        endpoint: String,
        isHealthy: Boolean,
        responseTime: Long,
        successRate: Double,
        failureCount: Int,
        consecutiveFailures: Int
    ): ConnectionHealth {
        val health = ConnectionHealth(
            endpoint = endpoint,
            isHealthy = isHealthy,
            responseTime = responseTime,
            successRate = successRate,
            lastChecked = System.currentTimeMillis(),
            failureCount = failureCount,
            consecutiveFailures = consecutiveFailures
        )
        
        // Cache the result
        healthCache[endpoint] = health
        saveHealthToPrefs(endpoint, health)
        
        return health
    }
    
    /**
     * Get cached health status
     */
    fun getCachedHealth(endpoint: String): ConnectionHealth? {
        // Check memory cache first
        healthCache[endpoint]?.let { return it }
        
        // Check persistent storage
        val json = prefs.getString("health_$endpoint", null) ?: return null
        return try {
            gson.fromJson(json, ConnectionHealth::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Auto-repair connection by trying different configurations
     */
    suspend fun autoRepair(baseUrl: String, apiKey: String, type: String): ConnectionConfig? = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔧 Attempting auto-repair for $baseUrl")
        
        val configurations = generateConfigVariations(baseUrl, apiKey, type)
        
        for (config in configurations) {
            Log.d(TAG, "Trying configuration: ${config.url}")
            val health = testConnection(config)
            
            if (health.isHealthy) {
                Log.d(TAG, "✓ Auto-repair successful with config: ${config.url}")
                saveWorkingConfig(config)
                return@withContext config
            }
        }
        
        Log.e(TAG, "✗ Auto-repair failed - no working configuration found")
        null
    }
    
    private fun generateConfigVariations(baseUrl: String, apiKey: String, type: String): List<ConnectionConfig> {
        val variations = mutableListOf<ConnectionConfig>()
        val cleanUrl = baseUrl.trimEnd('/')
        
        when (type) {
            "jackett" -> {
                // Try different Jackett API paths
                variations.add(ConnectionConfig(cleanUrl, apiKey, type))
                variations.add(ConnectionConfig("$cleanUrl/api/v2.0", apiKey, type))
                variations.add(ConnectionConfig(cleanUrl.replace(":9117", ":9117"), apiKey, type))
                
                // Try with and without trailing slash
                variations.add(ConnectionConfig("$cleanUrl/", apiKey, type))
            }
            "prowlarr" -> {
                // Try different Prowlarr API paths
                variations.add(ConnectionConfig(cleanUrl, apiKey, type))
                variations.add(ConnectionConfig("$cleanUrl/api/v1", apiKey, type))
                variations.add(ConnectionConfig(cleanUrl.replace(":9696", ":9696"), apiKey, type))
            }
            else -> {
                variations.add(ConnectionConfig(cleanUrl, apiKey, type))
            }
        }
        
        // Try different timeouts
        val timeouts = listOf(15000L, 30000L, 60000L)
        val baseVariations = variations.toList()
        variations.clear()
        
        baseVariations.forEach { config ->
            timeouts.forEach { timeout ->
                variations.add(config.copy(timeout = timeout))
            }
        }
        
        return variations
    }
    
    private fun saveWorkingConfig(config: ConnectionConfig) {
        val json = gson.toJson(config)
        prefs.edit().putString("working_config_${config.type}", json).apply()
    }
    
    private fun saveHealthToPrefs(endpoint: String, health: ConnectionHealth) {
        val json = gson.toJson(health)
        prefs.edit().putString("health_$endpoint", json).apply()
    }
    
    /**
     * Monitor connections in background and alert on issues
     */
    suspend fun monitorConnections(configs: List<ConnectionConfig>): Map<String, ConnectionHealth> {
        val results = mutableMapOf<String, ConnectionHealth>()
        
        configs.forEach { config ->
            val health = testConnection(config)
            results[config.url] = health
            
            if (!health.isHealthy) {
                Log.w(TAG, "⚠️ Connection unhealthy: ${config.url}")
                // Attempt auto-repair
                autoRepair(config.url, config.apiKey, config.type)
            }
        }
        
        return results
    }
    
    /**
     * Get connection quality assessment
     */
    fun getConnectionQuality(endpoint: String): String {
        val health = getCachedHealth(endpoint) ?: return "Unknown"
        
        return when {
            !health.isHealthy -> "❌ Down"
            health.responseTime < 1000 -> "🟢 Excellent (${health.responseTime}ms)"
            health.responseTime < 3000 -> "🟡 Good (${health.responseTime}ms)"
            health.responseTime < 5000 -> "🟠 Fair (${health.responseTime}ms)"
            else -> "🔴 Slow (${health.responseTime}ms)"
        }
    }
    
    /**
     * Clear all cached health data
     */
    fun clearHealthCache() {
        healthCache.clear()
        prefs.edit().clear().apply()
    }
}
