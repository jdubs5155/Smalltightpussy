package com.zim.jackettprowler.automation

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

/**
 * Deep Parsing Analyzer - Tool-X Inspired Advanced Parser Extractor
 * 
 * This is the MAGIC tool that automatically detects:
 * - Exact CSS selectors for every field (title, seeders, leechers, size, magnet, etc.)
 * - Table column positions
 * - Number patterns (seeders/leechers)
 * - Size format patterns
 * - Pagination structure
 * - API endpoints with their parameters
 * 
 * User just provides URL → Tool does ALL the rest!
 */
class DeepParsingAnalyzer {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    companion object {
        private const val TAG = "DeepParsingAnalyzer"
        
        // User agents for rotation
        private val USER_AGENTS = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        
        // Number patterns for seeders/leechers
        private val NUMBER_PATTERNS = listOf(
            Regex("""\d+"""),
            Regex("""[\d,]+"""),
            Regex("""[\d.]+[KM]?""", RegexOption.IGNORE_CASE)
        )
        
        // Size patterns (1.5 GB, 700 MB, etc.)
        private val SIZE_PATTERNS = listOf(
            Regex("""[\d,.]+\s*(GB|MB|KB|TB|GiB|MiB|KiB|TiB|B)""", RegexOption.IGNORE_CASE),
            Regex("""[\d,.]+\s*(G|M|K|T)""", RegexOption.IGNORE_CASE)
        )
        
        // Keywords for element identification
        private val TITLE_KEYWORDS = listOf("name", "title", "torrent", "file")
        private val SEEDERS_KEYWORDS = listOf("seed", "se", "s", "up", "ul")
        private val LEECHERS_KEYWORDS = listOf("leech", "le", "l", "down", "dl", "peer")
        private val SIZE_KEYWORDS = listOf("size", "sz", "filesize")
        private val DATE_KEYWORDS = listOf("date", "added", "uploaded", "time", "age")
        private val CATEGORY_KEYWORDS = listOf("cat", "category", "type")
    }

    /**
     * Complete parsing analysis result with ALL detected selectors
     */
    data class DeepAnalysisResult(
        val success: Boolean,
        val confidence: Double,
        val siteInfo: SiteInfo,
        val detectedSelectors: DetectedSelectors,
        val tableStructure: TableStructure?,
        val apiEndpoints: List<DetectedAPI>,
        val paginationInfo: PaginationInfo?,
        val recommendations: List<String>,
        val rawHtmlSample: String
    )

    data class SiteInfo(
        val name: String,
        val baseUrl: String,
        val searchUrl: String,
        val searchMethod: String, // GET, POST, AJAX
        val searchParamName: String,
        val hasCloudflare: Boolean,
        val hasCaptcha: Boolean,
        val requiresAuth: Boolean,
        val isOnionSite: Boolean
    )

    data class DetectedSelectors(
        val resultContainer: SelectorInfo,
        val title: SelectorInfo,
        val magnetLink: SelectorInfo?,
        val torrentLink: SelectorInfo?,
        val seeders: SelectorInfo?,
        val leechers: SelectorInfo?,
        val size: SelectorInfo?,
        val date: SelectorInfo?,
        val category: SelectorInfo?,
        val uploader: SelectorInfo?,
        val detailsLink: SelectorInfo?
    )

    data class SelectorInfo(
        val selector: String,
        val confidence: Double,
        val sampleValue: String,
        val alternativeSelectors: List<String> = emptyList()
    )

    data class TableStructure(
        val headerRow: String?,
        val dataRows: String,
        val columnMapping: Map<Int, String>, // column index -> field name
        val totalColumns: Int
    )

    data class DetectedAPI(
        val url: String,
        val type: String, // torznab, json, rss, xml
        val method: String,
        val parameters: Map<String, String>,
        val requiresAuth: Boolean
    )

    data class PaginationInfo(
        val type: String, // "url_param", "page_number", "offset", "ajax"
        val paramName: String,
        val nextPageSelector: String?,
        val pageNumberPattern: String?
    )

    /**
     * Perform deep analysis of a torrent site
     * This is the MAIN FUNCTION that does all the magic!
     */
    suspend fun analyzeDeep(
        url: String,
        testQuery: String = "ubuntu"
    ): DeepAnalysisResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔬 Starting deep analysis of: $url")
        
        try {
            // Build search URL
            val searchUrl = buildSearchUrl(url, testQuery)
            Log.d(TAG, "📡 Fetching search page: $searchUrl")
            
            // Fetch the page
            val document = fetchPage(searchUrl)
            val rawHtml = document.html().take(5000) // Sample for debugging
            
            // Extract site info
            val siteInfo = extractSiteInfo(document, url, searchUrl, testQuery)
            Log.d(TAG, "✓ Site info extracted: ${siteInfo.name}")
            
            // Deep selector detection
            val detectedSelectors = detectAllSelectors(document)
            Log.d(TAG, "✓ Selectors detected, confidence: ${detectedSelectors.resultContainer.confidence}")
            
            // Detect table structure if applicable
            val tableStructure = detectTableStructure(document)
            
            // Find API endpoints
            val apis = detectAPIs(document, siteInfo.baseUrl)
            
            // Detect pagination
            val pagination = detectPagination(document, searchUrl)
            
            // Generate recommendations
            val recommendations = generateRecommendations(siteInfo, detectedSelectors, tableStructure)
            
            // Calculate overall confidence
            val confidence = calculateOverallConfidence(detectedSelectors, tableStructure, apis)
            
            DeepAnalysisResult(
                success = true,
                confidence = confidence,
                siteInfo = siteInfo,
                detectedSelectors = detectedSelectors,
                tableStructure = tableStructure,
                apiEndpoints = apis,
                paginationInfo = pagination,
                recommendations = recommendations,
                rawHtmlSample = rawHtml
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Deep analysis failed: ${e.message}", e)
            createFailedResult(url, e.message ?: "Unknown error")
        }
    }

    private suspend fun fetchPage(url: String): Document {
        val userAgent = USER_AGENTS.random()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }
            val html = response.body?.string() ?: throw Exception("Empty response")
            Jsoup.parse(html, url)
        }
    }

    private fun buildSearchUrl(baseUrl: String, query: String): String {
        val cleanUrl = baseUrl.trimEnd('/')
        val base = cleanUrl.replace(Regex("(https?://[^/]+).*"), "$1")
        
        // Try common search patterns
        val searchPatterns = listOf(
            "$base/search/$query",
            "$base/search?q=$query",
            "$base/search?search=$query",
            "$base/search.php?q=$query",
            "$base/torrents?search=$query",
            "$base/?s=$query",
            "$base/browse?search=$query"
        )
        
        // If URL already has query params, use as-is
        if (cleanUrl.contains("search") || cleanUrl.contains("?q=") || cleanUrl.contains("query")) {
            return cleanUrl.replace(Regex("(q|search|query)=[^&]*"), "$1=$query")
        }
        
        // Return first pattern (will be validated by caller)
        return searchPatterns.first()
    }

    private fun extractSiteInfo(
        document: Document,
        originalUrl: String,
        searchUrl: String,
        testQuery: String
    ): SiteInfo {
        // Site name detection
        val siteName = document.select("meta[property='og:site_name']").attr("content").takeIf { it.isNotBlank() }
            ?: document.select("meta[name='application-name']").attr("content").takeIf { it.isNotBlank() }
            ?: document.title().split("-", "|", "::").firstOrNull()?.trim()
            ?: originalUrl.replace(Regex("https?://(www\\.)?"), "").split("/").first()
        
        val baseUrl = originalUrl.replace(Regex("(https?://[^/]+).*"), "$1")
        
        // Detect search method
        val searchForm = document.select("form").find { form ->
            form.select("input").any { 
                it.attr("name").contains("search", ignoreCase = true) ||
                it.attr("name").contains("q", ignoreCase = true)
            }
        }
        
        val searchMethod = searchForm?.attr("method")?.uppercase()?.takeIf { it.isNotBlank() } ?: "GET"
        val searchParamName = searchForm?.select("input[type='text'], input[type='search']")
            ?.firstOrNull()?.attr("name")?.takeIf { it.isNotBlank() } ?: "q"
        
        // Detect protections
        val hasCloudflare = document.html().contains("cloudflare", ignoreCase = true)
        val hasCaptcha = document.select("script[src*='recaptcha'], div.g-recaptcha, div.h-captcha").isNotEmpty()
        val requiresAuth = document.select("input[type='password']").isNotEmpty() ||
                          document.select("a[href*='login']").isNotEmpty() &&
                          document.select("a[href*='logout']").isEmpty()
        
        return SiteInfo(
            name = siteName,
            baseUrl = baseUrl,
            searchUrl = searchUrl.replace(testQuery, "{query}"),
            searchMethod = searchMethod,
            searchParamName = searchParamName,
            hasCloudflare = hasCloudflare,
            hasCaptcha = hasCaptcha,
            requiresAuth = requiresAuth,
            isOnionSite = originalUrl.contains(".onion")
        )
    }

    /**
     * The CORE function that detects ALL selectors automatically
     */
    private fun detectAllSelectors(document: Document): DetectedSelectors {
        Log.d(TAG, "🔍 Detecting all selectors...")
        
        // Find result container first
        val resultContainer = detectResultContainer(document)
        Log.d(TAG, "  Result container: ${resultContainer.selector}")
        
        // Get sample result items
        val resultItems = document.select(resultContainer.selector)
        val sampleItem = resultItems.firstOrNull()
        
        // Detect each field
        val title = detectTitleSelector(document, sampleItem, resultContainer.selector)
        val magnetLink = detectMagnetSelector(document, sampleItem)
        val torrentLink = detectTorrentLinkSelector(document, sampleItem)
        val seeders = detectSeedersSelector(document, sampleItem, resultContainer.selector)
        val leechers = detectLeechersSelector(document, sampleItem, resultContainer.selector)
        val size = detectSizeSelector(document, sampleItem, resultContainer.selector)
        val date = detectDateSelector(document, sampleItem)
        val category = detectCategorySelector(document, sampleItem)
        val detailsLink = detectDetailsLinkSelector(document, sampleItem)
        
        return DetectedSelectors(
            resultContainer = resultContainer,
            title = title,
            magnetLink = magnetLink,
            torrentLink = torrentLink,
            seeders = seeders,
            leechers = leechers,
            size = size,
            date = date,
            category = category,
            uploader = null, // TODO: Implement
            detailsLink = detailsLink
        )
    }

    /**
     * Detect the container that holds all torrent results
     */
    private fun detectResultContainer(document: Document): SelectorInfo {
        val candidates = mutableListOf<Pair<String, Int>>()
        
        // Check common container patterns
        val containerPatterns = listOf(
            "table.torrents tbody tr" to 0,
            "table#torrents tbody tr" to 0,
            "table tbody tr" to 0,
            "table tr" to 0,
            ".torrent-list .torrent-item" to 1,
            ".torrent-row" to 1,
            ".torrent" to 1,
            ".search-result" to 1,
            ".result-item" to 1,
            ".results tr" to 0,
            "div[class*='torrent']" to 1,
            "tr[class*='torrent']" to 0,
            "ul.torrents > li" to 1
        )
        
        for ((pattern, _) in containerPatterns) {
            try {
                val elements = document.select(pattern)
                if (elements.size >= 2) { // At least 2 results
                    candidates.add(pattern to elements.size)
                }
            } catch (e: Exception) {
                // Invalid selector, skip
            }
        }
        
        // Also try generic table detection
        document.select("table").forEach { table ->
            val rows = table.select("tr")
            if (rows.size >= 3) {
                // Check if rows have torrent-like content
                val hasLinks = rows.any { it.select("a[href]").isNotEmpty() }
                val hasNumbers = rows.any { it.text().contains(Regex("\\d+")) }
                if (hasLinks && hasNumbers) {
                    val selector = generateUniqueSelector(table) + " tr"
                    candidates.add(selector to rows.size)
                }
            }
        }
        
        // Select best candidate (most results)
        val best = candidates.maxByOrNull { it.second }
        
        return if (best != null) {
            val sample = document.select(best.first).firstOrNull()?.text()?.take(100) ?: ""
            SelectorInfo(
                selector = best.first,
                confidence = minOf(best.second / 10.0, 1.0),
                sampleValue = sample,
                alternativeSelectors = candidates.filter { it.first != best.first }.map { it.first }.take(3)
            )
        } else {
            SelectorInfo(
                selector = "table tr",
                confidence = 0.3,
                sampleValue = "",
                alternativeSelectors = listOf(".torrent", ".result")
            )
        }
    }

    /**
     * Detect title/name selector
     */
    private fun detectTitleSelector(document: Document, sampleItem: Element?, containerSelector: String): SelectorInfo {
        val candidates = mutableListOf<Triple<String, Double, String>>()
        
        // Common title patterns
        val titlePatterns = listOf(
            "a.title" to 0.9,
            "a.torrent-name" to 0.9,
            ".torrent-title a" to 0.85,
            ".name a" to 0.85,
            "td.name a" to 0.8,
            "td a[href*='torrent']" to 0.75,
            "td a[href*='details']" to 0.75,
            "a[href*='/torrent/']" to 0.7,
            "td:nth-child(1) a" to 0.6,
            "td:nth-child(2) a" to 0.6,
            ".coll-1 a" to 0.7
        )
        
        // Try each pattern
        for ((pattern, baseScore) in titlePatterns) {
            try {
                val elements = if (sampleItem != null) {
                    sampleItem.select(pattern)
                } else {
                    document.select("$containerSelector $pattern")
                }
                
                if (elements.isNotEmpty()) {
                    val text = elements.first().text()
                    // Title should be reasonably long
                    if (text.length in 5..200) {
                        val score = baseScore * (if (text.length > 20) 1.0 else 0.8)
                        candidates.add(Triple(pattern, score, text))
                    }
                }
            } catch (e: Exception) { }
        }
        
        // Also check by attribute analysis
        sampleItem?.select("a")?.forEach { link ->
            val href = link.attr("href")
            val text = link.text()
            if (text.length in 10..200 && (href.contains("torrent") || href.contains("detail"))) {
                val selector = generateRelativeSelector(link, sampleItem)
                if (selector.isNotBlank() && !candidates.any { it.first == selector }) {
                    candidates.add(Triple(selector, 0.7, text))
                }
            }
        }
        
        val best = candidates.maxByOrNull { it.second }
        
        return if (best != null) {
            SelectorInfo(
                selector = best.first,
                confidence = best.second,
                sampleValue = best.third.take(80),
                alternativeSelectors = candidates.filter { it.first != best.first }.map { it.first }.take(3)
            )
        } else {
            SelectorInfo(
                selector = "a",
                confidence = 0.3,
                sampleValue = "",
                alternativeSelectors = emptyList()
            )
        }
    }

    /**
     * Detect magnet link selector
     */
    private fun detectMagnetSelector(document: Document, sampleItem: Element?): SelectorInfo? {
        val magnetLinks = sampleItem?.select("a[href^='magnet:']")
            ?: document.select("a[href^='magnet:']")
        
        if (magnetLinks.isEmpty()) return null
        
        val link = magnetLinks.first()
        val selector = when {
            link.hasClass("magnet") -> "a.magnet"
            link.attr("title").contains("magnet", ignoreCase = true) -> "a[title*='magnet']"
            else -> "a[href^='magnet:']"
        }
        
        return SelectorInfo(
            selector = selector,
            confidence = 0.95,
            sampleValue = link.attr("href").take(100),
            alternativeSelectors = listOf("a[href^='magnet:']")
        )
    }

    /**
     * Detect .torrent download link selector
     */
    private fun detectTorrentLinkSelector(document: Document, sampleItem: Element?): SelectorInfo? {
        val torrentLinks = sampleItem?.select("a[href$='.torrent'], a[href*='download']")
            ?: document.select("a[href$='.torrent'], a[href*='download']")
        
        if (torrentLinks.isEmpty()) return null
        
        val link = torrentLinks.first()
        val href = link.attr("href")
        
        val selector = when {
            href.endsWith(".torrent") -> "a[href$='.torrent']"
            href.contains("download") -> "a[href*='download']"
            link.hasClass("download") -> "a.download"
            else -> "a[href*='.torrent']"
        }
        
        return SelectorInfo(
            selector = selector,
            confidence = 0.9,
            sampleValue = href.take(100),
            alternativeSelectors = listOf("a[href$='.torrent']", "a.download")
        )
    }

    /**
     * Detect seeders selector by finding numeric patterns
     */
    private fun detectSeedersSelector(document: Document, sampleItem: Element?, containerSelector: String): SelectorInfo? {
        return detectNumericField(document, sampleItem, containerSelector, SEEDERS_KEYWORDS, "seeders")
    }

    /**
     * Detect leechers selector
     */
    private fun detectLeechersSelector(document: Document, sampleItem: Element?, containerSelector: String): SelectorInfo? {
        return detectNumericField(document, sampleItem, containerSelector, LEECHERS_KEYWORDS, "leechers")
    }

    /**
     * Generic numeric field detector (seeders/leechers)
     */
    private fun detectNumericField(
        document: Document,
        sampleItem: Element?,
        containerSelector: String,
        keywords: List<String>,
        fieldName: String
    ): SelectorInfo? {
        val candidates = mutableListOf<Triple<String, Double, String>>()
        
        // Check by class/attribute keywords
        keywords.forEach { keyword ->
            listOf(
                "td.$keyword" to 0.9,
                "span.$keyword" to 0.85,
                ".$keyword" to 0.8,
                "td[class*='$keyword']" to 0.75,
                "[class*='$keyword']" to 0.7
            ).forEach { (pattern, score) ->
                try {
                    val elements = sampleItem?.select(pattern) ?: document.select("$containerSelector $pattern")
                    elements.firstOrNull()?.let { elem ->
                        val text = elem.text().trim()
                        if (text.matches(Regex("^[\\d,]+$")) || text.matches(Regex("^\\d+[KM]?$"))) {
                            candidates.add(Triple(pattern, score, text))
                        }
                    }
                } catch (e: Exception) { }
            }
        }
        
        // Try table column detection by header
        detectTableColumnByHeader(document, keywords)?.let { (selector, text) ->
            candidates.add(Triple(selector, 0.85, text))
        }
        
        val best = candidates.maxByOrNull { it.second }
        
        return best?.let {
            SelectorInfo(
                selector = it.first,
                confidence = it.second,
                sampleValue = it.third,
                alternativeSelectors = candidates.filter { c -> c.first != it.first }.map { c -> c.first }.take(2)
            )
        }
    }

    /**
     * Detect size selector
     */
    private fun detectSizeSelector(document: Document, sampleItem: Element?, containerSelector: String): SelectorInfo? {
        val candidates = mutableListOf<Triple<String, Double, String>>()
        
        // Check by keywords
        SIZE_KEYWORDS.forEach { keyword ->
            listOf(
                "td.$keyword" to 0.9,
                ".$keyword" to 0.85,
                "td[class*='$keyword']" to 0.8,
                "[class*='$keyword']" to 0.75
            ).forEach { (pattern, score) ->
                try {
                    val elements = sampleItem?.select(pattern) ?: document.select("$containerSelector $pattern")
                    elements.firstOrNull()?.let { elem ->
                        val text = elem.text().trim()
                        if (SIZE_PATTERNS.any { it.containsMatchIn(text) }) {
                            candidates.add(Triple(pattern, score, text))
                        }
                    }
                } catch (e: Exception) { }
            }
        }
        
        // Try to find any element with size-like content
        sampleItem?.select("td, span, div")?.forEach { elem ->
            val text = elem.text().trim()
            if (SIZE_PATTERNS.any { it.containsMatchIn(text) } && text.length < 20) {
                val selector = generateRelativeSelector(elem, sampleItem)
                if (selector.isNotBlank() && !candidates.any { it.first == selector }) {
                    candidates.add(Triple(selector, 0.6, text))
                }
            }
        }
        
        val best = candidates.maxByOrNull { it.second }
        
        return best?.let {
            SelectorInfo(
                selector = it.first,
                confidence = it.second,
                sampleValue = it.third,
                alternativeSelectors = candidates.filter { c -> c.first != it.first }.map { c -> c.first }.take(2)
            )
        }
    }

    /**
     * Detect date/time selector
     */
    private fun detectDateSelector(document: Document, sampleItem: Element?): SelectorInfo? {
        val datePatterns = listOf(
            Regex("""\d{4}-\d{2}-\d{2}"""),
            Regex("""\d{2}/\d{2}/\d{4}"""),
            Regex("""\d+ (min|hour|day|week|month|year)s? ago""", RegexOption.IGNORE_CASE)
        )
        
        sampleItem?.select("td, span, time")?.forEach { elem ->
            val text = elem.text().trim()
            if (datePatterns.any { it.containsMatchIn(text) }) {
                return SelectorInfo(
                    selector = generateRelativeSelector(elem, sampleItem),
                    confidence = 0.8,
                    sampleValue = text,
                    alternativeSelectors = emptyList()
                )
            }
        }
        
        return null
    }

    /**
     * Detect category selector
     */
    private fun detectCategorySelector(document: Document, sampleItem: Element?): SelectorInfo? {
        val categorySelectors = listOf(
            "td.cat" to 0.9,
            ".category" to 0.85,
            "td[class*='cat']" to 0.8,
            "a[href*='cat=']" to 0.75
        )
        
        for ((selector, confidence) in categorySelectors) {
            val elements = sampleItem?.select(selector) ?: continue
            elements.firstOrNull()?.let { elem ->
                return SelectorInfo(
                    selector = selector,
                    confidence = confidence,
                    sampleValue = elem.text().take(30),
                    alternativeSelectors = emptyList()
                )
            }
        }
        
        return null
    }

    /**
     * Detect details page link
     */
    private fun detectDetailsLinkSelector(document: Document, sampleItem: Element?): SelectorInfo? {
        val detailsPatterns = listOf(
            "a[href*='/torrent/']" to 0.9,
            "a[href*='/details/']" to 0.9,
            "a[href*='id=']" to 0.7,
            "a.torrent-name" to 0.85
        )
        
        for ((selector, confidence) in detailsPatterns) {
            val elements = sampleItem?.select(selector) ?: continue
            elements.firstOrNull()?.let { elem ->
                return SelectorInfo(
                    selector = selector,
                    confidence = confidence,
                    sampleValue = elem.attr("href").take(100),
                    alternativeSelectors = emptyList()
                )
            }
        }
        
        return null
    }

    /**
     * Detect table structure with column mapping
     */
    private fun detectTableStructure(document: Document): TableStructure? {
        val tables = document.select("table")
        
        for (table in tables) {
            val headerRow = table.select("thead tr, tr:first-child").firstOrNull()
            val headers = headerRow?.select("th, td")?.map { it.text().lowercase().trim() } ?: continue
            
            if (headers.size < 3) continue
            
            val columnMapping = mutableMapOf<Int, String>()
            
            headers.forEachIndexed { index, header ->
                when {
                    TITLE_KEYWORDS.any { header.contains(it) } -> columnMapping[index] = "title"
                    SEEDERS_KEYWORDS.any { header.contains(it) } -> columnMapping[index] = "seeders"
                    LEECHERS_KEYWORDS.any { header.contains(it) } -> columnMapping[index] = "leechers"
                    SIZE_KEYWORDS.any { header.contains(it) } -> columnMapping[index] = "size"
                    DATE_KEYWORDS.any { header.contains(it) } -> columnMapping[index] = "date"
                    CATEGORY_KEYWORDS.any { header.contains(it) } -> columnMapping[index] = "category"
                }
            }
            
            if (columnMapping.isNotEmpty()) {
                return TableStructure(
                    headerRow = generateUniqueSelector(headerRow) ?: "thead tr",
                    dataRows = generateUniqueSelector(table) + " tbody tr",
                    columnMapping = columnMapping,
                    totalColumns = headers.size
                )
            }
        }
        
        return null
    }

    /**
     * Detect table column by analyzing headers
     */
    private fun detectTableColumnByHeader(document: Document, keywords: List<String>): Pair<String, String>? {
        document.select("table").forEach { table ->
            val headerRow = table.select("thead tr, tr:first-child").firstOrNull() ?: return@forEach
            val headers = headerRow.select("th, td")
            
            headers.forEachIndexed { index, header ->
                val headerText = header.text().lowercase()
                if (keywords.any { headerText.contains(it) }) {
                    val dataRows = table.select("tbody tr, tr:not(:first-child)")
                    dataRows.firstOrNull()?.select("td")?.getOrNull(index)?.let { cell ->
                        return "td:nth-child(${index + 1})" to cell.text()
                    }
                }
            }
        }
        
        return null
    }

    /**
     * Detect API endpoints
     */
    private fun detectAPIs(document: Document, baseUrl: String): List<DetectedAPI> {
        val apis = mutableListOf<DetectedAPI>()
        
        // Check for Torznab/RSS
        document.select("link[type='application/rss+xml']").forEach { link ->
            apis.add(DetectedAPI(
                url = resolveUrl(baseUrl, link.attr("href")),
                type = "rss",
                method = "GET",
                parameters = emptyMap(),
                requiresAuth = false
            ))
        }
        
        // Check for API in scripts
        val scripts = document.select("script").joinToString("\n") { it.html() }
        
        Regex("""["'](/api/[^"']+)["']""").findAll(scripts).forEach { match ->
            apis.add(DetectedAPI(
                url = "$baseUrl${match.groupValues[1]}",
                type = "json",
                method = "GET",
                parameters = emptyMap(),
                requiresAuth = false
            ))
        }
        
        // Check for Torznab
        if (scripts.contains("torznab", ignoreCase = true) || document.html().contains("torznab", ignoreCase = true)) {
            apis.add(DetectedAPI(
                url = "$baseUrl/api",
                type = "torznab",
                method = "GET",
                parameters = mapOf("t" to "search", "q" to "{query}", "apikey" to "{apikey}"),
                requiresAuth = true
            ))
        }
        
        return apis
    }

    /**
     * Detect pagination mechanism
     */
    private fun detectPagination(document: Document, searchUrl: String): PaginationInfo? {
        // Check for page parameter in URL
        if (searchUrl.contains("page=") || searchUrl.contains("p=")) {
            return PaginationInfo(
                type = "url_param",
                paramName = if (searchUrl.contains("page=")) "page" else "p",
                nextPageSelector = null,
                pageNumberPattern = "page=\\d+"
            )
        }
        
        // Check for pagination links
        val paginationSelectors = listOf(
            ".pagination a.next",
            ".pager a[rel='next']",
            "a.next",
            "a[href*='page=']"
        )
        
        for (selector in paginationSelectors) {
            document.select(selector).firstOrNull()?.let { link ->
                return PaginationInfo(
                    type = "page_number",
                    paramName = "page",
                    nextPageSelector = selector,
                    pageNumberPattern = "page=\\d+"
                )
            }
        }
        
        return null
    }

    /**
     * Generate recommendations based on analysis
     */
    private fun generateRecommendations(
        siteInfo: SiteInfo,
        selectors: DetectedSelectors,
        tableStructure: TableStructure?
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (siteInfo.hasCloudflare) {
            recommendations.add("⚠️ Cloudflare detected - increase rate limiting to avoid blocks")
        }
        
        if (siteInfo.hasCaptcha) {
            recommendations.add("⚠️ CAPTCHA detected - manual solving may be required")
        }
        
        if (siteInfo.requiresAuth) {
            recommendations.add("🔒 Authentication required - configure credentials")
        }
        
        if (selectors.magnetLink == null && selectors.torrentLink == null) {
            recommendations.add("⚠️ No direct download links found - may need to parse details page")
        }
        
        if (selectors.seeders == null) {
            recommendations.add("ℹ️ Seeders not detected - results won't have health info")
        }
        
        if (tableStructure != null) {
            recommendations.add("✓ Table structure detected - using column-based extraction")
        }
        
        if (selectors.resultContainer.confidence > 0.8) {
            recommendations.add("✓ High confidence detection - configuration should work well")
        } else if (selectors.resultContainer.confidence < 0.5) {
            recommendations.add("⚠️ Low confidence - manual verification recommended")
        }
        
        return recommendations
    }

    private fun calculateOverallConfidence(
        selectors: DetectedSelectors,
        tableStructure: TableStructure?,
        apis: List<DetectedAPI>
    ): Double {
        var score = 0.0
        var factors = 0
        
        // Result container (critical)
        score += selectors.resultContainer.confidence * 2
        factors += 2
        
        // Title (critical)
        score += selectors.title.confidence * 2
        factors += 2
        
        // Download method (important)
        if (selectors.magnetLink != null) {
            score += selectors.magnetLink.confidence
            factors++
        } else if (selectors.torrentLink != null) {
            score += selectors.torrentLink.confidence
            factors++
        } else {
            factors++ // Penalize missing download
        }
        
        // Optional fields
        selectors.seeders?.let { score += it.confidence * 0.5; factors++ }
        selectors.leechers?.let { score += it.confidence * 0.3; factors++ }
        selectors.size?.let { score += it.confidence * 0.5; factors++ }
        
        // Bonus for table structure
        if (tableStructure != null) {
            score += 0.5
            factors++
        }
        
        // Bonus for APIs
        if (apis.isNotEmpty()) {
            score += 0.5
            factors++
        }
        
        return score / factors
    }

    private fun generateUniqueSelector(element: Element?): String? {
        if (element == null) return null
        
        return when {
            element.id().isNotBlank() -> "#${element.id()}"
            element.className().isNotBlank() -> ".${element.className().split(" ").first()}"
            else -> element.tagName()
        }
    }

    private fun generateRelativeSelector(element: Element, parent: Element?): String {
        if (parent == null) return element.cssSelector()
        
        return when {
            element.hasClass("") && element.className().isNotBlank() -> 
                ".${element.className().split(" ").first()}"
            element.tagName() == "td" -> {
                val index = element.parent()?.children()?.indexOf(element) ?: 0
                "td:nth-child(${index + 1})"
            }
            else -> element.tagName()
        }
    }

    private fun resolveUrl(baseUrl: String, url: String): String {
        return when {
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$baseUrl$url"
            else -> "$baseUrl/$url"
        }
    }

    private fun createFailedResult(url: String, error: String): DeepAnalysisResult {
        return DeepAnalysisResult(
            success = false,
            confidence = 0.0,
            siteInfo = SiteInfo(
                name = "",
                baseUrl = url,
                searchUrl = "",
                searchMethod = "GET",
                searchParamName = "q",
                hasCloudflare = false,
                hasCaptcha = false,
                requiresAuth = false,
                isOnionSite = url.contains(".onion")
            ),
            detectedSelectors = DetectedSelectors(
                resultContainer = SelectorInfo("", 0.0, ""),
                title = SelectorInfo("", 0.0, ""),
                magnetLink = null,
                torrentLink = null,
                seeders = null,
                leechers = null,
                size = null,
                date = null,
                category = null,
                uploader = null,
                detailsLink = null
            ),
            tableStructure = null,
            apiEndpoints = emptyList(),
            paginationInfo = null,
            recommendations = listOf("❌ Analysis failed: $error"),
            rawHtmlSample = ""
        )
    }
}
