package com.zim.jackettprowler.services

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zim.jackettprowler.TorrentResult
import java.util.concurrent.TimeUnit

/**
 * Search Result Cache Manager
 * 
 * Provides intelligent caching for search results:
 * - Memory cache for instant results
 * - Disk cache for persistence
 * - Auto-expiration
 * - LRU eviction
 * - Smart pre-fetching
 */
class SearchResultCache(private val context: Context) {
    
    companion object {
        private const val TAG = "SearchCache"
        private const val PREFS_NAME = "search_cache"
        private const val MAX_MEMORY_ENTRIES = 50
        private const val MAX_DISK_ENTRIES = 200
        private val DEFAULT_TTL_MS = TimeUnit.MINUTES.toMillis(30)
        private val POPULAR_QUERY_TTL_MS = TimeUnit.HOURS.toMillis(2)
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // Memory cache with LRU eviction
    private val memoryCache = object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
            return size > MAX_MEMORY_ENTRIES
        }
    }
    
    data class CacheEntry(
        val query: String,
        val source: String,
        val results: List<TorrentResult>,
        val timestamp: Long,
        val ttl: Long,
        val hitCount: Int = 0
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() > timestamp + ttl
    }
    
    data class CacheStats(
        val memoryEntries: Int,
        val diskEntries: Int,
        val totalHits: Long,
        val totalMisses: Long,
        val hitRate: Double,
        val avgResultCount: Double
    )
    
    /**
     * Get cached results for a query
     */
    fun get(query: String, source: String): List<TorrentResult>? {
        val key = generateKey(query, source)
        
        // Check memory cache first
        memoryCache[key]?.let { entry ->
            if (!entry.isExpired()) {
                Log.d(TAG, "Memory cache HIT for '$query' from $source")
                incrementHitCount()
                return entry.results
            } else {
                memoryCache.remove(key)
            }
        }
        
        // Check disk cache
        val diskEntry = loadFromDisk(key)
        if (diskEntry != null && !diskEntry.isExpired()) {
            Log.d(TAG, "Disk cache HIT for '$query' from $source")
            memoryCache[key] = diskEntry // Promote to memory
            incrementHitCount()
            return diskEntry.results
        }
        
        Log.d(TAG, "Cache MISS for '$query' from $source")
        incrementMissCount()
        return null
    }
    
    /**
     * Store results in cache
     */
    fun put(query: String, source: String, results: List<TorrentResult>) {
        if (results.isEmpty()) return
        
        val key = generateKey(query, source)
        val ttl = if (isPopularQuery(query)) POPULAR_QUERY_TTL_MS else DEFAULT_TTL_MS
        
        val entry = CacheEntry(
            query = query,
            source = source,
            results = results,
            timestamp = System.currentTimeMillis(),
            ttl = ttl
        )
        
        // Store in memory
        memoryCache[key] = entry
        
        // Store on disk
        saveToDisk(key, entry)
        
        Log.d(TAG, "Cached ${results.size} results for '$query' from $source (TTL: ${ttl}ms)")
    }
    
    /**
     * Clear all caches
     */
    fun clear() {
        memoryCache.clear()
        prefs.edit().clear().apply()
        Log.d(TAG, "Cache cleared")
    }
    
    /**
     * Clear expired entries
     */
    fun cleanup(): Int {
        var cleanedCount = 0
        
        // Clean memory cache
        val expiredMemoryKeys = memoryCache.entries
            .filter { it.value.isExpired() }
            .map { it.key }
        expiredMemoryKeys.forEach { 
            memoryCache.remove(it)
            cleanedCount++
        }
        
        // Clean disk cache
        val allDiskKeys = prefs.all.keys.filter { it.startsWith("entry_") }
        allDiskKeys.forEach { key ->
            loadFromDisk(key.removePrefix("entry_"))?.let { entry ->
                if (entry.isExpired()) {
                    prefs.edit().remove(key).apply()
                    cleanedCount++
                }
            }
        }
        
        Log.d(TAG, "Cleaned $cleanedCount expired entries")
        return cleanedCount
    }
    
    /**
     * Get cache statistics
     */
    fun getStats(): CacheStats {
        val hits = prefs.getLong("stats_hits", 0)
        val misses = prefs.getLong("stats_misses", 0)
        val total = hits + misses
        
        val diskEntryCount = prefs.all.keys.count { it.startsWith("entry_") }
        val avgResults = memoryCache.values
            .map { it.results.size }
            .average()
            .takeIf { !it.isNaN() } ?: 0.0
        
        return CacheStats(
            memoryEntries = memoryCache.size,
            diskEntries = diskEntryCount,
            totalHits = hits,
            totalMisses = misses,
            hitRate = if (total > 0) hits.toDouble() / total else 0.0,
            avgResultCount = avgResults
        )
    }
    
    /**
     * Pre-fetch popular/recent queries
     */
    fun getRecentQueries(limit: Int = 10): List<String> {
        return prefs.all
            .filter { it.key.startsWith("entry_") }
            .mapNotNull { loadFromDisk(it.key.removePrefix("entry_"))?.query }
            .distinct()
            .take(limit)
    }
    
    // Private helpers
    
    private fun generateKey(query: String, source: String): String {
        return "${query.lowercase().trim()}_${source.lowercase()}"
    }
    
    private fun isPopularQuery(query: String): Boolean {
        // Common search terms get longer cache time
        val popularTerms = listOf(
            "ubuntu", "linux", "windows", "movie", "series",
            "game", "music", "anime", "software", "book"
        )
        return popularTerms.any { query.contains(it, ignoreCase = true) }
    }
    
    private fun loadFromDisk(key: String): CacheEntry? {
        return try {
            val json = prefs.getString("entry_$key", null) ?: return null
            gson.fromJson(json, CacheEntry::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cache entry: ${e.message}")
            null
        }
    }
    
    private fun saveToDisk(key: String, entry: CacheEntry) {
        try {
            // Check disk cache size
            val currentSize = prefs.all.keys.count { it.startsWith("entry_") }
            if (currentSize >= MAX_DISK_ENTRIES) {
                evictOldestDiskEntry()
            }
            
            val json = gson.toJson(entry)
            prefs.edit().putString("entry_$key", json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cache entry: ${e.message}")
        }
    }
    
    private fun evictOldestDiskEntry() {
        var oldestKey: String? = null
        var oldestTimestamp = Long.MAX_VALUE
        
        prefs.all.filter { it.key.startsWith("entry_") }.forEach { (key, _) ->
            loadFromDisk(key.removePrefix("entry_"))?.let { entry ->
                if (entry.timestamp < oldestTimestamp) {
                    oldestTimestamp = entry.timestamp
                    oldestKey = key
                }
            }
        }
        
        oldestKey?.let { prefs.edit().remove(it).apply() }
    }
    
    private fun incrementHitCount() {
        val current = prefs.getLong("stats_hits", 0)
        prefs.edit().putLong("stats_hits", current + 1).apply()
    }
    
    private fun incrementMissCount() {
        val current = prefs.getLong("stats_misses", 0)
        prefs.edit().putLong("stats_misses", current + 1).apply()
    }
}
