package com.zim.jackettprowler

import java.text.SimpleDateFormat
import java.util.*

data class TorrentResult(
    val title: String,
    val link: String,
    val sizeBytes: Long,
    val seeders: Int,
    val indexer: String? = null,
    val guid: String = "",
    val description: String = "",
    val pubDate: String = "",
    val category: String = "",
    val peers: Int = 0,
    val leechers: Int = 0,
    val grabs: Int = 0,
    val magnetUrl: String = "",
    val infoHash: String = "",
    val imdbId: String = ""
) {
    fun sizePretty(): String {
        val kb = sizeBytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.2f KB", kb)
            else -> "$sizeBytes B"
        }
    }
    
    fun getHealthRatio(): Float {
        return if (leechers > 0) {
            seeders.toFloat() / leechers.toFloat()
        } else if (seeders > 0) {
            Float.MAX_VALUE
        } else {
            0f
        }
    }
    
    fun getHealthStatus(): String {
        return when {
            seeders == 0 -> "Dead"
            seeders >= 50 -> "Excellent"
            seeders >= 20 -> "Good"
            seeders >= 5 -> "Fair"
            else -> "Poor"
        }
    }
    
    fun getHealthColor(): Int {
        return when {
            seeders == 0 -> 0xFFFF0000.toInt() // Red
            seeders >= 50 -> 0xFF00FF00.toInt() // Green
            seeders >= 20 -> 0xFF90EE90.toInt() // Light Green
            seeders >= 5 -> 0xFFFFFF00.toInt() // Yellow
            else -> 0xFFFFA500.toInt() // Orange
        }
    }
    
    fun formattedPubDate(): String {
        if (pubDate.isEmpty()) return "Unknown"
        
        return try {
            val inputFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
            val date = inputFormat.parse(pubDate)
            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            pubDate
        }
    }
    
    fun isMagnetLink(): Boolean {
        return link.startsWith("magnet:") || magnetUrl.startsWith("magnet:")
    }
    
    fun getDownloadLink(): String {
        return when {
            magnetUrl.isNotEmpty() -> magnetUrl
            link.isNotEmpty() -> link
            else -> ""
        }
    }
}
