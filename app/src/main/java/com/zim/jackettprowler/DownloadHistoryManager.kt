package com.zim.jackettprowler

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manager for tracking download history
 */
class DownloadHistoryManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("download_history", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    data class DownloadEntry(
        val title: String,
        val size: Long,
        val indexer: String,
        val timestamp: Long,
        val magnetUrl: String = "",
        val infoHash: String = ""
    ) {
        fun formattedDate(): String {
            val date = Date(timestamp)
            val format = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            return format.format(date)
        }
    }
    
    /**
     * Add a download to history
     */
    fun addDownload(result: TorrentResult) {
        val history = getHistory().toMutableList()
        
        val entry = DownloadEntry(
            title = result.title,
            size = result.sizeBytes,
            indexer = result.indexer ?: "Unknown",
            timestamp = System.currentTimeMillis(),
            magnetUrl = result.magnetUrl,
            infoHash = result.infoHash
        )
        
        // Add to beginning of list
        history.add(0, entry)
        
        // Keep only last 100 downloads
        if (history.size > 100) {
            history.removeAt(history.size - 1)
        }
        
        saveHistory(history)
    }
    
    /**
     * Get download history
     */
    fun getHistory(): List<DownloadEntry> {
        val json = prefs.getString("history", null) ?: return emptyList()
        
        return try {
            val type = object : TypeToken<List<DownloadEntry>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Clear all history
     */
    fun clearHistory() {
        prefs.edit().remove("history").apply()
    }
    
    /**
     * Get total downloads count
     */
    fun getTotalDownloads(): Int {
        return getHistory().size
    }
    
    /**
     * Get downloads from specific indexer
     */
    fun getDownloadsByIndexer(indexer: String): List<DownloadEntry> {
        return getHistory().filter { it.indexer == indexer }
    }
    
    /**
     * Search history
     */
    fun searchHistory(query: String): List<DownloadEntry> {
        val lowerQuery = query.lowercase()
        return getHistory().filter { 
            it.title.lowercase().contains(lowerQuery)
        }
    }
    
    private fun saveHistory(history: List<DownloadEntry>) {
        val json = gson.toJson(history)
        prefs.edit().putString("history", json).apply()
    }
}
