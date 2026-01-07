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
 * Imports and manages indexers from Jackett and Prowlarr
 */
class IndexerImporter(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val prefs: SharedPreferences = context.getSharedPreferences("imported_indexers", Context.MODE_PRIVATE)
    
    data class ImportedIndexer(
        val id: String,
        val name: String,
        val torznabUrl: String,
        val apiKey: String,
        val source: String, // "jackett" or "prowlarr"
        val description: String = "",
        val language: String = "en-US",
        val isEnabled: Boolean = true,
        val capabilities: List<String> = emptyList()
    )
    
    data class ImportResult(
        val success: Boolean,
        val importedCount: Int,
        val totalCount: Int,
        val errors: List<String> = emptyList(),
        val indexers: List<ImportedIndexer> = emptyList()
    )
    
    /**
     * Import all indexers from Jackett
     */
    suspend fun importFromJackett(baseUrl: String, apiKey: String): ImportResult = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/v2.0/indexers?configured=true&apikey=$apiKey"
            val request = Request.Builder().url(url).get().build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ImportResult(
                        success = false,
                        importedCount = 0,
                        totalCount = 0,
                        errors = listOf("HTTP ${response.code}: ${response.message}")
                    )
                }
                
                val jsonBody = response.body?.string() ?: ""
                val indexers = parseJackettIndexers(jsonBody, baseUrl, apiKey)
                saveIndexers(indexers, "jackett")
                
                ImportResult(
                    success = true,
                    importedCount = indexers.size,
                    totalCount = indexers.size,
                    indexers = indexers
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ImportResult(
                success = false,
                importedCount = 0,
                totalCount = 0,
                errors = listOf(e.message ?: "Unknown error")
            )
        }
    }
    
    /**
     * Import all indexers from Prowlarr
     */
    suspend fun importFromProwlarr(baseUrl: String, apiKey: String): ImportResult = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl.trimEnd('/')}/api/v1/indexer"
            val request = Request.Builder()
                .url(url)
                .header("X-Api-Key", apiKey)
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ImportResult(
                        success = false,
                        importedCount = 0,
                        totalCount = 0,
                        errors = listOf("HTTP ${response.code}: ${response.message}")
                    )
                }
                
                val jsonBody = response.body?.string() ?: ""
                val indexers = parseProwlarrIndexers(jsonBody, baseUrl, apiKey)
                saveIndexers(indexers, "prowlarr")
                
                ImportResult(
                    success = true,
                    importedCount = indexers.size,
                    totalCount = indexers.size,
                    indexers = indexers
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ImportResult(
                success = false,
                importedCount = 0,
                totalCount = 0,
                errors = listOf(e.message ?: "Unknown error")
            )
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
        val jackettResult = importFromJackett(jackettBaseUrl, jackettApiKey)
        val prowlarrResult = importFromProwlarr(prowlarrBaseUrl, prowlarrApiKey)
        
        val allIndexers = jackettResult.indexers + prowlarrResult.indexers
        val allErrors = jackettResult.errors + prowlarrResult.errors
        
        ImportResult(
            success = jackettResult.success || prowlarrResult.success,
            importedCount = jackettResult.importedCount + prowlarrResult.importedCount,
            totalCount = jackettResult.totalCount + prowlarrResult.totalCount,
            errors = allErrors,
            indexers = allIndexers
        )
    }
    
    private fun parseJackettIndexers(json: String, baseUrl: String, apiKey: String): List<ImportedIndexer> {
        val indexers = mutableListOf<ImportedIndexer>()
        
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                
                val id = obj.optString("id", "")
                val name = obj.optString("name", id)
                val description = obj.optString("description", "")
                val language = obj.optString("language", "en-US")
                val configured = obj.optBoolean("configured", false)
                
                if (configured && id.isNotEmpty()) {
                    val torznabUrl = "${baseUrl.trimEnd('/')}/api/v2.0/indexers/$id/results/torznab"
                    
                    val caps = mutableListOf<String>()
                    val capsObj = obj.optJSONObject("caps")
                    if (capsObj != null) {
                        val searching = capsObj.optJSONObject("searching")
                        if (searching != null) {
                            if (searching.optJSONObject("search")?.optString("available") == "yes") caps.add("search")
                            if (searching.optJSONObject("tv-search")?.optString("available") == "yes") caps.add("tv-search")
                            if (searching.optJSONObject("movie-search")?.optString("available") == "yes") caps.add("movie-search")
                        }
                    }
                    
                    indexers.add(
                        ImportedIndexer(
                            id = "jackett_$id",
                            name = name,
                            torznabUrl = torznabUrl,
                            apiKey = apiKey,
                            source = "jackett",
                            description = description,
                            language = language,
                            capabilities = caps
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return indexers
    }
    
    private fun parseProwlarrIndexers(json: String, baseUrl: String, apiKey: String): List<ImportedIndexer> {
        val indexers = mutableListOf<ImportedIndexer>()
        
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                
                val id = obj.optInt("id", -1)
                val name = obj.optString("name", "")
                val enable = obj.optBoolean("enable", false)
                val protocol = obj.optString("protocol", "")
                
                if (enable && id > 0 && protocol.equals("torrent", ignoreCase = true)) {
                    val torznabUrl = "${baseUrl.trimEnd('/')}/api/v1/indexer/$id/newznab"
                    
                    val caps = mutableListOf<String>()
                    val capsArray = obj.optJSONArray("capabilities")
                    if (capsArray != null) {
                        for (j in 0 until capsArray.length()) {
                            val cap = capsArray.getJSONObject(j)
                            val capName = cap.optString("id", "")
                            if (capName.isNotEmpty()) {
                                caps.add(capName)
                            }
                        }
                    }
                    
                    indexers.add(
                        ImportedIndexer(
                            id = "prowlarr_$id",
                            name = name,
                            torznabUrl = torznabUrl,
                            apiKey = apiKey,
                            source = "prowlarr",
                            capabilities = caps
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return indexers
    }
    
    private fun saveIndexers(indexers: List<ImportedIndexer>, source: String) {
        val existing = getImportedIndexers().filter { it.source != source }.toMutableList()
        existing.addAll(indexers)
        
        val json = gson.toJson(existing)
        prefs.edit().putString("indexers", json).apply()
    }
    
    /**
     * Get all imported indexers
     */
    fun getImportedIndexers(): List<ImportedIndexer> {
        val json = prefs.getString("indexers", null) ?: return emptyList()
        
        return try {
            val type = object : TypeToken<List<ImportedIndexer>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Get enabled indexers only
     */
    fun getEnabledIndexers(): List<ImportedIndexer> {
        return getImportedIndexers().filter { it.isEnabled }
    }
    
    /**
     * Toggle indexer enabled state
     */
    fun toggleIndexer(indexerId: String, enabled: Boolean) {
        val indexers = getImportedIndexers().map {
            if (it.id == indexerId) it.copy(isEnabled = enabled) else it
        }
        
        val json = gson.toJson(indexers)
        prefs.edit().putString("indexers", json).apply()
    }
    
    /**
     * Update indexer enabled state (alias for toggleIndexer for consistency)
     */
    fun updateIndexerState(indexerId: String, enabled: Boolean) {
        toggleIndexer(indexerId, enabled)
    }
    
    /**
     * Remove an imported indexer
     */
    fun removeIndexer(indexerId: String) {
        val indexers = getImportedIndexers().filter { it.id != indexerId }
        val json = gson.toJson(indexers)
        prefs.edit().putString("indexers", json).apply()
    }
    
    /**
     * Clear all imported indexers
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
    
    /**
     * Search across all enabled imported indexers
     */
    suspend fun searchAcrossImported(query: String, limit: Int = 100): List<TorrentResult> = withContext(Dispatchers.IO) {
        val results = mutableSetOf<TorrentResult>()
        val enabledIndexers = getEnabledIndexers()
        
        for (indexer in enabledIndexers) {
            try {
                val service = TorznabService(indexer.torznabUrl, indexer.apiKey, client)
                val indexerResults = service.search(query, TorznabService.SearchType.SEARCH, limit = limit)
                results.addAll(indexerResults.map { it.copy(indexer = indexer.name) })
            } catch (e: Exception) {
                e.printStackTrace()
                // Continue with other indexers
            }
        }
        
        results.toList().sortedByDescending { it.seeders }
    }
}
