package com.zim.jackettprowler

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service for importing indexer configurations from Jackett and Prowlarr
 */
class IndexerImportService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val prefs: SharedPreferences = context.getSharedPreferences("indexers", Context.MODE_PRIVATE)
    
    data class IndexerConfig(
        val id: String,
        val name: String,
        val torznabUrl: String,
        val apiKey: String,
        val source: String, // "jackett" or "prowlarr"
        val categories: List<String> = emptyList(),
        val enabled: Boolean = true,
        val type: String = "torznab"
    )
    
    /**
     * Import all indexers from Jackett
     */
    suspend fun importFromJackett(
        jackettBaseUrl: String,
        jackettApiKey: String
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val url = "${jackettBaseUrl.trimEnd('/')}/api/v2.0/indexers?apikey=$jackettApiKey"
            val request = Request.Builder().url(url).get().build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ImportResult(false, 0, "HTTP ${response.code}: ${response.message}")
                }
                
                val json = response.body?.string() ?: return@withContext ImportResult(false, 0, "Empty response")
                val indexers = parseJackettIndexers(json, jackettBaseUrl, jackettApiKey)
                
                saveIndexers(indexers)
                ImportResult(true, indexers.size, "Successfully imported ${indexers.size} indexers from Jackett")
            }
        } catch (e: Exception) {
            ImportResult(false, 0, "Error: ${e.message}")
        }
    }
    
    /**
     * Import all indexers from Prowlarr
     */
    suspend fun importFromProwlarr(
        prowlarrBaseUrl: String,
        prowlarrApiKey: String
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val url = "${prowlarrBaseUrl.trimEnd('/')}/api/v1/indexer"
            val request = Request.Builder()
                .url(url)
                .header("X-Api-Key", prowlarrApiKey)
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ImportResult(false, 0, "HTTP ${response.code}: ${response.message}")
                }
                
                val json = response.body?.string() ?: return@withContext ImportResult(false, 0, "Empty response")
                val indexers = parseProwlarrIndexers(json, prowlarrBaseUrl, prowlarrApiKey)
                
                saveIndexers(indexers)
                ImportResult(true, indexers.size, "Successfully imported ${indexers.size} indexers from Prowlarr")
            }
        } catch (e: Exception) {
            ImportResult(false, 0, "Error: ${e.message}")
        }
    }
    
    /**
     * Import from both Jackett and Prowlarr
     */
    suspend fun importFromBoth(
        jackettBaseUrl: String,
        jackettApiKey: String,
        prowlarrBaseUrl: String,
        prowlarrApiKey: String
    ): ImportResult = withContext(Dispatchers.IO) {
        var totalImported = 0
        val errors = mutableListOf<String>()
        
        // Import from Jackett
        val jackettResult = importFromJackett(jackettBaseUrl, jackettApiKey)
        if (jackettResult.success) {
            totalImported += jackettResult.count
        } else {
            errors.add("Jackett: ${jackettResult.message}")
        }
        
        // Import from Prowlarr
        val prowlarrResult = importFromProwlarr(prowlarrBaseUrl, prowlarrApiKey)
        if (prowlarrResult.success) {
            totalImported += prowlarrResult.count
        } else {
            errors.add("Prowlarr: ${prowlarrResult.message}")
        }
        
        val message = if (errors.isEmpty()) {
            "Successfully imported $totalImported indexers"
        } else {
            "Imported $totalImported indexers with errors:\n${errors.joinToString("\n")}"
        }
        
        ImportResult(totalImported > 0, totalImported, message)
    }
    
    private fun parseJackettIndexers(
        json: String,
        baseUrl: String,
        apiKey: String
    ): List<IndexerConfig> {
        val indexers = mutableListOf<IndexerConfig>()
        
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val indexer = jsonArray.getJSONObject(i)
                val id = indexer.getString("id")
                val name = indexer.getString("name")
                val configured = indexer.optBoolean("configured", true)
                
                if (configured) {
                    val torznabUrl = "${baseUrl.trimEnd('/')}/api/v2.0/indexers/$id/results/torznab"
                    
                    indexers.add(
                        IndexerConfig(
                            id = "jackett_$id",
                            name = name,
                            torznabUrl = torznabUrl,
                            apiKey = apiKey,
                            source = "jackett",
                            enabled = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return indexers
    }
    
    private fun parseProwlarrIndexers(
        json: String,
        baseUrl: String,
        apiKey: String
    ): List<IndexerConfig> {
        val indexers = mutableListOf<IndexerConfig>()
        
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val indexer = jsonArray.getJSONObject(i)
                val id = indexer.getInt("id")
                val name = indexer.getString("name")
                val enable = indexer.optBoolean("enable", true)
                
                if (enable) {
                    val torznabUrl = "${baseUrl.trimEnd('/')}/api/v1/indexer/$id/newznab"
                    
                    indexers.add(
                        IndexerConfig(
                            id = "prowlarr_$id",
                            name = name,
                            torznabUrl = torznabUrl,
                            apiKey = apiKey,
                            source = "prowlarr",
                            enabled = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return indexers
    }
    
    /**
     * Save indexers to SharedPreferences
     */
    private fun saveIndexers(indexers: List<IndexerConfig>) {
        val existing = getIndexers().toMutableList()
        
        // Add new indexers (avoid duplicates)
        indexers.forEach { newIndexer ->
            val exists = existing.any { it.id == newIndexer.id }
            if (!exists) {
                existing.add(newIndexer)
            }
        }
        
        val json = gson.toJson(existing)
        prefs.edit().putString("indexer_list", json).apply()
    }
    
    /**
     * Get all saved indexers
     */
    fun getIndexers(): List<IndexerConfig> {
        val json = prefs.getString("indexer_list", null) ?: return emptyList()
        
        return try {
            val type = object : TypeToken<List<IndexerConfig>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get enabled indexers only
     */
    fun getEnabledIndexers(): List<IndexerConfig> {
        return getIndexers().filter { it.enabled }
    }
    
    /**
     * Toggle indexer on/off
     */
    fun toggleIndexer(indexerId: String, enabled: Boolean) {
        val indexers = getIndexers().map { 
            if (it.id == indexerId) it.copy(enabled = enabled) else it
        }
        
        val json = gson.toJson(indexers)
        prefs.edit().putString("indexer_list", json).apply()
    }
    
    /**
     * Remove an indexer
     */
    fun removeIndexer(indexerId: String) {
        val indexers = getIndexers().filter { it.id != indexerId }
        val json = gson.toJson(indexers)
        prefs.edit().putString("indexer_list", json).apply()
    }
    
    /**
     * Clear all indexers
     */
    fun clearAllIndexers() {
        prefs.edit().remove("indexer_list").apply()
    }
    
    data class ImportResult(
        val success: Boolean,
        val count: Int,
        val message: String
    )
}
