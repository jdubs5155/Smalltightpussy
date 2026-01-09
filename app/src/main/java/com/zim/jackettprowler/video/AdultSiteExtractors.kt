package com.zim.jackettprowler.video

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Site-specific extractors for adult video sites
 * Each extractor knows exactly how to parse its target site
 * Bypasses CORS and rate limiting through proper request handling
 */
object AdultSiteExtractors {
    
    private const val TAG = "AdultSiteExtractors"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("DNT", "1")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .build()
            chain.proceed(request)
        }
        .build()
    
    private val MOBILE_UA = "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    
    /**
     * Get the appropriate extractor for a site ID
     */
    fun getExtractor(siteId: String): SiteExtractor? {
        return when {
            siteId.contains("pornhub") -> PornHubExtractor()
            siteId.contains("xvideos") -> XVideosExtractor()
            siteId.contains("xhamster") -> XHamsterExtractor()
            siteId.contains("xnxx") -> XNXXExtractor()
            siteId.contains("redtube") -> RedTubeExtractor()
            siteId.contains("youporn") -> YouPornExtractor()
            siteId.contains("spankbang") -> SpankBangExtractor()
            siteId.contains("eporner") -> EpornerExtractor()
            siteId.contains("porntrex") -> PornTrexExtractor()
            siteId.contains("tnaflix") -> TNAFlixExtractor()
            siteId.contains("tube8") -> Tube8Extractor()
            siteId.contains("beeg") -> BeegExtractor()
            siteId.contains("thumbzilla") -> ThumbzillaExtractor()
            siteId.contains("pornone") -> PornoneExtractor()
            siteId.contains("hclips") -> HClipsExtractor()
            siteId.contains("txxx") -> TxxxExtractor()
            siteId.contains("drtuber") -> DrTuberExtractor()
            siteId.contains("sunporno") -> SunPornoExtractor()
            siteId.contains("anyporn") -> AnyPornExtractor()
            siteId.contains("upornia") -> UporniaExtractor()
            siteId.contains("fuq") -> FuqExtractor()
            siteId.contains("gotporn") -> GotPornExtractor()
            siteId.contains("4tube") -> FourTubeExtractor()
            else -> GenericAdultExtractor()
        }
    }
    
    /**
     * Base interface for all site extractors
     */
    interface SiteExtractor {
        suspend fun search(query: String, limit: Int = 50): List<VideoResult>
        fun getSiteName(): String
    }
    
    /**
     * XVideos Extractor - Works via HTML scraping
     */
    class XVideosExtractor : SiteExtractor {
        override fun getSiteName() = "XVideos"
        
        override suspend fun search(query: String, limit: Int): List<VideoResult> = withContext(Dispatchers.IO) {
            val results = mutableListOf<VideoResult>()
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://www.xvideos.com/?k=$encodedQuery"
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", DESKTOP_UA)
                    .header("Cookie", "age_verified=1")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext results
                    val html = response.body?.string() ?: return@withContext results
                    val doc = Jsoup.parse(html)
                    
                    // XVideos uses .thumb-block for video containers
                    doc.select("div.thumb-block").take(limit).forEach { block ->
                        try {
                            val linkEl = block.selectFirst("div.thumb a") ?: block.selectFirst("a")
                            val titleEl = block.selectFirst("p.title a") ?: block.selectFirst(".title a")
                            val thumbEl = block.selectFirst("img")
                            val durationEl = block.selectFirst(".duration") ?: block.selectFirst("span.duration")
                            
                            val title = titleEl?.text() ?: titleEl?.attr("title") ?: ""
                            val videoUrl = linkEl?.attr("href")?.let {
                                if (it.startsWith("/")) "https://www.xvideos.com$it" else it
                            } ?: ""
                            val thumbnail = thumbEl?.attr("data-src") 
                                ?: thumbEl?.attr("src") ?: ""
                            val duration = durationEl?.text() ?: ""
                            
                            if (title.isNotEmpty() && videoUrl.isNotEmpty()) {
                                results.add(VideoResult(
                                    title = title,
                                    videoUrl = videoUrl,
                                    thumbnailUrl = thumbnail,
                                    duration = duration,
                                    views = "",
                                    channel = "",
                                    source = "XVideos",
                                    siteType = VideoSiteType.GENERIC
                                ))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "XVideos parse error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "XVideos search error: ${e.message}")
            }
            results
        }
    }
    
    /**
     * XNXX Extractor - Similar to XVideos
     */
    class XNXXExtractor : SiteExtractor {
        override fun getSiteName() = "XNXX"
        
        override suspend fun search(query: String, limit: Int): List<VideoResult> = withContext(Dispatchers.IO) {
            val results = mutableListOf<VideoResult>()
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://www.xnxx.com/search/$encodedQuery"
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", DESKTOP_UA)
                    .header("Cookie", "age_verified=1")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext results
                    val html = response.body?.string() ?: return@withContext results
                    val doc = Jsoup.parse(html)
                    
                    // XNXX uses .thumb-block or .mozaique .thumb
                    doc.select("div.thumb-block, div.mozaique div.thumb").take(limit).forEach { block ->
                        try {
                            val linkEl = block.selectFirst("a")
                            val thumbEl = block.selectFirst("img")
                            val titleEl = block.selectFirst("p.metadata a") ?: block.selectFirst(".title a")
                            val durationEl = block.selectFirst(".metadata .right") ?: block.selectFirst(".duration")
                            
                            val title = titleEl?.text() ?: linkEl?.attr("title") ?: thumbEl?.attr("alt") ?: ""
                            val videoUrl = linkEl?.attr("href")?.let {
                                if (it.startsWith("/")) "https://www.xnxx.com$it" else it
                            } ?: ""
                            val thumbnail = thumbEl?.attr("data-src") ?: thumbEl?.attr("src") ?: ""
                            val duration = durationEl?.text() ?: ""
                            
                            if (title.isNotEmpty() && videoUrl.isNotEmpty()) {
                                results.add(VideoResult(
                                    title = title,
                                    videoUrl = videoUrl,
                                    thumbnailUrl = thumbnail,
                                    duration = duration,
                                    views = "",
                                    channel = "",
                                    source = "XNXX",
                                    siteType = VideoSiteType.GENERIC
                                ))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "XNXX parse error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "XNXX search error: ${e.message}")
            }
            results
        }
    }
    
    /**
     * PornHub Extractor - Uses JSON API
     */
    class PornHubExtractor : SiteExtractor {
        override fun getSiteName() = "PornHub"
        
        override suspend fun search(query: String, limit: Int): List<VideoResult> = withContext(Dispatchers.IO) {
            val results = mutableListOf<VideoResult>()
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://www.pornhub.com/video/search?search=$encodedQuery"
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", DESKTOP_UA)
                    .header("Cookie", "age_verified=1; accessAgeDisclaimerPH=1")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext results
                    val html = response.body?.string() ?: return@withContext results
                    val doc = Jsoup.parse(html)
                    
                    // PornHub video containers
                    doc.select("li.videoBox, div.videoBox, li.pcVideoListItem").take(limit).forEach { box ->
                        try {
                            val linkEl = box.selectFirst("a.linkVideoThumb") ?: box.selectFirst("a[href*='/view_video']")
                            val imgEl = box.selectFirst("img")
                            val titleEl = box.selectFirst("span.title a") ?: box.selectFirst(".title")
                            val durationEl = box.selectFirst("var.duration") ?: box.selectFirst(".duration")
                            val viewsEl = box.selectFirst("span.views var") ?: box.selectFirst(".views")
                            
                            val title = titleEl?.text() ?: linkEl?.attr("title") ?: imgEl?.attr("alt") ?: ""
                            val videoUrl = linkEl?.attr("href")?.let {
                                if (it.startsWith("/")) "https://www.pornhub.com$it" else it
                            } ?: ""
                            val thumbnail = imgEl?.attr("data-thumb_url") 
                                ?: imgEl?.attr("data-src") 
                                ?: imgEl?.attr("src") ?: ""
                            val duration = durationEl?.text() ?: ""
                            val views = viewsEl?.text() ?: ""
                            
                            if (title.isNotEmpty() && videoUrl.isNotEmpty()) {
                                results.add(VideoResult(
                                    title = title,
                                    videoUrl = videoUrl,
                                    thumbnailUrl = thumbnail,
                                    duration = duration,
                                    views = views,
                                    channel = "",
                                    source = "PornHub",
                                    siteType = VideoSiteType.GENERIC
                                ))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "PornHub parse error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "PornHub search error: ${e.message}")
            }
            results
        }
    }
    
    /**
     * xHamster Extractor
     */
    class XHamsterExtractor : SiteExtractor {
        override fun getSiteName() = "xHamster"
        
        override suspend fun search(query: String, limit: Int): List<VideoResult> = withContext(Dispatchers.IO) {
            val results = mutableListOf<VideoResult>()
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://xhamster.com/search/$encodedQuery"
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", DESKTOP_UA)
                    .header("Cookie", "age_verified=1; xh_consent=1")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext results
                    val html = response.body?.string() ?: return@withContext results
                    val doc = Jsoup.parse(html)
                    
                    // Try both old and new layouts
                    val containers = doc.select("div.thumb-list__item, div.video-thumb-info, article.thumb-list__item")
                    
                    containers.take(limit).forEach { item ->
                        try {
                            val linkEl = item.selectFirst("a[href*='/videos/']") ?: item.selectFirst("a")
                            val imgEl = item.selectFirst("img")
                            val titleEl = item.selectFirst("a.video-thumb-info__name") ?: item.selectFirst(".title")
                            val durationEl = item.selectFirst("div.thumb-image-container__duration") ?: item.selectFirst(".duration")
                            val viewsEl = item.selectFirst("span.video-thumb-views") ?: item.selectFirst(".views")
                            
                            val title = titleEl?.text() ?: linkEl?.attr("title") ?: imgEl?.attr("alt") ?: ""
                            val videoUrl = linkEl?.attr("href") ?: ""
                            val thumbnail = imgEl?.attr("src") ?: imgEl?.attr("data-src") ?: ""
                            val duration = durationEl?.text() ?: ""
                            val views = viewsEl?.text() ?: ""
                            
                            if (title.isNotEmpty() && videoUrl.isNotEmpty()) {
                                results.add(VideoResult(
                                    title = title,
                                    videoUrl = videoUrl,
                                    thumbnailUrl = thumbnail,
                                    duration = duration,
                                    views = views,
                                    channel = "",
                                    source = "xHamster",
                                    siteType = VideoSiteType.GENERIC
                                ))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "xHamster parse error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "xHamster search error: ${e.message}")
            }
            results
        }
    }
    
    /**
     * RedTube Extractor
     */
    class RedTubeExtractor : SiteExtractor {
        override fun getSiteName() = "RedTube"
        
        override suspend fun search(query: String, limit: Int): List<VideoResult> = withContext(Dispatchers.IO) {
            val results = mutableListOf<VideoResult>()
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://www.redtube.com/?search=$encodedQuery"
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", DESKTOP_UA)
                    .header("Cookie", "age_verified=1")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext results
                    val html = response.body?.string() ?: return@withContext results
                    val doc = Jsoup.parse(html)
                    
                    doc.select("li.videoBox, div.video-box, li.video_block").take(limit).forEach { box ->
                        try {
                            val linkEl = box.selectFirst("a.videoThumb") ?: box.selectFirst("a[href*='redtube.com']")
                            val imgEl = box.selectFirst("img")
                            val titleEl = box.selectFirst("span.video_title") ?: box.selectFirst(".video-title")
                            val durationEl = box.selectFirst("span.duration") ?: box.selectFirst(".video-duration")
                            val viewsEl = box.selectFirst("span.video_count") ?: box.selectFirst(".views")
                            
                            val title = titleEl?.text() ?: linkEl?.attr("title") ?: ""
                            val videoUrl = linkEl?.attr("href")?.let {
                                if (it.startsWith("/")) "https://www.redtube.com$it" else it
                            } ?: ""
                            val thumbnail = imgEl?.attr("data-thumb_url") 
                                ?: imgEl?.attr("data-src") 
                                ?: imgEl?.attr("src") ?: ""
                            val duration = durationEl?.text() ?: ""
                            val views = viewsEl?.text() ?: ""
                            
                            if (title.isNotEmpty() && videoUrl.isNotEmpty()) {
                                results.add(VideoResult(
                                    title = title,
                                    videoUrl = videoUrl,
                                    thumbnailUrl = thumbnail,
                                    duration = duration,
                                    views = views,
                                    channel = "",
                                    source = "RedTube",
                                    siteType = VideoSiteType.GENERIC
                                ))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "RedTube parse error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "RedTube search error: ${e.message}")
            }
            results
        }
    }
    
    /**
     * YouPorn Extractor
     */
    class YouPornExtractor : SiteExtractor {
        override fun getSiteName() = "YouPorn"
        
        override suspend fun search(query: String, limit: Int): List<VideoResult> = withContext(Dispatchers.IO) {
            val results = mutableListOf<VideoResult>()
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://www.youporn.com/search/?query=$encodedQuery"
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", DESKTOP_UA)
                    .header("Cookie", "age_verified=1")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext results
                    val html = response.body?.string() ?: return@withContext results
                    val doc = Jsoup.parse(html)
                    
                    doc.select("div.video-box, li.video-list-item, div.videoThumb").take(limit).forEach { box ->
                        try {
                            val linkEl = box.selectFirst("a[href*='/watch/']") ?: box.selectFirst("a")
                            val imgEl = box.selectFirst("img")
                            val titleEl = box.selectFirst("div.video-title") ?: box.selectFirst(".title")
                            val durationEl = box.selectFirst("span.video-duration") ?: box.selectFirst(".duration")
                            val viewsEl = box.selectFirst("span.video-views") ?: box.selectFirst(".views")
                            
                            val title = titleEl?.text() ?: linkEl?.attr("title") ?: ""
                            val videoUrl = linkEl?.attr("href")?.let {
                                if (it.startsWith("/")) "https://www.youporn.com$it" else it
                            } ?: ""
                            val thumbnail = imgEl?.attr("data-src") ?: imgEl?.attr("src") ?: ""
                            val duration = durationEl?.text() ?: ""
                            val views = viewsEl?.text() ?: ""
                            
                            if (title.isNotEmpty() && videoUrl.isNotEmpty()) {
                                results.add(VideoResult(
                                    title = title,
                                    videoUrl = videoUrl,
                                    thumbnailUrl = thumbnail,
                                    duration = duration,
                                    views = views,
                                    channel = "",
                                    source = "YouPorn",
                                    siteType = VideoSiteType.GENERIC
                                ))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "YouPorn parse error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "YouPorn search error: ${e.message}")
            }
            results
        }
    }
    
    /**
     * SpankBang Extractor
     */
    class SpankBangExtractor : SiteExtractor {
        override fun getSiteName() = "SpankBang"
        
        override suspend fun search(query: String, limit: Int): List<VideoResult> = withContext(Dispatchers.IO) {
            val results = mutableListOf<VideoResult>()
            try {
                val encodedQuery = URLEncoder.encode(query.replace(" ", "+"), "UTF-8")
                val url = "https://spankbang.com/s/$encodedQuery/"
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", DESKTOP_UA)
                    .header("Cookie", "age_verified=1")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext results
                    val html = response.body?.string() ?: return@withContext results
                    val doc = Jsoup.parse(html)
                    
                    doc.select("div.video-item, div.video-list__item").take(limit).forEach { item ->
                        try {
                            val linkEl = item.selectFirst("a.thumb") ?: item.selectFirst("a")
                            val imgEl = item.selectFirst("img, picture source")
                            val titleEl = item.selectFirst("a.n") ?: item.selectFirst(".name")
                            val durationEl = item.selectFirst("span.l") ?: item.selectFirst(".length")
                            val viewsEl = item.selectFirst("span.v") ?: item.selectFirst(".views")
                            
                            val title = titleEl?.text() ?: linkEl?.attr("title") ?: ""
                            val videoUrl = linkEl?.attr("href")?.let {
                                if (it.startsWith("/")) "https://spankbang.com$it" else it
                            } ?: ""
                            val thumbnail = imgEl?.attr("data-src") ?: imgEl?.attr("src") ?: imgEl?.attr("srcset")?.split(" ")?.firstOrNull() ?: ""
                            val duration = durationEl?.text() ?: ""
                            val views = viewsEl?.text() ?: ""
                            
                            if (title.isNotEmpty() && videoUrl.isNotEmpty()) {
                                results.add(VideoResult(
                                    title = title,
                                    videoUrl = videoUrl,
                                    thumbnailUrl = thumbnail,
                                    duration = duration,
                                    views = views,
                                    channel = "",
                                    source = "SpankBang",
                                    siteType = VideoSiteType.GENERIC
                                ))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "SpankBang parse error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SpankBang search error: ${e.message}")
            }
            results
        }
    }
    
    /**
     * Eporner Extractor
     */
    class EpornerExtractor : SiteExtractor {
        override fun getSiteName() = "Eporner"
        
        override suspend fun search(query: String, limit: Int): List<VideoResult> = withContext(Dispatchers.IO) {
            val results = mutableListOf<VideoResult>()
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://www.eporner.com/search/$encodedQuery/"
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", DESKTOP_UA)
                    .header("Cookie", "age_verified=1")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext results
                    val html = response.body?.string() ?: return@withContext results
                    val doc = Jsoup.parse(html)
                    
                    doc.select("div.mb, div.video-box").take(limit).forEach { box ->
                        try {
                            val linkEl = box.selectFirst("a[href*='/video/']") ?: box.selectFirst("a")
                            val imgEl = box.selectFirst("img")
                            val titleEl = box.selectFirst("p.mbtit a") ?: box.selectFirst(".title")
                            val durationEl = box.selectFirst("span.mbtim") ?: box.selectFirst(".duration")
                            val viewsEl = box.selectFirst("span.mbvie") ?: box.selectFirst(".views")
                            
                            val title = titleEl?.text() ?: linkEl?.attr("title") ?: ""
                            val videoUrl = linkEl?.attr("href")?.let {
                                if (it.startsWith("/")) "https://www.eporner.com$it" else it
                            } ?: ""
                            val thumbnail = imgEl?.attr("data-src") ?: imgEl?.attr("src") ?: ""
                            val duration = durationEl?.text() ?: ""
                            val views = viewsEl?.text() ?: ""
                            
                            if (title.isNotEmpty() && videoUrl.isNotEmpty()) {
                                results.add(VideoResult(
                                    title = title,
                                    videoUrl = videoUrl,
                                    thumbnailUrl = thumbnail,
                                    duration = duration,
                                    views = views,
                                    channel = "",
                                    source = "Eporner",
                                    siteType = VideoSiteType.GENERIC
                                ))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Eporner parse error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Eporner search error: ${e.message}")
            }
            results
        }
    }
    
    /**
     * PornTrex Extractor
     */
    class PornTrexExtractor : SiteExtractor {
        override fun getSiteName() = "Porntrex"
        
        override suspend fun search(query: String, limit: Int): List<VideoResult> = withContext(Dispatchers.IO) {
            val results = mutableListOf<VideoResult>()
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://www.porntrex.com/search/$encodedQuery/"
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", DESKTOP_UA)
                    .header("Cookie", "age_verified=1")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext results
                    val html = response.body?.string() ?: return@withContext results
                    val doc = Jsoup.parse(html)
                    
                    doc.select("div.video-item, div.item.thumb").take(limit).forEach { item ->
                        try {
                            val linkEl = item.selectFirst("a[href*='/video/']") ?: item.selectFirst("a.thumb")
                            val imgEl = item.selectFirst("img")
                            val titleEl = item.selectFirst("a.title") ?: item.selectFirst(".video-title")
                            val durationEl = item.selectFirst("span.time") ?: item.selectFirst(".duration")
                            val viewsEl = item.selectFirst("span.views") ?: item.selectFirst(".views")
                            
                            val title = titleEl?.text() ?: linkEl?.attr("title") ?: ""
                            val videoUrl = linkEl?.attr("href")?.let {
                                if (it.startsWith("/")) "https://www.porntrex.com$it" else it
                            } ?: ""
                            val thumbnail = imgEl?.attr("data-src") ?: imgEl?.attr("src") ?: ""
                            val duration = durationEl?.text() ?: ""
                            val views = viewsEl?.text() ?: ""
                            
                            if (title.isNotEmpty() && videoUrl.isNotEmpty()) {
                                results.add(VideoResult(
                                    title = title,
                                    videoUrl = videoUrl,
                                    thumbnailUrl = thumbnail,
                                    duration = duration,
                                    views = views,
                                    channel = "",
                                    source = "Porntrex",
                                    siteType = VideoSiteType.GENERIC
                                ))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Porntrex parse error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Porntrex search error: ${e.message}")
            }
            results
        }
    }
    
    /**
     * TNAFlix Extractor
     */
    class TNAFlixExtractor : SiteExtractor {
        override fun getSiteName() = "TNAFlix"
        
        override suspend fun search(query: String, limit: Int): List<VideoResult> = withContext(Dispatchers.IO) {
            val results = mutableListOf<VideoResult>()
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://www.tnaflix.com/search.php?what=$encodedQuery"
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", DESKTOP_UA)
                    .header("Cookie", "age_verified=1")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext results
                    val html = response.body?.string() ?: return@withContext results
                    val doc = Jsoup.parse(html)
                    
                    doc.select("div.vidHolder, div.video-item").take(limit).forEach { item ->
                        try {
                            val linkEl = item.selectFirst("a.thumContainer") ?: item.selectFirst("a")
                            val imgEl = item.selectFirst("img")
                            val titleEl = item.selectFirst("a.video-title") ?: item.selectFirst(".title")
                            val durationEl = item.selectFirst("span.videoDuration") ?: item.selectFirst(".duration")
                            
                            val title = titleEl?.text() ?: linkEl?.attr("title") ?: ""
                            val videoUrl = linkEl?.attr("href")?.let {
                                if (it.startsWith("/")) "https://www.tnaflix.com$it" else it
                            } ?: ""
                            val thumbnail = imgEl?.attr("data-src") ?: imgEl?.attr("src") ?: ""
                            val duration = durationEl?.text() ?: ""
                            
                            if (title.isNotEmpty() && videoUrl.isNotEmpty()) {
                                results.add(VideoResult(
                                    title = title,
                                    videoUrl = videoUrl,
                                    thumbnailUrl = thumbnail,
                                    duration = duration,
                                    views = "",
                                    channel = "",
                                    source = "TNAFlix",
                                    siteType = VideoSiteType.GENERIC
                                ))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "TNAFlix parse error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "TNAFlix search error: ${e.message}")
            }
            results
        }
    }
    
    /**
     * Tube8 Extractor
     */
    class Tube8Extractor : SiteExtractor {
        override fun getSiteName() = "Tube8"
        
        override suspend fun search(query: String, limit: Int): List<VideoResult> = withContext(Dispatchers.IO) {
            val results = mutableListOf<VideoResult>()
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://www.tube8.com/searches?q=$encodedQuery"
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", DESKTOP_UA)
                    .header("Cookie", "age_verified=1")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext results
                    val html = response.body?.string() ?: return@withContext results
                    val doc = Jsoup.parse(html)
                    
                    doc.select("li.videoBox, div.video-box").take(limit).forEach { box ->
                        try {
                            val linkEl = box.selectFirst("a.videoThumb") ?: box.selectFirst("a")
                            val imgEl = box.selectFirst("img")
                            val titleEl = box.selectFirst("span.title") ?: box.selectFirst(".title")
                            val durationEl = box.selectFirst("var.duration") ?: box.selectFirst(".duration")
                            
                            val title = titleEl?.text() ?: linkEl?.attr("title") ?: ""
                            val videoUrl = linkEl?.attr("href")?.let {
                                if (it.startsWith("/")) "https://www.tube8.com$it" else it
                            } ?: ""
                            val thumbnail = imgEl?.attr("data-thumb_url") ?: imgEl?.attr("data-src") ?: imgEl?.attr("src") ?: ""
                            val duration = durationEl?.text() ?: ""
                            
                            if (title.isNotEmpty() && videoUrl.isNotEmpty()) {
                                results.add(VideoResult(
                                    title = title,
                                    videoUrl = videoUrl,
                                    thumbnailUrl = thumbnail,
                                    duration = duration,
                                    views = "",
                                    channel = "",
                                    source = "Tube8",
                                    siteType = VideoSiteType.GENERIC
                                ))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Tube8 parse error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Tube8 search error: ${e.message}")
            }
            results
        }
    }
    
    /**
     * Beeg Extractor - Uses API
     */
    class BeegExtractor : SiteExtractor {
        override fun getSiteName() = "Beeg"
        
        override suspend fun search(query: String, limit: Int): List<VideoResult> = withContext(Dispatchers.IO) {
            val results = mutableListOf<VideoResult>()
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                // Beeg has a search API
                val url = "https://beeg.com/search?q=$encodedQuery"
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", DESKTOP_UA)
                    .header("Cookie", "age_verified=1")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext results
                    val html = response.body?.string() ?: return@withContext results
                    val doc = Jsoup.parse(html)
                    
                    doc.select("article, div.video-item, div.thumb").take(limit).forEach { item ->
                        try {
                            val linkEl = item.selectFirst("a[href*='/video']") ?: item.selectFirst("a")
                            val imgEl = item.selectFirst("img")
                            val titleEl = item.selectFirst("h2") ?: item.selectFirst(".title")
                            val durationEl = item.selectFirst("time") ?: item.selectFirst(".duration")
                            
                            val title = titleEl?.text() ?: linkEl?.attr("title") ?: ""
                            val videoUrl = linkEl?.attr("href")?.let {
                                if (it.startsWith("/")) "https://beeg.com$it" else it
                            } ?: ""
                            val thumbnail = imgEl?.attr("data-src") ?: imgEl?.attr("src") ?: ""
                            val duration = durationEl?.text() ?: ""
                            
                            if (title.isNotEmpty() && videoUrl.isNotEmpty()) {
                                results.add(VideoResult(
                                    title = title,
                                    videoUrl = videoUrl,
                                    thumbnailUrl = thumbnail,
                                    duration = duration,
                                    views = "",
                                    channel = "",
                                    source = "Beeg",
                                    siteType = VideoSiteType.GENERIC
                                ))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Beeg parse error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Beeg search error: ${e.message}")
            }
            results
        }
    }
    
    /**
     * Thumbzilla Extractor
     */
    class ThumbzillaExtractor : SiteExtractor {
        override fun getSiteName() = "Thumbzilla"
        
        override suspend fun search(query: String, limit: Int): List<VideoResult> = withContext(Dispatchers.IO) {
            val results = mutableListOf<VideoResult>()
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://www.thumbzilla.com/video/search?search=$encodedQuery"
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", DESKTOP_UA)
                    .header("Cookie", "age_verified=1")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext results
                    val html = response.body?.string() ?: return@withContext results
                    val doc = Jsoup.parse(html)
                    
                    doc.select("li.videoBox, div.video-item").take(limit).forEach { item ->
                        try {
                            val linkEl = item.selectFirst("a.phimage") ?: item.selectFirst("a")
                            val imgEl = item.selectFirst("img")
                            val titleEl = item.selectFirst("span.title") ?: item.selectFirst(".title")
                            val durationEl = item.selectFirst("var.duration") ?: item.selectFirst(".duration")
                            
                            val title = titleEl?.text() ?: linkEl?.attr("title") ?: ""
                            val videoUrl = linkEl?.attr("href")?.let {
                                if (it.startsWith("/")) "https://www.thumbzilla.com$it" else it
                            } ?: ""
                            val thumbnail = imgEl?.attr("data-thumb_url") ?: imgEl?.attr("data-src") ?: imgEl?.attr("src") ?: ""
                            val duration = durationEl?.text() ?: ""
                            
                            if (title.isNotEmpty() && videoUrl.isNotEmpty()) {
                                results.add(VideoResult(
                                    title = title,
                                    videoUrl = videoUrl,
                                    thumbnailUrl = thumbnail,
                                    duration = duration,
                                    views = "",
                                    channel = "",
                                    source = "Thumbzilla",
                                    siteType = VideoSiteType.GENERIC
                                ))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Thumbzilla parse error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Thumbzilla search error: ${e.message}")
            }
            results
        }
    }
    
    // Additional extractors using similar patterns
    class PornoneExtractor : SiteExtractor {
        override fun getSiteName() = "Pornone"
        override suspend fun search(query: String, limit: Int) = genericSearch("https://www.pornone.com", "/search?q={query}", query, limit, "Pornone")
    }
    
    class HClipsExtractor : SiteExtractor {
        override fun getSiteName() = "HClips"
        override suspend fun search(query: String, limit: Int) = genericSearch("https://www.hclips.com", "/search/{query}", query, limit, "HClips")
    }
    
    class TxxxExtractor : SiteExtractor {
        override fun getSiteName() = "Txxx"
        override suspend fun search(query: String, limit: Int) = genericSearch("https://www.txxx.com", "/search/?q={query}", query, limit, "Txxx")
    }
    
    class DrTuberExtractor : SiteExtractor {
        override fun getSiteName() = "DrTuber"
        override suspend fun search(query: String, limit: Int) = genericSearch("https://www.drtuber.com", "/search/{query}", query, limit, "DrTuber")
    }
    
    class SunPornoExtractor : SiteExtractor {
        override fun getSiteName() = "Sunporno"
        override suspend fun search(query: String, limit: Int) = genericSearch("https://www.sunporno.com", "/search/{query}/", query, limit, "Sunporno")
    }
    
    class AnyPornExtractor : SiteExtractor {
        override fun getSiteName() = "Anyporn"
        override suspend fun search(query: String, limit: Int) = genericSearch("https://www.anyporn.com", "/search/?q={query}", query, limit, "Anyporn")
    }
    
    class UporniaExtractor : SiteExtractor {
        override fun getSiteName() = "Upornia"
        override suspend fun search(query: String, limit: Int) = genericSearch("https://www.upornia.com", "/search/{query}/", query, limit, "Upornia")
    }
    
    class FuqExtractor : SiteExtractor {
        override fun getSiteName() = "Fuq"
        override suspend fun search(query: String, limit: Int) = genericSearch("https://www.fuq.com", "/search/{query}/", query, limit, "Fuq")
    }
    
    class GotPornExtractor : SiteExtractor {
        override fun getSiteName() = "GotPorn"
        override suspend fun search(query: String, limit: Int) = genericSearch("https://www.gotporn.com", "/results?search={query}", query, limit, "GotPorn")
    }
    
    class FourTubeExtractor : SiteExtractor {
        override fun getSiteName() = "4tube"
        override suspend fun search(query: String, limit: Int) = genericSearch("https://www.4tube.com", "/search?q={query}", query, limit, "4tube")
    }
    
    /**
     * Generic adult site extractor for sites with similar layouts
     */
    class GenericAdultExtractor : SiteExtractor {
        override fun getSiteName() = "Generic"
        override suspend fun search(query: String, limit: Int): List<VideoResult> = emptyList()
    }
    
    /**
     * Generic search function for similar site layouts
     */
    private suspend fun genericSearch(
        baseUrl: String,
        searchPath: String,
        query: String,
        limit: Int,
        siteName: String
    ): List<VideoResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoResult>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = baseUrl + searchPath.replace("{query}", encodedQuery)
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DESKTOP_UA)
                .header("Cookie", "age_verified=1")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext results
                val html = response.body?.string() ?: return@withContext results
                val doc = Jsoup.parse(html)
                
                // Try multiple common container patterns
                val containers = doc.select(
                    "div.video-item, div.video-box, div.thumb-block, " +
                    "li.video-item, article.video, div.item.thumb, " +
                    "div.thumb, div.vidHolder, div.video-wrapper"
                )
                
                containers.take(limit).forEach { item ->
                    try {
                        // Try multiple link patterns
                        val linkEl = item.selectFirst("a[href*='/video']") 
                            ?: item.selectFirst("a[href*='/watch']") 
                            ?: item.selectFirst("a.thumb")
                            ?: item.selectFirst("a")
                        
                        val imgEl = item.selectFirst("img")
                        
                        // Try multiple title patterns
                        val titleEl = item.selectFirst(".title a") 
                            ?: item.selectFirst("a.title")
                            ?: item.selectFirst(".video-title")
                            ?: item.selectFirst("h3 a")
                            ?: item.selectFirst("h4 a")
                        
                        val durationEl = item.selectFirst(".duration") 
                            ?: item.selectFirst(".time") 
                            ?: item.selectFirst("span.length")
                        
                        val viewsEl = item.selectFirst(".views") 
                            ?: item.selectFirst(".video-views")
                        
                        val title = titleEl?.text() ?: linkEl?.attr("title") ?: imgEl?.attr("alt") ?: ""
                        val videoUrl = linkEl?.attr("href")?.let {
                            if (it.startsWith("http")) it else "$baseUrl$it"
                        } ?: ""
                        val thumbnail = imgEl?.attr("data-src") 
                            ?: imgEl?.attr("data-original") 
                            ?: imgEl?.attr("src") ?: ""
                        val duration = durationEl?.text() ?: ""
                        val views = viewsEl?.text() ?: ""
                        
                        if (title.isNotEmpty() && videoUrl.isNotEmpty()) {
                            results.add(VideoResult(
                                title = title,
                                videoUrl = videoUrl,
                                thumbnailUrl = if (thumbnail.startsWith("http")) thumbnail else "$baseUrl$thumbnail",
                                duration = duration,
                                views = views,
                                channel = "",
                                source = siteName,
                                siteType = VideoSiteType.GENERIC
                            ))
                        }
                    } catch (e: Exception) {
                        // Skip failed items
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "$siteName search error: ${e.message}")
        }
        results
    }
}
