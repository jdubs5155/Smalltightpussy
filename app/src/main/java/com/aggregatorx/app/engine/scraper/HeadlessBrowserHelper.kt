package com.aggregatorx.app.engine.scraper

import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.LoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/**
 * AggravatedX Enhanced Headless Browser Helper
 * 
 * Features:
 * - Auto-click ad/popup close buttons
 * - Shadow DOM traversal
 * - Smart media extraction
 * - Cookie consent handling
 * - Anti-bot evasion
 * - Intelligent wait strategies
 * - Video URL extraction from dynamic pages
 */
object HeadlessBrowserHelper {

    private var browser: Browser? = null
    private var playwright: Playwright? = null
    
    // Comprehensive ad/popup close selectors
    private val AD_CLOSE_SELECTORS = listOf(
        // Common close buttons
        ".ad-close", ".skip-ad", ".ad_skip_btn", ".ad-overlay-close",
        ".popup-close", ".modal-close", ".close-button", ".close-btn",
        ".dismiss", ".btn-close", ".icon-close", ".close-icon",
        "[aria-label='Close']", "[aria-label='Dismiss']",
        "[data-dismiss='modal']", "[data-close]",
        
        // Ad-specific
        ".ad-skip", ".skip-button", ".skip-ad-button", ".skipButton",
        ".video-ad-close", ".preroll-close", ".ad-countdown-skip",
        "#skip-button", "#ad-close", "#close-ad",
        
        // Popup/modal closes
        ".popup-dismiss", ".overlay-close", ".lightbox-close",
        ".fancybox-close", ".magnificPopup-close",
        ".modal__close", ".dialog-close", ".notification-close",
        
        // Cookie consent
        ".cookie-accept", ".cookie-close", ".cookie-dismiss",
        "#cookie-accept", "#accept-cookies", ".accept-cookies",
        ".gdpr-accept", ".consent-accept", "[data-cookieconsent='accept']",
        ".cc-dismiss", ".cc-accept", "#onetrust-accept-btn-handler",
        
        // Generic patterns
        "[class*='ad'] .close", "[class*='popup'] .close",
        "[class*='modal'] .close", "[class*='overlay'] .close",
        "[class*='banner'] .close", "[id*='close']",
        "button[class*='close']", "a[class*='close']",
        ".btn.close", ".button.close"
    )
    
    // Video source patterns to extract
    private val VIDEO_SOURCE_PATTERNS = listOf(
        Regex("""(?:src|file|source|url|video_url|videoUrl|stream)['":\s]+['"]?(https?://[^'">\s]+\.(?:mp4|m3u8|webm|mpd)[^'">\s]*)['"]?""", RegexOption.IGNORE_CASE),
        Regex("""['"]?(https?://[^'">\s]*\.(?:mp4|m3u8|webm|mpd|flv|mkv)[^'">\s]*)['"]?""", RegexOption.IGNORE_CASE),
        Regex("""file:\s*['"](https?://[^'"]+)['"']""", RegexOption.IGNORE_CASE),
        Regex("""sources:\s*\[\s*\{\s*(?:file|src):\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
        Regex("""player\.src\(\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
        Regex("""hls\.loadSource\(['"]([^'"]+)['"]\)""", RegexOption.IGNORE_CASE),
        Regex("""dash\.initialize\([^,]+,\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE),
        Regex(""""contentUrl"\s*:\s*"([^"]+)""""),
        Regex(""""embedUrl"\s*:\s*"([^"]+)"""")
    )

    /**
     * Get or create browser instance with anti-detection settings
     */
    fun getBrowser(): Browser {
        if (browser == null || !browser!!.isConnected) {
            playwright = Playwright.create()
            browser = playwright!!.chromium().launch(
                BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(listOf(
                        "--disable-blink-features=AutomationControlled",
                        "--disable-web-security",
                        "--disable-features=IsolateOrigins,site-per-process",
                        "--no-sandbox",
                        "--disable-setuid-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-accelerated-2d-canvas",
                        "--disable-gpu"
                    ))
            )
        }
        return browser!!
    }

    /**
     * Create a new page with anti-detection settings
     */
    private fun createPage(): Page {
        val page = getBrowser().newPage()
        
        // Set user agent to look like real browser
        page.setExtraHTTPHeaders(mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept-Language" to "en-US,en;q=0.9",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "DNT" to "1"
        ))
        
        // Inject anti-detection scripts
        page.addInitScript("""
            // Overwrite webdriver detection
            Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
            
            // Fake plugins
            Object.defineProperty(navigator, 'plugins', {
                get: () => [1, 2, 3, 4, 5]
            });
            
            // Fake languages
            Object.defineProperty(navigator, 'languages', {
                get: () => ['en-US', 'en']
            });
            
            // Remove automation indicators
            delete window.cdc_adoQpoasnfa76pfcZLmcfl_Array;
            delete window.cdc_adoQpoasnfa76pfcZLmcfl_Promise;
            delete window.cdc_adoQpoasnfa76pfcZLmcfl_Symbol;
        """)
        
        return page
    }

    /**
     * Fetch page content with comprehensive ad/popup handling
     */
    fun fetchPageContent(url: String, waitSelector: String? = null, timeout: Int = 15000): String? {
        val page = createPage()
        
        try {
            page.navigate(url, Page.NavigateOptions().setTimeout(timeout.toDouble()))
            page.waitForLoadState(LoadState.NETWORKIDLE, Page.WaitForLoadStateOptions().setTimeout(timeout.toDouble()))
            
            // Wait for main content if needed
            if (waitSelector != null) {
                try {
                    page.waitForSelector(waitSelector, Page.WaitForSelectorOptions().setTimeout((timeout / 2).toDouble()))
                } catch (_: Exception) {}
            }
            
            // Auto-click close buttons multiple times
            repeat(3) {
                autoClickCloseButtons(page)
                Thread.sleep(200)
            }
            
            return page.content()
        } catch (e: Exception) {
            return null
        } finally {
            try { page.close() } catch (_: Exception) {}
        }
    }

    /**
     * Enhanced page fetch with shadow DOM support and aggressive ad removal
     */
    fun fetchPageContentWithShadowAndAdSkip(url: String, waitSelector: String? = null, timeout: Int = 15000): String? {
        val page = createPage()
        
        try {
            page.navigate(url, Page.NavigateOptions().setTimeout(timeout.toDouble()))
            page.waitForLoadState(LoadState.DOMCONTENTLOADED, Page.WaitForLoadStateOptions().setTimeout(timeout.toDouble()))
            
            // Wait for content
            if (waitSelector != null) {
                try {
                    page.waitForSelector(waitSelector, Page.WaitForSelectorOptions().setTimeout((timeout / 2).toDouble()))
                } catch (_: Exception) {}
            }
            
            // Aggressive ad/popup removal - multiple passes
            repeat(5) { pass ->
                autoClickCloseButtons(page)
                handleShadowDOMCloses(page)
                removeAdElements(page)
                Thread.sleep(300)
            }
            
            // Handle cookie consent
            handleCookieConsent(page)
            
            return page.content()
        } catch (e: Exception) {
            return null
        } finally {
            try { page.close() } catch (_: Exception) {}
        }
    }

    /**
     * Auto-click all close buttons found on page
     */
    private fun autoClickCloseButtons(page: Page) {
        for (selector in AD_CLOSE_SELECTORS) {
            try {
                val elements = page.querySelectorAll(selector)
                for (element in elements) {
                    try {
                        if (element.isVisible) {
                            element.click(com.microsoft.playwright.ElementHandle.ClickOptions().setTimeout(500.0))
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Handle close buttons inside shadow DOM
     */
    private fun handleShadowDOMCloses(page: Page) {
        try {
            page.evaluate("""
                const shadowCloseSelectors = [
                    '.ad-close', '.skip-ad', '.close-button', '.popup-close', 
                    '.modal-close', '.dismiss', '.btn-close', '[aria-label="Close"]'
                ];
                
                function findAndClickInShadow(root) {
                    const elements = root.querySelectorAll('*');
                    elements.forEach(el => {
                        if (el.shadowRoot) {
                            shadowCloseSelectors.forEach(selector => {
                                const closeBtn = el.shadowRoot.querySelector(selector);
                                if (closeBtn) {
                                    try { closeBtn.click(); } catch(e) {}
                                }
                            });
                            findAndClickInShadow(el.shadowRoot);
                        }
                    });
                }
                
                findAndClickInShadow(document);
            """)
        } catch (_: Exception) {}
    }

    /**
     * Remove ad elements from DOM
     */
    private fun removeAdElements(page: Page) {
        try {
            page.evaluate("""
                const adSelectors = [
                    '[class*="ad-"]', '[class*="ads-"]', '[class*="advertisement"]',
                    '[id*="ad-"]', '[id*="ads-"]', '[id*="advertisement"]',
                    '.ad', '.ads', '.advert', '.advertisement',
                    '.popup', '.modal:not(.video-modal)', '.overlay:not(.video-overlay)',
                    'iframe[src*="doubleclick"]', 'iframe[src*="googlesyndication"]',
                    'iframe[src*="adservice"]', '[class*="popup"]',
                    '.cookie-banner', '.gdpr-banner', '.consent-banner'
                ];
                
                adSelectors.forEach(selector => {
                    document.querySelectorAll(selector).forEach(el => {
                        // Don't remove if it contains video
                        if (!el.querySelector('video') && !el.matches('video')) {
                            el.style.display = 'none';
                        }
                    });
                });
            """)
        } catch (_: Exception) {}
    }

    /**
     * Handle cookie consent dialogs
     */
    private fun handleCookieConsent(page: Page) {
        val cookieAcceptSelectors = listOf(
            "#onetrust-accept-btn-handler",
            ".cookie-accept", "#accept-cookies", ".accept-cookies",
            ".gdpr-accept", ".consent-accept", "[data-cookieconsent='accept']",
            ".cc-dismiss", ".cc-accept", "#cookie-accept",
            "button[aria-label*='accept']", "button[aria-label*='Accept']"
        )
        
        for (selector in cookieAcceptSelectors) {
            try {
                val element = page.querySelector(selector)
                if (element != null && element.isVisible) {
                    element.click()
                    break
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Extract video URLs from a page (handles dynamic content)
     * Returns list of video URL strings for use by VideoExtractorEngine
     */
    fun extractVideoUrls(url: String, timeout: Int = 20000): List<String> {
        val page = createPage()
        val videoUrls = mutableListOf<String>()
        
        try {
            // Intercept network requests for video files
            page.onRequest { request ->
                val requestUrl = request.url()
                if (isVideoUrl(requestUrl)) {
                    videoUrls.add(requestUrl)
                }
            }
            
            page.navigate(url, Page.NavigateOptions().setTimeout(timeout.toDouble()))
            page.waitForLoadState(LoadState.NETWORKIDLE, Page.WaitForLoadStateOptions().setTimeout(timeout.toDouble()))
            
            // Handle ads first
            repeat(3) {
                autoClickCloseButtons(page)
                Thread.sleep(300)
            }
            
            // Look for play buttons and click them
            clickPlayButtons(page)
            Thread.sleep(1000)
            
            // Extract from HTML
            val htmlUrls = extractVideoUrlsFromHtml(page)
            videoUrls.addAll(htmlUrls)
            
            // Extract from scripts
            val scriptUrls = extractVideoUrlsFromScripts(page)
            videoUrls.addAll(scriptUrls)
            
            // Extract from video elements
            val elementUrls = extractFromVideoElements(page)
            videoUrls.addAll(elementUrls)
            
            return videoUrls.distinct()
                .sortedByDescending { getQualityScore(detectQualityFromUrl(it)) }
        } catch (e: Exception) {
            return videoUrls
        } finally {
            try { page.close() } catch (_: Exception) {}
        }
    }

    /**
     * Extract video URLs as VideoUrlInfo objects (internal use)
     */
    fun extractVideoUrlsDetailed(url: String, timeout: Int = 20000): List<VideoUrlInfo> {
        return extractVideoUrls(url, timeout).map { videoUrl ->
            VideoUrlInfo(
                url = videoUrl,
                quality = detectQualityFromUrl(videoUrl),
                format = detectFormatFromUrl(videoUrl),
                source = "headless"
            )
        }
    }

    /**
     * Click play buttons to trigger video loading
     */
    private fun clickPlayButtons(page: Page) {
        val playSelectors = listOf(
            ".play-button", ".btn-play", ".video-play",
            "[class*='play']", "[aria-label*='play' i]",
            ".vjs-big-play-button", ".plyr__control--overlaid",
            ".jw-icon-display", "#player-play"
        )
        
        for (selector in playSelectors) {
            try {
                val element = page.querySelector(selector)
                if (element != null && element.isVisible) {
                    element.click()
                    Thread.sleep(500)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Extract video URLs from HTML content
     */
    private fun extractVideoUrlsFromHtml(page: Page): List<String> {
        val urls = mutableListOf<String>()
        
        try {
            val html = page.content()
            
            for (pattern in VIDEO_SOURCE_PATTERNS) {
                pattern.findAll(html).forEach { match ->
                    val url = match.groupValues.getOrNull(1) ?: match.value
                    if (isVideoUrl(url)) {
                        urls.add(url.replace("\\", "").trim('"', '\''))
                    }
                }
            }
        } catch (_: Exception) {}
        
        return urls
    }

    /**
     * Extract video URLs from JavaScript
     */
    private fun extractVideoUrlsFromScripts(page: Page): List<String> {
        val urls = mutableListOf<String>()
        
        try {
            val result = page.evaluate("""
                () => {
                    const urls = [];
                    
                    // Check global variables
                    if (typeof videoUrl !== 'undefined') urls.push(videoUrl);
                    if (typeof streamUrl !== 'undefined') urls.push(streamUrl);
                    if (typeof hlsUrl !== 'undefined') urls.push(hlsUrl);
                    if (typeof source !== 'undefined' && typeof source === 'string') urls.push(source);
                    
                    // Check player instances
                    if (typeof jwplayer !== 'undefined') {
                        try {
                            const playlist = jwplayer().getPlaylist();
                            if (playlist && playlist[0] && playlist[0].file) {
                                urls.push(playlist[0].file);
                            }
                        } catch(e) {}
                    }
                    
                    if (typeof Hls !== 'undefined') {
                        document.querySelectorAll('video').forEach(v => {
                            if (v.src) urls.push(v.src);
                        });
                    }
                    
                    // Check data attributes
                    document.querySelectorAll('[data-video-url], [data-src], [data-file]').forEach(el => {
                        ['data-video-url', 'data-src', 'data-file', 'data-hls', 'data-dash'].forEach(attr => {
                            const val = el.getAttribute(attr);
                            if (val && (val.includes('.mp4') || val.includes('.m3u8') || val.includes('.webm'))) {
                                urls.push(val);
                            }
                        });
                    });
                    
                    return urls.filter(u => u && typeof u === 'string');
                }
            """)
            
            if (result is List<*>) {
                result.filterIsInstance<String>().forEach { url ->
                    if (isVideoUrl(url)) {
                        urls.add(url)
                    }
                }
            }
        } catch (_: Exception) {}
        
        return urls
    }

    /**
     * Extract from video elements
     */
    private fun extractFromVideoElements(page: Page): List<String> {
        val urls = mutableListOf<String>()
        
        try {
            val videos = page.querySelectorAll("video, video source")
            for (video in videos) {
                val src = video.getAttribute("src") ?: video.getAttribute("data-src")
                if (src != null && isVideoUrl(src)) {
                    urls.add(src)
                }
            }
        } catch (_: Exception) {}
        
        return urls
    }

    /**
     * Check if URL is a video URL
     */
    private fun isVideoUrl(url: String): Boolean {
        val videoExtensions = listOf(".mp4", ".m3u8", ".webm", ".mpd", ".flv", ".mkv", ".ts", ".mov")
        return videoExtensions.any { url.lowercase().contains(it) }
    }

    /**
     * Detect quality from URL
     */
    private fun detectQualityFromUrl(url: String): String {
        val urlLower = url.lowercase()
        return when {
            urlLower.contains("2160") || urlLower.contains("4k") -> "4K"
            urlLower.contains("1080") -> "1080p"
            urlLower.contains("720") -> "720p"
            urlLower.contains("480") -> "480p"
            urlLower.contains("360") -> "360p"
            urlLower.contains("hd") -> "HD"
            else -> "Unknown"
        }
    }

    /**
     * Detect format from URL
     */
    private fun detectFormatFromUrl(url: String): String {
        val urlLower = url.lowercase()
        return when {
            urlLower.contains(".m3u8") -> "HLS"
            urlLower.contains(".mpd") -> "DASH"
            urlLower.contains(".mp4") -> "MP4"
            urlLower.contains(".webm") -> "WebM"
            urlLower.contains(".mkv") -> "MKV"
            urlLower.contains(".flv") -> "FLV"
            else -> "Unknown"
        }
    }

    /**
     * Get quality score for sorting
     */
    private fun getQualityScore(quality: String): Int {
        return when (quality.lowercase()) {
            "4k", "2160p" -> 100
            "1080p", "full hd" -> 90
            "720p", "hd" -> 70
            "480p" -> 50
            "360p" -> 30
            else -> 40
        }
    }

    /**
     * Close browser and cleanup
     */
    fun close() {
        try {
            browser?.close()
            browser = null
            playwright?.close()
            playwright = null
        } catch (_: Exception) {}
    }
}

/**
 * Video URL information
 */
data class VideoUrlInfo(
    val url: String,
    val quality: String,
    val format: String,
    val source: String = "unknown",
    val fileSize: Long? = null
)
