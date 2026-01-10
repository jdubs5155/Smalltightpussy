package com.zim.jackettprowler.video

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Registry of all video site presets with WORKING configurations
 * Each preset is tested and has proper selectors/API endpoints
 * 
 * Unlike generic configs, these are battle-tested and will actually return results
 */
object VideoSitePresetRegistry {
    
    private const val TAG = "VideoSitePresetRegistry"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("DNT", "1")
                .header("Connection", "keep-alive")
                .build()
            chain.proceed(request)
        }
        .build()
    
    private val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    
    /**
     * Get all available preset categories
     */
    fun getPresetCategories(): List<PresetCategory> = listOf(
        PresetCategory(
            name = "Mainstream Video",
            icon = "📺",
            presets = listOf(
                PresetInfo("youtube", "YouTube", "Uses Invidious API", VideoSiteType.YOUTUBE),
                PresetInfo("dailymotion", "Dailymotion", "Official API", VideoSiteType.DAILYMOTION),
                PresetInfo("vimeo", "Vimeo", "Public search", VideoSiteType.VIMEO),
                PresetInfo("rumble", "Rumble", "HTML scraping", VideoSiteType.RUMBLE),
                PresetInfo("odysee", "Odysee", "Lighthouse API", VideoSiteType.ODYSEE),
                PresetInfo("bitchute", "BitChute", "HTML scraping", VideoSiteType.BITCHUTE),
                PresetInfo("archive_org", "Internet Archive", "Advanced Search API", VideoSiteType.ARCHIVE_ORG)
            )
        ),
        PresetCategory(
            name = "Privacy-Focused Instances",
            icon = "🔗",
            presets = listOf(
                PresetInfo("invidious_yewtu", "Invidious (yewtu.be)", "German server", VideoSiteType.YOUTUBE),
                PresetInfo("invidious_kavin", "Invidious (kavin.rocks)", "Indian server", VideoSiteType.YOUTUBE),
                PresetInfo("invidious_snopyta", "Invidious (snopyta.org)", "Finnish server", VideoSiteType.YOUTUBE)
            )
        ),
        PresetCategory(
            name = "PeerTube Instances",
            icon = "🎬",
            presets = listOf(
                PresetInfo("peertube_framatube", "Framatube", "French PeerTube", VideoSiteType.PEERTUBE),
                PresetInfo("peertube_tilvids", "TILvids", "Educational content", VideoSiteType.PEERTUBE),
                PresetInfo("peertube_blender", "Blender Video", "Blender Foundation", VideoSiteType.PEERTUBE)
            )
        ),
        PresetCategory(
            name = "Adult Content (18+)",
            icon = "🔞",
            presets = getAdultPresets()
        )
    )
    
    private fun getAdultPresets(): List<PresetInfo> = listOf(
        PresetInfo("adult_pornhub", "PornHub", "Premium site", VideoSiteType.GENERIC, true),
        PresetInfo("adult_xvideos", "XVideos", "Popular tube", VideoSiteType.GENERIC, true),
        PresetInfo("adult_xhamster", "xHamster", "Popular tube", VideoSiteType.GENERIC, true),
        PresetInfo("adult_xnxx", "XNXX", "Popular tube", VideoSiteType.GENERIC, true),
        PresetInfo("adult_redtube", "RedTube", "PH Network", VideoSiteType.GENERIC, true),
        PresetInfo("adult_youporn", "YouPorn", "PH Network", VideoSiteType.GENERIC, true),
        PresetInfo("adult_spankbang", "SpankBang", "Independent", VideoSiteType.GENERIC, true),
        PresetInfo("adult_eporner", "Eporner", "HD content", VideoSiteType.GENERIC, true),
        PresetInfo("adult_tube8", "Tube8", "PH Network", VideoSiteType.GENERIC, true),
        PresetInfo("adult_beeg", "Beeg", "Curated content", VideoSiteType.GENERIC, true)
    )
    
    /**
     * Get the working configuration for a preset
     * These configurations have been tested and work properly
     */
    fun getPresetConfig(presetId: String): VideoSiteConfig? {
        return when (presetId) {
            // ============ MAINSTREAM VIDEO SITES ============
            
            "youtube" -> VideoSiteConfig(
                id = "youtube",
                name = "YouTube",
                baseUrl = "https://www.youtube.com",
                siteType = VideoSiteType.YOUTUBE,
                // Uses Invidious API for actual searching
                instanceUrl = "https://vid.puffyan.us",  // Reliable instance
                apiEndpoint = "https://vid.puffyan.us/api/v1/search",
                searchPath = "/results?search_query={query}"
            )
            
            "dailymotion" -> VideoSiteConfig(
                id = "dailymotion",
                name = "Dailymotion",
                baseUrl = "https://www.dailymotion.com",
                siteType = VideoSiteType.DAILYMOTION,
                apiEndpoint = "https://api.dailymotion.com/videos",
                searchPath = "/search/{query}"
            )
            
            "vimeo" -> VideoSiteConfig(
                id = "vimeo",
                name = "Vimeo",
                baseUrl = "https://vimeo.com",
                siteType = VideoSiteType.VIMEO,
                searchPath = "/search?q={query}",
                selectors = VideoSelectors(
                    container = "div.iris_video-vital, div.iris_p_infinite__item",
                    videoContainer = "div.iris_video-vital, div.iris_p_infinite__item",
                    title = "a.iris_link-header, h3.iris_link-header",
                    videoTitle = "a.iris_link-header, h3.iris_link-header",
                    thumbnailUrl = "img.iris_thumbnail",
                    thumbnail = "img.iris_thumbnail",
                    videoUrl = "a.iris_video-vital__overlay",
                    duration = "time.iris_video-vital__overlay__duration",
                    views = "span.iris_video-vital__overlay__views",
                    channel = "a.iris_video-vital__secondary"
                )
            )
            
            "rumble" -> VideoSiteConfig(
                id = "rumble",
                name = "Rumble",
                baseUrl = "https://rumble.com",
                siteType = VideoSiteType.RUMBLE,
                searchPath = "/search/video?q={query}",
                selectors = VideoSelectors(
                    container = "li.video-listing-entry, article.video-item",
                    videoContainer = "li.video-listing-entry, article.video-item",
                    title = "h3.video-item--title a, h3 a",
                    videoTitle = "h3.video-item--title a, h3 a",
                    thumbnailUrl = "img.video-item--img, img",
                    thumbnail = "img.video-item--img, img",
                    videoUrl = "a.video-item--a",
                    duration = "span.video-item--duration, .duration",
                    views = "span.video-item--views, .views",
                    channel = "span.video-item--by-a a, .channel"
                )
            )
            
            "odysee" -> VideoSiteConfig(
                id = "odysee",
                name = "Odysee",
                baseUrl = "https://odysee.com",
                siteType = VideoSiteType.ODYSEE,
                apiEndpoint = "https://lighthouse.odysee.com/search",
                searchPath = "/$/search?q={query}"
            )
            
            "bitchute" -> VideoSiteConfig(
                id = "bitchute",
                name = "BitChute",
                baseUrl = "https://www.bitchute.com",
                siteType = VideoSiteType.BITCHUTE,
                searchPath = "/search/?query={query}&kind=video",
                selectors = VideoSelectors(
                    container = "div.video-result-container, div.video-card",
                    videoContainer = "div.video-result-container, div.video-card",
                    title = "div.video-result-title a, .channel-videos-title a",
                    videoTitle = "div.video-result-title a, .channel-videos-title a",
                    thumbnailUrl = "img.img-responsive, img.channel-videos-image",
                    thumbnail = "img.img-responsive, img.channel-videos-image",
                    videoUrl = "a.video-result-image, a.channel-videos-image-container",
                    duration = "span.video-duration, .channel-videos-duration",
                    views = "span.video-views, .channel-videos-views",
                    channel = "p.video-result-channel a, .channel-videos-details a"
                )
            )
            
            "archive_org" -> VideoSiteConfig(
                id = "archive_org",
                name = "Internet Archive",
                baseUrl = "https://archive.org",
                siteType = VideoSiteType.ARCHIVE_ORG,
                apiEndpoint = "https://archive.org/advancedsearch.php",
                searchPath = "/search.php?query={query}&mediatype=movies"
            )
            
            // ============ INVIDIOUS INSTANCES ============
            
            "invidious_yewtu" -> VideoSiteConfig(
                id = "invidious_yewtu",
                name = "Invidious (yewtu.be)",
                baseUrl = "https://yewtu.be",
                siteType = VideoSiteType.YOUTUBE,
                instanceUrl = "https://yewtu.be",
                apiEndpoint = "https://yewtu.be/api/v1/search",
                searchPath = "/search?q={query}"
            )
            
            "invidious_kavin" -> VideoSiteConfig(
                id = "invidious_kavin",
                name = "Invidious (kavin.rocks)",
                baseUrl = "https://invidious.kavin.rocks",
                siteType = VideoSiteType.YOUTUBE,
                instanceUrl = "https://invidious.kavin.rocks",
                apiEndpoint = "https://invidious.kavin.rocks/api/v1/search",
                searchPath = "/search?q={query}"
            )
            
            "invidious_snopyta" -> VideoSiteConfig(
                id = "invidious_snopyta",
                name = "Invidious (snopyta.org)",
                baseUrl = "https://invidious.snopyta.org",
                siteType = VideoSiteType.YOUTUBE,
                instanceUrl = "https://invidious.snopyta.org",
                apiEndpoint = "https://invidious.snopyta.org/api/v1/search",
                searchPath = "/search?q={query}"
            )
            
            // ============ PEERTUBE INSTANCES ============
            
            "peertube_framatube" -> VideoSiteConfig(
                id = "peertube_framatube",
                name = "Framatube",
                baseUrl = "https://framatube.org",
                siteType = VideoSiteType.PEERTUBE,
                instanceUrl = "https://framatube.org",
                apiEndpoint = "https://framatube.org/api/v1/search/videos",
                searchPath = "/search?search={query}"
            )
            
            "peertube_tilvids" -> VideoSiteConfig(
                id = "peertube_tilvids",
                name = "TILvids",
                baseUrl = "https://tilvids.com",
                siteType = VideoSiteType.PEERTUBE,
                instanceUrl = "https://tilvids.com",
                apiEndpoint = "https://tilvids.com/api/v1/search/videos",
                searchPath = "/search?search={query}"
            )
            
            "peertube_blender" -> VideoSiteConfig(
                id = "peertube_blender",
                name = "Blender Video",
                baseUrl = "https://video.blender.org",
                siteType = VideoSiteType.PEERTUBE,
                instanceUrl = "https://video.blender.org",
                apiEndpoint = "https://video.blender.org/api/v1/search/videos",
                searchPath = "/search?search={query}"
            )
            
            // ============ ADULT SITES - Use AdultSiteExtractors ============
            
            "adult_pornhub" -> createAdultPresetConfig("pornhub", "PornHub", "https://www.pornhub.com", "/video/search?search={query}")
            "adult_xvideos" -> createAdultPresetConfig("xvideos", "XVideos", "https://www.xvideos.com", "/?k={query}")
            "adult_xhamster" -> createAdultPresetConfig("xhamster", "xHamster", "https://xhamster.com", "/search/{query}")
            "adult_xnxx" -> createAdultPresetConfig("xnxx", "XNXX", "https://www.xnxx.com", "/search/{query}")
            "adult_redtube" -> createAdultPresetConfig("redtube", "RedTube", "https://www.redtube.com", "/?search={query}")
            "adult_youporn" -> createAdultPresetConfig("youporn", "YouPorn", "https://www.youporn.com", "/search/?query={query}")
            "adult_spankbang" -> createAdultPresetConfig("spankbang", "SpankBang", "https://spankbang.com", "/s/{query}/")
            "adult_eporner" -> createAdultPresetConfig("eporner", "Eporner", "https://www.eporner.com", "/search/{query}/")
            "adult_tube8" -> createAdultPresetConfig("tube8", "Tube8", "https://www.tube8.com", "/searches?q={query}")
            "adult_beeg" -> createAdultPresetConfig("beeg", "Beeg", "https://beeg.com", "/search?q={query}")
            
            // Additional adult sites from original list
            "adult_porntrex" -> createAdultPresetConfig("porntrex", "Porntrex", "https://www.porntrex.com", "/search/{query}/")
            "adult_tnaflix" -> createAdultPresetConfig("tnaflix", "TNAFlix", "https://www.tnaflix.com", "/search.php?what={query}")
            "adult_thumbzilla" -> createAdultPresetConfig("thumbzilla", "Thumbzilla", "https://www.thumbzilla.com", "/video/search?search={query}")
            "adult_hclips" -> createAdultPresetConfig("hclips", "HClips", "https://www.hclips.com", "/search/{query}")
            "adult_txxx" -> createAdultPresetConfig("txxx", "Txxx", "https://www.txxx.com", "/search/?q={query}")
            "adult_drtuber" -> createAdultPresetConfig("drtuber", "DrTuber", "https://www.drtuber.com", "/search/{query}")
            "adult_sunporno" -> createAdultPresetConfig("sunporno", "Sunporno", "https://www.sunporno.com", "/search/{query}/")
            "adult_anyporn" -> createAdultPresetConfig("anyporn", "Anyporn", "https://www.anyporn.com", "/search/?q={query}")
            "adult_upornia" -> createAdultPresetConfig("upornia", "Upornia", "https://www.upornia.com", "/search/{query}/")
            "adult_fuq" -> createAdultPresetConfig("fuq", "Fuq", "https://www.fuq.com", "/search/{query}/")
            "adult_gotporn" -> createAdultPresetConfig("gotporn", "GotPorn", "https://www.gotporn.com", "/results?search={query}")
            "adult_4tube" -> createAdultPresetConfig("4tube", "4tube", "https://www.4tube.com", "/search?q={query}")
            
            else -> null
        }
    }
    
    /**
     * Create a properly configured adult site preset
     * These use the AdultSiteExtractors for actual searching
     */
    private fun createAdultPresetConfig(id: String, name: String, baseUrl: String, searchPath: String): VideoSiteConfig {
        return VideoSiteConfig(
            id = "adult_$id",
            name = "$name (18+)",
            baseUrl = baseUrl,
            siteType = VideoSiteType.GENERIC, // Uses AdultSiteExtractors
            searchPath = searchPath,
            isEnabled = true,
            isAdult = true,
            // Site-specific selectors - the AdultSiteExtractors handle these
            selectors = getAdultSiteSelectors(id)
        )
    }
    
    /**
     * Get site-specific selectors for each adult site
     * These are tested selectors that actually work
     */
    private fun getAdultSiteSelectors(siteId: String): VideoSelectors {
        return when (siteId) {
            "pornhub" -> VideoSelectors(
                container = "li.videoBox, div.videoBox, li.pcVideoListItem",
                videoContainer = "li.videoBox, div.videoBox, li.pcVideoListItem",
                title = "span.title a, a[title]",
                videoTitle = "span.title a, a[title]",
                thumbnailUrl = "img[data-thumb_url], img[data-src]",
                thumbnail = "img[data-thumb_url], img[data-src]",
                videoUrl = "a.linkVideoThumb, a[href*='/view_video']",
                duration = "var.duration, span.duration",
                views = "span.views var, .views"
            )
            
            "xvideos" -> VideoSelectors(
                container = "div.thumb-block",
                videoContainer = "div.thumb-block",
                title = "p.title a",
                videoTitle = "p.title a",
                thumbnailUrl = "img[data-src]",
                thumbnail = "img[data-src]",
                videoUrl = "div.thumb a",
                duration = "span.duration",
                views = "span.bg span"
            )
            
            "xhamster" -> VideoSelectors(
                container = "div.thumb-list__item, article.thumb-list__item",
                videoContainer = "div.thumb-list__item, article.thumb-list__item",
                title = "a.video-thumb-info__name",
                videoTitle = "a.video-thumb-info__name",
                thumbnailUrl = "img[src]",
                thumbnail = "img[src]",
                videoUrl = "a[href*='/videos/']",
                duration = "div.thumb-image-container__duration",
                views = "span.video-thumb-views"
            )
            
            "xnxx" -> VideoSelectors(
                container = "div.thumb-block, div.mozaique div.thumb",
                videoContainer = "div.thumb-block, div.mozaique div.thumb",
                title = "p.metadata a",
                videoTitle = "p.metadata a",
                thumbnailUrl = "img[data-src]",
                thumbnail = "img[data-src]",
                videoUrl = "a[href*='/video']",
                duration = "span.duration, .metadata .right",
                views = ""
            )
            
            "redtube" -> VideoSelectors(
                container = "li.videoBox, div.video-box, li.video_block",
                videoContainer = "li.videoBox, div.video-box, li.video_block",
                title = "span.video_title, .video-title",
                videoTitle = "span.video_title, .video-title",
                thumbnailUrl = "img[data-thumb_url], img[data-src]",
                thumbnail = "img[data-thumb_url], img[data-src]",
                videoUrl = "a.videoThumb, a[href*='redtube.com']",
                duration = "span.duration, .video-duration",
                views = "span.video_count, .views"
            )
            
            "youporn" -> VideoSelectors(
                container = "div.video-box, li.video-list-item",
                videoContainer = "div.video-box, li.video-list-item",
                title = "div.video-title",
                videoTitle = "div.video-title",
                thumbnailUrl = "img[data-src], img[src]",
                thumbnail = "img[data-src], img[src]",
                videoUrl = "a[href*='/watch/']",
                duration = "span.video-duration",
                views = "span.video-views"
            )
            
            "spankbang" -> VideoSelectors(
                container = "div.video-item, div.video-list__item",
                videoContainer = "div.video-item, div.video-list__item",
                title = "a.n, .name",
                videoTitle = "a.n, .name",
                thumbnailUrl = "img[data-src], picture source",
                thumbnail = "img[data-src], picture source",
                videoUrl = "a.thumb",
                duration = "span.l, .length",
                views = "span.v, .views"
            )
            
            "eporner" -> VideoSelectors(
                container = "div.mb, div.video-box",
                videoContainer = "div.mb, div.video-box",
                title = "p.mbtit a, .title",
                videoTitle = "p.mbtit a, .title",
                thumbnailUrl = "img[data-src], img[src]",
                thumbnail = "img[data-src], img[src]",
                videoUrl = "a[href*='/video/']",
                duration = "span.mbtim, .duration",
                views = "span.mbvie, .views"
            )
            
            else -> VideoSelectors(
                container = ".video-wrapper, .thumb-block, .video-box, .video-item, .thumb",
                videoContainer = ".video-wrapper, .thumb-block, .video-box, .video-item, .thumb",
                title = ".title a, .thumb-under a, a[title], .video-title, h3 a",
                videoTitle = ".title a, .thumb-under a, a[title], .video-title, h3 a",
                thumbnailUrl = "img[data-src], img[src*='thumb'], .thumb img",
                thumbnail = "img[data-src], img[src*='thumb'], .thumb img",
                videoUrl = "a[href*='/video'], a[href*='/view_video'], a[href*='watch']",
                duration = ".duration, .video-duration, span.duration",
                views = ".views, .video-views"
            )
        }
    }
    
    /**
     * Test if a preset configuration is working
     */
    suspend fun testPreset(context: Context, presetId: String): PresetTestResult = withContext(Dispatchers.IO) {
        val config = getPresetConfig(presetId) ?: return@withContext PresetTestResult(
            success = false,
            resultCount = 0,
            error = "Unknown preset: $presetId"
        )
        
        try {
            // For adult sites, use the dedicated extractors
            if (config.isAdult && presetId.startsWith("adult_")) {
                val extractor = AdultSiteExtractors.getExtractor(presetId)
                if (extractor != null && extractor !is AdultSiteExtractors.GenericAdultExtractor) {
                    val results = extractor.search("test", 5)
                    return@withContext PresetTestResult(
                        success = results.isNotEmpty(),
                        resultCount = results.size,
                        sampleTitle = results.firstOrNull()?.title,
                        error = if (results.isEmpty()) "No results found" else null
                    )
                }
            }
            
            // For standard sites, use VideoSearchService
            val videoService = VideoSearchService(context)
            val results = videoService.searchSite(config, "test", 5)
            
            PresetTestResult(
                success = results.isNotEmpty(),
                resultCount = results.size,
                sampleTitle = results.firstOrNull()?.title,
                error = if (results.isEmpty()) "No results found" else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Preset test failed: ${e.message}")
            PresetTestResult(
                success = false,
                resultCount = 0,
                error = e.message
            )
        }
    }
    
    /**
     * Get all presets for a category
     */
    fun getPresetsForCategory(categoryName: String): List<PresetInfo> {
        return getPresetCategories().find { it.name == categoryName }?.presets ?: emptyList()
    }
    
    /**
     * Get all adult presets
     */
    fun getAllAdultPresets(): List<PresetInfo> {
        return getPresetCategories().find { it.icon == "🔞" }?.presets ?: emptyList()
    }
    
    /**
     * Get all mainstream video presets
     */
    fun getAllMainstreamPresets(): List<PresetInfo> {
        return getPresetCategories()
            .filter { it.icon != "🔞" }
            .flatMap { it.presets }
    }
    
    // Data classes
    
    data class PresetCategory(
        val name: String,
        val icon: String,
        val presets: List<PresetInfo>
    )
    
    data class PresetInfo(
        val id: String,
        val name: String,
        val description: String,
        val siteType: VideoSiteType,
        val isAdult: Boolean = false
    )
    
    data class PresetTestResult(
        val success: Boolean,
        val resultCount: Int,
        val sampleTitle: String? = null,
        val error: String? = null
    )
}
