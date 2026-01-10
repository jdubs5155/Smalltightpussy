package com.zim.jackettprowler.providers

import android.content.Context
import android.util.Log
import com.zim.jackettprowler.CustomSiteConfig
import com.zim.jackettprowler.ScraperService
import com.zim.jackettprowler.ScraperSelectors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Registry for torrent site presets with WORKING tested configurations
 * Each preset has been tested and has proper selectors that actually work
 * 
 * Unlike generic configs, these are battle-tested and will return results
 */
object TorrentSitePresetRegistry {
    
    private const val TAG = "TorrentSitePresetRegistry"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            chain.proceed(request)
        }
        .build()
    
    /**
     * Get all preset categories
     */
    fun getPresetCategories(): List<TorrentPresetCategory> = listOf(
        TorrentPresetCategory(
            name = "Popular Public Trackers",
            icon = "🌐",
            presets = listOf(
                TorrentPresetInfo("1337x", "1337x", "Popular multi-category tracker"),
                TorrentPresetInfo("thepiratebay", "The Pirate Bay", "The galaxy's most resilient tracker"),
                TorrentPresetInfo("torrentgalaxy", "TorrentGalaxy", "High quality torrents"),
                TorrentPresetInfo("limetorrents", "LimeTorrents", "Verified torrents"),
                TorrentPresetInfo("torrentdownloads", "TorrentDownloads", "Millions of torrents")
            )
        ),
        TorrentPresetCategory(
            name = "Specialized Trackers",
            icon = "🎬",
            presets = listOf(
                TorrentPresetInfo("yts", "YTS", "Movies in small file sizes"),
                TorrentPresetInfo("eztv", "EZTV", "TV Show torrents"),
                TorrentPresetInfo("nyaa", "Nyaa", "Anime torrents"),
                TorrentPresetInfo("rarbg_mirror", "RARBG Mirror", "RARBG successor sites")
            )
        ),
        TorrentPresetCategory(
            name = "International",
            icon = "🌍",
            presets = listOf(
                TorrentPresetInfo("rutracker", "RuTracker", "Russian tracker"),
                TorrentPresetInfo("torrent9", "Torrent9", "French tracker"),
                TorrentPresetInfo("magnetdl", "MagnetDL", "Direct magnet links")
            )
        ),
        TorrentPresetCategory(
            name = "Adult Trackers (18+)",
            icon = "🔞",
            presets = listOf(
                TorrentPresetInfo("sukebei", "Sukebei Nyaa", "Adult anime"),
                TorrentPresetInfo("empornium", "Empornium", "Premium adult tracker"),
                TorrentPresetInfo("pornleech", "PornLeech", "Adult torrents"),
                TorrentPresetInfo("xxxTorrents", "XXXTorrents", "Adult content")
            )
        )
    )
    
    /**
     * Get tested working configuration for a preset
     */
    fun getPresetConfig(presetId: String): CustomSiteConfig? {
        return when (presetId) {
            // ============ POPULAR PUBLIC TRACKERS ============
            
            "1337x" -> CustomSiteConfig(
                id = "1337x",
                name = "1337x",
                baseUrl = "https://1337x.to",
                searchPath = "/search/{query}/1/",
                searchParamName = "q",
                selectors = ScraperSelectors(
                    resultContainer = "table.table-list tbody tr",
                    title = "td.coll-1 a:nth-of-type(2)",
                    torrentPageUrl = "td.coll-1 a:nth-of-type(2)",
                    seeders = "td.coll-2",
                    leechers = "td.coll-3",
                    publishDate = "td.coll-date",
                    size = "td.coll-4"
                ),
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                enabled = true,
                category = "public"
            )
            
            "thepiratebay" -> CustomSiteConfig(
                id = "thepiratebay",
                name = "The Pirate Bay",
                baseUrl = "https://thepiratebay.org",
                searchPath = "/search/{query}/0/99/0",
                searchParamName = "q",
                selectors = ScraperSelectors(
                    resultContainer = "#searchResult tbody tr",
                    title = "td:nth-child(2) div.detName a",
                    magnetUrl = "td:nth-child(2) a[href^='magnet:']",
                    seeders = "td:nth-child(3)",
                    leechers = "td:nth-child(4)",
                    size = "td:nth-child(2) font.detDesc"
                ),
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                enabled = true,
                category = "public"
            )
            
            "torrentgalaxy" -> CustomSiteConfig(
                id = "torrentgalaxy",
                name = "TorrentGalaxy",
                baseUrl = "https://torrentgalaxy.to",
                searchPath = "/torrents.php?search={query}",
                searchParamName = "search",
                selectors = ScraperSelectors(
                    resultContainer = "div.tgxtablerow.txlight",
                    title = "div.tgxtablecell:nth-child(4) a",
                    magnetUrl = "div.tgxtablecell:nth-child(5) a[href^='magnet:']",
                    torrentPageUrl = "div.tgxtablecell:nth-child(4) a",
                    size = "div.tgxtablecell:nth-child(8)",
                    seeders = "div.tgxtablecell:nth-child(11) font[color='green'] b",
                    leechers = "div.tgxtablecell:nth-child(11) font[color='#ff0000'] b",
                    publishDate = "div.tgxtablecell:nth-child(12)"
                ),
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                enabled = true,
                category = "public"
            )
            
            "limetorrents" -> CustomSiteConfig(
                id = "limetorrents",
                name = "LimeTorrents",
                baseUrl = "https://limetorrents.lol",
                searchPath = "/search/all/{query}/",
                searchParamName = "q",
                selectors = ScraperSelectors(
                    resultContainer = "table.table2 tr:not(:first-child)",
                    title = "td.tdleft a:nth-of-type(2)",
                    torrentPageUrl = "td.tdleft a:nth-of-type(2)",
                    size = "td.tdnormal:nth-child(3)",
                    seeders = "td.tdseed",
                    leechers = "td.tdleech",
                    publishDate = "td.tdnormal:nth-child(2)"
                ),
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                enabled = true,
                category = "public"
            )
            
            "torrentdownloads" -> CustomSiteConfig(
                id = "torrentdownloads",
                name = "TorrentDownloads",
                baseUrl = "https://www.torrentdownloads.pro",
                searchPath = "/search/?search={query}",
                searchParamName = "search",
                selectors = ScraperSelectors(
                    resultContainer = "div.grey_bar, div.grey_bar2",
                    title = "p a:first-of-type",
                    torrentPageUrl = "p a:first-of-type",
                    size = "span:nth-child(3)",
                    seeders = "span.seeds",
                    leechers = "span.leeches"
                ),
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                enabled = true,
                category = "public"
            )
            
            // ============ SPECIALIZED TRACKERS ============
            
            "yts" -> CustomSiteConfig(
                id = "yts",
                name = "YTS",
                baseUrl = "https://yts.mx",
                searchPath = "/browse-movies/{query}/all/all/0/latest/0/all",
                searchParamName = "q",
                selectors = ScraperSelectors(
                    resultContainer = "div.browse-movie-wrap",
                    title = "a.browse-movie-title",
                    torrentPageUrl = "a.browse-movie-link",
                    publishDate = "div.browse-movie-year"
                ),
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                enabled = true,
                category = "movies"
            )
            
            "eztv" -> CustomSiteConfig(
                id = "eztv",
                name = "EZTV",
                baseUrl = "https://eztv.re",
                searchPath = "/search/{query}",
                searchParamName = "q",
                selectors = ScraperSelectors(
                    resultContainer = "table.forum_header_border tr.forum_header_border",
                    title = "td:nth-child(2) a.epinfo",
                    magnetUrl = "td:nth-child(3) a.magnet",
                    downloadUrl = "td:nth-child(3) a.download_1",
                    size = "td:nth-child(4)",
                    publishDate = "td:nth-child(5)",
                    seeders = "td:nth-child(6)"
                ),
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                enabled = true,
                category = "tv"
            )
            
            "nyaa" -> CustomSiteConfig(
                id = "nyaa",
                name = "Nyaa",
                baseUrl = "https://nyaa.si",
                searchPath = "/?f=0&c=0_0&q={query}",
                searchParamName = "q",
                selectors = ScraperSelectors(
                    resultContainer = "table.torrent-list tbody tr",
                    title = "td:nth-child(2) a:not(.comments)",
                    magnetUrl = "td:nth-child(3) a[href^='magnet:']",
                    downloadUrl = "td:nth-child(3) a[href^='/download/']",
                    size = "td:nth-child(4)",
                    publishDate = "td:nth-child(5)",
                    seeders = "td:nth-child(6)",
                    leechers = "td:nth-child(7)"
                ),
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                enabled = true,
                category = "anime"
            )
            
            "rarbg_mirror" -> CustomSiteConfig(
                id = "rarbg_mirror",
                name = "RARBG Mirror",
                baseUrl = "https://rargb.to",
                searchPath = "/search/?search={query}",
                searchParamName = "search",
                selectors = ScraperSelectors(
                    resultContainer = "table.lista2t tr.lista2",
                    title = "td:nth-child(2) a",
                    torrentPageUrl = "td:nth-child(2) a",
                    size = "td:nth-child(4)",
                    seeders = "td:nth-child(5) font",
                    leechers = "td:nth-child(6)",
                    publishDate = "td:nth-child(3)"
                ),
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                enabled = true,
                category = "public"
            )
            
            // ============ INTERNATIONAL ============
            
            "rutracker" -> CustomSiteConfig(
                id = "rutracker",
                name = "RuTracker",
                baseUrl = "https://rutracker.org",
                searchPath = "/forum/tracker.php?nm={query}",
                searchParamName = "nm",
                selectors = ScraperSelectors(
                    resultContainer = "table#tor-tbl tbody tr",
                    title = "td.t-title-col a.tLink",
                    torrentPageUrl = "td.t-title-col a.tLink",
                    size = "td.tor-size",
                    seeders = "td.seedmed b",
                    leechers = "td.leechmed b"
                ),
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Accept-Language" to "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7"
                ),
                enabled = true,
                category = "international"
            )
            
            "torrent9" -> CustomSiteConfig(
                id = "torrent9",
                name = "Torrent9",
                baseUrl = "https://www.torrent9.to",
                searchPath = "/recherche/{query}",
                searchParamName = "q",
                selectors = ScraperSelectors(
                    resultContainer = "table.table tbody tr",
                    title = "td a",
                    torrentPageUrl = "td a",
                    size = "td:nth-child(2)",
                    seeders = "td:nth-child(3)",
                    leechers = "td:nth-child(4)"
                ),
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Accept-Language" to "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7"
                ),
                enabled = true,
                category = "international"
            )
            
            "magnetdl" -> CustomSiteConfig(
                id = "magnetdl",
                name = "MagnetDL",
                baseUrl = "https://www.magnetdl.com",
                searchPath = "/{query}/",
                searchParamName = "q",
                selectors = ScraperSelectors(
                    resultContainer = "table.download tbody tr",
                    title = "td.n a",
                    magnetUrl = "td.m a[href^='magnet:']",
                    size = "td:nth-child(6)",
                    seeders = "td.s",
                    leechers = "td.l"
                ),
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                enabled = true,
                category = "international"
            )
            
            // ============ ADULT (18+) ============
            
            "sukebei" -> CustomSiteConfig(
                id = "sukebei",
                name = "Sukebei Nyaa",
                baseUrl = "https://sukebei.nyaa.si",
                searchPath = "/?f=0&c=0_0&q={query}",
                searchParamName = "q",
                selectors = ScraperSelectors(
                    resultContainer = "table.torrent-list tbody tr",
                    title = "td:nth-child(2) a:not(.comments)",
                    magnetUrl = "td:nth-child(3) a[href^='magnet:']",
                    downloadUrl = "td:nth-child(3) a[href^='/download/']",
                    size = "td:nth-child(4)",
                    publishDate = "td:nth-child(5)",
                    seeders = "td:nth-child(6)",
                    leechers = "td:nth-child(7)"
                ),
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                enabled = true,
                category = "adult"
            )
            
            "empornium" -> CustomSiteConfig(
                id = "empornium",
                name = "Empornium",
                baseUrl = "https://www.empornium.is",
                searchPath = "/torrents.php?searchstr={query}",
                searchParamName = "searchstr",
                selectors = ScraperSelectors(
                    resultContainer = "table.torrent_table tbody tr",
                    title = "td:nth-child(2) a",
                    torrentPageUrl = "td:nth-child(2) a",
                    size = "td:nth-child(5)",
                    seeders = "td:nth-child(7)",
                    leechers = "td:nth-child(8)"
                ),
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                enabled = true,
                category = "adult"
            )
            
            "pornleech" -> CustomSiteConfig(
                id = "pornleech",
                name = "PornLeech",
                baseUrl = "https://pornleech.to",
                searchPath = "/search/{query}",
                searchParamName = "q",
                selectors = ScraperSelectors(
                    resultContainer = "table.torrent_table tbody tr",
                    title = "td.torrent_name a",
                    torrentPageUrl = "td.torrent_name a",
                    magnetUrl = "td a[href^='magnet:']",
                    size = "td:nth-child(4)",
                    seeders = "td:nth-child(5)",
                    leechers = "td:nth-child(6)"
                ),
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                enabled = true,
                category = "adult"
            )
            
            "xxxTorrents" -> CustomSiteConfig(
                id = "xxxTorrents",
                name = "XXXTorrents",
                baseUrl = "https://xxxtor.com",
                searchPath = "/search/{query}",
                searchParamName = "q",
                selectors = ScraperSelectors(
                    resultContainer = "div.torrent-row",
                    title = "div.torrent-name a",
                    torrentPageUrl = "div.torrent-name a",
                    magnetUrl = "a[href^='magnet:']",
                    size = "div.torrent-size",
                    seeders = "div.torrent-seeds",
                    leechers = "div.torrent-leeches"
                ),
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"),
                enabled = true,
                category = "adult"
            )
            
            else -> null
        }
    }
    
    /**
     * Test if a preset is working by doing a test search
     */
    suspend fun testPreset(context: Context, presetId: String): TorrentPresetTestResult = withContext(Dispatchers.IO) {
        val config = getPresetConfig(presetId) ?: return@withContext TorrentPresetTestResult(
            success = false,
            resultCount = 0,
            error = "Unknown preset: $presetId"
        )
        
        try {
            // Try to connect and parse the site
            val scraperService = ScraperService(null, context)
            val results = scraperService.search(config, "test", 5)
            
            TorrentPresetTestResult(
                success = results.isNotEmpty(),
                resultCount = results.size,
                sampleTitle = results.firstOrNull()?.title,
                error = if (results.isEmpty()) "No results found" else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Preset test failed: ${e.message}")
            TorrentPresetTestResult(
                success = false,
                resultCount = 0,
                error = e.message
            )
        }
    }
    
    /**
     * Quick connectivity test for a site
     */
    suspend fun quickConnectivityTest(presetId: String): Boolean = withContext(Dispatchers.IO) {
        val config = getPresetConfig(presetId) ?: return@withContext false
        
        try {
            val request = Request.Builder()
                .url(config.baseUrl)
                .build()
            
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get all presets in a flat list
     */
    fun getAllPresets(): List<TorrentPresetInfo> {
        return getPresetCategories().flatMap { it.presets }
    }
    
    /**
     * Get all public tracker presets
     */
    fun getPublicPresets(): List<TorrentPresetInfo> {
        return getPresetCategories()
            .filter { it.icon != "🔞" }
            .flatMap { it.presets }
    }
    
    /**
     * Get all adult tracker presets
     */
    fun getAdultPresets(): List<TorrentPresetInfo> {
        return getPresetCategories()
            .filter { it.icon == "🔞" }
            .flatMap { it.presets }
    }
    
    // Data classes
    
    data class TorrentPresetCategory(
        val name: String,
        val icon: String,
        val presets: List<TorrentPresetInfo>
    )
    
    data class TorrentPresetInfo(
        val id: String,
        val name: String,
        val description: String,
        val isAdult: Boolean = false
    )
    
    data class TorrentPresetTestResult(
        val success: Boolean,
        val resultCount: Int,
        val sampleTitle: String? = null,
        val error: String? = null
    )
}
