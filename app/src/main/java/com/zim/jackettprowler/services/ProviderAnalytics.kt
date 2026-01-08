package com.zim.jackettprowler.services

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.TimeUnit

/**
 * Provider Analytics Service
 * 
 * Tracks and analyzes provider performance:
 * - Success/failure rates
 * - Average response times
 * - Result counts
 * - Usage patterns
 * - Provider ranking
 */
class ProviderAnalytics(context: Context) {
    
    companion object {
        private const val TAG = "ProviderAnalytics"
        private const val PREFS_NAME = "provider_analytics"
        private const val MAX_HISTORY_DAYS = 30
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    data class ProviderMetrics(
        val providerId: String,
        val providerName: String,
        var totalRequests: Long = 0,
        var successfulRequests: Long = 0,
        var failedRequests: Long = 0,
        var totalResponseTimeMs: Long = 0,
        var totalResults: Long = 0,
        var lastRequestTime: Long = 0,
        var lastSuccessTime: Long = 0,
        var lastFailureTime: Long = 0,
        var lastErrorMessage: String? = null,
        var consecutiveFailures: Int = 0
    ) {
        val successRate: Double
            get() = if (totalRequests > 0) successfulRequests.toDouble() / totalRequests else 0.0
        
        val averageResponseTimeMs: Long
            get() = if (successfulRequests > 0) totalResponseTimeMs / successfulRequests else 0
        
        val averageResultCount: Double
            get() = if (successfulRequests > 0) totalResults.toDouble() / successfulRequests else 0.0
        
        val healthScore: Double
            get() {
                // Weighted score: success rate (60%) + response time (20%) + result count (20%)
                val successScore = successRate * 60
                val timeScore = when {
                    averageResponseTimeMs <= 500 -> 20.0
                    averageResponseTimeMs <= 1000 -> 15.0
                    averageResponseTimeMs <= 2000 -> 10.0
                    averageResponseTimeMs <= 5000 -> 5.0
                    else -> 0.0
                }
                val resultScore = when {
                    averageResultCount >= 50 -> 20.0
                    averageResultCount >= 20 -> 15.0
                    averageResultCount >= 10 -> 10.0
                    averageResultCount >= 5 -> 5.0
                    else -> 2.0
                }
                return successScore + timeScore + resultScore
            }
    }
    
    data class SearchEvent(
        val query: String,
        val providerId: String,
        val timestamp: Long,
        val durationMs: Long,
        val resultCount: Int,
        val success: Boolean,
        val errorMessage: String? = null
    )
    
    data class AnalyticsSummary(
        val totalSearches: Long,
        val totalProviders: Int,
        val topProviders: List<ProviderMetrics>,
        val averageResponseTime: Long,
        val overallSuccessRate: Double,
        val mostUsedProviders: List<Pair<String, Long>>,
        val recentErrors: List<Pair<String, String>>
    )
    
    /**
     * Record a successful search
     */
    fun recordSuccess(
        providerId: String,
        providerName: String,
        query: String,
        responseTimeMs: Long,
        resultCount: Int
    ) {
        val metrics = getMetrics(providerId) ?: ProviderMetrics(providerId, providerName)
        
        metrics.apply {
            totalRequests++
            successfulRequests++
            totalResponseTimeMs += responseTimeMs
            totalResults += resultCount
            lastRequestTime = System.currentTimeMillis()
            lastSuccessTime = System.currentTimeMillis()
            consecutiveFailures = 0
        }
        
        saveMetrics(providerId, metrics)
        recordSearchEvent(SearchEvent(
            query = query,
            providerId = providerId,
            timestamp = System.currentTimeMillis(),
            durationMs = responseTimeMs,
            resultCount = resultCount,
            success = true
        ))
        
        Log.d(TAG, "Recorded success for $providerName: ${resultCount} results in ${responseTimeMs}ms")
    }
    
    /**
     * Record a failed search
     */
    fun recordFailure(
        providerId: String,
        providerName: String,
        query: String,
        errorMessage: String
    ) {
        val metrics = getMetrics(providerId) ?: ProviderMetrics(providerId, providerName)
        
        metrics.apply {
            totalRequests++
            failedRequests++
            lastRequestTime = System.currentTimeMillis()
            lastFailureTime = System.currentTimeMillis()
            lastErrorMessage = errorMessage
            consecutiveFailures++
        }
        
        saveMetrics(providerId, metrics)
        recordSearchEvent(SearchEvent(
            query = query,
            providerId = providerId,
            timestamp = System.currentTimeMillis(),
            durationMs = 0,
            resultCount = 0,
            success = false,
            errorMessage = errorMessage
        ))
        
        Log.d(TAG, "Recorded failure for $providerName: $errorMessage")
    }
    
    /**
     * Get metrics for a specific provider
     */
    fun getMetrics(providerId: String): ProviderMetrics? {
        return try {
            val json = prefs.getString("metrics_$providerId", null) ?: return null
            gson.fromJson(json, ProviderMetrics::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading metrics for $providerId: ${e.message}")
            null
        }
    }
    
    /**
     * Get all provider metrics
     */
    fun getAllMetrics(): List<ProviderMetrics> {
        return prefs.all.keys
            .filter { it.startsWith("metrics_") }
            .mapNotNull { key ->
                try {
                    val json = prefs.getString(key, null) ?: return@mapNotNull null
                    gson.fromJson(json, ProviderMetrics::class.java)
                } catch (e: Exception) {
                    null
                }
            }
    }
    
    /**
     * Get providers ranked by health score
     */
    fun getRankedProviders(): List<ProviderMetrics> {
        return getAllMetrics().sortedByDescending { it.healthScore }
    }
    
    /**
     * Get top N providers by performance
     */
    fun getTopProviders(count: Int = 10): List<ProviderMetrics> {
        return getRankedProviders().take(count)
    }
    
    /**
     * Get providers that should be avoided (too many failures)
     */
    fun getUnreliableProviders(): List<ProviderMetrics> {
        return getAllMetrics().filter { 
            it.consecutiveFailures >= 3 || it.successRate < 0.5 
        }
    }
    
    /**
     * Check if a provider should be skipped
     */
    fun shouldSkipProvider(providerId: String): Boolean {
        val metrics = getMetrics(providerId) ?: return false
        
        // Skip if 5+ consecutive failures
        if (metrics.consecutiveFailures >= 5) {
            // But allow retry after 30 minutes
            val timeSinceLastFailure = System.currentTimeMillis() - metrics.lastFailureTime
            if (timeSinceLastFailure < TimeUnit.MINUTES.toMillis(30)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Get comprehensive analytics summary
     */
    fun getSummary(): AnalyticsSummary {
        val allMetrics = getAllMetrics()
        val events = getRecentEvents(100)
        
        val totalSearches = allMetrics.sumOf { it.totalRequests }
        val totalSuccessful = allMetrics.sumOf { it.successfulRequests }
        val totalResponseTime = allMetrics.sumOf { it.totalResponseTimeMs }
        
        val mostUsed = allMetrics
            .sortedByDescending { it.totalRequests }
            .take(5)
            .map { it.providerName to it.totalRequests }
        
        val recentErrors = events
            .filter { !it.success && it.errorMessage != null }
            .take(5)
            .map { it.providerId to (it.errorMessage ?: "Unknown error") }
        
        return AnalyticsSummary(
            totalSearches = totalSearches,
            totalProviders = allMetrics.size,
            topProviders = getTopProviders(5),
            averageResponseTime = if (totalSuccessful > 0) totalResponseTime / totalSuccessful else 0,
            overallSuccessRate = if (totalSearches > 0) totalSuccessful.toDouble() / totalSearches else 0.0,
            mostUsedProviders = mostUsed,
            recentErrors = recentErrors
        )
    }
    
    /**
     * Export analytics as JSON
     */
    fun exportAsJson(): String {
        val data = mapOf(
            "summary" to getSummary(),
            "providers" to getAllMetrics(),
            "recentEvents" to getRecentEvents(50)
        )
        return gson.toJson(data)
    }
    
    /**
     * Clear all analytics data
     */
    fun clear() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Analytics cleared")
    }
    
    /**
     * Cleanup old data
     */
    fun cleanup() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(MAX_HISTORY_DAYS.toLong())
        
        // Clean old events
        val events = getRecentEvents(1000)
        val recentEvents = events.filter { it.timestamp > cutoff }
        saveEvents(recentEvents)
        
        Log.d(TAG, "Cleaned ${events.size - recentEvents.size} old events")
    }
    
    // Private helpers
    
    private fun saveMetrics(providerId: String, metrics: ProviderMetrics) {
        prefs.edit()
            .putString("metrics_$providerId", gson.toJson(metrics))
            .apply()
    }
    
    private fun recordSearchEvent(event: SearchEvent) {
        val events = getRecentEvents(500).toMutableList()
        events.add(0, event)
        
        // Keep only last 500 events
        if (events.size > 500) {
            events.subList(500, events.size).clear()
        }
        
        saveEvents(events)
    }
    
    private fun getRecentEvents(limit: Int): List<SearchEvent> {
        return try {
            val json = prefs.getString("search_events", null) ?: return emptyList()
            val type = object : TypeToken<List<SearchEvent>>() {}.type
            val events: List<SearchEvent> = gson.fromJson(json, type)
            events.take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading events: ${e.message}")
            emptyList()
        }
    }
    
    private fun saveEvents(events: List<SearchEvent>) {
        prefs.edit()
            .putString("search_events", gson.toJson(events))
            .apply()
    }
}
