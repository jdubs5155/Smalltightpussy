package com.zim.jackettprowler.video

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Service for searching and scraping clearnet video sites
 * Now with site-specific extractors for adult sites!
 */
class VideoSearchService(private val context: Context) {
    
    companion object {
        private const val TAG = "VideoSearchService"
        private const val PREFS_NAME = "video_sites"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("DNT", "1")
                .header("Connection", "keep-alive")
                .build()
            chain.proceed(request)
        }
        .build()
    
    private val gson = Gson()
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val siteInfiltrator = VideoSiteInfiltrator(context)
    
    /**
     * Search all enabled video sites
     */
    suspend fun searchAll(query: String, limit: Int = 50): VideoSearchResult = withContext(Dispatchers.IO) {
        val sites = getEnabledSites()
        if (sites.isEmpty()) {
            return@withContext VideoSearchResult(
                success = false,
                results = emptyList(),
                errors = listOf("No video sites configured"),
                sourceStats = emptyMap()
            )
        }
        
        val allResults = mutableListOf<VideoResult>()
        val errors = mutableListOf<String>()
        val sourceStats = mutableMapOf<String, Int>()
        
        // Search each site in parallel
        val jobs = sites.map { site ->
            async {
                try {
                    val results = searchSite(site, query, limit / sites.size.coerceAtLeast(1))
                    Pair(site.name, results)
                } catch (e: Exception) {
                    Log.e(TAG, "Error searching ${site.name}: ${e.message}")
                    errors.add("${site.name}: ${e.message}")
                    Pair(site.name, emptyList())
                }
            }
        }
        
        val results = jobs.awaitAll()
        results.forEach { (siteName, siteResults) ->
            allResults.addAll(siteResults)
            if (siteResults.isNotEmpty()) {
                sourceStats[siteName] = siteResults.size
            }
        }
        
        VideoSearchResult(
            success = allResults.isNotEmpty(),
            results = allResults.take(limit),
            errors = errors,
            sourceStats = sourceStats
        )
    }
    
    /**
     * Search a specific video site
     * Uses site-specific extractors for adult sites
     */
    suspend fun searchSite(site: VideoSiteConfig, query: String, limit: Int = 20): List<VideoResult> = withContext(Dispatchers.IO) {
        // Check if this is an adult site with a dedicated extractor
        if (site.isAdult || site.id.startsWith("adult_")) {
            val extractor = AdultSiteExtractors.getExtractor(site.id)
            if (extractor != null && extractor !is AdultSiteExtractors.GenericAdultExtractor) {
                Log.d(TAG, "Using site-specific extractor for ${site.name}")
                return@withContext extractor.search(query, limit)
            }
        }
        
        // Standard extraction for non-adult sites
        return@withContext when (site.siteType) {
            VideoSiteType.YOUTUBE -> searchYouTube(site, query, limit)
            VideoSiteType.DAILYMOTION -> searchDailymotion(site, query, limit)
            VideoSiteType.VIMEO -> searchVimeo(site, query, limit)
            VideoSiteType.RUMBLE -> searchRumble(site, query, limit)
            VideoSiteType.ODYSEE -> searchOdysee(site, query, limit)
            VideoSiteType.BITCHUTE -> searchBitChute(site, query, limit)
            VideoSiteType.PEERTUBE -> searchPeerTube(site, query, limit)
            VideoSiteType.ARCHIVE_ORG -> searchArchiveOrg(site, query, limit)
            VideoSiteType.TWITCH -> searchTwitch(site, query, limit)
            VideoSiteType.GENERIC -> searchGeneric(site, query, limit)
        }
    }
    
    /**
     * Search YouTube via Invidious/Piped instance
     */
    private suspend fun searchYouTube(site: VideoSiteConfig, query: String, limit: Int): List<VideoResult> {
        val instanceUrl = site.instanceUrl.ifEmpty { 
            // Default Invidious instances
            getWorkingInvidiousInstance()
        }
        
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$instanceUrl/api/v1/search?q=$encodedQuery&type=video"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            
            val json = response.body?.string() ?: return emptyList()
            val results = mutableListOf<VideoResult>()
            
            try {
                val array = JSONArray(json)
                for (i in 0 until minOf(array.length(), limit)) {
                    val item = array.getJSONObject(i)
                    if (item.optString("type") == "video") {
                        results.add(VideoResult(
                            title = item.optString("title", ""),
                            videoUrl = "https://www.youtube.com/watch?v=${item.optString("videoId")}",
                            thumbnailUrl = item.optJSONArray("videoThumbnails")?.optJSONObject(0)?.optString("url") ?: "",
                            duration = formatDuration(item.optLong("lengthSeconds", 0)),
                            views = item.optLong("viewCount", 0).toString(),
                            channel = item.optString("author", ""),
                            uploadDate = item.optString("publishedText", ""),
                            source = site.name,
                            siteType = VideoSiteType.YOUTUBE
                        ))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing YouTube results: ${e.message}")
            }
            
            return results
        }
    }
    
    /**
     * Search Dailymotion
     */
    private suspend fun searchDailymotion(site: VideoSiteConfig, query: String, limit: Int): List<VideoResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.dailymotion.com/videos?search=$encodedQuery&fields=id,title,thumbnail_url,duration,views_total,owner.screenname,created_time&limit=$limit"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            
            val json = response.body?.string() ?: return emptyList()
            val results = mutableListOf<VideoResult>()
            
            try {
                val obj = JSONObject(json)
                val list = obj.optJSONArray("list") ?: return emptyList()
                
                for (i in 0 until list.length()) {
                    val item = list.getJSONObject(i)
                    results.add(VideoResult(
                        title = item.optString("title", ""),
                        videoUrl = "https://www.dailymotion.com/video/${item.optString("id")}",
                        thumbnailUrl = item.optString("thumbnail_url", ""),
                        duration = formatDuration(item.optLong("duration", 0)),
                        views = item.optLong("views_total", 0).toString(),
                        channel = item.optJSONObject("owner")?.optString("screenname") ?: "",
                        source = site.name,
                        siteType = VideoSiteType.DAILYMOTION
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing Dailymotion results: ${e.message}")
            }
            
            return results
        }
    }
    
    /**
     * Search Vimeo
     */
    private suspend fun searchVimeo(site: VideoSiteConfig, query: String, limit: Int): List<VideoResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://vimeo.com/search/page:1/sort:relevant/format:json?q=$encodedQuery"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "application/json")
            .build()
        
        // Fallback to HTML scraping for Vimeo
        return searchGenericWithSelectors(
            baseUrl = "https://vimeo.com",
            searchPath = "/search?q=$encodedQuery",
            selectors = VideoSelectors(
                container = "div.iris_video-vital",
                title = "a.iris_link-header",
                thumbnailUrl = "img",
                duration = "time",
                channel = "span.vimeo-author"
            ),
            siteName = site.name,
            siteType = VideoSiteType.VIMEO,
            limit = limit
        )
    }
    
    /**
     * Search Rumble
     */
    private suspend fun searchRumble(site: VideoSiteConfig, query: String, limit: Int): List<VideoResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        return searchGenericWithSelectors(
            baseUrl = "https://rumble.com",
            searchPath = "/search/video?q=$encodedQuery",
            selectors = VideoSelectors(
                container = "li.video-listing-entry",
                title = "h3.video-item--title",
                thumbnailUrl = "img.video-item--img",
                duration = "span.video-item--duration",
                views = "span.video-item--views",
                channel = "span.video-item--by-a"
            ),
            siteName = site.name,
            siteType = VideoSiteType.RUMBLE,
            limit = limit
        )
    }
    
    /**
     * Search Odysee (LBRY)
     */
    private suspend fun searchOdysee(site: VideoSiteConfig, query: String, limit: Int): List<VideoResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://lighthouse.odysee.com/search?s=$encodedQuery&size=$limit&from=0&nsfw=false&free_only=true"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            
            val json = response.body?.string() ?: return emptyList()
            val results = mutableListOf<VideoResult>()
            
            try {
                val array = JSONArray(json)
                for (i in 0 until minOf(array.length(), limit)) {
                    val item = array.getJSONObject(i)
                    val claimId = item.optString("claimId", "")
                    val name = item.optString("name", "")
                    val channel = item.optString("channel", "")
                    
                    results.add(VideoResult(
                        title = item.optString("title", name),
                        videoUrl = "https://odysee.com/$channel/$name",
                        thumbnailUrl = item.optString("thumbnail_url", ""),
                        duration = formatDuration(item.optLong("duration", 0)),
                        views = "",
                        channel = channel,
                        uploadDate = item.optString("release_time", ""),
                        source = site.name,
                        siteType = VideoSiteType.ODYSEE
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing Odysee results: ${e.message}")
            }
            
            return results
        }
    }
    
    /**
     * Search BitChute
     */
    private suspend fun searchBitChute(site: VideoSiteConfig, query: String, limit: Int): List<VideoResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        return searchGenericWithSelectors(
            baseUrl = "https://www.bitchute.com",
            searchPath = "/search/?query=$encodedQuery&kind=video",
            selectors = VideoSelectors(
                container = "div.video-result-container",
                title = "div.video-result-title a",
                thumbnailUrl = "img.img-responsive",
                duration = "span.video-duration",
                views = "span.video-views",
                channel = "p.video-result-channel a"
            ),
            siteName = site.name,
            siteType = VideoSiteType.BITCHUTE,
            limit = limit
        )
    }
    
    /**
     * Search PeerTube instances
     */
    private suspend fun searchPeerTube(site: VideoSiteConfig, query: String, limit: Int): List<VideoResult> {
        val instanceUrl = site.instanceUrl.ifEmpty { site.baseUrl }
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$instanceUrl/api/v1/search/videos?search=$encodedQuery&count=$limit"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            
            val json = response.body?.string() ?: return emptyList()
            val results = mutableListOf<VideoResult>()
            
            try {
                val obj = JSONObject(json)
                val data = obj.optJSONArray("data") ?: return emptyList()
                
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    results.add(VideoResult(
                        title = item.optString("name", ""),
                        videoUrl = item.optString("url", ""),
                        thumbnailUrl = item.optString("thumbnailPath", "").let { 
                            if (it.startsWith("http")) it else "$instanceUrl$it"
                        },
                        duration = formatDuration(item.optLong("duration", 0)),
                        views = item.optInt("views", 0).toString(),
                        channel = item.optJSONObject("channel")?.optString("displayName") ?: "",
                        uploadDate = item.optString("publishedAt", ""),
                        source = site.name,
                        siteType = VideoSiteType.PEERTUBE
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing PeerTube results: ${e.message}")
            }
            
            return results
        }
    }
    
    /**
     * Search Internet Archive
     */
    private suspend fun searchArchiveOrg(site: VideoSiteConfig, query: String, limit: Int): List<VideoResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://archive.org/advancedsearch.php?q=$encodedQuery+AND+mediatype:movies&fl[]=identifier,title,description,creator,date,downloads,item_size&rows=$limit&output=json"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            
            val json = response.body?.string() ?: return emptyList()
            val results = mutableListOf<VideoResult>()
            
            try {
                val obj = JSONObject(json)
                val docs = obj.optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
                
                for (i in 0 until docs.length()) {
                    val item = docs.getJSONObject(i)
                    val identifier = item.optString("identifier", "")
                    
                    results.add(VideoResult(
                        title = item.optString("title", ""),
                        videoUrl = "https://archive.org/details/$identifier",
                        thumbnailUrl = "https://archive.org/services/img/$identifier",
                        views = item.optInt("downloads", 0).toString(),
                        channel = item.optString("creator", ""),
                        uploadDate = item.optString("date", ""),
                        description = item.optString("description", ""),
                        source = site.name,
                        siteType = VideoSiteType.ARCHIVE_ORG
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing Archive.org results: ${e.message}")
            }
            
            return results
        }
    }
    
    /**
     * Search Twitch VODs
     */
    private suspend fun searchTwitch(site: VideoSiteConfig, query: String, limit: Int): List<VideoResult> {
        // Twitch requires OAuth - use scraping fallback
        return searchGenericWithSelectors(
            baseUrl = "https://www.twitch.tv",
            searchPath = "/search?term=$query&type=video",
            selectors = VideoSelectors(
                container = "div[data-a-target='search-result-video']",
                title = "h3",
                thumbnailUrl = "img",
                duration = "div.tw-media-card-stat",
                channel = "a.tw-link"
            ),
            siteName = site.name,
            siteType = VideoSiteType.TWITCH,
            limit = limit
        )
    }
    
    /**
     * Search generic video site with custom selectors
     */
    private suspend fun searchGeneric(site: VideoSiteConfig, query: String, limit: Int): List<VideoResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = site.baseUrl + site.searchPath.replace("{query}", encodedQuery)
        
        return searchGenericWithSelectors(
            baseUrl = site.baseUrl,
            searchPath = site.searchPath.replace("{query}", encodedQuery),
            selectors = site.selectors,
            siteName = site.name,
            siteType = site.siteType,
            limit = limit
        )
    }
    
    /**
     * Generic HTML scraping for video sites
     */
    private suspend fun searchGenericWithSelectors(
        baseUrl: String,
        searchPath: String,
        selectors: VideoSelectors,
        siteName: String,
        siteType: VideoSiteType,
        limit: Int
    ): List<VideoResult> {
        val url = baseUrl + searchPath
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()
        
        // Resolve selector aliases
        val containerSelector = selectors.container.ifEmpty { selectors.videoContainer }
        val titleSelector = selectors.title.ifEmpty { selectors.videoTitle }
        val thumbSelector = selectors.thumbnailUrl.ifEmpty { selectors.thumbnail }
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            
            val html = response.body?.string() ?: return emptyList()
            val results = mutableListOf<VideoResult>()
            
            try {
                val doc = Jsoup.parse(html, baseUrl)
                val items = if (containerSelector.isNotEmpty()) {
                    doc.select(containerSelector)
                } else {
                    doc.select("a[href*=video], a[href*=watch], div.video, article")
                }
                
                for (item in items.take(limit)) {
                    val title = if (titleSelector.isNotEmpty()) {
                        item.select(titleSelector).text()
                    } else {
                        item.text()
                    }
                    
                    if (title.isBlank()) continue
                    
                    val videoUrl = if (selectors.videoUrl.isNotEmpty()) {
                        item.select(selectors.videoUrl).attr("abs:href")
                    } else {
                        item.attr("abs:href").ifEmpty { 
                            item.select("a").first()?.attr("abs:href") ?: ""
                        }
                    }
                    
                    val thumbnail = if (thumbSelector.isNotEmpty()) {
                        item.select(thumbSelector).attr("abs:src").ifEmpty {
                            item.select(thumbSelector).attr("data-src").ifEmpty {
                                item.select(thumbSelector).attr("data-original")
                            }
                        }
                    } else {
                        item.select("img").first()?.attr("abs:src") ?: ""
                    }
                    
                    results.add(VideoResult(
                        title = title,
                        videoUrl = videoUrl,
                        thumbnailUrl = thumbnail,
                        duration = if (selectors.duration.isNotEmpty()) item.select(selectors.duration).text() else "",
                        views = if (selectors.views.isNotEmpty()) item.select(selectors.views).text() else "",
                        channel = if (selectors.channel.isNotEmpty()) item.select(selectors.channel).text() else "",
                        uploadDate = if (selectors.uploadDate.isNotEmpty()) item.select(selectors.uploadDate).text() else "",
                        source = siteName,
                        siteType = siteType
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing generic site: ${e.message}")
            }
            
            return results
        }
    }
    
    // =================== Site Management ===================
    
    /**
     * Add a new video site (auto-detects configuration)
     */
    suspend fun addSite(url: String): AddSiteResult = withContext(Dispatchers.IO) {
        try {
            val config = siteInfiltrator.analyzeAndConfigure(url)
            if (config != null) {
                saveSite(config)
                AddSiteResult(true, config, null)
            } else {
                AddSiteResult(false, null, "Could not auto-configure site")
            }
        } catch (e: Exception) {
            AddSiteResult(false, null, e.message)
        }
    }
    
    /**
     * Save a video site configuration
     */
    fun saveSite(config: VideoSiteConfig) {
        val sites = getAllSites().toMutableList()
        sites.removeAll { it.id == config.id }
        sites.add(config)
        
        val json = gson.toJson(sites)
        prefs.edit().putString("sites", json).apply()
    }
    
    /**
     * Remove a video site
     */
    fun removeSite(siteId: String) {
        val sites = getAllSites().filter { it.id != siteId }
        val json = gson.toJson(sites)
        prefs.edit().putString("sites", json).apply()
    }
    
    /**
     * Toggle site enabled status
     */
    fun toggleSite(siteId: String, enabled: Boolean) {
        val sites = getAllSites().map { 
            if (it.id == siteId) it.copy(isEnabled = enabled) else it 
        }
        val json = gson.toJson(sites)
        prefs.edit().putString("sites", json).apply()
    }
    
    /**
     * Get all configured video sites
     */
    fun getAllSites(): List<VideoSiteConfig> {
        val json = prefs.getString("sites", "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<VideoSiteConfig>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get only enabled video sites
     */
    fun getEnabledSites(): List<VideoSiteConfig> {
        return getAllSites().filter { it.isEnabled }
    }
    
    // =================== Utility Functions ===================
    
    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return ""
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%d:%02d", minutes, secs)
        }
    }
    
    private suspend fun getWorkingInvidiousInstance(): String {
        // List of public Invidious instances
        val instances = listOf(
            "https://invidious.snopyta.org",
            "https://yewtu.be",
            "https://invidious.kavin.rocks",
            "https://vid.puffyan.us",
            "https://inv.riverside.rocks",
            "https://invidious.osi.kr"
        )
        
        // Try to find a working instance
        for (instance in instances) {
            try {
                val request = Request.Builder()
                    .url("$instance/api/v1/stats")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return instance
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }
        
        return instances.first() // Default to first one
    }
    
    data class VideoSearchResult(
        val success: Boolean,
        val results: List<VideoResult>,
        val errors: List<String>,
        val sourceStats: Map<String, Int>
    ) {
        fun getStatusSummary(): String {
            return if (success) {
                "${results.size} videos from ${sourceStats.size} sources"
            } else {
                "No results found"
            }
        }
        
        fun getDetailedStatus(): String {
            return buildString {
                appendLine("Video Search Results")
                appendLine("==================")
                sourceStats.forEach { (source, count) ->
                    appendLine("• $source: $count videos")
                }
                if (errors.isNotEmpty()) {
                    appendLine("\nErrors:")
                    errors.forEach { appendLine("• $it") }
                }
            }
        }
    }
    
    data class AddSiteResult(
        val success: Boolean,
        val config: VideoSiteConfig?,
        val error: String?
    )
}
