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

// ============================================================
// ADDITIONAL PROVIDERS - Expanding to 100+
// ============================================================

/**
 * BT4G - BitTorrent For Geeks
 */
class ProviderBT4G : BaseIndexerProvider() {
    override val id = "bt4g"
    override val name = "BT4G"
    override val baseUrl = "https://bt4g.org"
    override val description = "BitTorrent search engine with DHT"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/{query}",
        selectors = selectors(
            container = "div.one_result",
            title = "h5 a",
            magnetUrl = "a[href^='magnet:']",
            size = "span.cpill:contains('Size')",
            seeders = "span.cpill:contains('Seeder')"
        )
    )
}

/**
 * BTDigg DHT - BitTorrent DHT Search Alternative
 * Note: This uses a different base URL than the international ProviderBTDigg
 */
class ProviderBTDiggDHT : BaseIndexerProvider() {
    override val id = "btdigg-dht"
    override val name = "BTDigg DHT"
    override val baseUrl = "https://btdig.com"
    override val description = "BitTorrent DHT search engine"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search?q={query}",
        selectors = selectors(
            container = "div.one_result",
            title = "div.torrent_name a",
            magnetUrl = "a.torrent_magnet",
            size = "span.torrent_size",
            seeders = "span.torrent_age"
        )
    )
}

/**
 * Academic Torrents
 */
class ProviderAcademicTorrents : BaseIndexerProvider() {
    override val id = "academictorrents"
    override val name = "Academic Torrents"
    override val baseUrl = "https://academictorrents.com"
    override val description = "Research datasets and academic materials"
    override val category = listOf("Academic", "Research", "Datasets")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/browse.php?search={query}",
        selectors = selectors(
            container = "table.table tr",
            title = "td a.title",
            torrentPageUrl = "td a.title",
            size = "td.size"
        )
    )
}

/**
 * Internet Archive Torrents
 */
class ProviderInternetArchive : BaseIndexerProvider() {
    override val id = "internetarchive"
    override val name = "Internet Archive"
    override val baseUrl = "https://archive.org"
    override val description = "Digital library with legal torrents"
    override val category = listOf("Archive", "Legal", "Media")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search.php?query={query}&mediatype=software",
        selectors = selectors(
            container = "div.results div.item",
            title = "div.ttl a",
            torrentPageUrl = "div.ttl a",
            size = "span.by"
        )
    )
}

/**
 * Legit Torrents - Legal content
 */
class ProviderLegitTorrents : BaseIndexerProvider() {
    override val id = "legittorrents"
    override val name = "Legit Torrents"
    override val baseUrl = "https://legittorrents.info"
    override val description = "100% legal torrent tracker"
    override val category = listOf("Legal", "Movies", "Music")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/index.php?search={query}",
        selectors = selectors(
            container = "table.lista tr",
            title = "td a",
            torrentPageUrl = "td a",
            size = "td:nth-child(4)",
            seeders = "td:nth-child(5)",
            leechers = "td:nth-child(6)"
        )
    )
}

/**
 * Linux Tracker
 */
class ProviderLinuxTracker : BaseIndexerProvider() {
    override val id = "linuxtracker"
    override val name = "Linux Tracker"
    override val baseUrl = "https://linuxtracker.org"
    override val description = "Linux distributions torrent tracker"
    override val category = listOf("Linux", "Software", "Legal")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/index.php?search={query}",
        selectors = selectors(
            container = "table.lista tr",
            title = "td.lista a",
            torrentPageUrl = "td.lista a",
            size = "td:nth-child(5)"
        )
    )
}

/**
 * SeedPeer - Multi-category
 */
class ProviderSeedPeer : BaseIndexerProvider() {
    override val id = "seedpeer"
    override val name = "SeedPeer"
    override val baseUrl = "https://www.seedpeer.me"
    override val description = "Multi-category torrent search"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/{query}",
        selectors = selectors(
            container = "table.table tr",
            title = "td a.title",
            magnetUrl = "a[href^='magnet:']",
            size = "td:nth-child(2)",
            seeders = "td:nth-child(3)"
        )
    )
}

/**
 * TorrentFreak - Popular torrents
 */
class ProviderTorrentFreak : BaseIndexerProvider() {
    override val id = "torrentfreak"
    override val name = "TorrentFreak"
    override val baseUrl = "https://torrentfreak.com"
    override val description = "Top torrents news and lists"
    override val category = listOf("News", "Popular")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/?s={query}",
        selectors = selectors(
            container = "article",
            title = "h2.entry-title a",
            torrentPageUrl = "h2.entry-title a"
        )
    )
}

/**
 * iDope - DHT crawler
 */
class ProviderIDope : BaseIndexerProvider() {
    override val id = "idope"
    override val name = "iDope"
    override val baseUrl = "https://idope.se"
    override val description = "DHT torrent crawler and search"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search?q={query}",
        selectors = selectors(
            container = "div.result",
            title = "div.result-info div.result-title a",
            magnetUrl = "a.magnet[href^='magnet:']",
            size = "div.result-size",
            seeders = "div.result-seeders"
        )
    )
}

/**
 * Torrent Paradise - DHT indexed
 */
class ProviderTorrentParadise : BaseIndexerProvider() {
    override val id = "torrentparadise"
    override val name = "Torrent Paradise"
    override val baseUrl = "https://torrent-paradise.ml"
    override val description = "Decentralized torrent search"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search?q={query}",
        selectors = selectors(
            container = "div.result",
            title = "a.name",
            magnetUrl = "a[href^='magnet:']",
            size = "span.size",
            seeders = "span.seeders"
        )
    )
}

/**
 * TorrentProject - Aggregator
 */
class ProviderTorrentProjectNew : BaseIndexerProvider() {
    override val id = "torrentproject_new"
    override val name = "TorrentProject"
    override val baseUrl = "https://torrentproject2.com"
    override val description = "Torrent aggregator and search"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/?t={query}",
        selectors = selectors(
            container = "div.result",
            title = "a.title",
            magnetUrl = "a.magnet",
            size = "span.size",
            seeders = "span.seed"
        )
    )
}

/**
 * TorrentDownload - Direct downloads
 */
class ProviderTorrentDownload : BaseIndexerProvider() {
    override val id = "torrentdownload"
    override val name = "TorrentDownload"
    override val baseUrl = "https://torrentdownload.info"
    override val description = "Torrent search and download"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search?q={query}",
        selectors = selectors(
            container = "table.table tr",
            title = "td a.title",
            magnetUrl = "td a.magnet",
            size = "td:nth-child(3)",
            seeders = "td:nth-child(4)",
            leechers = "td:nth-child(5)"
        )
    )
}

/**
 * GloDLS - Global downloads
 */
class ProviderGloDLS : BaseIndexerProvider() {
    override val id = "glodls"
    override val name = "GloDLS"
    override val baseUrl = "https://www.glodls.to"
    override val description = "Global torrent downloads"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search_results.php?search={query}",
        selectors = selectors(
            container = "table.ttable_headinner tr",
            title = "td.ttable_col1 a",
            torrentPageUrl = "td.ttable_col1 a",
            size = "td.ttable_col2",
            seeders = "td.ttable_col_s",
            leechers = "td.ttable_col_l"
        )
    )
}

/**
 * MagnetDL - Magnet links
 */
class ProviderMagnetDLNew : BaseIndexerProvider() {
    override val id = "magnetdl_new"
    override val name = "MagnetDL"
    override val baseUrl = "https://www.magnetdl.com"
    override val description = "Direct magnet link search"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/{query}/",
        selectors = selectors(
            container = "table.download tr",
            title = "td.n a",
            magnetUrl = "td.m a",
            size = "td.s",
            seeders = "td.s:nth-child(6)",
            leechers = "td.l"
        )
    )
}

/**
 * YourBittorrent - Community tracker
 */
class ProviderYourBittorrent : BaseIndexerProvider() {
    override val id = "yourbittorrent"
    override val name = "YourBittorrent"
    override val baseUrl = "https://yourbittorrent.com"
    override val description = "Community torrent search"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/?q={query}",
        selectors = selectors(
            container = "table.table tr",
            title = "td a.title",
            magnetUrl = "td a.magnet",
            size = "td:nth-child(3)",
            seeders = "td:nth-child(4)"
        )
    )
}

/**
 * Zooqle Alternative
 */
class ProviderZooqleAlt : BaseIndexerProvider() {
    override val id = "zooqle_alt"
    override val name = "Zooqle Alt"
    override val baseUrl = "https://zooqle.unblockit.ch"
    override val description = "Zooqle mirror site"
    
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
 * Torrentz2.is - Meta search
 */
class ProviderTorrentz2Alt : BaseIndexerProvider() {
    override val id = "torrentz2_alt"
    override val name = "Torrentz2 Alt"
    override val baseUrl = "https://torrentz2.is"
    override val description = "Torrentz2 mirror meta search"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search?q={query}",
        selectors = selectors(
            container = "div.results dl",
            title = "dt a",
            torrentPageUrl = "dt a",
            size = "dd span:nth-child(3)"
        )
    )
}

/**
 * ETTV Torrents
 */
class ProviderETTV : BaseIndexerProvider() {
    override val id = "ettv"
    override val name = "ETTV"
    override val baseUrl = "https://www.ettvcentral.com"
    override val description = "TV show torrents"
    override val category = listOf("TV", "Shows")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrents-search.php?search={query}",
        selectors = selectors(
            container = "table.table tr",
            title = "td a.torrent-title",
            torrentPageUrl = "td a.torrent-title",
            size = "td:nth-child(4)",
            seeders = "td:nth-child(5)",
            leechers = "td:nth-child(6)"
        )
    )
}

/**
 * TorrentDay Style
 */
class ProviderTorrentDayStyle : BaseIndexerProvider() {
    override val id = "torrentday_style"
    override val name = "TorrentDay Style"
    override val baseUrl = "https://torrentday.cool"
    override val description = "Private tracker style public torrents"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/browse?search={query}",
        selectors = selectors(
            container = "table#torrent_table tr",
            title = "td.name a",
            torrentPageUrl = "td.name a",
            size = "td.size",
            seeders = "td.seeders",
            leechers = "td.leechers"
        )
    )
}

/**
 * ExtraTorrent Clone
 */
class ProviderExtraTorrentClone : BaseIndexerProvider() {
    override val id = "extratorrent_clone"
    override val name = "ExtraTorrent Clone"
    override val baseUrl = "https://extratorrent.st"
    override val description = "ExtraTorrent successor site"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/?search={query}",
        selectors = selectors(
            container = "table.tl tr",
            title = "td.tli a",
            torrentPageUrl = "td.tli a",
            size = "td:nth-child(5)",
            seeders = "td:nth-child(6)",
            leechers = "td:nth-child(7)"
        )
    )
}

/**
 * RARBG Mirror 2
 */
class ProviderRarbgMirror2 : BaseIndexerProvider() {
    override val id = "rarbg_mirror2"
    override val name = "RARBG Mirror 2"
    override val baseUrl = "https://rarbgmirror.org"
    override val description = "RARBG alternative mirror"
    
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
 * 1337x Mirror
 */
class Provider1337xMirror : BaseIndexerProvider() {
    override val id = "1337x_mirror"
    override val name = "1337x Mirror"
    override val baseUrl = "https://1337x.unblockit.tv"
    override val description = "1337x proxy/mirror site"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/{query}/1/",
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
 * Pirate Bay Mirror
 */
class ProviderTPBMirror : BaseIndexerProvider() {
    override val id = "tpb_mirror"
    override val name = "TPB Mirror"
    override val baseUrl = "https://thepiratebay.unblockit.tv"
    override val description = "The Pirate Bay proxy site"
    
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
 * KickAss Torrents Clone
 */
class ProviderKATClone : BaseIndexerProvider() {
    override val id = "kat_clone"
    override val name = "KAT Clone"
    override val baseUrl = "https://katcr.to"
    override val description = "KickAssTorrents successor"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/{query}/",
        selectors = selectors(
            container = "table.torrents_table tr",
            title = "td.torrentname a.cellMainLink",
            magnetUrl = "a.icon-magnet[href^='magnet:']",
            size = "td.nobr",
            seeders = "td.green",
            leechers = "td.red"
        )
    )
}