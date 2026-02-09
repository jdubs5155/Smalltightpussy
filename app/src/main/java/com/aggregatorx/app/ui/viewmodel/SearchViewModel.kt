package com.aggregatorx.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aggregatorx.app.data.model.*
import com.aggregatorx.app.data.repository.AggregatorRepository
import com.aggregatorx.app.engine.media.DownloadManager
import com.aggregatorx.app.engine.media.DownloadState
import com.aggregatorx.app.engine.media.VideoExtractorEngine
import com.aggregatorx.app.engine.media.VideoExtractionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: AggregatorRepository,
    private val videoExtractor: VideoExtractorEngine,
    private val downloadManager: DownloadManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    private val _providerResults = MutableStateFlow<List<ProviderSearchResults>>(emptyList())
    val providerResults: StateFlow<List<ProviderSearchResults>> = _providerResults.asStateFlow()
    
    private val _videoExtractionState = MutableStateFlow<VideoExtractionState>(VideoExtractionState.Idle)
    val videoExtractionState: StateFlow<VideoExtractionState> = _videoExtractionState.asStateFlow()
    
    val downloads: StateFlow<Map<String, DownloadState>> = downloadManager.downloads
    
    init {
        // Load recent searches
        viewModelScope.launch {
            repository.getRecentSearches().collect { searches ->
                _uiState.update { it.copy(recentSearches = searches) }
            }
        }
    }
    
    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }
    
    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isSearching = true, 
                    searchCompleted = false,
                    currentSearchQuery = query
                ) 
            }
            _providerResults.value = emptyList()
            
            val results = mutableListOf<ProviderSearchResults>()
            
            repository.searchAllProviders(query)
                .catch { e ->
                    _uiState.update { 
                        it.copy(error = e.message ?: "Search failed") 
                    }
                }
                .collect { providerResult ->
                    results.add(providerResult)
                    _providerResults.value = results.toList()
                    
                    // Update aggregated results
                    val aggregated = repository.aggregateSearchResults(query, results)
                    _uiState.update { 
                        it.copy(
                            aggregatedResults = aggregated,
                            totalResults = aggregated.totalResults,
                            successfulProviders = aggregated.successfulProviders,
                            failedProviders = aggregated.failedProviders
                        ) 
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
     * Extract video URL from a page URL - suspend function for inline preview
     * Uses smart extraction: tries fast methods first, falls back to headless browser with auto-click
     * Returns the extracted video URL or null if extraction fails
     */
    suspend fun extractVideoUrlForPreview(pageUrl: String): String? {
        return try {
            // Use the optimized preview extraction which handles ads automatically
            videoExtractor.extractVideoUrlForPreview(pageUrl)
        } catch (e: Exception) {
            null
        }
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
                        isStream = extractionResult.isStream
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
        val isStream: Boolean
    ) : VideoExtractionState()
    data class Error(val message: String) : VideoExtractionState()
}
