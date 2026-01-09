package com.zim.jackettprowler.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Service for extracting and downloading videos from various platforms
 * Uses web scraping and API-based methods to find direct video URLs
 */
class VideoDownloadService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    companion object {
        const val PREFS_NAME = "video_download_prefs"
        const val PREF_DOWNLOAD_PATH = "download_path"
        const val PREF_QUALITY = "preferred_quality"
        
        // Common user agent for web requests
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 11; SM-A325F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Mobile Safari/537.36"
    }

    /**
     * Result of video extraction
     */
    data class VideoStream(
        val url: String,
        val quality: String,
        val format: String,
        val fileSize: Long = 0,
        val isAudioOnly: Boolean = false
    )

    data class ExtractionResult(
        val success: Boolean,
        val streams: List<VideoStream> = emptyList(),
        val title: String = "",
        val error: String = ""
    )

    /**
     * Extract downloadable streams from a video URL
     */
    suspend fun extractStreams(video: VideoResult): ExtractionResult = withContext(Dispatchers.IO) {
        try {
            when (video.siteType) {
                VideoSiteType.YOUTUBE -> extractYouTubeStreams(video)
                VideoSiteType.DAILYMOTION -> extractDailymotionStreams(video)
                VideoSiteType.VIMEO -> extractVimeoStreams(video)
                VideoSiteType.RUMBLE -> extractRumbleStreams(video)
                VideoSiteType.ODYSEE -> extractOdyseeStreams(video)
                VideoSiteType.BITCHUTE -> extractBitChuteStreams(video)
                VideoSiteType.PEERTUBE -> extractPeerTubeStreams(video)
                VideoSiteType.ARCHIVE_ORG -> extractArchiveStreams(video)
                VideoSiteType.TWITCH -> extractTwitchStreams(video)
                VideoSiteType.GENERIC -> extractGenericStreams(video)
            }
        } catch (e: Exception) {
            ExtractionResult(false, error = "Extraction failed: ${e.message}")
        }
    }

    /**
     * Extract YouTube/Invidious streams
     */
    private suspend fun extractYouTubeStreams(video: VideoResult): ExtractionResult {
        // Try to use Invidious API first (no API key required)
        val videoId = extractYouTubeVideoId(video.videoUrl)
        if (videoId != null) {
            // List of public Invidious instances
            val invidiousInstances = listOf(
                "https://invidious.snopyta.org",
                "https://yewtu.be",
                "https://vid.puffyan.us",
                "https://invidious.kavin.rocks"
            )
            
            for (instance in invidiousInstances) {
                try {
                    val apiUrl = "$instance/api/v1/videos/$videoId"
                    val request = Request.Builder()
                        .url(apiUrl)
                        .header("User-Agent", USER_AGENT)
                        .build()
                    
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val json = response.body?.string() ?: continue
                        return parseInvidiousResponse(json, video.title)
                    }
                } catch (e: Exception) {
                    continue
                }
            }
        }
        
        // Fallback: Try to extract from direct URL if it's an Invidious URL
        if (video.directUrl.isNotEmpty()) {
            return ExtractionResult(
                success = true,
                streams = listOf(VideoStream(video.directUrl, "default", "mp4")),
                title = video.title
            )
        }
        
        return ExtractionResult(false, error = "Could not extract YouTube video streams")
    }
    
    private fun parseInvidiousResponse(json: String, fallbackTitle: String): ExtractionResult {
        val streams = mutableListOf<VideoStream>()
        
        try {
            // Parse formatStreams and adaptiveFormats
            val formatPattern = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"[^}]*\"quality\"\\s*:\\s*\"([^\"]+)\"[^}]*\"type\"\\s*:\\s*\"([^\"]+)\"")
            val matcher = formatPattern.matcher(json)
            
            while (matcher.find()) {
                val url = matcher.group(1)?.replace("\\/", "/") ?: continue
                val quality = matcher.group(2) ?: "unknown"
                val type = matcher.group(3) ?: "video/mp4"
                
                val isAudio = type.contains("audio")
                val format = when {
                    type.contains("mp4") -> "mp4"
                    type.contains("webm") -> "webm"
                    type.contains("audio") -> "mp3"
                    else -> "unknown"
                }
                
                streams.add(VideoStream(url, quality, format, isAudioOnly = isAudio))
            }
            
            // Extract title
            val titlePattern = Pattern.compile("\"title\"\\s*:\\s*\"([^\"]+)\"")
            val titleMatcher = titlePattern.matcher(json)
            val title = if (titleMatcher.find()) {
                titleMatcher.group(1) ?: fallbackTitle
            } else {
                fallbackTitle
            }
            
            return ExtractionResult(
                success = streams.isNotEmpty(),
                streams = streams.sortedByDescending { 
                    when {
                        it.quality.contains("1080") -> 5
                        it.quality.contains("720") -> 4
                        it.quality.contains("480") -> 3
                        it.quality.contains("360") -> 2
                        else -> 1
                    }
                },
                title = title
            )
        } catch (e: Exception) {
            return ExtractionResult(false, error = "Failed to parse video data: ${e.message}")
        }
    }

    /**
     * Extract Dailymotion streams using their player API
     */
    private suspend fun extractDailymotionStreams(video: VideoResult): ExtractionResult {
        val videoId = extractDailymotionVideoId(video.videoUrl) ?: return ExtractionResult(false, error = "Invalid Dailymotion URL")
        
        try {
            val apiUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string() ?: return ExtractionResult(false, error = "Empty response")
                
                val streams = mutableListOf<VideoStream>()
                val qualityPattern = Pattern.compile("\"(\\d+)\"\\s*:\\s*\\[\\{[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"")
                val matcher = qualityPattern.matcher(json)
                
                while (matcher.find()) {
                    val quality = matcher.group(1) ?: continue
                    val url = matcher.group(2)?.replace("\\/", "/")?.replace("\\u0026", "&") ?: continue
                    streams.add(VideoStream(url, "${quality}p", "mp4"))
                }
                
                return ExtractionResult(success = streams.isNotEmpty(), streams = streams, title = video.title)
            }
        } catch (e: Exception) {
            return ExtractionResult(false, error = "Dailymotion extraction failed: ${e.message}")
        }
        
        return ExtractionResult(false, error = "Could not extract Dailymotion streams")
    }

    /**
     * Extract Vimeo streams
     */
    private suspend fun extractVimeoStreams(video: VideoResult): ExtractionResult {
        val videoId = extractVimeoVideoId(video.videoUrl) ?: return ExtractionResult(false, error = "Invalid Vimeo URL")
        
        try {
            val configUrl = "https://player.vimeo.com/video/$videoId/config"
            val request = Request.Builder()
                .url(configUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://vimeo.com/")
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string() ?: return ExtractionResult(false, error = "Empty response")
                
                val streams = mutableListOf<VideoStream>()
                
                // Extract progressive downloads
                val progressivePattern = Pattern.compile("\"progressive\"\\s*:\\s*\\[(.*?)\\]")
                val progressiveMatcher = progressivePattern.matcher(json)
                
                if (progressiveMatcher.find()) {
                    val progressiveJson = progressiveMatcher.group(1) ?: ""
                    val urlPattern = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"[^}]*\"quality\"\\s*:\\s*\"([^\"]+)\"")
                    val urlMatcher = urlPattern.matcher(progressiveJson)
                    
                    while (urlMatcher.find()) {
                        val url = urlMatcher.group(1)?.replace("\\/", "/") ?: continue
                        val quality = urlMatcher.group(2) ?: "unknown"
                        streams.add(VideoStream(url, quality, "mp4"))
                    }
                }
                
                return ExtractionResult(success = streams.isNotEmpty(), streams = streams, title = video.title)
            }
        } catch (e: Exception) {
            return ExtractionResult(false, error = "Vimeo extraction failed: ${e.message}")
        }
        
        return ExtractionResult(false, error = "Could not extract Vimeo streams")
    }

    /**
     * Extract Rumble streams
     */
    private suspend fun extractRumbleStreams(video: VideoResult): ExtractionResult {
        try {
            val request = Request.Builder()
                .url(video.videoUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: return ExtractionResult(false, error = "Empty response")
                
                val streams = mutableListOf<VideoStream>()
                
                // Look for mp4 URLs in the page
                val mp4Pattern = Pattern.compile("\"mp4\"\\s*:\\s*\\{[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"")
                val mp4Matcher = mp4Pattern.matcher(html)
                
                while (mp4Matcher.find()) {
                    val url = mp4Matcher.group(1)?.replace("\\/", "/") ?: continue
                    streams.add(VideoStream(url, "auto", "mp4"))
                }
                
                // Alternative pattern for direct video URLs
                if (streams.isEmpty()) {
                    val directPattern = Pattern.compile("(https?://[^\"\\s]+\\.mp4[^\"\\s]*)")
                    val directMatcher = directPattern.matcher(html)
                    while (directMatcher.find()) {
                        val url = directMatcher.group(1) ?: continue
                        if (url.contains("rumble.com") || url.contains("rumble-video")) {
                            streams.add(VideoStream(url, "auto", "mp4"))
                        }
                    }
                }
                
                return ExtractionResult(success = streams.isNotEmpty(), streams = streams.distinctBy { it.url }, title = video.title)
            }
        } catch (e: Exception) {
            return ExtractionResult(false, error = "Rumble extraction failed: ${e.message}")
        }
        
        return ExtractionResult(false, error = "Could not extract Rumble streams")
    }

    /**
     * Extract Odysee/LBRY streams
     */
    private suspend fun extractOdyseeStreams(video: VideoResult): ExtractionResult {
        try {
            // Odysee uses LBRY protocol, try to get direct stream URL
            val request = Request.Builder()
                .url(video.videoUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: return ExtractionResult(false, error = "Empty response")
                
                val streams = mutableListOf<VideoStream>()
                
                // Look for contentUrl or direct video links
                val streamPattern = Pattern.compile("\"contentUrl\"\\s*:\\s*\"([^\"]+)\"")
                val streamMatcher = streamPattern.matcher(html)
                
                if (streamMatcher.find()) {
                    val url = streamMatcher.group(1)?.replace("\\/", "/") ?: ""
                    if (url.isNotEmpty()) {
                        streams.add(VideoStream(url, "auto", "mp4"))
                    }
                }
                
                // Also try source array
                val sourcePattern = Pattern.compile("\"src\"\\s*:\\s*\"(https?://[^\"]+)\"[^}]*\"type\"\\s*:\\s*\"video")
                val sourceMatcher = sourcePattern.matcher(html)
                while (sourceMatcher.find()) {
                    val url = sourceMatcher.group(1)?.replace("\\/", "/") ?: continue
                    streams.add(VideoStream(url, "auto", "mp4"))
                }
                
                return ExtractionResult(success = streams.isNotEmpty(), streams = streams.distinctBy { it.url }, title = video.title)
            }
        } catch (e: Exception) {
            return ExtractionResult(false, error = "Odysee extraction failed: ${e.message}")
        }
        
        return ExtractionResult(false, error = "Could not extract Odysee streams")
    }

    /**
     * Extract BitChute streams
     */
    private suspend fun extractBitChuteStreams(video: VideoResult): ExtractionResult {
        try {
            val request = Request.Builder()
                .url(video.videoUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: return ExtractionResult(false, error = "Empty response")
                
                val streams = mutableListOf<VideoStream>()
                
                // BitChute uses direct mp4 links
                val sourcePattern = Pattern.compile("<source\\s+src=\"([^\"]+)\"[^>]*type=\"video/mp4\"")
                val sourceMatcher = sourcePattern.matcher(html)
                
                while (sourceMatcher.find()) {
                    val url = sourceMatcher.group(1) ?: continue
                    streams.add(VideoStream(url, "auto", "mp4"))
                }
                
                // Fallback: look for any mp4 URL
                if (streams.isEmpty()) {
                    val mp4Pattern = Pattern.compile("(https?://[^\"\\s]+\\.mp4)")
                    val mp4Matcher = mp4Pattern.matcher(html)
                    while (mp4Matcher.find()) {
                        val url = mp4Matcher.group(1) ?: continue
                        streams.add(VideoStream(url, "auto", "mp4"))
                    }
                }
                
                return ExtractionResult(success = streams.isNotEmpty(), streams = streams.distinctBy { it.url }, title = video.title)
            }
        } catch (e: Exception) {
            return ExtractionResult(false, error = "BitChute extraction failed: ${e.message}")
        }
        
        return ExtractionResult(false, error = "Could not extract BitChute streams")
    }

    /**
     * Extract PeerTube streams - PeerTube instances have a standard API
     */
    private suspend fun extractPeerTubeStreams(video: VideoResult): ExtractionResult {
        try {
            // Extract base URL and video ID from PeerTube URL
            val urlPattern = Pattern.compile("(https?://[^/]+)/(?:videos/watch|w)/([a-zA-Z0-9-]+)")
            val matcher = urlPattern.matcher(video.videoUrl)
            
            if (matcher.find()) {
                val baseUrl = matcher.group(1)
                val videoId = matcher.group(2)
                
                val apiUrl = "$baseUrl/api/v1/videos/$videoId"
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = response.body?.string() ?: return ExtractionResult(false, error = "Empty response")
                    
                    val streams = mutableListOf<VideoStream>()
                    
                    // Parse files array
                    val filePattern = Pattern.compile("\"fileUrl\"\\s*:\\s*\"([^\"]+)\"[^}]*\"resolution\"\\s*:\\s*\\{[^}]*\"id\"\\s*:\\s*(\\d+)")
                    val fileMatcher = filePattern.matcher(json)
                    
                    while (fileMatcher.find()) {
                        val url = fileMatcher.group(1)?.replace("\\/", "/") ?: continue
                        val resolution = fileMatcher.group(2) ?: "auto"
                        streams.add(VideoStream(url, "${resolution}p", "mp4"))
                    }
                    
                    return ExtractionResult(success = streams.isNotEmpty(), streams = streams, title = video.title)
                }
            }
        } catch (e: Exception) {
            return ExtractionResult(false, error = "PeerTube extraction failed: ${e.message}")
        }
        
        return ExtractionResult(false, error = "Could not extract PeerTube streams")
    }

    /**
     * Extract Archive.org streams
     */
    private suspend fun extractArchiveStreams(video: VideoResult): ExtractionResult {
        try {
            // Archive.org has direct download links
            val identifier = extractArchiveIdentifier(video.videoUrl)
            
            if (identifier != null) {
                val metadataUrl = "https://archive.org/metadata/$identifier"
                val request = Request.Builder()
                    .url(metadataUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = response.body?.string() ?: return ExtractionResult(false, error = "Empty response")
                    
                    val streams = mutableListOf<VideoStream>()
                    
                    // Parse files array for video files
                    val filePattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+\\.mp4)\"")
                    val fileMatcher = filePattern.matcher(json)
                    
                    while (fileMatcher.find()) {
                        val filename = fileMatcher.group(1) ?: continue
                        val url = "https://archive.org/download/$identifier/$filename"
                        streams.add(VideoStream(url, "auto", "mp4"))
                    }
                    
                    return ExtractionResult(success = streams.isNotEmpty(), streams = streams, title = video.title)
                }
            }
            
            // Fallback: if directUrl is available
            if (video.directUrl.isNotEmpty()) {
                return ExtractionResult(
                    success = true,
                    streams = listOf(VideoStream(video.directUrl, "auto", "mp4")),
                    title = video.title
                )
            }
        } catch (e: Exception) {
            return ExtractionResult(false, error = "Archive.org extraction failed: ${e.message}")
        }
        
        return ExtractionResult(false, error = "Could not extract Archive.org streams")
    }

    /**
     * Extract Twitch VOD/Clip streams - Note: Live streams not supported
     */
    private suspend fun extractTwitchStreams(video: VideoResult): ExtractionResult {
        // Twitch requires OAuth for API access, which complicates things
        // For now, we'll just return the video URL for opening in browser/app
        return ExtractionResult(
            success = false,
            error = "Twitch downloads require the Twitch app or browser. Tap to open in browser."
        )
    }

    /**
     * Extract streams from generic video pages
     */
    private suspend fun extractGenericStreams(video: VideoResult): ExtractionResult {
        try {
            val request = Request.Builder()
                .url(video.videoUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: return ExtractionResult(false, error = "Empty response")
                
                val streams = mutableListOf<VideoStream>()
                
                // Try common video source patterns
                
                // 1. HTML5 video source tags
                val sourcePattern = Pattern.compile("<source\\s+[^>]*src=\"([^\"]+)\"[^>]*type=\"video/([^\"]+)\"", Pattern.CASE_INSENSITIVE)
                val sourceMatcher = sourcePattern.matcher(html)
                while (sourceMatcher.find()) {
                    val url = sourceMatcher.group(1) ?: continue
                    val format = sourceMatcher.group(2) ?: "mp4"
                    streams.add(VideoStream(url, "auto", format.split(";")[0]))
                }
                
                // 2. Video tag src attribute
                val videoPattern = Pattern.compile("<video[^>]+src=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
                val videoMatcher = videoPattern.matcher(html)
                while (videoMatcher.find()) {
                    val url = videoMatcher.group(1) ?: continue
                    val format = if (url.contains(".webm")) "webm" else "mp4"
                    streams.add(VideoStream(url, "auto", format))
                }
                
                // 3. Direct video file URLs in JavaScript/JSON
                val directPattern = Pattern.compile("(https?://[^\"'\\s]+\\.(mp4|webm|m3u8)[^\"'\\s]*)")
                val directMatcher = directPattern.matcher(html)
                while (directMatcher.find()) {
                    val url = directMatcher.group(1)?.replace("\\/", "/") ?: continue
                    val format = directMatcher.group(2) ?: "mp4"
                    if (!url.contains("thumbnail") && !url.contains("poster")) {
                        streams.add(VideoStream(url, "auto", format))
                    }
                }
                
                // 4. og:video meta tag
                val ogVideoPattern = Pattern.compile("<meta[^>]+property=\"og:video\"[^>]+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)
                val ogVideoMatcher = ogVideoPattern.matcher(html)
                if (ogVideoMatcher.find()) {
                    val url = ogVideoMatcher.group(1) ?: ""
                    if (url.isNotEmpty() && !url.contains("embed")) {
                        streams.add(VideoStream(url, "auto", "mp4"))
                    }
                }
                
                return ExtractionResult(
                    success = streams.isNotEmpty(),
                    streams = streams.distinctBy { it.url },
                    title = video.title
                )
            }
        } catch (e: Exception) {
            return ExtractionResult(false, error = "Generic extraction failed: ${e.message}")
        }
        
        return ExtractionResult(false, error = "Could not find video streams on this page")
    }

    // Helper methods to extract video IDs from URLs
    
    private fun extractYouTubeVideoId(url: String): String? {
        val patterns = listOf(
            Pattern.compile("(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/|invidious[^/]*/watch\\?v=)([a-zA-Z0-9_-]{11})"),
            Pattern.compile("v=([a-zA-Z0-9_-]{11})")
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }
    
    private fun extractDailymotionVideoId(url: String): String? {
        val pattern = Pattern.compile("dailymotion\\.com/video/([a-zA-Z0-9]+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }
    
    private fun extractVimeoVideoId(url: String): String? {
        val pattern = Pattern.compile("vimeo\\.com/(\\d+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }
    
    private fun extractArchiveIdentifier(url: String): String? {
        val pattern = Pattern.compile("archive\\.org/(?:details|download)/([^/\\?]+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * Download a video stream to device storage
     */
    suspend fun downloadVideo(stream: VideoStream, title: String, onProgress: (Int) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        try {
            val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").take(100)
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val videoDir = File(downloadDir, "JackettProwler/Videos")
            videoDir.mkdirs()
            
            val extension = when {
                stream.format == "webm" -> "webm"
                stream.format == "mp3" || stream.isAudioOnly -> "mp3"
                else -> "mp4"
            }
            
            val outputFile = File(videoDir, "$sanitizedTitle.$extension")
            
            val request = Request.Builder()
                .url(stream.url)
                .header("User-Agent", USER_AGENT)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Download failed: ${response.code}"))
                }
                
                val body = response.body ?: return@withContext Result.failure(Exception("Empty response"))
                val contentLength = body.contentLength()
                
                FileOutputStream(outputFile).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            
                            if (contentLength > 0) {
                                val progress = ((totalBytesRead * 100) / contentLength).toInt()
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
            
            // Notify media scanner
            val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            scanIntent.data = Uri.fromFile(outputFile)
            context.sendBroadcast(scanIntent)
            
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get download options for external apps
     */
    fun getExternalDownloaderIntent(url: String, title: String): Intent {
        // Create intent for ADM or other download managers
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.putExtra("android.intent.extra.TITLE", title)
        return intent
    }
}
