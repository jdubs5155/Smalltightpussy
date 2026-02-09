package com.aggregatorx.app.engine.media

import android.content.Context
import android.os.Environment
import com.aggregatorx.app.engine.scraper.HeadlessBrowserHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AggravatedX Enhanced Video Extraction Engine
 * 
 * Features:
 * - Direct video link extraction (mp4, webm, etc.)
 * - HLS streams (.m3u8)
 * - DASH streams (.mpd)
 * - Embedded players (YouTube, Vimeo, etc.)
 * - Custom video players
 * - Headless browser fallback with auto-click ad bypass
 * - Shadow DOM traversal
 * - Auto-selects highest quality available
 * - Intelligent fallback chain
 */
@Singleton
class VideoExtractorEngine @Inject constructor() {
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    companion object {
        private const val USER_AGENT = 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        
        // Video file extensions
        private val VIDEO_EXTENSIONS = listOf(
            ".mp4", ".webm", ".mkv", ".avi", ".mov", ".m4v",
            ".flv", ".wmv", ".3gp", ".ts", ".m3u8", ".mpd"
        )
        
        // Quality preferences (highest first)
        private val QUALITY_ORDER = listOf(
            "2160p", "4k", "1080p", "720p", "480p", "360p", "240p"
        )
        
        // Quality keywords with scores
        private val QUALITY_SCORES = mapOf(
            "4k" to 100, "2160" to 100, "2160p" to 100,
            "1080" to 90, "1080p" to 90, "fullhd" to 90, "full hd" to 90,
            "720" to 70, "720p" to 70, "hd" to 70,
            "480" to 50, "480p" to 50, "sd" to 50,
            "360" to 30, "360p" to 30,
            "240" to 20, "240p" to 20
        )
    }
    
    /**
     * Smart video preview extraction - optimized for inline preview playback
     * Tries fast methods first, falls back to headless browser with auto-click if needed
     * Returns just the URL string for preview, or null if extraction fails
     */
    suspend fun extractVideoUrlForPreview(pageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            // Quick check: Is this a direct video URL already?
            if (VIDEO_EXTENSIONS.any { pageUrl.endsWith(it, ignoreCase = true) }) {
                return@withContext pageUrl
            }
            
            // Try fast HTML extraction first (no headless browser needed)
            val fastResult = extractVideoUrlFast(pageUrl)
            if (fastResult != null) {
                return@withContext fastResult
            }
            
            // Fall back to full extraction with headless browser + auto-click
            val fullResult = extractVideoUrl(pageUrl)
            if (fullResult.success && fullResult.videoUrl != null) {
                return@withContext fullResult.videoUrl
            }
            
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Fast video extraction without headless browser
     * Used for sites that don't require JavaScript rendering
     */
    private suspend fun extractVideoUrlFast(pageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val document = Jsoup.connect(pageUrl)
                .userAgent(USER_AGENT)
                .timeout(8000)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .get()
            
            // Try quick extraction methods
            val allVideos = mutableListOf<VideoUrlInfo>()
            
            extractFromVideoTag(document, pageUrl)?.let { allVideos.add(it) }
            extractFromSourceTag(document, pageUrl)?.let { allVideos.add(it) }
            extractFromScripts(document, pageUrl)?.let { allVideos.add(it) }
            extractFromDataAttributes(document, pageUrl)?.let { allVideos.add(it) }
            
            if (allVideos.isNotEmpty()) {
                return@withContext selectHighestQuality(allVideos).url
            }
            
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Extract video URL from a content page - tries multiple methods
     * Auto-selects the highest quality available
     * Uses headless browser with auto-click ad bypass for JS-heavy sites
     */
    suspend fun extractVideoUrl(pageUrl: String): VideoExtractionResult = withContext(Dispatchers.IO) {
        try {
            // First try standard HTML parsing
            val document = Jsoup.connect(pageUrl)
                .userAgent(USER_AGENT)
                .timeout(15000)
                .followRedirects(true)
                .get()
            
            // Try multiple extraction methods and collect all videos
            val allVideos = mutableListOf<VideoUrlInfo>()
            
            extractFromVideoTag(document, pageUrl)?.let { allVideos.add(it) }
            extractFromSourceTag(document, pageUrl)?.let { allVideos.add(it) }
            extractFromIframe(document, pageUrl)?.let { allVideos.add(it) }
            extractFromScripts(document, pageUrl)?.let { allVideos.add(it) }
            extractFromDataAttributes(document, pageUrl)?.let { allVideos.add(it) }
            extractFromJsonLd(document, pageUrl)?.let { allVideos.add(it) }
            
            // If standard parsing found videos, select best quality
            if (allVideos.isNotEmpty()) {
                val bestVideo = selectHighestQuality(allVideos)
                return@withContext VideoExtractionResult(
                    success = true,
                    videoUrl = bestVideo.url,
                    quality = bestVideo.quality,
                    format = bestVideo.format,
                    isStream = bestVideo.isStream
                )
            }
            
            // Fallback to headless browser with auto-click ad bypass
            val headlessResult = extractWithHeadlessBrowser(pageUrl)
            if (headlessResult != null) {
                return@withContext headlessResult
            }
            
            VideoExtractionResult(
                success = false,
                error = "Could not extract video URL from any source"
            )
        } catch (e: Exception) {
            // Try headless browser as last resort
            try {
                val headlessResult = extractWithHeadlessBrowser(pageUrl)
                if (headlessResult != null) {
                    return@withContext headlessResult
                }
            } catch (_: Exception) {}
            
            VideoExtractionResult(
                success = false,
                error = e.message ?: "Extraction failed"
            )
        }
    }
    
    /**
     * Extract video using headless browser with auto-click ad bypass
     * This handles JavaScript-heavy sites and automatically clicks through ads/popups
     */
    private suspend fun extractWithHeadlessBrowser(pageUrl: String): VideoExtractionResult? = withContext(Dispatchers.IO) {
        try {
            // Use HeadlessBrowserHelper with shadow DOM support and ad skipper
            val pageContent = HeadlessBrowserHelper.fetchPageContentWithShadowAndAdSkip(
                url = pageUrl,
                waitSelector = "video, source, [data-video-url], iframe[src*='player']",
                timeout = 20000
            )
            
            if (pageContent.isNullOrEmpty()) {
                return@withContext null
            }
            
            val document = Jsoup.parse(pageContent, pageUrl)
            val allVideos = mutableListOf<VideoUrlInfo>()
            
            // Extract from the JS-rendered content
            extractFromVideoTag(document, pageUrl)?.let { allVideos.add(it) }
            extractFromSourceTag(document, pageUrl)?.let { allVideos.add(it) }
            extractFromScripts(document, pageUrl)?.let { allVideos.add(it) }
            extractFromDataAttributes(document, pageUrl)?.let { allVideos.add(it) }
            
            // Also try to extract directly from headless browser video detection
            val headlessBrowserVideos = HeadlessBrowserHelper.extractVideoUrls(pageUrl)
            headlessBrowserVideos.forEach { url ->
                allVideos.add(VideoUrlInfo(
                    url = url,
                    quality = detectQuality(url),
                    format = detectFormat(url),
                    isStream = url.contains(".m3u8") || url.contains(".mpd")
                ))
            }
            
            if (allVideos.isEmpty()) {
                return@withContext null
            }
            
            val bestVideo = selectHighestQuality(allVideos)
            VideoExtractionResult(
                success = true,
                videoUrl = bestVideo.url,
                quality = bestVideo.quality,
                format = bestVideo.format,
                isStream = bestVideo.isStream
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Select highest quality video from available options
     */
    private fun selectHighestQuality(videos: List<VideoUrlInfo>): VideoUrlInfo {
        if (videos.isEmpty()) throw IllegalArgumentException("No videos to select from")
        if (videos.size == 1) return videos.first()
        
        return videos.maxByOrNull { video ->
            val qualityScore = QUALITY_SCORES.entries
                .filter { video.quality.lowercase().contains(it.key) || video.url.lowercase().contains(it.key) }
                .maxOfOrNull { it.value } ?: 0
            
            // Prefer non-stream formats for downloads
            val formatBonus = when {
                video.format == "mp4" -> 10
                video.format == "webm" -> 5
                video.isStream -> -5
                else -> 0
            }
            
            qualityScore + formatBonus
        } ?: videos.first()
    }
    
    /**
     * Extract from <video> tag
     */
    private fun extractFromVideoTag(document: Document, baseUrl: String): VideoUrlInfo? {
        val video = document.select("video").firstOrNull() ?: return null
        
        val src = video.attr("src").takeIf { it.isNotEmpty() }
            ?: video.attr("data-src").takeIf { it.isNotEmpty() }
        
        if (src != null) {
            return VideoUrlInfo(
                url = normalizeUrl(src, baseUrl),
                quality = detectQuality(src),
                format = detectFormat(src),
                isStream = src.contains(".m3u8") || src.contains(".mpd")
            )
        }
        
        return null
    }
    
    /**
     * Extract from <source> tags
     */
    private fun extractFromSourceTag(document: Document, baseUrl: String): VideoUrlInfo? {
        val sources = document.select("video source, source[type*='video']")
        // Always select the highest quality available
        val sortedSources = sources.sortedWith(compareBy({
            val src = it.attr("src")
            val label = it.attr("label") ?: it.attr("data-label") ?: ""
            QUALITY_ORDER.indexOfFirst { q -> src.contains(q, ignoreCase = true) || label.contains(q, ignoreCase = true) }
        }, {
            // Prefer mp4 over others if quality is equal
            val src = it.attr("src")
            if (src.endsWith(".mp4")) 0 else 1
        }))
        sortedSources.firstOrNull { it.attr("src").isNotEmpty() }?.let { source ->
            val src = source.attr("src")
            return VideoUrlInfo(
                url = normalizeUrl(src, baseUrl),
                quality = detectQuality(src),
                format = detectFormat(src),
                isStream = src.contains(".m3u8") || src.contains(".mpd")
            )
        }
        return null
    }
    
    /**
     * Extract from iframe embeds
     */
    private suspend fun extractFromIframe(document: Document, baseUrl: String): VideoUrlInfo? {
        val iframes = document.select("iframe[src]")
        
        for (iframe in iframes) {
            val src = iframe.attr("src")
            
            // YouTube
            if (src.contains("youtube.com") || src.contains("youtu.be")) {
                return VideoUrlInfo(
                    url = src,
                    quality = "HD",
                    format = "youtube",
                    isStream = true,
                    isEmbed = true
                )
            }
            
            // Vimeo
            if (src.contains("vimeo.com")) {
                return VideoUrlInfo(
                    url = src,
                    quality = "HD",
                    format = "vimeo",
                    isStream = true,
                    isEmbed = true
                )
            }
            
            // Try to extract from embed page
            if (src.contains("embed") || src.contains("player")) {
                try {
                    val embedDoc = Jsoup.connect(normalizeUrl(src, baseUrl))
                        .userAgent(USER_AGENT)
                        .timeout(15000)
                        .get()
                    
                    val videoUrl = extractFromVideoTag(embedDoc, src)
                        ?: extractFromSourceTag(embedDoc, src)
                        ?: extractFromScripts(embedDoc, src)
                    
                    if (videoUrl != null) return videoUrl
                } catch (e: Exception) {
                    continue
                }
            }
        }
        
        return null
    }
    
    /**
     * Extract video URLs from JavaScript
     */
    private fun extractFromScripts(document: Document, baseUrl: String): VideoUrlInfo? {
        val scripts = document.select("script").html()
        
        // Common patterns for video URLs in JS
        val patterns = listOf(
            Regex("""(?:src|file|source|url|video_url|videoUrl|stream)['":\s]+['"]?(https?://[^'">\s]+\.(?:mp4|m3u8|webm|mpd)[^'">\s]*)['"]?""", RegexOption.IGNORE_CASE),
            Regex("""['"]?(https?://[^'">\s]+\.(?:mp4|m3u8|webm|mpd)[^'">\s]*)['"]?""", RegexOption.IGNORE_CASE),
            Regex("""file:\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
            Regex("""sources:\s*\[\s*\{\s*(?:file|src):\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
            Regex("""player\.src\(\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
            Regex("""video:\s*['"]([^'"]+\.(?:mp4|m3u8|webm))['"]""", RegexOption.IGNORE_CASE)
        )
        
        // Find all matches and pick best quality
        val foundUrls = mutableListOf<String>()
        
        for (pattern in patterns) {
            pattern.findAll(scripts).forEach { match ->
                val url = match.groupValues.getOrNull(1) ?: match.value
                if (VIDEO_EXTENSIONS.any { url.contains(it, ignoreCase = true) }) {
                    foundUrls.add(url)
                }
            }
        }
        
        // Sort by quality and return best
        val bestUrl = foundUrls
            .distinctBy { it }
            .sortedByDescending { url ->
                QUALITY_ORDER.indexOfFirst { q -> url.contains(q, ignoreCase = true) }
                    .let { if (it == -1) -100 else -it }
            }
            .firstOrNull()
        
        return bestUrl?.let {
            val cleanUrl = it.replace("\\", "").trim('"', '\'')
            VideoUrlInfo(
                url = normalizeUrl(cleanUrl, baseUrl),
                quality = detectQuality(cleanUrl),
                format = detectFormat(cleanUrl),
                isStream = cleanUrl.contains(".m3u8") || cleanUrl.contains(".mpd")
            )
        }
    }
    
    /**
     * Extract from data attributes
     */
    private fun extractFromDataAttributes(document: Document, baseUrl: String): VideoUrlInfo? {
        val dataAttrs = listOf(
            "[data-video-url]", "[data-src]", "[data-video]",
            "[data-file]", "[data-stream]", "[data-mp4]",
            "[data-hls]", "[data-dash]"
        )
        
        for (selector in dataAttrs) {
            val element = document.select(selector).firstOrNull()
            if (element != null) {
                val attrName = selector.removeSurrounding("[", "]")
                val url = element.attr(attrName)
                if (url.isNotEmpty() && VIDEO_EXTENSIONS.any { url.contains(it, ignoreCase = true) }) {
                    return VideoUrlInfo(
                        url = normalizeUrl(url, baseUrl),
                        quality = detectQuality(url),
                        format = detectFormat(url),
                        isStream = url.contains(".m3u8") || url.contains(".mpd")
                    )
                }
            }
        }
        
        return null
    }
    
    /**
     * Extract from JSON-LD schema
     */
    private fun extractFromJsonLd(document: Document, baseUrl: String): VideoUrlInfo? {
        val jsonLd = document.select("script[type='application/ld+json']")
        
        for (script in jsonLd) {
            val json = script.html()
            
            // Look for contentUrl or embedUrl
            val urlPatterns = listOf(
                Regex(""""contentUrl"\s*:\s*"([^"]+)""""),
                Regex(""""embedUrl"\s*:\s*"([^"]+)""""),
                Regex(""""url"\s*:\s*"([^"]+\.(?:mp4|m3u8|webm))"""")
            )
            
            for (pattern in urlPatterns) {
                pattern.find(json)?.groupValues?.getOrNull(1)?.let { url ->
                    return VideoUrlInfo(
                        url = normalizeUrl(url, baseUrl),
                        quality = detectQuality(url),
                        format = detectFormat(url),
                        isStream = url.contains(".m3u8") || url.contains(".mpd")
                    )
                }
            }
        }
        
        return null
    }
    
    /**
     * Get video preview/thumbnail URL
     */
    suspend fun getVideoPreviewUrl(pageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val document = Jsoup.connect(pageUrl)
                .userAgent(USER_AGENT)
                .timeout(15000)
                .get()
            
            // Look for preview/poster
            val video = document.select("video[poster]").firstOrNull()
            if (video != null) {
                val poster = video.attr("poster")
                if (poster.isNotEmpty()) {
                    return@withContext normalizeUrl(poster, pageUrl)
                }
            }
            
            // Look for og:video or og:image
            document.select("meta[property='og:video']").firstOrNull()?.attr("content")?.let {
                if (it.isNotEmpty()) return@withContext it
            }
            
            document.select("meta[property='og:image']").firstOrNull()?.attr("content")?.let {
                if (it.isNotEmpty()) return@withContext it
            }
            
            // Look for preview gif/webp
            document.select("[data-preview], .preview, .gif-preview").firstOrNull()?.let { elem ->
                elem.attr("data-preview").takeIf { it.isNotEmpty() }
                    ?: elem.attr("src").takeIf { it.isNotEmpty() }
            }?.let { return@withContext normalizeUrl(it, pageUrl) }
            
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Download video to storage
     */
    suspend fun downloadVideo(
        videoUrl: String,
        fileName: String,
        onProgress: (Int) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(videoUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext DownloadResult(
                    success = false,
                    error = "HTTP ${response.code}"
                )
            }
            
            val body = response.body ?: return@withContext DownloadResult(
                success = false,
                error = "Empty response"
            )
            
            // Create download directory
            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "AggregatorX"
            )
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            // Sanitize filename
            val sanitizedName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val extension = detectFormat(videoUrl).let { 
                if (it.isNotEmpty()) ".$it" else ".mp4" 
            }
            val file = File(downloadDir, "$sanitizedName$extension")
            
            // Download with progress
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            
            FileOutputStream(file).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            onProgress(progress)
                        }
                    }
                }
            }
            
            DownloadResult(
                success = true,
                filePath = file.absolutePath,
                fileSize = file.length()
            )
        } catch (e: Exception) {
            DownloadResult(
                success = false,
                error = e.message ?: "Download failed"
            )
        }
    }
    
    // Helper functions
    private fun normalizeUrl(url: String, baseUrl: String): String {
        return when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> {
                val base = URL(baseUrl)
                "${base.protocol}://${base.host}$url"
            }
            else -> {
                val base = URL(baseUrl)
                "${base.protocol}://${base.host}/$url"
            }
        }
    }
    
    private fun detectQuality(url: String): String {
        val urlLower = url.lowercase()
        return QUALITY_ORDER.find { urlLower.contains(it) } ?: "Unknown"
    }
    
    private fun detectFormat(url: String): String {
        val urlLower = url.lowercase()
        return when {
            urlLower.contains(".m3u8") -> "m3u8"
            urlLower.contains(".mpd") -> "mpd"
            urlLower.contains(".mp4") -> "mp4"
            urlLower.contains(".webm") -> "webm"
            urlLower.contains(".mkv") -> "mkv"
            urlLower.contains(".avi") -> "avi"
            urlLower.contains(".mov") -> "mov"
            else -> ""
        }
    }
}

data class VideoUrlInfo(
    val url: String,
    val quality: String,
    val format: String,
    val isStream: Boolean,
    val isEmbed: Boolean = false
)

data class VideoExtractionResult(
    val success: Boolean,
    val videoUrl: String? = null,
    val quality: String? = null,
    val format: String? = null,
    val isStream: Boolean = false,
    val error: String? = null
)

data class DownloadResult(
    val success: Boolean,
    val filePath: String? = null,
    val fileSize: Long = 0,
    val error: String? = null
)
