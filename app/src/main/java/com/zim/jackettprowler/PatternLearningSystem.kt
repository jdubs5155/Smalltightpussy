package com.zim.jackettprowler

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Element

/**
 * Machine learning-inspired pattern learner that improves scraping accuracy over time
 * by analyzing successful and failed scraping attempts
 */
class PatternLearningSystem(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("pattern_learning", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val TAG = "PatternLearning"
    }
    
    data class SelectorPattern(
        val selector: String,
        val successCount: Int = 0,
        val failCount: Int = 0,
        val avgConfidence: Double = 0.0,
        val lastUsed: Long = System.currentTimeMillis()
    ) {
        fun getScore(): Double {
            if (successCount + failCount == 0) return 0.0
            val successRate = successCount.toDouble() / (successCount + failCount)
            val recencyFactor = 1.0 / (1.0 + (System.currentTimeMillis() - lastUsed) / (86400000.0 * 30)) // 30 days
            return (successRate * 0.7 + avgConfidence * 0.2 + recencyFactor * 0.1)
        }
    }
    
    data class SitePatterns(
        val siteId: String,
        val titlePatterns: MutableList<SelectorPattern> = mutableListOf(),
        val magnetPatterns: MutableList<SelectorPattern> = mutableListOf(),
        val seedersPatterns: MutableList<SelectorPattern> = mutableListOf(),
        val leechersPatterns: MutableList<SelectorPattern> = mutableListOf(),
        val sizePatterns: MutableList<SelectorPattern> = mutableListOf()
    )
    
    /**
     * Record a successful selector use
     */
    fun recordSuccess(siteId: String, selectorType: String, selector: String, confidence: Double = 1.0) {
        val patterns = getSitePatterns(siteId)
        val patternList = getPatternList(patterns, selectorType)
        
        val existingPattern = patternList.find { it.selector == selector }
        if (existingPattern != null) {
            patternList.remove(existingPattern)
            val newAvg = (existingPattern.avgConfidence * existingPattern.successCount + confidence) / (existingPattern.successCount + 1)
            patternList.add(existingPattern.copy(
                successCount = existingPattern.successCount + 1,
                avgConfidence = newAvg,
                lastUsed = System.currentTimeMillis()
            ))
        } else {
            patternList.add(SelectorPattern(
                selector = selector,
                successCount = 1,
                avgConfidence = confidence
            ))
        }
        
        saveSitePatterns(patterns)
        Log.d(TAG, "Recorded success for $siteId - $selectorType: $selector (confidence: $confidence)")
    }
    
    /**
     * Record a failed selector use
     */
    fun recordFailure(siteId: String, selectorType: String, selector: String) {
        val patterns = getSitePatterns(siteId)
        val patternList = getPatternList(patterns, selectorType)
        
        val existingPattern = patternList.find { it.selector == selector }
        if (existingPattern != null) {
            patternList.remove(existingPattern)
            patternList.add(existingPattern.copy(
                failCount = existingPattern.failCount + 1,
                lastUsed = System.currentTimeMillis()
            ))
        } else {
            patternList.add(SelectorPattern(
                selector = selector,
                failCount = 1
            ))
        }
        
        saveSitePatterns(patterns)
        Log.d(TAG, "Recorded failure for $siteId - $selectorType: $selector")
    }
    
    /**
     * Get recommended selectors for a site based on learning history
     */
    fun getRecommendedSelectors(siteId: String, selectorType: String): List<String> {
        val patterns = getSitePatterns(siteId)
        val patternList = getPatternList(patterns, selectorType)
        
        return patternList
            .sortedByDescending { it.getScore() }
            .take(5)
            .map { it.selector }
    }
    
    /**
     * Suggest improvements for a custom site config based on learned patterns
     */
    suspend fun suggestImprovements(config: CustomSiteConfig): CustomSiteConfig = withContext(Dispatchers.IO) {
        val patterns = getSitePatterns(config.id)
        
        val improvedSelectors = config.selectors.copy(
            title = patterns.titlePatterns.maxByOrNull { it.getScore() }?.selector ?: config.selectors.title,
            magnetUrl = patterns.magnetPatterns.maxByOrNull { it.getScore() }?.selector ?: config.selectors.magnetUrl,
            seeders = patterns.seedersPatterns.maxByOrNull { it.getScore() }?.selector ?: config.selectors.seeders,
            leechers = patterns.leechersPatterns.maxByOrNull { it.getScore() }?.selector ?: config.selectors.leechers,
            size = patterns.sizePatterns.maxByOrNull { it.getScore() }?.selector ?: config.selectors.size
        )
        
        config.copy(selectors = improvedSelectors)
    }
    
    /**
     * Analyze scraping results and automatically improve selectors
     */
    fun analyzeResults(siteId: String, config: CustomSiteConfig, results: List<TorrentResult>) {
        if (results.isEmpty()) {
            // No results might indicate selector issues
            recordFailure(siteId, "title", config.selectors.title)
            return
        }
        
        // Analyze result quality
        val hasValidTitles = results.all { it.title.isNotBlank() && it.title.length > 3 }
        val hasValidSeeders = results.any { it.seeders > 0 }
        val hasValidSize = results.any { it.sizeBytes > 0 }
        
        val confidence = calculateResultConfidence(results)
        
        if (hasValidTitles) {
            recordSuccess(siteId, "title", config.selectors.title, confidence)
        } else {
            recordFailure(siteId, "title", config.selectors.title)
        }
        
        if (hasValidSeeders && config.selectors.seeders != null) {
            recordSuccess(siteId, "seeders", config.selectors.seeders!!, confidence)
        }
        
        if (hasValidSize && config.selectors.size != null) {
            recordSuccess(siteId, "size", config.selectors.size!!, confidence)
        }
        
        Log.d(TAG, "Analyzed $siteId: ${results.size} results, confidence: $confidence")
    }
    
    /**
     * Calculate confidence score based on result quality
     */
    private fun calculateResultConfidence(results: List<TorrentResult>): Double {
        var score = 0.0
        var factors = 0
        
        // Title quality
        val validTitles = results.count { it.title.isNotBlank() && it.title.length > 3 }
        score += validTitles.toDouble() / results.size
        factors++
        
        // Seeders present
        val withSeeders = results.count { it.seeders >= 0 }
        score += withSeeders.toDouble() / results.size
        factors++
        
        // Size present (sizeBytes > 0)
        val withSize = results.count { it.sizeBytes > 0 }
        score += withSize.toDouble() / results.size
        factors++
        
        // Download links present
        val withDownload = results.count { it.magnetUrl.isNotBlank() || it.link.isNotBlank() }
        score += withDownload.toDouble() / results.size
        factors++
        
        return score / factors
    }
    
    /**
     * Get all learned patterns for a site
     */
    private fun getSitePatterns(siteId: String): SitePatterns {
        val json = prefs.getString(siteId, null) ?: return SitePatterns(siteId)
        
        return try {
            gson.fromJson(json, SitePatterns::class.java) ?: SitePatterns(siteId)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading patterns for $siteId", e)
            SitePatterns(siteId)
        }
    }
    
    /**
     * Save patterns for a site
     */
    private fun saveSitePatterns(patterns: SitePatterns) {
        val json = gson.toJson(patterns)
        prefs.edit().putString(patterns.siteId, json).apply()
    }
    
    /**
     * Get the appropriate pattern list for a selector type
     */
    private fun getPatternList(patterns: SitePatterns, selectorType: String): MutableList<SelectorPattern> {
        return when (selectorType.lowercase()) {
            "title" -> patterns.titlePatterns
            "magnet" -> patterns.magnetPatterns
            "seeders", "seeds" -> patterns.seedersPatterns
            "leechers", "leeches" -> patterns.leechersPatterns
            "size" -> patterns.sizePatterns
            else -> mutableListOf()
        }
    }
    
    /**
     * Get learning statistics for a site
     */
    fun getSiteStats(siteId: String): String {
        val patterns = getSitePatterns(siteId)
        
        return buildString {
            appendLine("Learning Statistics for $siteId:")
            appendLine()
            appendLine("Title Patterns: ${patterns.titlePatterns.size}")
            patterns.titlePatterns.sortedByDescending { it.getScore() }.take(3).forEach {
                appendLine("  • ${it.selector} (Score: %.2f)".format(it.getScore()))
            }
            appendLine()
            appendLine("Magnet Patterns: ${patterns.magnetPatterns.size}")
            appendLine("Seeders Patterns: ${patterns.seedersPatterns.size}")
            appendLine("Leechers Patterns: ${patterns.leechersPatterns.size}")
            appendLine("Size Patterns: ${patterns.sizePatterns.size}")
        }
    }
    
    /**
     * Clear all learned patterns
     */
    fun clearAllPatterns() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Cleared all learned patterns")
    }
    
    /**
     * Export learned patterns as JSON
     */
    fun exportPatterns(): String {
        val allPatterns = mutableMapOf<String, SitePatterns>()
        prefs.all.forEach { (key, value) ->
            if (value is String) {
                try {
                    allPatterns[key] = gson.fromJson(value, SitePatterns::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing pattern for $key", e)
                }
            }
        }
        return gson.toJson(allPatterns)
    }
    
    /**
     * Import learned patterns from JSON
     */
    fun importPatterns(json: String) {
        try {
            val type = object : TypeToken<Map<String, SitePatterns>>() {}.type
            val patterns: Map<String, SitePatterns> = gson.fromJson(json, type)
            
            patterns.forEach { (siteId, sitePatterns) ->
                saveSitePatterns(sitePatterns)
            }
            
            Log.d(TAG, "Imported patterns for ${patterns.size} sites")
        } catch (e: Exception) {
            Log.e(TAG, "Error importing patterns", e)
            throw e
        }
    }
}
