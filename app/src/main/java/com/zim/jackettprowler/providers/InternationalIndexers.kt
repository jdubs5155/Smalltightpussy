package com.zim.jackettprowler.providers

import com.zim.jackettprowler.CustomSiteConfig

/**
 * Rutracker - Largest Russian torrent site
 */
class ProviderRutracker : BaseIndexerProvider() {
    override val id = "rutracker"
    override val name = "RuTracker"
    override val baseUrl = "https://rutracker.org"
    override val description = "RuTracker - Largest Russian torrent tracker"
    override val language = "ru-RU"
    override val isPrivate = true
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/forum/tracker.php?nm={query}",
        selectors = selectors(
            container = "table#tor-tbl tbody tr",
            title = "td.t-title a.tLink",
            torrentPageUrl = "td.t-title a.tLink",
            downloadUrl = "td.tor-size a",
            size = "td.tor-size",
            seeders = "td.seedmed",
            leechers = "td.leechmed",
            publishDate = "td:nth-child(10)"
        )
    )
}

/**
 * Magnetdl - Magnet link database
 */
class ProviderMagnetDL : BaseIndexerProvider() {
    override val id = "magnetdl"
    override val name = "MagnetDL"
    override val baseUrl = "https://www.magnetdl.com"
    override val description = "MagnetDL - Magnet link database"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/{query}/",
        selectors = selectors(
            container = "table.download tbody tr",
            title = "td.m a",
            magnetUrl = "td.m a[href^='magnet:']",
            torrentPageUrl = "td.n a:first",
            size = "td.s",
            publishDate = "td.d"
        )
    )
}

/**
 * Glodls - DDL and torrents
 */
class ProviderGlodls : BaseIndexerProvider() {
    override val id = "glodls"
    override val name = "Glodls"
    override val baseUrl = "https://glodls.to"
    override val description = "Glodls - Direct download links and torrents"
    
    override fun getMirrors() = listOf(
        "https://gtso.cc"
    )
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search_results.php?search={query}",
        selectors = selectors(
            container = "table.ttable_headinner tbody tr",
            title = "td:nth-child(2) a",
            torrentPageUrl = "td:nth-child(2) a",
            size = "td:nth-child(6)",
            seeders = "td:nth-child(8)",
            publishDate = "td:nth-child(3)"
        )
    )
}

/**
 * Torrent9 - French tracker
 */
class ProviderTorrent9 : BaseIndexerProvider() {
    override val id = "torrent9"
    override val name = "Torrent9"
    override val baseUrl = "https://www.torrent9.red"
    override val description = "Torrent9 - French torrent site"
    override val language = "fr-FR"
    
    override fun getMirrors() = listOf(
        "https://ww1.torrent9.to",
        "https://ww2.torrent9.gg"
    )
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search_torrent/{query}.html",
        selectors = selectors(
            container = "table.table tbody tr",
            title = "td:nth-child(1) a",
            torrentPageUrl = "td:nth-child(1) a",
            size = "td:nth-child(2)",
            seeders = "td:nth-child(3)",
            leechers = "td:nth-child(4)"
        )
    )
}

/**
 * IsoHunt - Reincarnation of classic site
 */
class ProviderIsoHunt : BaseIndexerProvider() {
    override val id = "isohunt"
    override val name = "IsoHunt"
    override val baseUrl = "https://isohunt.ee"
    override val description = "IsoHunt - Torrent search engine"
    
    override fun getMirrors() = listOf(
        "https://isohunt.nz",
        "https://isohunt2.net"
    )
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrents/?ihq={query}",
        selectors = selectors(
            container = "table.table tbody tr",
            title = "td.title-row a span.title",
            torrentPageUrl = "td.title-row a",
            magnetUrl = "td a[href^='magnet:']",
            size = "td.size-row",
            seeders = "td.sn",
            publishDate = "td.date-row"
        )
    )
}

/**
 * Torrentz2 - Meta search engine
 */
class ProviderTorrentz2 : BaseIndexerProvider() {
    override val id = "torrentz2"
    override val name = "Torrentz2"
    override val baseUrl = "https://torrentz2.nz"
    override val description = "Torrentz2 - Torrent meta-search engine"
    
    override fun getMirrors() = listOf(
        "https://torrentz2eu.me",
        "https://torrentz2.eu"
    )
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search?f={query}",
        selectors = selectors(
            container = "dl dt",
            title = "a",
            torrentPageUrl = "a",
            publishDate = "span.a",
            size = "span.s"
        )
    )
}

/**
 * Demonoid - Classic tracker
 */
class ProviderDemonoid : BaseIndexerProvider() {
    override val id = "demonoid"
    override val name = "Demonoid"
    override val baseUrl = "https://www.demonoid.is"
    override val description = "Demonoid - Classic semi-private tracker"
    override val isPrivate = true
    
    override fun getMirrors() = listOf(
        "https://www.dnoid.to"
    )
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/files/?query={query}",
        selectors = selectors(
            container = "table.font_12px tbody tr",
            title = "td:nth-child(2) a",
            torrentPageUrl = "td:nth-child(2) a",
            size = "td:nth-child(4)",
            seeders = "td:nth-child(7) font",
            leechers = "td:nth-child(8) font"
        )
    )
}

/**
 * BTDigg - DHT search engine
 */
class ProviderBTDigg : BaseIndexerProvider() {
    override val id = "btdigg"
    override val name = "BTDigg"
    override val baseUrl = "https://btdig.com"
    override val description = "BTDigg - DHT search engine"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search?q={query}",
        selectors = selectors(
            container = "div.one_result",
            title = "div.torrent_name a",
            magnetUrl = "div.torrent_magnet a",
            infoHash = "div.torrent_magnet a",
            size = "span.torrent_size",
            publishDate = "span.torrent_date"
        )
    )
}

/**
 * TorrentProject - Torrent indexer
 */
class ProviderTorrentProject : BaseIndexerProvider() {
    override val id = "torrentproject"
    override val name = "TorrentProject"
    override val baseUrl = "https://torrentproject2.com"
    override val description = "TorrentProject - Large torrent index"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/?s={query}",
        selectors = selectors(
            container = "div#similarfiles div",
            title = "div.tt-name a",
            torrentPageUrl = "div.tt-name a",
            size = "div.tt-size",
            seeders = "div.tt-seeds",
            leechers = "div.tt-peers"
        )
    )
}

/**
 * Kickass Torrents alternatives
 */
class ProviderKickassTorrents : BaseIndexerProvider() {
    override val id = "kickasstorrents"
    override val name = "KickassTorrents"
    override val baseUrl = "https://katcr.to"
    override val description = "KickassTorrents - Community edition"
    
    override fun getMirrors() = listOf(
        "https://kickass.cm",
        "https://kat.sx"
    )
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/katsearch/page/1/{query}",
        selectors = selectors(
            container = "table.torrents_table tbody tr",
            title = "td.name div.torrents_table__title a",
            magnetUrl = "td.action a[href^='magnet:']",
            torrentPageUrl = "td.name div.torrents_table__title a",
            size = "td.size",
            publishDate = "td.date",
            seeders = "td.seeds",
            leechers = "td.leeches"
        )
    )
}


