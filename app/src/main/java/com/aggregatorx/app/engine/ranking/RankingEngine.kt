package com.aggregatorx.app.engine.ranking

import com.aggregatorx.app.data.model.AggregatedSearchResults
import com.aggregatorx.app.data.model.ProviderSearchResults
import com.aggregatorx.app.data.model.SearchResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln
import kotlin.math.max

/**
 * Intelligent Result Ranking Engine
 * 
 * Uses multiple factors to rank and score search results:
 * - Text relevance (TF-IDF inspired)
 * - Provider reliability score
 * - Content freshness
 * - User engagement signals (seeders, views, ratings)
 * - Quality indicators
 */
@Singleton
class RankingEngine @Inject constructor() {
    
    companion object {
        // Scoring weights
        private const val WEIGHT_TEXT_RELEVANCE = 0.35f
        private const val WEIGHT_PROVIDER_SCORE = 0.15f
        private const val WEIGHT_FRESHNESS = 0.15f
        private const val WEIGHT_ENGAGEMENT = 0.20f
        private const val WEIGHT_QUALITY = 0.15f
        
        // Text matching bonuses
        private const val EXACT_MATCH_BONUS = 30f
        private const val TITLE_START_BONUS = 20f
        private const val ALL_TERMS_BONUS = 15f
        private const val WORD_ORDER_BONUS = 10f
    }
    
    /**
     * Rank and aggregate results from all providers
     */
    fun rankAndAggregate(
        query: String,
        providerResults: List<ProviderSearchResults>
    ): AggregatedSearchResults {
        val startTime = System.currentTimeMillis()

        // Calculate scores for all results
        val scoredResults = providerResults.flatMap { pr ->
            pr.results.map { result ->
                ScoredResult(
                    result = result,
                    providerScore = calculateProviderScore(pr),
                    score = calculateFinalScore(result, query, pr)
                )
            }
        }

        // Get top results across all providers
        val topResults = scoredResults
            .sortedByDescending { it.score }
            .take(20)
            .map { it.result.copy(relevanceScore = it.score) }

        // Find related/similar results (by title/keywords)
        val relatedResults = scoredResults
            .filter { it.score > 0.2f }
            .sortedByDescending { it.score }
            .distinctBy { it.result.title.lowercase() }
            .take(20)
            .map { it.result.copy(relevanceScore = it.score) }

        // Re-rank results within each provider
        val rankedProviderResults = providerResults.map { pr ->
            val rankedResults = pr.results
                .map { result ->
                    result.copy(
                        relevanceScore = calculateFinalScore(result, query, pr)
                    )
                }
                .sortedByDescending { it.relevanceScore }

            pr.copy(results = rankedResults)
        }

        return AggregatedSearchResults(
            query = query,
            providerResults = rankedProviderResults,
            totalResults = providerResults.sumOf { it.results.size },
            searchTime = System.currentTimeMillis() - startTime,
            successfulProviders = providerResults.count { it.success },
            failedProviders = providerResults.count { !it.success },
            topResults = topResults,
            relatedResults = relatedResults
        )
    }
    
    /**
     * Calculate final score combining all factors
     */
    private fun calculateFinalScore(
        result: SearchResult,
        query: String,
        providerResults: ProviderSearchResults
    ): Float {
        val textScore = calculateTextRelevance(result.title, result.description, query)
        val providerScore = calculateProviderScore(providerResults)
        val freshnessScore = calculateFreshnessScore(result.date)
        val engagementScore = calculateEngagementScore(result)
        val qualityScore = calculateQualityScore(result)
        
        return (textScore * WEIGHT_TEXT_RELEVANCE +
                providerScore * WEIGHT_PROVIDER_SCORE +
                freshnessScore * WEIGHT_FRESHNESS +
                engagementScore * WEIGHT_ENGAGEMENT +
                qualityScore * WEIGHT_QUALITY) * 100f
    }
    
    /**
     * Calculate text relevance score using enhanced TF-IDF-like approach
     */
    private fun calculateTextRelevance(title: String, description: String?, query: String): Float {
        val titleLower = title.lowercase()
        val descLower = description?.lowercase() ?: ""
        val queryLower = query.lowercase()
        val queryTerms = queryLower.split(Regex("\\s+")).filter { it.length > 1 }
        
        if (queryTerms.isEmpty()) return 0f
        
        var score = 0f
        
        // Exact match in title
        if (titleLower.contains(queryLower)) {
            score += EXACT_MATCH_BONUS
        }
        
        // Title starts with query
        if (titleLower.startsWith(queryLower)) {
            score += TITLE_START_BONUS
        }
        
        // Term frequency analysis
        var titleMatches = 0
        var descMatches = 0
        val matchedTerms = mutableListOf<String>()
        
        queryTerms.forEach { term ->
            // Title matching (higher weight)
            val titleOccurrences = countOccurrences(titleLower, term)
            if (titleOccurrences > 0) {
                titleMatches++
                matchedTerms.add(term)
                // TF-IDF inspired: diminishing returns for repeated terms
                score += (1 + ln(titleOccurrences.toDouble())).toFloat() * 5f
                
                // Position bonus - earlier matches are better
                val position = titleLower.indexOf(term)
                score += max(0f, 5f - (position / 20f))
            }
            
            // Description matching (lower weight)
            val descOccurrences = countOccurrences(descLower, term)
            if (descOccurrences > 0) {
                descMatches++
                score += (1 + ln(descOccurrences.toDouble())).toFloat() * 2f
            }
        }
        
        // All terms matched bonus
        if (titleMatches == queryTerms.size) {
            score += ALL_TERMS_BONUS
        }
        
        // Word order preservation bonus
        if (queryTerms.size > 1 && preservesWordOrder(titleLower, queryTerms)) {
            score += WORD_ORDER_BONUS
        }
        
        // Coverage ratio
        val coverageRatio = titleMatches.toFloat() / queryTerms.size
        score *= (0.5f + coverageRatio * 0.5f)
        
        // Length penalty for very long titles
        if (title.length > 150) {
            score *= 0.9f
        }
        
        return score.coerceIn(0f, 100f) / 100f
    }
    
    /**
     * Calculate provider reliability score
     */
    private fun calculateProviderScore(providerResults: ProviderSearchResults): Float {
        val provider = providerResults.provider
        
        var score = 0.5f // Base score
        
        // Success rate factor
        if (provider.totalSearches > 0) {
            val successRate = 1f - (provider.failedSearches.toFloat() / provider.totalSearches)
            score += successRate * 0.3f
        }
        
        // Response time factor
        if (providerResults.searchTime < 1000) {
            score += 0.1f
        } else if (providerResults.searchTime < 3000) {
            score += 0.05f
        }
        
        // Health score from provider
        score += provider.healthScore * 0.1f
        
        return score.coerceIn(0f, 1f)
    }
    
    /**
     * Calculate freshness score based on date
     */
    private fun calculateFreshnessScore(date: String?): Float {
        if (date.isNullOrEmpty()) return 0.3f // Default for unknown dates
        
        // Try to parse relative dates
        val dateLower = date.lowercase()
        
        return when {
            dateLower.contains("today") || dateLower.contains("hour") -> 1.0f
            dateLower.contains("yesterday") -> 0.9f
            dateLower.contains("day") && extractNumber(dateLower) ?: 99 <= 7 -> 0.8f
            dateLower.contains("week") && extractNumber(dateLower) ?: 99 <= 2 -> 0.7f
            dateLower.contains("month") && extractNumber(dateLower) ?: 99 <= 1 -> 0.6f
            dateLower.contains("month") && extractNumber(dateLower) ?: 99 <= 3 -> 0.5f
            dateLower.contains("year") && extractNumber(dateLower) ?: 99 <= 1 -> 0.3f
            else -> 0.2f
        }
    }
    
    /**
     * Calculate engagement score based on user signals
     */
    private fun calculateEngagementScore(result: SearchResult): Float {
        var score = 0f
        var factors = 0
        
        // Seeders (for torrents)
        result.seeders?.let { seeders ->
            factors++
            score += when {
                seeders >= 1000 -> 1.0f
                seeders >= 100 -> 0.8f
                seeders >= 10 -> 0.5f
                seeders > 0 -> 0.3f
                else -> 0.1f
            }
        }
        
        // Views
        result.views?.let { views ->
            factors++
            score += when {
                views >= 1000000 -> 1.0f
                views >= 100000 -> 0.8f
                views >= 10000 -> 0.6f
                views >= 1000 -> 0.4f
                else -> 0.2f
            }
        }
        
        // Rating
        result.rating?.let { rating ->
            factors++
            score += (rating / 10f).coerceIn(0f, 1f)
        }
        
        return if (factors > 0) score / factors else 0.5f
    }
    
    /**
     * Calculate quality score based on content indicators
     */
    private fun calculateQualityScore(result: SearchResult): Float {
        var score = 0.5f
        
        // Quality indicator in title
        val qualityIndicators = mapOf(
            "4k" to 1.0f, "2160p" to 1.0f,
            "1080p" to 0.9f, "full hd" to 0.9f,
            "720p" to 0.7f, "hd" to 0.7f,
            "bluray" to 0.85f, "blu-ray" to 0.85f,
            "remux" to 0.95f, "web-dl" to 0.8f,
            "webrip" to 0.75f, "hdtv" to 0.7f,
            "cam" to 0.2f, "ts" to 0.25f,
            "screener" to 0.3f
        )
        
        val titleLower = result.title.lowercase()
        qualityIndicators.forEach { (indicator, qualityScore) ->
            if (titleLower.contains(indicator)) {
                score = max(score, qualityScore)
            }
        }
        
        // Explicit quality field
        result.quality?.let { quality ->
            val qualityLower = quality.lowercase()
            qualityIndicators.forEach { (indicator, qualityScore) ->
                if (qualityLower.contains(indicator)) {
                    score = max(score, qualityScore)
                }
            }
        }
        
        // Has thumbnail (indicates better content)
        if (!result.thumbnailUrl.isNullOrEmpty()) {
            score += 0.05f
        }
        
        // Has description
        if (!result.description.isNullOrEmpty()) {
            score += 0.05f
        }
        
        return score.coerceIn(0f, 1f)
    }
    
    // Helper methods
    private fun countOccurrences(text: String, term: String): Int {
        var count = 0
        var index = 0
        while (true) {
            index = text.indexOf(term, index)
            if (index == -1) break
            count++
            index += term.length
        }
        return count
    }
    
    private fun preservesWordOrder(text: String, terms: List<String>): Boolean {
        var lastIndex = -1
        for (term in terms) {
            val index = text.indexOf(term, lastIndex + 1)
            if (index == -1) return false
            lastIndex = index
        }
        return true
    }
    
    private fun extractNumber(text: String): Int? {
        return Regex("\\d+").find(text)?.value?.toIntOrNull()
    }
    
    data class ScoredResult(
        val result: SearchResult,
        val providerScore: Float,
        val score: Float
    )
}
