package com.aggregatorx.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aggregatorx.app.data.model.*
import com.aggregatorx.app.data.repository.AggregatorRepository
import com.aggregatorx.app.engine.media.DownloadManager
import com.aggregatorx.app.engine.media.DownloadState
import com.aggregatorx.app.engine.media.VideoExtractorEngine
import com.aggregatorx.app.engine.media.VideoExtractionResult
import com.aggregatorx.app.engine.media.VideoStreamResolver
import com.aggregatorx.app.engine.util.EngineUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lightweight result returned by video extraction so the UI can forward
 * both the playable URL **and** the HTTP headers that the CDN expects.
 */
data class VideoPreviewResult(
    val videoUrl: String,
    val headers: Map<String, String> = emptyMap()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: AggregatorRepository,
    private val videoExtractor: VideoExtractorEngine,
    private val videoStreamResolver: VideoStreamResolver,
    private val downloadManager: DownloadManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    private val _providerResults = MutableStateFlow<List<ProviderSearchResults>>(emptyList())
    val providerResults: StateFlow<List<ProviderSearchResults>> = _providerResults.asStateFlow()
    
    private val _videoExtractionState = MutableStateFlow<VideoExtractionState>(VideoExtractionState.Idle)
    val videoExtractionState: StateFlow<VideoExtractionState> = _videoExtractionState.asStateFlow()
    
    val downloads: StateFlow<Map<String, DownloadState>> = downloadManager.downloads

    // ── Liked-result state ──────────────────────────────────────────────
    private val _likedUrls = MutableStateFlow<Set<String>>(emptySet())
    val likedUrls: StateFlow<Set<String>> = _likedUrls.asStateFlow()
    
    // Video URL cache for faster repeated preview loads (caches full result with headers)
    private val videoPreviewCache = java.util.concurrent.ConcurrentHashMap<String, VideoPreviewResult>()
    
    // Current search job - cancel when new search starts
    private var currentSearchJob: kotlinx.coroutines.Job? = null
    
    init {
        // Load recent searches
        viewModelScope.launch {
            repository.getRecentSearches().collect { searches ->
                _uiState.update { it.copy(recentSearches = searches) }
            }
        }
        // Pre-load liked URLs so the UI can render heart icons immediately
        viewModelScope.launch {
            _likedUrls.value = repository.getAllLikedUrls()
        }
    }

    /** Toggle like on a search result. Updates local set immediately for snappy UI. */
    fun toggleLike(result: SearchResult) {
        viewModelScope.launch {
            val nowLiked = repository.toggleLike(result)
            _likedUrls.update { urls ->
                if (nowLiked) urls + result.url else urls - result.url
            }
        }
    }
    
    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }
    
    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        
        // Cancel any previous search to prevent stale results
        currentSearchJob?.cancel()
        
        currentSearchJob = viewModelScope.launch {
            // ALWAYS clear cache for each search to ensure fresh results
            // This prevents stale results from being shown for different queries
            repository.clearSearchCache()
            videoPreviewCache.clear()
            
            // Reset UI state immediately for responsive feedback
            _uiState.update { 
                it.copy(
                    isSearching = true, 
                    searchCompleted = false,
                    currentSearchQuery = query,
                    // Reset totals and aggregated results for new search
                    totalResults = 0,
                    successfulProviders = 0,
                    failedProviders = 0,
                    aggregatedResults = null,
                    error = null
                ) 
            }
            // Clear provider results immediately
            _providerResults.value = emptyList()
            
            val results = mutableListOf<ProviderSearchResults>()
            
            repository.searchAllProviders(query)
                .catch { e ->
                    // Only set error if we have NO results at all — otherwise partial results are fine
                    if (results.isEmpty()) {
                        _uiState.update { 
                            it.copy(error = e.message ?: "Search failed") 
                        }
                    }
                }
                .collect { providerResult ->
                    results.add(providerResult)
                    _providerResults.value = results.toList()
                    
                    // Update aggregated results — wrap in try-catch so aggregation
                    // failure never kills the collection loop
                    try {
                        val aggregated = repository.aggregateSearchResults(query, results)
                        _uiState.update { 
                            it.copy(
                                aggregatedResults = aggregated,
                                totalResults = aggregated.totalResults,
                                successfulProviders = aggregated.successfulProviders,
                                failedProviders = aggregated.failedProviders,
                                error = null // clear any previous error since we got results
                            ) 
                        }
                    } catch (e: Exception) {
                        // Aggregation failed — still show raw provider results
                        _uiState.update {
                            it.copy(
                                totalResults = results.sumOf { r -> r.results.size },
                                successfulProviders = results.count { r -> r.success },
                                failedProviders = results.count { r -> !r.success }
                            )
                        }
                    }
                }
            
            _uiState.update { it.copy(isSearching = false, searchCompleted = true) }
        }
    }
    
    fun clearSearch() {
        _uiState.update { 
            it.copy(
                query = "",
                aggregatedResults = null,
                searchCompleted = false,
                error = null
            ) 
        }
        _providerResults.value = emptyList()
        // Clear video URL cache when search is cleared
        videoPreviewCache.clear()
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
        }
    }
    
    fun searchFromHistory(query: String) {
        _uiState.update { it.copy(query = query) }
        search()
    }
    
    /**
     * Extract video URL from a page URL using the FULL resolution chain:
     *   1) Cache hit
     *   2) VideoExtractorEngine fast (JSoup, no browser)
     *   3) VideoExtractorEngine full (known host extractors + iframe + headless)
     *   4) VideoStreamResolver (direct → proxy → headless → site-specific extractors)
     *   5) Direct URL probe: if pageUrl itself looks playable, try it directly
     *   6) Fallback: return the page URL itself for embeddable sites
     *   7) Last resort: pass the raw URL to the player (never returns null)
     *
     * Returns [VideoPreviewResult] with both the playable URL and the HTTP
     * headers (Referer / Origin / UA) that the CDN usually requires.
     * This method NEVER returns null — it always falls back to the raw URL
     * so the fullscreen player always opens and the user can Retry or
     * Open-in-Browser from the player's error UI.
     */
    suspend fun extractVideoForPreview(pageUrl: String): VideoPreviewResult {
        // 1. Cache hit
        videoPreviewCache[pageUrl]?.let { return it }

        return try {
            // 2. Fast extraction (JSoup only – no headless browser)
            val fastUrl = videoExtractor.extractVideoUrlForPreview(pageUrl)
            if (!fastUrl.isNullOrEmpty()) {
                val result = VideoPreviewResult(
                    videoUrl = fastUrl,
                    headers = buildPlaybackHeaders(pageUrl)
                )
                videoPreviewCache[pageUrl] = result
                return result
            }

            // 3. Full extraction (known host extractors + standard HTML + headless)
            val fullExtraction = videoExtractor.extractVideoUrl(pageUrl)
            if (fullExtraction.success && !fullExtraction.videoUrl.isNullOrEmpty()) {
                val result = VideoPreviewResult(
                    videoUrl = fullExtraction.videoUrl,
                    headers = buildPlaybackHeaders(pageUrl)
                )
                videoPreviewCache[pageUrl] = result
                return result
            }

            // 4. Full resolution chain (proxy → headless → site-specific)
            val resolved = videoStreamResolver.resolveVideoStream(pageUrl)
            if (resolved.success && !resolved.streamUrl.isNullOrEmpty()) {
                val result = VideoPreviewResult(
                    videoUrl = resolved.streamUrl,
                    headers = resolved.headers ?: buildPlaybackHeaders(pageUrl)
                )
                videoPreviewCache[pageUrl] = result
                return result
            }

            // 5. Direct URL probe – if the page URL itself looks like a video, try it
            val lowerUrl = pageUrl.lowercase()
            val videoExtensions = listOf(".mp4", ".m3u8", ".webm", ".mpd", ".mkv", ".mov", ".ts")
            if (videoExtensions.any { lowerUrl.contains(it) }) {
                val result = VideoPreviewResult(
                    videoUrl = pageUrl,
                    headers = buildPlaybackHeaders(pageUrl)
                )
                videoPreviewCache[pageUrl] = result
                return result
            }

            // 6. Embeddable sites – just hand the page URL to the player
            if (lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be") ||
                lowerUrl.contains("vimeo.com") || lowerUrl.contains("dailymotion.com") ||
                lowerUrl.contains("rumble.com") || lowerUrl.contains("bitchute.com") ||
                lowerUrl.contains("odysee.com")) {
                val result = VideoPreviewResult(
                    videoUrl = pageUrl,
                    headers = buildPlaybackHeaders(pageUrl)
                )
                videoPreviewCache[pageUrl] = result
                return result
            }

            // 7. Last resort — pass the raw URL to the player and let ExoPlayer
            //    attempt it.  The player will cycle through Progressive/HLS/DASH
            //    formats automatically and show Retry/Open-in-Browser on failure.
            //    Returning null here used to cause silent "no source" toasts; this
            //    is strictly better because the user at least sees the player UI.
            val fallback = VideoPreviewResult(
                videoUrl = pageUrl,
                headers = buildPlaybackHeaders(pageUrl)
            )
            videoPreviewCache[pageUrl] = fallback
            return fallback
        } catch (e: Exception) {
            // Even on exception, return the raw URL so the player can attempt it
            return VideoPreviewResult(
                videoUrl = pageUrl,
                headers = buildPlaybackHeaders(pageUrl)
            )
        }
    }

    /** Convenience wrapper that keeps the old String?-returning signature for callers that don't need headers. */
    suspend fun extractVideoUrlForPreview(pageUrl: String): String? {
        return extractVideoForPreview(pageUrl)?.videoUrl
    }

    /** Build standard playback headers from the originating page URL. */
    private fun buildPlaybackHeaders(pageUrl: String): Map<String, String> {
        val origin = try {
            val uri = android.net.Uri.parse(pageUrl)
            "${uri.scheme}://${uri.host}"
        } catch (_: Exception) { pageUrl }
        return mapOf(
            "User-Agent" to EngineUtils.DEFAULT_USER_AGENT,
            "Referer" to "$origin/",
            "Origin" to origin,
            "Accept" to "*/*"
        )
    }
    
    /**
     * Extract video URL from a search result page
     */
    fun extractVideoUrl(result: SearchResult) {
        viewModelScope.launch {
            _videoExtractionState.value = VideoExtractionState.Extracting(result.title)
            
            try {
                val extractionResult = videoExtractor.extractVideoUrl(result.url)
                
                if (extractionResult.success && extractionResult.videoUrl != null) {
                    _videoExtractionState.value = VideoExtractionState.Success(
                        videoUrl = extractionResult.videoUrl,
                        title = result.title,
                        quality = extractionResult.quality,
                        isStream = extractionResult.format in listOf("m3u8", "mpd", "hls"),
                        headers = emptyMap()
                    )
                } else {
                    _videoExtractionState.value = VideoExtractionState.Error(
                        extractionResult.error ?: "Could not extract video URL"
                    )
                }
            } catch (e: Exception) {
                _videoExtractionState.value = VideoExtractionState.Error(
                    e.message ?: "Video extraction failed"
                )
            }
        }
    }
    
    /**
     * Reset video extraction state
     */
    fun resetVideoState() {
        _videoExtractionState.value = VideoExtractionState.Idle
    }
    
    /**
     * Start download for a search result
     */
    fun downloadResult(result: SearchResult) {
        viewModelScope.launch {
            try {
                downloadManager.downloadFromPage(result.url, result.title)
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "Download failed: ${e.message}") 
                }
            }
        }
    }
    
    /**
     * Download from extracted video URL directly
     */
    fun downloadVideoUrl(videoUrl: String, title: String) {
        viewModelScope.launch {
            try {
                downloadManager.downloadDirect(videoUrl, title)
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "Download failed: ${e.message}") 
                }
            }
        }
    }
    
    /**
     * Cancel a download
     */
    fun cancelDownload(downloadId: String) {
        downloadManager.cancelDownload(downloadId)
    }
}

data class SearchUiState(
    val query: String = "",
    val currentSearchQuery: String = "",
    val isSearching: Boolean = false,
    val searchCompleted: Boolean = false,
    val aggregatedResults: AggregatedSearchResults? = null,
    val totalResults: Int = 0,
    val successfulProviders: Int = 0,
    val failedProviders: Int = 0,
    val recentSearches: List<SearchHistoryEntry> = emptyList(),
    val error: String? = null
)

/**
 * State for video extraction process
 */
sealed class VideoExtractionState {
    object Idle : VideoExtractionState()
    data class Extracting(val title: String) : VideoExtractionState()
    data class Success(
        val videoUrl: String,
        val title: String,
        val quality: String?,
        val isStream: Boolean,
        val headers: Map<String, String> = emptyMap()
    ) : VideoExtractionState()
    data class Error(val message: String) : VideoExtractionState()
}
