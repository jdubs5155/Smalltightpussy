package com.aggregatorx.app.engine.ai

import com.aggregatorx.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.ln

/**
 * AggravatedX AI Decision Engine
 * 
 * Provides intelligent decision-making for:
 * - Provider ranking and selection
 * - Content relevance scoring
 * - Adaptive scraping strategy selection
 * - Pattern learning and recognition
 * - Failure prediction and prevention
 * - Smart retry logic
 * - Quality detection and preference
 */
@Singleton
class AIDecisionEngine @Inject constructor() {
    
    // Learning data - provider performance history
    private val providerScores = ConcurrentHashMap<String, ProviderAIScore>()
    
    // Pattern learning cache
    private val learnedPatterns = ConcurrentHashMap<String, List<LearnedPattern>>()
    
    // Quality preference weights
    private val qualityWeights = mapOf(
        "4k" to 1.0f,
        "2160p" to 1.0f,
        "1080p" to 0.9f,
        "full hd" to 0.9f,
        "720p" to 0.7f,
        "hd" to 0.7f,
        "480p" to 0.5f,
        "sd" to 0.4f,
        "360p" to 0.3f,
        "240p" to 0.2f
    )
    
    private val _aiState = MutableStateFlow(AIState())
    val aiState: StateFlow<AIState> = _aiState
    
    companion object {
        // Decay factor for historical data (newer data has more weight)
        private const val DECAY_FACTOR = 0.95f
        
        // Minimum confidence threshold for decisions
        private const val MIN_CONFIDENCE = 0.3f
        
        // Learning rate for score updates
        private const val LEARNING_RATE = 0.1f
        
        // Keywords that indicate high quality content
        private val QUALITY_KEYWORDS = setOf(
            "hdr", "dolby", "atmos", "remux", "bluray", "blu-ray",
            "webrip", "web-dl", "hdtv", "proper", "repack"
        )
        
        // Keywords that indicate potentially problematic content
        private val RISK_KEYWORDS = setOf(
            "cam", "ts", "telesync", "hdcam", "workprint", "screener"
        )
    }
    
    /**
     * Rank providers for a search query based on AI analysis
     * Returns providers sorted by predicted success rate
     */
    suspend fun rankProviders(
        providers: List<Provider>,
        query: String
    ): List<Provider> = withContext(Dispatchers.Default) {
        providers.map { provider ->
            val score = calculateProviderScore(provider, query)
            provider to score
        }.sortedByDescending { it.second }
         .map { it.first }
    }
    
    /**
     * Calculate AI score for a provider based on multiple factors
     */
    private fun calculateProviderScore(provider: Provider, query: String): Float {
        val historicalScore = providerScores[provider.id]
        
        // Base score from provider stats
        var score = provider.successRate * 100
        
        // Factor in response time (faster is better)
        val responseTimeScore = when {
            provider.avgResponseTime < 1000 -> 20f
            provider.avgResponseTime < 3000 -> 15f
            provider.avgResponseTime < 5000 -> 10f
            provider.avgResponseTime < 10000 -> 5f
            else -> 0f
        }
        score += responseTimeScore
        
        // Factor in historical AI data
        historicalScore?.let { aiScore ->
            score += aiScore.overallScore * 30
            
            // Bonus for consistent providers
            if (aiScore.consistencyScore > 0.8f) {
                score += 15f
            }
            
            // Penalty for high failure rate
            if (aiScore.failureRate > 0.5f) {
                score -= 20f
            }
        }
        
        // Category relevance (if query hints at category)
        if (matchesCategoryForQuery(provider.category, query)) {
            score += 25f
        }
        
        // Health score factor
        score += provider.healthScore * 10
        
        return score.coerceIn(0f, 200f)
    }
    
    /**
     * Score search results using AI relevance analysis
     */
    suspend fun scoreResults(
        results: List<SearchResult>,
        query: String
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        val queryTerms = tokenizeQuery(query)
        
        results.map { result ->
            val aiScore = calculateResultRelevance(result, queryTerms)
            result.copy(relevanceScore = aiScore)
        }.sortedByDescending { it.relevanceScore }
    }
    
    /**
     * Calculate relevance score for a single result
     */
    private fun calculateResultRelevance(result: SearchResult, queryTerms: List<String>): Float {
        var score = 0f
        
        val titleLower = result.title.lowercase()
        val descLower = result.description?.lowercase() ?: ""
        
        // Term matching with position weighting
        queryTerms.forEachIndexed { index, term ->
            val positionWeight = 1f - (index * 0.1f).coerceAtMost(0.5f)
            
            // Title match is more important
            if (titleLower.contains(term)) {
                score += 30f * positionWeight
                
                // Exact word match bonus
                if (titleLower.split(Regex("\\W+")).contains(term)) {
                    score += 15f * positionWeight
                }
                
                // Title starts with term - big bonus
                if (titleLower.startsWith(term)) {
                    score += 20f
                }
            }
            
            // Description match
            if (descLower.contains(term)) {
                score += 10f * positionWeight
            }
        }
        
        // Quality indicators
        val qualityScore = calculateQualityScore(result)
        score += qualityScore * 20
        
        // Has thumbnail (indicates real content)
        if (!result.thumbnailUrl.isNullOrEmpty()) {
            score += 5f
        }
        
        // Seeders for torrent results (popular = likely relevant)
        result.seeders?.let { seeders ->
            score += when {
                seeders > 1000 -> 15f
                seeders > 100 -> 10f
                seeders > 10 -> 5f
                seeders > 0 -> 2f
                else -> -5f // No seeders is a bad sign
            }
        }
        
        // Rating factor
        result.rating?.let { rating ->
            score += (rating / 10f) * 10
        }
        
        // Penalize risk keywords
        if (RISK_KEYWORDS.any { titleLower.contains(it) }) {
            score -= 15f
        }
        
        return score.coerceIn(0f, 100f)
    }
    
    /**
     * Calculate quality score from result metadata
     */
    fun calculateQualityScore(result: SearchResult): Float {
        val titleLower = result.title.lowercase()
        val qualityStr = result.quality?.lowercase() ?: ""
        
        var maxScore = 0f
        
        // Check explicit quality field
        qualityWeights.forEach { (quality, weight) ->
            if (qualityStr.contains(quality) || titleLower.contains(quality)) {
                maxScore = maxOf(maxScore, weight)
            }
        }
        
        // Check quality keywords
        if (QUALITY_KEYWORDS.any { titleLower.contains(it) }) {
            maxScore += 0.1f
        }
        
        return maxScore.coerceAtMost(1f)
    }
    
    /**
     * Determine best scraping strategy for a site
     */
    fun recommendScrapingStrategy(
        analysis: SiteAnalysis?,
        previousFailures: List<String>
    ): ScrapingStrategy {
        if (analysis == null) {
            return ScrapingStrategy.HTML_PARSING
        }
        
        // Check if JavaScript is required
        if (analysis.requiresJavaScript) {
            return if (previousFailures.contains("DYNAMIC_CONTENT")) {
                ScrapingStrategy.HEADLESS_BROWSER
            } else {
                ScrapingStrategy.DYNAMIC_CONTENT
            }
        }
        
        // Check if site has API
        if (analysis.hasAPI && previousFailures.isEmpty()) {
            return ScrapingStrategy.API_BASED
        }
        
        // Fallback logic based on failures
        return when {
            previousFailures.contains("HTML_PARSING") && 
            previousFailures.contains("DYNAMIC_CONTENT") -> ScrapingStrategy.HEADLESS_BROWSER
            
            previousFailures.contains("HTML_PARSING") -> ScrapingStrategy.DYNAMIC_CONTENT
            
            else -> analysis.scrapingStrategy
        }
    }
    
    /**
     * Predict if a request will fail based on patterns
     */
    fun predictFailure(provider: Provider): FailurePrediction {
        val aiScore = providerScores[provider.id]
        
        if (aiScore == null) {
            return FailurePrediction(
                likelihood = 0.2f, // Default low risk for unknown
                reason = null,
                recommendation = "First time scraping - proceed with caution"
            )
        }
        
        val failureLikelihood = aiScore.failureRate * 
            (1 - aiScore.consistencyScore) * 
            (if (aiScore.lastFailureRecent) 1.5f else 1f)
        
        return when {
            failureLikelihood > 0.7f -> FailurePrediction(
                likelihood = failureLikelihood,
                reason = "High historical failure rate",
                recommendation = "Use headless browser with extended timeout"
            )
            failureLikelihood > 0.4f -> FailurePrediction(
                likelihood = failureLikelihood,
                reason = "Moderate failure risk",
                recommendation = "Try standard scraping with retry"
            )
            else -> FailurePrediction(
                likelihood = failureLikelihood,
                reason = null,
                recommendation = "Proceed normally"
            )
        }
    }
    
    /**
     * Learn from scraping result (success or failure)
     */
    fun recordResult(
        providerId: String,
        success: Boolean,
        responseTime: Long,
        resultCount: Int
    ) {
        val existing = providerScores[providerId] ?: ProviderAIScore()
        
        val newSuccessRate = existing.successRate * DECAY_FACTOR + 
            (if (success) 1f else 0f) * (1 - DECAY_FACTOR)
        
        val newAvgResults = existing.avgResultCount * DECAY_FACTOR + 
            resultCount * (1 - DECAY_FACTOR)
        
        val newAvgTime = existing.avgResponseTime * DECAY_FACTOR + 
            responseTime * (1 - DECAY_FACTOR)
        
        val newConsistency = calculateConsistency(existing, success, resultCount)
        
        providerScores[providerId] = existing.copy(
            successRate = newSuccessRate,
            failureRate = 1 - newSuccessRate,
            avgResultCount = newAvgResults,
            avgResponseTime = newAvgTime,
            consistencyScore = newConsistency,
            totalAttempts = existing.totalAttempts + 1,
            lastAttemptTime = System.currentTimeMillis(),
            lastFailureRecent = !success,
            overallScore = calculateOverallScore(newSuccessRate, newConsistency, newAvgTime)
        )
    }
    
    /**
     * Learn content patterns from successful extractions
     */
    fun learnPattern(
        domain: String,
        patternType: PatternType,
        selector: String,
        confidence: Float
    ) {
        val existing = learnedPatterns[domain]?.toMutableList() ?: mutableListOf()
        
        val existingPattern = existing.find { it.type == patternType && it.selector == selector }
        
        if (existingPattern != null) {
            // Update confidence with weighted average
            val newConfidence = existingPattern.confidence * 0.7f + confidence * 0.3f
            existing.remove(existingPattern)
            existing.add(existingPattern.copy(
                confidence = newConfidence,
                usageCount = existingPattern.usageCount + 1,
                lastUsed = System.currentTimeMillis()
            ))
        } else {
            existing.add(LearnedPattern(
                type = patternType,
                selector = selector,
                confidence = confidence,
                usageCount = 1,
                lastUsed = System.currentTimeMillis()
            ))
        }
        
        // Keep only top patterns
        learnedPatterns[domain] = existing
            .sortedByDescending { it.confidence * ln(it.usageCount.toFloat() + 1) }
            .take(20)
    }
    
    /**
     * Get learned patterns for a domain
     */
    fun getLearnedPatterns(domain: String, type: PatternType): List<LearnedPattern> {
        return learnedPatterns[domain]?.filter { it.type == type } ?: emptyList()
    }
    
    /**
     * Smart retry decision
     */
    fun shouldRetry(
        provider: Provider,
        attemptNumber: Int,
        lastError: String?
    ): RetryDecision {
        val maxRetries = when {
            provider.successRate > 0.8f -> 3
            provider.successRate > 0.5f -> 2
            else -> 1
        }
        
        if (attemptNumber >= maxRetries) {
            return RetryDecision(
                shouldRetry = false,
                reason = "Max retries reached"
            )
        }
        
        // Don't retry certain errors
        val nonRetryableErrors = listOf(
            "403", "404", "blocked", "cloudflare", "captcha"
        )
        
        if (lastError != null && nonRetryableErrors.any { lastError.lowercase().contains(it) }) {
            return RetryDecision(
                shouldRetry = false,
                reason = "Non-retryable error: $lastError",
                alternativeStrategy = ScrapingStrategy.HEADLESS_BROWSER
            )
        }
        
        // Calculate delay based on attempt number
        val delay = (1000L * (1 shl attemptNumber)).coerceAtMost(10000L)
        
        return RetryDecision(
            shouldRetry = true,
            delay = delay,
            reason = "Retry attempt ${attemptNumber + 1}"
        )
    }
    
    /**
     * Select best download quality from available options
     */
    fun selectBestQuality(qualities: List<QualityOption>): QualityOption? {
        return qualities
            .filter { it.isAvailable }
            .maxByOrNull { option ->
                val baseScore = qualityWeights[option.quality.lowercase()] ?: 0.3f
                val sizeScore = when {
                    option.fileSize == null -> 0f
                    option.fileSize > 10_000_000_000 -> 0.9f // >10GB
                    option.fileSize > 5_000_000_000 -> 0.8f  // >5GB
                    option.fileSize > 2_000_000_000 -> 0.7f  // >2GB
                    option.fileSize > 1_000_000_000 -> 0.6f  // >1GB
                    else -> 0.5f
                }
                baseScore * 0.7f + sizeScore * 0.3f
            }
    }
    
    // Helper functions
    
    private fun tokenizeQuery(query: String): List<String> {
        return query.lowercase()
            .split(Regex("\\s+"))
            .filter { it.length > 1 }
            .map { it.trim(',', '.', '!', '?', '"', '\'') }
            .filter { it.isNotEmpty() }
    }
    
    private fun matchesCategoryForQuery(category: ProviderCategory, query: String): Boolean {
        val queryLower = query.lowercase()
        return when (category) {
            ProviderCategory.STREAMING -> 
                listOf("watch", "stream", "movie", "series", "tv", "episode").any { queryLower.contains(it) }
            ProviderCategory.TORRENT -> 
                listOf("download", "torrent", "magnet").any { queryLower.contains(it) }
            ProviderCategory.NEWS -> 
                listOf("news", "article", "latest").any { queryLower.contains(it) }
            ProviderCategory.MEDIA -> 
                listOf("video", "music", "audio", "photo").any { queryLower.contains(it) }
            else -> false
        }
    }
    
    private fun calculateConsistency(
        existing: ProviderAIScore,
        success: Boolean,
        resultCount: Int
    ): Float {
        if (existing.totalAttempts < 3) return 0.5f
        
        val successConsistency = if ((existing.successRate > 0.5f) == success) 0.1f else -0.1f
        val resultConsistency = if (kotlin.math.abs(resultCount - existing.avgResultCount) < 10) 0.1f else -0.05f
        
        return (existing.consistencyScore + successConsistency + resultConsistency).coerceIn(0f, 1f)
    }
    
    private fun calculateOverallScore(
        successRate: Float,
        consistency: Float,
        avgTime: Float
    ): Float {
        val timeScore = when {
            avgTime < 1000 -> 1f
            avgTime < 3000 -> 0.8f
            avgTime < 5000 -> 0.6f
            avgTime < 10000 -> 0.4f
            else -> 0.2f
        }
        
        return (successRate * 0.5f + consistency * 0.3f + timeScore * 0.2f)
    }
}

// Data classes

data class ProviderAIScore(
    val successRate: Float = 0.5f,
    val failureRate: Float = 0.5f,
    val avgResultCount: Float = 0f,
    val avgResponseTime: Float = 3000f,
    val consistencyScore: Float = 0.5f,
    val totalAttempts: Int = 0,
    val lastAttemptTime: Long = 0,
    val lastFailureRecent: Boolean = false,
    val overallScore: Float = 0.5f
)

data class LearnedPattern(
    val type: PatternType,
    val selector: String,
    val confidence: Float,
    val usageCount: Int = 1,
    val lastUsed: Long = System.currentTimeMillis()
)

data class FailurePrediction(
    val likelihood: Float,
    val reason: String?,
    val recommendation: String
)

data class RetryDecision(
    val shouldRetry: Boolean,
    val delay: Long = 0,
    val reason: String,
    val alternativeStrategy: ScrapingStrategy? = null
)

data class QualityOption(
    val quality: String,
    val url: String,
    val fileSize: Long? = null,
    val isAvailable: Boolean = true
)

data class AIState(
    val isProcessing: Boolean = false,
    val lastDecision: String = "",
    val confidence: Float = 0f
)
