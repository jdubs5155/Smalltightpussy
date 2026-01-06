package com.zim.jackettprowler

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.TimeUnit

/**
 * Service class for interacting with Torznab API (Jackett/Prowlarr)
 */
class TorznabService(
    private val baseUrl: String,
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    enum class SearchType {
        SEARCH,    // t=search - general search
        TVSEARCH,  // t=tvsearch - TV show search
        MOVIE,     // t=movie - movie search
        MUSIC,     // t=music - music search
        BOOK       // t=book - book search
    }

    /**
     * Test connection to the Torznab service
     */
    fun testConnection(): Boolean {
        return try {
            val url = buildUrl("caps", emptyMap())
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get capabilities of the Torznab service
     */
    fun getCapabilities(): TorznabCapabilities {
        val url = buildUrl("caps", emptyMap())
        val request = Request.Builder().url(url).get().build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code}")
            }
            
            val xml = response.body?.string() ?: throw RuntimeException("Empty response")
            return parseCapabilities(xml)
        }
    }

    /**
     * Search for torrents
     */
    fun search(
        query: String,
        searchType: SearchType = SearchType.SEARCH,
        category: String? = null,
        limit: Int = 100,
        offset: Int = 0
    ): List<TorrentResult> {
        val params = mutableMapOf(
            "q" to query,
            "limit" to limit.toString(),
            "offset" to offset.toString()
        )
        
        if (category != null) {
            params["cat"] = category
        }
        
        val url = buildUrl(searchType.name.lowercase(), params)
        val request = Request.Builder().url(url).get().build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code}")
            }
            
            val xml = response.body?.string() ?: throw RuntimeException("Empty response")
            return parseTorznabResults(xml)
        }
    }

    /**
     * Search for TV shows
     */
    fun searchTv(
        query: String,
        season: Int? = null,
        episode: Int? = null,
        imdbId: String? = null,
        tvdbId: String? = null
    ): List<TorrentResult> {
        val params = mutableMapOf("q" to query)
        
        season?.let { params["season"] = it.toString() }
        episode?.let { params["ep"] = it.toString() }
        imdbId?.let { params["imdbid"] = it }
        tvdbId?.let { params["tvdbid"] = it }
        
        val url = buildUrl("tvsearch", params)
        val request = Request.Builder().url(url).get().build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code}")
            }
            
            val xml = response.body?.string() ?: throw RuntimeException("Empty response")
            return parseTorznabResults(xml)
        }
    }

    /**
     * Search for movies
     */
    fun searchMovie(
        query: String,
        imdbId: String? = null,
        tmdbId: String? = null
    ): List<TorrentResult> {
        val params = mutableMapOf("q" to query)
        
        imdbId?.let { params["imdbid"] = it }
        tmdbId?.let { params["tmdbid"] = it }
        
        val url = buildUrl("movie", params)
        val request = Request.Builder().url(url).get().build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code}")
            }
            
            val xml = response.body?.string() ?: throw RuntimeException("Empty response")
            return parseTorznabResults(xml)
        }
    }

    private fun buildUrl(function: String, params: Map<String, String>): String {
        // Prowlarr uses /api/v1/search, Jackett uses /api/v2.0/indexers/all/results/torznab/api
        val apiPath = if (baseUrl.contains("prowlarr", ignoreCase = true) || 
                           baseUrl.contains(":9696") || 
                           testUrlForProwlarr()) {
            "/api/v1/search"
        } else {
            "/api/v2.0/indexers/all/results/torznab/api"
        }
        
        val url = "${baseUrl.trimEnd('/')}$apiPath"
        val uri = Uri.parse(url).buildUpon()
            .appendQueryParameter("t", function)
            .appendQueryParameter("apikey", apiKey)
        
        params.forEach { (key, value) ->
            uri.appendQueryParameter(key, value)
        }
        
        return uri.build().toString()
    }
    
    private fun testUrlForProwlarr(): Boolean {
        // Test if this is a Prowlarr instance by checking the response header or capabilities endpoint
        return try {
            val testUrl = "${baseUrl.trimEnd('/')}/api/v1/search?t=caps&apikey=$apiKey"
            val request = Request.Builder().url(testUrl).get().build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun parseCapabilities(xml: String): TorznabCapabilities {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())
        
        val categories = mutableListOf<TorznabCategory>()
        val searchModes = mutableSetOf<String>()
        var serverTitle = ""
        
        var event = parser.eventType
        var currentCategory: TorznabCategory? = null
        
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "server" -> {
                            serverTitle = parser.getAttributeValue(null, "title") ?: ""
                        }
                        "searching" -> {
                            // Parse search capabilities
                        }
                        "search" -> {
                            val available = parser.getAttributeValue(null, "available")
                            if (available == "yes") {
                                searchModes.add("search")
                            }
                        }
                        "tv-search" -> {
                            val available = parser.getAttributeValue(null, "available")
                            if (available == "yes") {
                                searchModes.add("tvsearch")
                            }
                        }
                        "movie-search" -> {
                            val available = parser.getAttributeValue(null, "available")
                            if (available == "yes") {
                                searchModes.add("movie")
                            }
                        }
                        "category" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val name = parser.getAttributeValue(null, "name")
                            if (id != null && name != null) {
                                currentCategory = TorznabCategory(id, name, mutableListOf())
                                categories.add(currentCategory)
                            }
                        }
                        "subcat" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val name = parser.getAttributeValue(null, "name")
                            if (id != null && name != null && currentCategory != null) {
                                currentCategory.subcategories.add(TorznabCategory(id, name, mutableListOf()))
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "category") {
                        currentCategory = null
                    }
                }
            }
            event = parser.next()
        }
        
        return TorznabCapabilities(serverTitle, categories, searchModes.toList())
    }

    private fun parseTorznabResults(xml: String): List<TorrentResult> {
        val results = mutableListOf<TorrentResult>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())
        
        var event = parser.eventType
        
        // Current item data
        var title = ""
        var link = ""
        var guid = ""
        var description = ""
        var pubDate = ""
        var category = ""
        var size = 0L
        var seeders = 0
        var peers = 0
        var leechers = 0
        var grabs = 0
        var indexer: String? = null
        var downloadUrl = ""
        var magnetUrl = ""
        var infoHash = ""
        var imdbId = ""
        
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "item" -> {
                            // Reset for new item
                            title = ""
                            link = ""
                            guid = ""
                            description = ""
                            pubDate = ""
                            category = ""
                            size = 0L
                            seeders = 0
                            peers = 0
                            leechers = 0
                            grabs = 0
                            indexer = null
                            downloadUrl = ""
                            magnetUrl = ""
                            infoHash = ""
                            imdbId = ""
                        }
                        "title" -> {
                            title = parser.nextText()
                        }
                        "link" -> {
                            link = parser.nextText()
                        }
                        "guid" -> {
                            guid = parser.nextText()
                        }
                        "description" -> {
                            description = parser.nextText()
                        }
                        "pubDate" -> {
                            pubDate = parser.nextText()
                        }
                        "category" -> {
                            category = parser.nextText()
                        }
                        "size" -> {
                            size = parser.nextText().toLongOrNull() ?: 0L
                        }
                        "enclosure" -> {
                            downloadUrl = parser.getAttributeValue(null, "url") ?: ""
                        }
                        "torznab:attr", "attr" -> {
                            val nameAttr = parser.getAttributeValue(null, "name")
                            val valueAttr = parser.getAttributeValue(null, "value") ?: ""
                            
                            when (nameAttr) {
                                "seeders" -> seeders = valueAttr.toIntOrNull() ?: 0
                                "peers" -> peers = valueAttr.toIntOrNull() ?: 0
                                "leechers" -> leechers = valueAttr.toIntOrNull() ?: 0
                                "grabs" -> grabs = valueAttr.toIntOrNull() ?: 0
                                "size" -> size = valueAttr.toLongOrNull() ?: size
                                "indexer", "jackett_indexer" -> indexer = valueAttr
                                "downloadurl" -> downloadUrl = valueAttr
                                "magneturl" -> magnetUrl = valueAttr
                                "infohash" -> infoHash = valueAttr
                                "imdbid", "imdb" -> imdbId = valueAttr
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item") {
                        if (title.isNotEmpty() && (link.isNotEmpty() || downloadUrl.isNotEmpty() || magnetUrl.isNotEmpty())) {
                            // Use downloadUrl if available, otherwise link, otherwise magnetUrl
                            val torrentLink = when {
                                downloadUrl.isNotEmpty() -> downloadUrl
                                link.isNotEmpty() -> link
                                else -> magnetUrl
                            }
                            
                            results.add(
                                TorrentResult(
                                    title = title,
                                    link = torrentLink,
                                    sizeBytes = size,
                                    seeders = seeders,
                                    indexer = indexer,
                                    guid = guid,
                                    description = description,
                                    pubDate = pubDate,
                                    category = category,
                                    peers = peers,
                                    leechers = leechers,
                                    grabs = grabs,
                                    magnetUrl = magnetUrl,
                                    infoHash = infoHash,
                                    imdbId = imdbId
                                )
                            )
                        }
                    }
                }
            }
            event = parser.next()
        }
        
        return results
    }
}

data class TorznabCapabilities(
    val serverTitle: String,
    val categories: List<TorznabCategory>,
    val searchModes: List<String>
)

data class TorznabCategory(
    val id: String,
    val name: String,
    val subcategories: MutableList<TorznabCategory>
)
