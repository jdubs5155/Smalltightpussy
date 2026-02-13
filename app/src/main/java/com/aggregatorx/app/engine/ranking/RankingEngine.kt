package com.aggregatorx.app.engine.ranking

import com.aggregatorx.app.data.model.AggregatedSearchResults
import com.aggregatorx.app.data.model.ProviderSearchResults
import com.aggregatorx.app.data.model.SearchResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Intelligent Result Ranking Engine
 * 
 * Uses multiple factors to rank and score search results:
 * - Text relevance (TF-IDF inspired with fuzzy matching)
 * - Provider reliability score
 * - Content freshness
 * - User engagement signals (seeders, views, ratings)
 * - Quality indicators
 * 
 * Features:
 * - Error providers automatically sorted to bottom
 * - Fuzzy/partial matching for better results
 * - Related content discovery even with partial matches
 * - Enhanced description-based matching
 * - Synonym and related term matching
 * - Smart fallback to related content when few matches
 */
@Singleton
class RankingEngine @Inject constructor() {
    
    companion object {
        // Scoring weights
        private const val WEIGHT_TEXT_RELEVANCE = 0.40f
        private const val WEIGHT_PROVIDER_SCORE = 0.10f
        private const val WEIGHT_FRESHNESS = 0.15f
        private const val WEIGHT_ENGAGEMENT = 0.20f
        private const val WEIGHT_QUALITY = 0.15f
        
        // Text matching bonuses
        private const val EXACT_MATCH_BONUS = 35f
        private const val TITLE_START_BONUS = 25f
        private const val ALL_TERMS_BONUS = 20f
        private const val WORD_ORDER_BONUS = 15f
        private const val PARTIAL_MATCH_BONUS = 10f
        private const val FUZZY_MATCH_BONUS = 5f
        private const val DESCRIPTION_MATCH_BONUS = 8f
        private const val SYNONYM_MATCH_BONUS = 6f
        
        // Minimum score thresholds - lowered to include more results
        private const val MIN_SCORE_FOR_TOP = 0.12f
        private const val MIN_SCORE_FOR_RELATED = 0.05f
        
        // Minimum results to show
        private const val MIN_TOP_RESULTS = 15
        private const val MIN_RELATED_RESULTS = 20
        
        // Common synonyms and related terms
        private val SYNONYMS = mapOf(
            "movie" to listOf("film", "cinema", "feature"),
            "film" to listOf("movie", "cinema", "feature"),
            "video" to listOf("clip", "footage", "recording"),
            "watch" to listOf("view", "stream", "play"),
            "download" to listOf("get", "save", "grab"),
            "hd" to listOf("high definition", "720p", "1080p"),
            "full" to listOf("complete", "entire", "whole"),
            "episode" to listOf("ep", "part", "chapter"),
            "season" to listOf("series", "s0"),
            "free" to listOf("gratis", "no cost"),
            "online" to listOf("streaming", "web"),
            "latest" to listOf("new", "recent", "newest"),
            "best" to listOf("top", "greatest", "finest")
        )
    }
    
    /**
     * Rank and aggregate results from all providers
     * Error providers are automatically placed at the bottom
     * ENHANCED: Provides more related results when main matches are few
     */
    fun rankAndAggregate(
        query: String,
        providerResults: List<ProviderSearchResults>
    ): AggregatedSearchResults {
        val startTime = System.currentTimeMillis()
        
        // Separate successful and failed providers
        val successfulProviders = providerResults.filter { it.success }
        val failedProviders = providerResults.filter { !it.success }

        // Calculate scores for all results from successful providers
        val scoredResults = successfulProviders.flatMap { pr ->
            pr.results.map { result ->
                ScoredResult(
                    result = result,
                    providerScore = calculateProviderScore(pr),
                    score = calculateFinalScore(result, query, pr)
                )
            }
        }

        // Get top results - best matches first
        var topResults = scoredResults
            .filter { it.score >= MIN_SCORE_FOR_TOP }
            .sortedByDescending { it.score }
            .take(25)
            .map { it.result.copy(relevanceScore = it.score) }

        // Find related/similar results (partial matches, fuzzy matches, synonym matches)
        var relatedResults = scoredResults
            .filter { it.score >= MIN_SCORE_FOR_RELATED && it.score < MIN_SCORE_FOR_TOP }
            .sortedByDescending { it.score }
            .distinctBy { normalizeTitle(it.result.title) }
            .take(40)
            .map { it.result.copy(relevanceScore = it.score) }

        // ENHANCED: If few top results, lower threshold and add more
        if (topResults.size < MIN_TOP_RESULTS) {
            val additionalTop = scoredResults
                .filter { it.score >= MIN_SCORE_FOR_RELATED && it.score < MIN_SCORE_FOR_TOP }
                .sortedByDescending { it.score }
                .take(MIN_TOP_RESULTS - topResults.size)
                .map { it.result.copy(relevanceScore = it.score) }
            
            topResults = topResults + additionalTop
            
            // Update related to exclude what's now in top
            val topUrls = topResults.map { it.url }.toSet()
            relatedResults = relatedResults.filter { it.url !in topUrls }
        }
        
        // ENHANCED: If still few results, generate related content from all available
        if (topResults.size + relatedResults.size < MIN_TOP_RESULTS + MIN_RELATED_RESULTS) {
            val existingUrls = (topResults + relatedResults).map { it.url }.toSet()
            val additionalRelated = scoredResults
                .filter { it.result.url !in existingUrls }
                .sortedByDescending { it.score }
                .take(MIN_RELATED_RESULTS)
                .map { it.result.copy(relevanceScore = maxOf(it.score, 0.05f)) }
            
            relatedResults = (relatedResults + additionalRelated).take(MIN_RELATED_RESULTS)
        }
        
        // Add synonym-based matches if still few results
        if (topResults.size < MIN_TOP_RESULTS) {
            val synonymResults = findSynonymMatches(query, scoredResults, topResults, relatedResults)
            topResults = (topResults + synonymResults).distinctBy { it.url }.take(25)
        }

        // Re-rank results within each successful provider
        val rankedSuccessfulProviders = successfulProviders.map { pr ->
            val rankedResults = pr.results
                .map { result ->
                    result.copy(
                        relevanceScore = calculateFinalScore(result, query, pr)
                    )
                }
                .sortedByDescending { it.relevanceScore }

            pr.copy(results = rankedResults)
        }.sortedByDescending { it.results.firstOrNull()?.relevanceScore ?: 0f }
        
        // Failed providers go at the bottom - keep original error info
        val orderedProviderResults = rankedSuccessfulProviders + failedProviders

        return AggregatedSearchResults(
            query = query,
            providerResults = orderedProviderResults,
            totalResults = successfulProviders.sumOf { it.results.size },
            searchTime = System.currentTimeMillis() - startTime,
            successfulProviders = successfulProviders.size,
            failedProviders = failedProviders.size,
            topResults = topResults,
            relatedResults = relatedResults
        )
    }
    
    /**
     * Find results that match synonyms of the query terms
     */
    private fun findSynonymMatches(
        query: String,
        allResults: List<ScoredResult>,
        existingTop: List<SearchResult>,
        existingRelated: List<SearchResult>
    ): List<SearchResult> {
        val existingUrls = (existingTop + existingRelated).map { it.url }.toSet()
        val queryTerms = query.lowercase().split(Regex("\\s+")).filter { it.length > 1 }
        
        // Get synonyms for query terms
        val synonymTerms = queryTerms.flatMap { term ->
            SYNONYMS[term] ?: emptyList()
        }.distinct()
        
        if (synonymTerms.isEmpty()) return emptyList()
        
        // Find results matching synonyms
        return allResults
            .filter { it.result.url !in existingUrls }
            .filter { scored ->
                val titleLower = scored.result.title.lowercase()
                val descLower = scored.result.description?.lowercase() ?: ""
                synonymTerms.any { synonym ->
                    titleLower.contains(synonym) || descLower.contains(synonym)
                }
            }
            .sortedByDescending { it.score }
            .take(10)
            .map { it.result.copy(relevanceScore = it.score + SYNONYM_MATCH_BONUS) }
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
     * Calculate text relevance score using enhanced TF-IDF-like approach with fuzzy matching
     * ENHANCED: Better description matching, synonym matching, URL path analysis
     */
    private fun calculateTextRelevance(title: String, description: String?, query: String): Float {
        val titleLower = title.lowercase()
        val descLower = description?.lowercase() ?: ""
        val queryLower = query.lowercase()
        val queryTerms = queryLower.split(Regex("\\s+")).filter { it.length > 1 }
        
        if (queryTerms.isEmpty()) return 0.1f  // Give small score even with empty query
        
        var score = 0f
        
        // Exact match in title - highest priority
        if (titleLower.contains(queryLower)) {
            score += EXACT_MATCH_BONUS
        }
        
        // Exact match in description - valuable
        if (descLower.contains(queryLower)) {
            score += DESCRIPTION_MATCH_BONUS * 2
        }
        
        // Title starts with query
        if (titleLower.startsWith(queryLower)) {
            score += TITLE_START_BONUS
        }
        
        // Term frequency analysis with fuzzy matching
        var titleMatches = 0
        var descMatches = 0
        var fuzzyMatches = 0
        var synonymMatches = 0
        val matchedTerms = mutableListOf<String>()
        
        queryTerms.forEach { term ->
            // Exact title matching (higher weight)
            val titleOccurrences = countOccurrences(titleLower, term)
            if (titleOccurrences > 0) {
                titleMatches++
                matchedTerms.add(term)
                // TF-IDF inspired: diminishing returns for repeated terms
                score += (1 + ln(titleOccurrences.toDouble())).toFloat() * 6f
                
                // Position bonus - earlier matches are better
                val position = titleLower.indexOf(term)
                score += max(0f, 6f - (position / 15f))
            } else {
                // Try fuzzy matching for typos/variations
                val fuzzyMatch = findFuzzyMatch(titleLower, term)
                if (fuzzyMatch != null) {
                    fuzzyMatches++
                    score += FUZZY_MATCH_BONUS
                }
                
                // ENHANCED: Try synonym matching
                val synonyms = SYNONYMS[term] ?: emptyList()
                for (synonym in synonyms) {
                    if (titleLower.contains(synonym)) {
                        synonymMatches++
                        score += SYNONYM_MATCH_BONUS
                        break
                    }
                }
                
                // Partial match - term contains part of query or vice versa
                if (term.length >= 3 && titleLower.contains(term.dropLast(1))) {
                    score += PARTIAL_MATCH_BONUS * 0.5f
                }
            }
            
            // ENHANCED: Description matching (now with higher weight)
            val descOccurrences = countOccurrences(descLower, term)
            if (descOccurrences > 0) {
                descMatches++
                score += (1 + ln(descOccurrences.toDouble())).toFloat() * 4f  // Increased from 3
            } else if (descLower.isNotEmpty()) {
                // Try fuzzy match in description
                val fuzzyDescMatch = findFuzzyMatch(descLower, term)
                if (fuzzyDescMatch != null) {
                    score += FUZZY_MATCH_BONUS * 0.7f
                }
                
                // Synonym match in description
                val synonyms = SYNONYMS[term] ?: emptyList()
                for (synonym in synonyms) {
                    if (descLower.contains(synonym)) {
                        score += SYNONYM_MATCH_BONUS * 0.5f
                        break
                    }
                }
            }
        }
        
        // All terms matched bonus (including fuzzy and synonym matches)
        val totalMatches = titleMatches + fuzzyMatches * 0.5f + synonymMatches * 0.3f
        if (titleMatches == queryTerms.size) {
            score += ALL_TERMS_BONUS
        } else if (totalMatches >= queryTerms.size * 0.7) {
            // Partial match bonus when most terms found
            score += ALL_TERMS_BONUS * 0.5f
        } else if (titleMatches + descMatches >= queryTerms.size) {
            // Bonus if terms found across title and description
            score += ALL_TERMS_BONUS * 0.4f
        }
        
        // Word order preservation bonus
        if (queryTerms.size > 1 && preservesWordOrder(titleLower, queryTerms)) {
            score += WORD_ORDER_BONUS
        }
        
        // Coverage ratio - how much of query is matched (including description)
        val coverageRatio = (titleMatches + descMatches * 0.6f + fuzzyMatches * 0.4f + synonymMatches * 0.3f) / queryTerms.size
        score *= (0.4f + coverageRatio * 0.6f)
        
        // Give minimum score if ANY match found (for related results)
        if (titleMatches > 0 || descMatches > 0 || fuzzyMatches > 0 || synonymMatches > 0) {
            score = max(score, 5f)
        }
        
        // Length penalty for very long titles (likely spam)
        if (title.length > 150) {
            score *= 0.85f
        }
        
        return score.coerceIn(0f, 100f) / 100f
    }
    
    /**
     * Find fuzzy match using Levenshtein-like distance
     */
    private fun findFuzzyMatch(text: String, term: String): String? {
        if (term.length < 3) return null
        
        // Split text into words
        val words = text.split(Regex("\\W+"))
        
        for (word in words) {
            if (word.length < term.length - 2 || word.length > term.length + 2) continue
            
            // Calculate similarity
            val similarity = calculateSimilarity(word, term)
            if (similarity >= 0.75f) {
                return word
            }
        }
        return null
    }
    
    /**
     * Calculate string similarity (0-1)
     */
    private fun calculateSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1f
        if (s1.isEmpty() || s2.isEmpty()) return 0f
        
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        
        // Quick check - if one starts with other
        if (longer.startsWith(shorter) || longer.endsWith(shorter)) return 0.9f
        
        // Simple character overlap check
        val commonChars = shorter.count { longer.contains(it) }
        return commonChars.toFloat() / longer.length
    }
    
    /**
     * Normalize title for deduplication
     */
    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .take(50)
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
