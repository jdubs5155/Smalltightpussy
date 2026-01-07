package com.zim.jackettprowler.providers

import com.zim.jackettprowler.CustomSiteConfig
import com.zim.jackettprowler.SelectorType

/**
 * 1337x - One of the most popular torrent sites
 */
class Provider1337x : BaseIndexerProvider() {
    override val id = "1337x"
    override val name = "1337x"
    override val baseUrl = "https://1337x.to"
    override val description = "1337x is a popular public torrent site with movies, TV, games, music, and more"
    
    override fun getMirrors() = listOf(
        "https://1337x.st",
        "https://1337x.gd",
        "https://x1337x.ws",
        "https://x1337x.eu",
        "https://x1337x.se"
    )
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/{query}/1/",
        searchParamName = "q",
        selectors = selectors(
            container = "table.table-list tbody tr",
            title = "td.coll-1 a:nth-of-type(2)",
            torrentPageUrl = "td.coll-1 a:nth-of-type(2)",
            seeders = "td.coll-2",
            leechers = "td.coll-3",
            publishDate = "td.coll-date",
            size = "td.coll-4"
        ),
        headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        )
    )
}

/**
 * The Pirate Bay - The most resilient torrent site
 */
class ProviderThePirateBay : BaseIndexerProvider() {
    override val id = "thepiratebay"
    override val name = "The Pirate Bay"
    override val baseUrl = "https://thepiratebay.org"
    override val description = "The Pirate Bay is the galaxy's most resilient torrent site"
    
    override fun getMirrors() = listOf(
        "https://tpb.party",
        "https://thepiratebay.zone",
        "https://thepiratebay10.org",
        "https://pirateproxy.live",
        "https://thehiddenbay.com"
    )
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/{query}/0/99/0",
        selectors = selectors(
            container = "#searchResult tbody tr",
            title = "td:nth-child(2) div.detName a",
            magnetUrl = "td:nth-child(2) a[href^='magnet:']",
            seeders = "td:nth-child(3)",
            leechers = "td:nth-child(4)",
            size = "td:nth-child(2) font.detDesc"
        )
    )
}

/**
 * RARBG alternatives and successors
 */
class ProviderTorrentGalaxy : BaseIndexerProvider() {
    override val id = "torrentgalaxy"
    override val name = "TorrentGalaxy"
    override val baseUrl = "https://torrentgalaxy.to"
    override val description = "TorrentGalaxy - High quality torrents"
    
    override fun getMirrors() = listOf(
        "https://tgx.rs",
        "https://tgx.sb",
        "https://torrentgalaxy.mx"
    )
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrents.php?search={query}",
        selectors = selectors(
            container = "div.tgxtablerow.txlight",
            title = "div.tgxtablecell:nth-child(4) a",
            magnetUrl = "div.tgxtablecell:nth-child(5) a[href^='magnet:']",
            torrentPageUrl = "div.tgxtablecell:nth-child(4) a",
            size = "div.tgxtablecell:nth-child(8)",
            seeders = "div.tgxtablecell:nth-child(11) font[color='green'] b",
            leechers = "div.tgxtablecell:nth-child(11) font[color='#ff0000'] b",
            publishDate = "div.tgxtablecell:nth-child(12)"
        )
    )
}

/**
 * YTS - Movies in small file sizes
 */
class ProviderYTS : BaseIndexerProvider() {
    override val id = "yts"
    override val name = "YTS"
    override val baseUrl = "https://yts.mx"
    override val description = "YTS - High quality movies in small file sizes"
    override val category = listOf("Movies")
    
    override fun getMirrors() = listOf(
        "https://yts.mx",
        "https://yts.lt",
        "https://yts.am"
    )
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/browse-movies/{query}/all/all/0/latest/0/all",
        selectors = selectors(
            container = "div.browse-movie-wrap",
            title = "a.browse-movie-title",
            torrentPageUrl = "a.browse-movie-link",
            publishDate = "div.browse-movie-year"
        ),
        category = "movies"
    )
}

/**
 * Nyaa - Anime torrents
 */
class ProviderNyaa : BaseIndexerProvider() {
    override val id = "nyaa"
    override val name = "Nyaa"
    override val baseUrl = "https://nyaa.si"
    override val description = "Nyaa - Anime, manga, and more"
    override val category = listOf("TV", "Movies", "Books")
    
    override fun getMirrors() = listOf(
        "https://nyaa.land",
        "https://nyaa.iss.one",
        "https://nyaa.iss.ink"
    )
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/?f=0&c=0_0&q={query}",
        selectors = selectors(
            container = "table.torrent-list tbody tr",
            title = "td:nth-child(2) a:not(.comments)",
            magnetUrl = "td:nth-child(3) a[href^='magnet:']",
            downloadUrl = "td:nth-child(3) a[href^='/download/']",
            size = "td:nth-child(4)",
            publishDate = "td:nth-child(5)",
            seeders = "td:nth-child(6)",
            leechers = "td:nth-child(7)"
        )
    )
}

/**
 * LimeTorrents - Popular public tracker
 */
class ProviderLimeTorrents : BaseIndexerProvider() {
    override val id = "limetorrents"
    override val name = "LimeTorrents"
    override val baseUrl = "https://limetorrents.lol"
    override val description = "LimeTorrents - Verified torrents"
    
    override fun getMirrors() = listOf(
        "https://limetorrents.pro",
        "https://limetorrents.asia",
        "https://limetor.com"
    )
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/all/{query}/",
        selectors = selectors(
            container = "table.table2 tr:not(:first-child)",
            title = "td.tdleft a:nth-of-type(2)",
            torrentPageUrl = "td.tdleft a:nth-of-type(2)",
            size = "td.tdnormal:nth-child(3)",
            seeders = "td.tdseed",
            leechers = "td.tdleech",
            publishDate = "td.tdnormal:nth-child(2)"
        )
    )
}

/**
 * EZTV - TV Show torrents
 */
class ProviderEZTV : BaseIndexerProvider() {
    override val id = "eztv"
    override val name = "EZTV"
    override val baseUrl = "https://eztv.re"
    override val description = "EZTV - TV Show torrents"
    override val category = listOf("TV")
    
    override fun getMirrors() = listOf(
        "https://eztv.tf",
        "https://eztv.yt",
        "https://eztv1.xyz"
    )
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/{query}",
        selectors = selectors(
            container = "table.forum_header_border tr.forum_header_border",
            title = "td:nth-child(2) a.epinfo",
            magnetUrl = "td:nth-child(3) a.magnet",
            downloadUrl = "td:nth-child(3) a.download_1",
            size = "td:nth-child(4)",
            publishDate = "td:nth-child(5)",
            seeders = "td:nth-child(6)"
        ),
        category = "tv"
    )
}

/**
 * TorrentDownloads - Large torrent index
 */
class ProviderTorrentDownloads : BaseIndexerProvider() {
    override val id = "torrentdownloads"
    override val name = "TorrentDownloads"
    override val baseUrl = "https://www.torrentdownloads.pro"
    override val description = "TorrentDownloads - Millions of torrents"
    
    override fun getMirrors() = listOf(
        "https://www.torrentdownloads.me",
        "https://www.torrentdownloads.info"
    )
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/?search={query}",
        selectors = selectors(
            container = "div.grey_bar3",
            title = "div.inner_container a",
            torrentPageUrl = "div.inner_container a",
            size = "span.attr_val:contains(Size)",
            seeders = "span.seeders",
            leechers = "span.leechers",
            publishDate = "span.attr_val:contains(Added)"
        )
    )
}

/**
 * Zooqle - Clean interface with verified torrents
 */
class ProviderZooqle : BaseIndexerProvider() {
    override val id = "zooqle"
    override val name = "Zooqle"
    override val baseUrl = "https://zooqle.com"
    override val description = "Zooqle - Verified torrents with clean interface"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search?q={query}",
        selectors = selectors(
            container = "table.table-torrents tbody tr",
            title = "td:nth-child(2) a",
            magnetUrl = "td:nth-child(3) a[href^='magnet:']",
            torrentPageUrl = "td:nth-child(2) a",
            size = "td:nth-child(4)",
            seeders = "td:nth-child(6) .progress-bar:first",
            leechers = "td:nth-child(6) .progress-bar:last",
            publishDate = "td:nth-child(5)"
        )
    )
}

/**
 * TorrentFunk - Multi-category index
 */
class ProviderTorrentFunk : BaseIndexerProvider() {
    override val id = "torrentfunk"
    override val name = "TorrentFunk"
    override val baseUrl = "https://www.torrentfunk.com"
    override val description = "TorrentFunk - All categories"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/all/torrents/{query}.html",
        selectors = selectors(
            container = "table.tmain tr:not(:first-child)",
            title = "td:nth-child(1) a",
            torrentPageUrl = "td:nth-child(1) a",
            size = "td:nth-child(2)",
            seeders = "td:nth-child(4)",
            leechers = "td:nth-child(5)",
            publishDate = "td:nth-child(3)"
        )
    )
}
