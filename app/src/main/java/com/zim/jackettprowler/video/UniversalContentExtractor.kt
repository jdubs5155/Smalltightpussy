package com.zim.jackettprowler.video

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Universal Content Extractor
 * 
 * Extracts video/content information from ANY webpage using multiple strategies:
 * 1. Meta tag extraction (og:video, twitter:player, schema.org)
 * 2. JSON-LD structured data
 * 3. Embedded player detection
 * 4. Direct video/audio source detection
 * 5. API response parsing
 * 6. Pattern-based URL extraction
 * 
 * Inspired by yt-dlp extractors but optimized for mobile/Android
 */
class UniversalContentExtractor {
    
    companion object {
        private const val TAG = "UniversalContentExtractor"
        
        // Video URL patterns
        private val VIDEO_PATTERNS = listOf(
            Pattern.compile("https?://[^\"'\\s]+\\.(?:mp4|m4v|webm|mkv|avi|mov|flv|wmv)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("https?://[^\"'\\s]+\\.(?:m3u8|mpd)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("sources?\\s*[=:]\\s*\\[?\\s*[{\"']([^\"']+\\.(?:mp4|m3u8))[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("file\\s*[=:]\\s*[\"']([^\"']+\\.(?:mp4|m3u8))[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("src\\s*[=:]\\s*[\"']([^\"']+\\.(?:mp4|m3u8))[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("video_url\\s*[=:]\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("stream_url\\s*[=:]\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("contentUrl\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
        )
        
        // Thumbnail patterns
        private val THUMBNAIL_PATTERNS = listOf(
            Pattern.compile("poster\\s*[=:]\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("thumbnail\\s*[=:]\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("image\\s*[=:]\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("preview\\s*[=:]\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        )
        
        // Duration patterns (various formats)
        private val DURATION_PATTERNS = listOf(
            Pattern.compile("(?:duration|length)[\"']?\\s*[=:]\\s*[\"']?(\\d+)[\"']?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(\\d{1,2}):(\\d{2}):(\\d{2})"),
            Pattern.compile("(\\d{1,2}):(\\d{2})"),
            Pattern.compile("PT(\\d+)H?(\\d*)M?(\\d*)S?", Pattern.CASE_INSENSITIVE)
        )
        
        // View count patterns
        private val VIEW_PATTERNS = listOf(
            Pattern.compile("(?:views?|plays?)[\"']?\\s*[=:]\\s*[\"']?([\\d,]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([\\d,]+)\\s*(?:views?|plays?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("interactionCount[\"']?\\s*:\\s*[\"']?([\\d,]+)", Pattern.CASE_INSENSITIVE)
        )
    }
    
    data class ExtractedContent(
        val title: String,
        val description: String,
        val videoUrls: List<VideoSource>,
        val thumbnailUrl: String,
        val duration: String,
        val views: Long,
        val uploadDate: String,
        val channel: String,
        val channelUrl: String,
        val categories: List<String>,
        val tags: List<String>,
        val embedUrl: String,
        val pageUrl: String,
        val isLive: Boolean,
        val rawMetadata: Map<String, String>
    )
    
    data class VideoSource(
        val url: String,
        val format: String,
        val quality: String,
        val filesize: Long
    )
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            chain.proceed(request)
        }
        .build()
    
    /**
     * Extract content from any URL
     */
    suspend fun extract(url: String): ExtractedContent? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch: ${response.code}")
                    return@withContext null
                }
                
                val html = response.body?.string() ?: return@withContext null
                val doc = Jsoup.parse(html, url)
                
                extractFromDocument(doc, html, url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed: ${e.message}")
            null
        }
    }
    
    /**
     * Extract content from pre-fetched document
     */
    fun extractFromDocument(doc: Document, html: String, pageUrl: String): ExtractedContent {
        val rawMetadata = mutableMapOf<String, String>()
        
        // 1. Extract from meta tags
        val metaData = extractMetaTags(doc)
        rawMetadata.putAll(metaData)
        
        // 2. Extract from JSON-LD
        val jsonLd = extractJsonLd(doc)
        rawMetadata.putAll(jsonLd)
        
        // 3. Extract video sources
        val videoUrls = extractVideoSources(doc, html, pageUrl)
        
        // 4. Extract from patterns in HTML/JS
        val patternData = extractFromPatterns(html)
        
        // Combine all sources
        val title = metaData["og:title"] 
            ?: jsonLd["name"] 
            ?: doc.title() 
            ?: ""
        
        val description = metaData["og:description"]
            ?: jsonLd["description"]
            ?: doc.select("meta[name='description']").attr("content")
            ?: ""
        
        val thumbnail = metaData["og:image"]
            ?: jsonLd["thumbnailUrl"]
            ?: patternData["thumbnail"]
            ?: doc.select("video[poster]").attr("poster")
            ?: ""
        
        val duration = jsonLd["duration"]
            ?: patternData["duration"]
            ?: extractDuration(html)
            ?: ""
        
        val views = parseViewCount(
            jsonLd["interactionCount"]
                ?: patternData["views"]
                ?: ""
        )
        
        val uploadDate = jsonLd["uploadDate"]
            ?: jsonLd["datePublished"]
            ?: metaData["article:published_time"]
            ?: ""
        
        val channel = jsonLd["author"]
            ?: doc.select("[class*='channel'], [class*='uploader'], [class*='author']").firstOrNull()?.text()
            ?: ""
        
        val channelUrl = jsonLd["authorUrl"]
            ?: doc.select("[class*='channel'] a, [class*='uploader'] a").attr("href")
            ?: ""
        
        val categories = extractCategories(doc, jsonLd)
        val tags = extractTags(doc, jsonLd)
        
        val embedUrl = metaData["og:video:url"]
            ?: metaData["twitter:player"]
            ?: jsonLd["embedUrl"]
            ?: ""
        
        val isLive = html.contains("\"isLive\":true", ignoreCase = true) ||
                html.contains("\"isLiveNow\":true", ignoreCase = true) ||
                doc.select("[class*='live']").isNotEmpty()
        
        return ExtractedContent(
            title = title,
            description = description,
            videoUrls = videoUrls,
            thumbnailUrl = thumbnail,
            duration = duration,
            views = views,
            uploadDate = uploadDate,
            channel = channel,
            channelUrl = channelUrl,
            categories = categories,
            tags = tags,
            embedUrl = embedUrl,
            pageUrl = pageUrl,
            isLive = isLive,
            rawMetadata = rawMetadata
        )
    }
    
    private fun extractMetaTags(doc: Document): Map<String, String> {
        val meta = mutableMapOf<String, String>()
        
        // Open Graph tags
        doc.select("meta[property^='og:']").forEach { el ->
            val property = el.attr("property")
            val content = el.attr("content")
            if (content.isNotBlank()) {
                meta[property] = content
            }
        }
        
        // Twitter cards
        doc.select("meta[name^='twitter:']").forEach { el ->
            val name = el.attr("name")
            val content = el.attr("content")
            if (content.isNotBlank()) {
                meta[name] = content
            }
        }
        
        // Standard meta tags
        doc.select("meta[name]").forEach { el ->
            val name = el.attr("name")
            val content = el.attr("content")
            if (content.isNotBlank() && !meta.containsKey(name)) {
                meta[name] = content
            }
        }
        
        return meta
    }
    
    private fun extractJsonLd(doc: Document): Map<String, String> {
        val data = mutableMapOf<String, String>()
        
        doc.select("script[type='application/ld+json']").forEach { script ->
            try {
                val json = script.html().trim()
                if (json.startsWith("{")) {
                    val obj = JSONObject(json)
                    extractFromJsonObject(obj, data)
                } else if (json.startsWith("[")) {
                    val arr = JSONArray(json)
                    for (i in 0 until arr.length()) {
                        extractFromJsonObject(arr.getJSONObject(i), data)
                    }
                }
            } catch (e: Exception) {
                // Invalid JSON, skip
            }
        }
        
        return data
    }
    
    private fun extractFromJsonObject(obj: JSONObject, data: MutableMap<String, String>) {
        // VideoObject or other content types
        if (obj.optString("@type").contains("Video", ignoreCase = true) ||
            obj.optString("@type").contains("Movie", ignoreCase = true) ||
            obj.optString("@type").contains("MediaObject", ignoreCase = true)) {
            
            obj.optString("name").takeIf { it.isNotBlank() }?.let { data["name"] = it }
            obj.optString("description").takeIf { it.isNotBlank() }?.let { data["description"] = it }
            obj.optString("thumbnailUrl").takeIf { it.isNotBlank() }?.let { data["thumbnailUrl"] = it }
            obj.optString("contentUrl").takeIf { it.isNotBlank() }?.let { data["contentUrl"] = it }
            obj.optString("embedUrl").takeIf { it.isNotBlank() }?.let { data["embedUrl"] = it }
            obj.optString("duration").takeIf { it.isNotBlank() }?.let { data["duration"] = it }
            obj.optString("uploadDate").takeIf { it.isNotBlank() }?.let { data["uploadDate"] = it }
            obj.optString("datePublished").takeIf { it.isNotBlank() }?.let { data["datePublished"] = it }
            
            // Interaction statistics
            obj.optJSONObject("interactionStatistic")?.let { stats ->
                stats.optString("userInteractionCount").takeIf { it.isNotBlank() }?.let { data["interactionCount"] = it }
            }
            
            // Try array format
            obj.optJSONArray("interactionStatistic")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val stat = arr.optJSONObject(i)
                    if (stat?.optString("interactionType")?.contains("Watch", ignoreCase = true) == true) {
                        stat.optString("userInteractionCount")?.let { data["interactionCount"] = it }
                    }
                }
            }
            
            // Author
            obj.optJSONObject("author")?.let { author ->
                author.optString("name").takeIf { it.isNotBlank() }?.let { data["author"] = it }
                author.optString("url").takeIf { it.isNotBlank() }?.let { data["authorUrl"] = it }
            }
            obj.optString("author").takeIf { it.isNotBlank() }?.let { data["author"] = it }
            
            // Thumbnail array
            obj.optJSONArray("thumbnail")?.let { arr ->
                arr.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }?.let { data["thumbnailUrl"] = it }
            }
        }
        
        // Handle @graph structure
        obj.optJSONArray("@graph")?.let { graph ->
            for (i in 0 until graph.length()) {
                extractFromJsonObject(graph.getJSONObject(i), data)
            }
        }
    }
    
    private fun extractVideoSources(doc: Document, html: String, baseUrl: String): List<VideoSource> {
        val sources = mutableListOf<VideoSource>()
        
        // From <video> tags
        doc.select("video source, video[src]").forEach { el ->
            val src = el.attr("src").takeIf { it.isNotBlank() } ?: el.attr("data-src")
            if (src.isNotBlank()) {
                val type = el.attr("type").ifBlank { detectFormat(src) }
                val quality = el.attr("label").ifBlank { el.attr("data-quality") }
                sources.add(VideoSource(
                    url = resolveUrl(src, baseUrl),
                    format = type,
                    quality = quality,
                    filesize = 0
                ))
            }
        }
        
        // From patterns in HTML/JS
        for (pattern in VIDEO_PATTERNS) {
            val matcher = pattern.matcher(html)
            while (matcher.find()) {
                val url = matcher.group(1) ?: matcher.group(0)
                if (url.isNotBlank() && url.startsWith("http")) {
                    val cleanUrl = url.replace("\\", "").replace("\"", "").replace("'", "")
                    if (sources.none { it.url == cleanUrl }) {
                        sources.add(VideoSource(
                            url = cleanUrl,
                            format = detectFormat(cleanUrl),
                            quality = detectQuality(cleanUrl),
                            filesize = 0
                        ))
                    }
                }
            }
        }
        
        return sources.distinctBy { it.url }
    }
    
    private fun extractFromPatterns(html: String): Map<String, String> {
        val data = mutableMapOf<String, String>()
        
        // Duration
        for (pattern in DURATION_PATTERNS) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                data["duration"] = matcher.group(0) ?: ""
                break
            }
        }
        
        // Views
        for (pattern in VIEW_PATTERNS) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                data["views"] = matcher.group(1) ?: ""
                break
            }
        }
        
        // Thumbnail
        for (pattern in THUMBNAIL_PATTERNS) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                data["thumbnail"] = matcher.group(1) ?: ""
                break
            }
        }
        
        return data
    }
    
    private fun extractDuration(html: String): String? {
        // PT1H23M45S format
        val iso8601Pattern = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")
        val iso8601Matcher = iso8601Pattern.matcher(html)
        if (iso8601Matcher.find()) {
            val hours = iso8601Matcher.group(1)?.toIntOrNull() ?: 0
            val minutes = iso8601Matcher.group(2)?.toIntOrNull() ?: 0
            val seconds = iso8601Matcher.group(3)?.toIntOrNull() ?: 0
            
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }
        
        // Seconds only
        val secondsPattern = Pattern.compile("(?:duration|length)[\"']?\\s*[=:]\\s*[\"']?(\\d+)[\"']?", Pattern.CASE_INSENSITIVE)
        val secondsMatcher = secondsPattern.matcher(html)
        if (secondsMatcher.find()) {
            val totalSeconds = secondsMatcher.group(1)?.toIntOrNull() ?: return null
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }
        
        return null
    }
    
    private fun parseViewCount(viewString: String): Long {
        if (viewString.isBlank()) return 0
        
        return try {
            viewString.replace(",", "")
                .replace(".", "")
                .replace(" ", "")
                .filter { it.isDigit() }
                .toLongOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    private fun extractCategories(doc: Document, jsonLd: Map<String, String>): List<String> {
        val categories = mutableListOf<String>()
        
        // From JSON-LD
        jsonLd["genre"]?.let { categories.add(it) }
        
        // From page structure
        doc.select("[class*='category'] a, [class*='genre'] a, a[rel='category tag']").forEach {
            val text = it.text().trim()
            if (text.isNotBlank() && text.length < 50) {
                categories.add(text)
            }
        }
        
        // From breadcrumbs
        doc.select("nav.breadcrumb a, .breadcrumbs a, [aria-label='breadcrumb'] a").forEach {
            val text = it.text().trim()
            if (text.isNotBlank() && text.lowercase() !in listOf("home", "videos", "all")) {
                categories.add(text)
            }
        }
        
        return categories.distinct().take(5)
    }
    
    private fun extractTags(doc: Document, jsonLd: Map<String, String>): List<String> {
        val tags = mutableListOf<String>()
        
        // From meta keywords
        doc.select("meta[name='keywords']").attr("content")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length < 30 }
            .forEach { tags.add(it) }
        
        // From page structure
        doc.select("[class*='tag'] a, a[rel='tag'], .tags a, .video-tags a").forEach {
            val text = it.text().trim()
            if (text.isNotBlank() && text.length < 30) {
                tags.add(text)
            }
        }
        
        return tags.distinct().take(20)
    }
    
    private fun resolveUrl(url: String, baseUrl: String): String {
        return when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> {
                val uri = URI(baseUrl)
                "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}$url"
            }
            else -> "$baseUrl/$url"
        }
    }
    
    private fun detectFormat(url: String): String {
        return when {
            url.contains(".m3u8") -> "hls"
            url.contains(".mpd") -> "dash"
            url.contains(".mp4") -> "mp4"
            url.contains(".webm") -> "webm"
            url.contains(".mkv") -> "mkv"
            url.contains(".flv") -> "flv"
            else -> "unknown"
        }
    }
    
    private fun detectQuality(url: String): String {
        val urlLower = url.lowercase()
        return when {
            urlLower.contains("2160") || urlLower.contains("4k") -> "2160p"
            urlLower.contains("1440") || urlLower.contains("2k") -> "1440p"
            urlLower.contains("1080") || urlLower.contains("fhd") -> "1080p"
            urlLower.contains("720") || urlLower.contains("hd") -> "720p"
            urlLower.contains("480") || urlLower.contains("sd") -> "480p"
            urlLower.contains("360") -> "360p"
            urlLower.contains("240") -> "240p"
            else -> "unknown"
        }
    }
    
    /**
     * Helper to extract video ID from various URL patterns
     */
    fun extractVideoId(url: String, patterns: List<Pattern>): String? {
        for (pattern in patterns) {
            val matcher = pattern.matcher(url)
            if (matcher.find() && matcher.groupCount() > 0) {
                return matcher.group(1)
            }
        }
        return null
    }
}
