package com.zim.jackettprowler.providers

import com.zim.jackettprowler.CustomSiteConfig

/**
 * Specialized torrent indexers - niche categories and DHT/meta search engines
 */

/**
 * BTScene - Scene releases
 */
class ProviderBTScene : BaseIndexerProvider() {
    override val id = "btscene"
    override val name = "BTScene"
    override val baseUrl = "https://btscene.cc"
    override val description = "Scene release tracker"
    override val category = listOf("Scene", "General")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/{query}",
        selectors = selectors(
            container = "div.torrent-item",
            title = "div.title a",
            magnetUrl = "a[href^='magnet:']",
            size = "div.size",
            seeders = "div.seeds",
            leechers = "div.leeches"
        )
    )
}

/**
 * TorrentSeeds - Verified torrents
 */
class ProviderTorrentSeeds : BaseIndexerProvider() {
    override val id = "torrentseeds"
    override val name = "TorrentSeeds"
    override val baseUrl = "https://torrentseeds.org"
    override val description = "Verified torrent aggregator"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/{query}",
        selectors = selectors(
            container = "table.torrents tr",
            title = "td.name a",
            magnetUrl = "td.magnet a",
            size = "td.size",
            seeders = "td.seeders",
            leechers = "td.leechers"
        )
    )
}

/**
 * ISOHunt - Old school torrent search
 */
class ProviderISOHunt : BaseIndexerProvider() {
    override val id = "isohunt"
    override val name = "ISOHunt"
    override val baseUrl = "https://isohunt.nz"
    override val description = "Classic torrent search engine"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrents/?ihq={query}",
        selectors = selectors(
            container = "table.table tbody tr",
            title = "td:nth-child(2) a",
            torrentPageUrl = "td:nth-child(2) a",
            size = "td:nth-child(4)",
            seeders = "td:nth-child(5)",
            leechers = "td:nth-child(6)"
        )
    )
}

/**
 * Monova - Alternative search engine
 */
class ProviderMonova : BaseIndexerProvider() {
    override val id = "monova"
    override val name = "Monova"
    override val baseUrl = "https://monova.org"
    override val description = "Torrent search engine"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search?term={query}",
        selectors = selectors(
            container = "table#searchResult tr",
            title = "td:nth-child(2) a",
            magnetUrl = "td:nth-child(1) a[href^='magnet:']",
            size = "td:nth-child(3)",
            seeders = "td:nth-child(4)",
            publishDate = "td:nth-child(5)"
        )
    )
}

/**
 * TorrentzEU - Meta search
 */
class ProviderTorrentzEU : BaseIndexerProvider() {
    override val id = "torrentzeu"
    override val name = "TorrentzEU"
    override val baseUrl = "https://torrentzeu.org"
    override val description = "Torrentz2 alternative"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search?f={query}",
        selectors = selectors(
            container = "div.results dl",
            title = "dt a",
            torrentPageUrl = "dt a",
            size = "dd span.s",
            seeders = "dd span.u",
            publishDate = "dd span.a"
        )
    )
}

/**
 * Idope - DHT crawler
 */
class ProviderIdope : BaseIndexerProvider() {
    override val id = "idope"
    override val name = "Idope"
    override val baseUrl = "https://idope.se"
    override val description = "DHT search engine"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrent-list/{query}/",
        selectors = selectors(
            container = "div.resultdiv",
            title = "div.resultdivtop a",
            magnetUrl = "div.resultdivbotton a[href^='magnet:']",
            size = "div.resultdivbottonlength",
            seeders = "div.resultdivbottonseed"
        )
    )
}

/**
 * SkyTorrents Clone
 */
class ProviderSkyTorrentsClone : BaseIndexerProvider() {
    override val id = "skytorrents-clone"
    override val name = "SkyTorrents Clone"
    override val baseUrl = "https://www.skytorrents.lol"
    override val description = "SkyTorrents mirror"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/{query}",
        selectors = selectors(
            container = "table tbody tr",
            title = "td:nth-child(1) a",
            magnetUrl = "td:nth-child(2) a[href^='magnet:']",
            size = "td:nth-child(2)",
            seeders = "td:nth-child(3)",
            publishDate = "td:nth-child(4)"
        )
    )
}

/**
 * TorrentAPI - JSON API wrapper
 */
class ProviderTorrentAPI : BaseIndexerProvider() {
    override val id = "torrentapi"
    override val name = "TorrentAPI"
    override val baseUrl = "https://torrentapi.org"
    override val description = "Public torrent API"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/pubapi_v2.php?mode=search&search_string={query}",
        selectors = selectors(
            container = "item",
            title = "title",
            magnetUrl = "link",
            size = "size",
            seeders = "seeders",
            publishDate = "pubDate"
        )
    )
}

/**
 * BitSearch - BitTorrent search engine
 */
class ProviderBitSearch : BaseIndexerProvider() {
    override val id = "bitsearch"
    override val name = "BitSearch"
    override val baseUrl = "https://bitsearch.to"
    override val description = "BitTorrent search engine"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search?q={query}",
        selectors = selectors(
            container = "div.card",
            title = "h5.title a",
            torrentPageUrl = "h5.title a",
            magnetUrl = "a[href^='magnet:']",
            size = "div.stats div:contains('Size')",
            seeders = "div.stats div:contains('Seeders')"
        )
    )
}

/**
 * TorrentWhiz - Search aggregator
 */
class ProviderTorrentWhiz : BaseIndexerProvider() {
    override val id = "torrentwhiz"
    override val name = "TorrentWhiz"
    override val baseUrl = "https://torrentwhiz.com"
    override val description = "Multi-source aggregator"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/{query}",
        selectors = selectors(
            container = "div.result",
            title = "div.title a",
            magnetUrl = "a.magnet",
            size = "span.size",
            seeders = "span.seeds"
        )
    )
}

/**
 * ExtraTorrent Clone
 */
class ProviderExtraTorrent : BaseIndexerProvider() {
    override val id = "extratorrent"
    override val name = "ExtraTorrent Clone"
    override val baseUrl = "https://extratorrent.unblockit.one"
    override val description = "ExtraTorrent mirror"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/?search={query}",
        selectors = selectors(
            container = "table.tl tbody tr",
            title = "td.tli a",
            torrentPageUrl = "td.tli a",
            size = "td:nth-child(5)",
            seeders = "td:nth-child(6)",
            leechers = "td:nth-child(7)"
        )
    )
}

/**
 * Kickass Hydra
 */
class ProviderKickassHydra : BaseIndexerProvider() {
    override val id = "kickasshydra"
    override val name = "Kickass Hydra"
    override val baseUrl = "https://kickasshydra.net"
    override val description = "KAT alternative"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search.php?q={query}",
        selectors = selectors(
            container = "table tbody tr",
            title = "td.name a",
            magnetUrl = "td.magnet a",
            size = "td.size",
            seeders = "td.seeders",
            leechers = "td.leechers"
        )
    )
}

/**
 * RARBG Mirror
 */
class ProviderRarbgMirror : BaseIndexerProvider() {
    override val id = "rarbgmirror"
    override val name = "RARBG Mirror"
    override val baseUrl = "https://rarbgmirror.com"
    override val description = "RARBG proxy site"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrents.php?search={query}",
        selectors = selectors(
            container = "table.lista2t tr.lista2",
            title = "td:nth-child(2) a",
            size = "td:nth-child(4)",
            seeders = "td:nth-child(5)",
            leechers = "td:nth-child(6)"
        )
    )
}

/**
 * TorrentKitty
 */
class ProviderTorrentKitty : BaseIndexerProvider() {
    override val id = "torrentkitty"
    override val name = "TorrentKitty"
    override val baseUrl = "https://www.torrentkitty.tv"
    override val description = "DHT search"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/{query}",
        selectors = selectors(
            container = "table#archiveResult tbody tr",
            title = "td:nth-child(1) a.name",
            magnetUrl = "td:nth-child(1) a[href^='magnet:']",
            size = "td:nth-child(2)",
            publishDate = "td:nth-child(3)"
        )
    )
}

/**
 * TorrentDB
 */
class ProviderTorrentDB : BaseIndexerProvider() {
    override val id = "torrentdb"
    override val name = "TorrentDB"
    override val baseUrl = "https://torrentdb.to"
    override val description = "Torrent database"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search?q={query}",
        selectors = selectors(
            container = "div.torrent-row",
            title = "div.name a",
            magnetUrl = "a.magnet",
            size = "div.size",
            seeders = "div.seeders"
        )
    )
}

/**
 * Bitsearch Alternative
 */
class ProviderBitsearch : BaseIndexerProvider() {
    override val id = "bitsearch-alt"
    override val name = "Bitsearch Alt"
    override val baseUrl = "https://bitsearch.co"
    override val description = "BitTorrent DHT search"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search?q={query}",
        selectors = selectors(
            container = "li.search-result",
            title = "h5 a",
            magnetUrl = "a.dl-magnet",
            size = "div.info span:contains('Size')",
            seeders = "div.info span.text-success"
        )
    )
}
/**
 * TorrentGuru - DHT torrent search
 */
class ProviderTorrentGuru : BaseIndexerProvider() {
    override val id = "torrentguru"
    override val name = "TorrentGuru"
    override val baseUrl = "https://torrentguru.io"
    override val description = "DHT-based torrent search engine"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search?q={query}",
        selectors = selectors(
            container = "div.result-item",
            title = "div.title a",
            magnetUrl = "a[href^='magnet:']",
            size = "span.size",
            seeders = "span.seeders"
        )
    )
}

/**
 * Snowfl - Clean DHT indexer
 */
class ProviderSnowfl : BaseIndexerProvider() {
    override val id = "snowfl"
    override val name = "Snowfl"
    override val baseUrl = "https://snowfl.com"
    override val description = "Minimalist DHT torrent search"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/{query}.html",
        selectors = selectors(
            container = "div.container > div",
            title = "h2 a",
            magnetUrl = "a.download[href^='magnet:']",
            size = "span.size"
        )
    )
}