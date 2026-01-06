package com.zim.jackettprowler.providers

import com.zim.jackettprowler.CustomSiteConfig
import com.zim.jackettprowler.ScraperSelectors
import com.zim.jackettprowler.SelectorType

/**
 * Adult content torrent indexers
 * NO CONTENT RESTRICTIONS - for testing custom moderation systems
 */

/**
 * Empornium - Private adult tracker
 */
class ProviderEmpornium : BaseIndexerProvider() {
    override val id = "empornium"
    override val name = "Empornium"
    override val baseUrl = "https://www.empornium.is"
    override val description = "Premier private adult content tracker"
    override val category = listOf("Adult", "XXX")
    override val isPrivate = true
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrents.php?searchstr={query}",
        selectors = selectors(
            container = "table.torrent_table tbody tr.torrent",
            title = "td:nth-child(2) a",
            downloadUrl = "td:nth-child(1) a[href^='torrents.php?action=download']",
            size = "td:nth-child(4)",
            seeders = "td:nth-child(6)",
            leechers = "td:nth-child(7)"
        ),
        enabled = false, // Requires authentication
        category = "adult"
    )
}

/**
 * PornLeech - Semi-private adult tracker
 */
class ProviderPornLeech : BaseIndexerProvider() {
    override val id = "pornleech"
    override val name = "PornLeech"
    override val baseUrl = "https://pornleech.com"
    override val description = "Adult content tracker"
    override val category = listOf("Adult")
    override val isPrivate = true
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrents.php?searchstr={query}",
        selectors = selectors(
            container = "table.torrent_table tbody tr",
            title = "td:nth-child(2) a",
            downloadUrl = "td:nth-child(1) a[href^='download.php']",
            size = "td:nth-child(4)",
            seeders = "td:nth-child(6)",
            leechers = "td:nth-child(7)"
        ),
        enabled = false,
        category = "adult"
    )
}

/**
 * Sukebei - Public adult anime tracker (Nyaa sister site)
 */
class ProviderSukebei : BaseIndexerProvider() {
    override val id = "sukebei"
    override val name = "Sukebei"
    override val baseUrl = "https://sukebei.nyaa.si"
    override val description = "Adult anime/hentai torrents"
    override val category = listOf("Adult", "Anime", "Hentai")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/?q={query}",
        selectors = selectors(
            container = "table.torrent-list tbody tr",
            title = "td:nth-child(2) a:not(.comments)",
            torrentPageUrl = "td:nth-child(2) a:not(.comments)",
            downloadUrl = "td:nth-child(3) a[href$='.torrent']",
            magnetUrl = "td:nth-child(3) a[href^='magnet:']",
            size = "td:nth-child(4)",
            seeders = "td:nth-child(6)",
            leechers = "td:nth-child(7)",
            publishDate = "td:nth-child(5)"
        ),
        enabled = true,
        category = "adult"
    )
}

/**
 * XVideos Torrents
 */
class ProviderXVideosTorrents : BaseIndexerProvider() {
    override val id = "xvideos-torrents"
    override val name = "XVideos Torrents"
    override val baseUrl = "https://xvideos-torrents.net"
    override val description = "Adult video torrents"
    override val category = listOf("Adult", "XXX")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/{query}",
        selectors = selectors(
            container = "div.torrent-row",
            title = "div.title a",
            torrentPageUrl = "div.title a",
            magnetUrl = "a.magnet[href^='magnet:']",
            size = "div.size",
            seeders = "div.seeds",
            leechers = "div.leeches"
        ),
        enabled = true,
        category = "adult"
    )
}

/**
 * Pornbay
 */
class ProviderPornbay : BaseIndexerProvider() {
    override val id = "pornbay"
    override val name = "Pornbay"
    override val baseUrl = "https://pornbay.org"
    override val description = "Adult torrent index"
    override val category = listOf("Adult")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search.php?q={query}",
        selectors = selectors(
            container = "table.data tbody tr",
            title = "td.name a",
            torrentPageUrl = "td.name a",
            magnetUrl = "td:nth-child(2) a[href^='magnet:']",
            size = "td.size",
            seeders = "td.seeders",
            leechers = "td.leechers"
        ),
        enabled = true,
        category = "adult"
    )
}

/**
 * TorrentKitty Adult
 */
class ProviderTorrentKittyAdult : BaseIndexerProvider() {
    override val id = "torrentkitty-adult"
    override val name = "TorrentKitty Adult"
    override val baseUrl = "https://www.torrentkitty.tv"
    override val description = "Adult DHT search"
    override val category = listOf("Adult", "DHT")
    
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
        ),
        enabled = true,
        category = "adult"
    )
}

/**
 * BTDigg Adult Mirror
 */
class ProviderBTDiggAdult : BaseIndexerProvider() {
    override val id = "btdigg-adult"
    override val name = "BTDigg Adult"
    override val baseUrl = "https://btdig.com"
    override val description = "Adult DHT search engine"
    override val category = listOf("Adult", "DHT", "Meta")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search?q={query}",
        selectors = selectors(
            container = "div.one_result",
            title = "div.torrent_name a",
            torrentPageUrl = "div.torrent_name a",
            magnetUrl = "div.torrent_magnet a[href^='magnet:']",
            size = "span.torrent_size",
            publishDate = "span.torrent_age"
        ),
        enabled = true,
        category = "adult"
    )
}

/**
 * Keep2Share Torrents
 */
class ProviderKeep2Share : BaseIndexerProvider() {
    override val id = "keep2share"
    override val name = "Keep2Share Torrents"
    override val baseUrl = "https://k2s.cc"
    override val description = "File host with torrents"
    override val category = listOf("Adult", "Files")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrents/search/{query}",
        selectors = selectors(
            container = "div.file-item",
            title = "div.file-name a",
            torrentPageUrl = "div.file-name a",
            size = "div.file-size",
            publishDate = "div.file-date"
        ),
        enabled = true,
        category = "adult"
    )
}

/**
 * XXXTorrents
 */
class ProviderXXXTorrents : BaseIndexerProvider() {
    override val id = "xxxtorrents"
    override val name = "XXXTorrents"
    override val baseUrl = "https://xxxtorrents.org"
    override val description = "Adult content tracker"
    override val category = listOf("Adult", "XXX")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/{query}",
        selectors = selectors(
            container = "table.torrents tbody tr",
            title = "td.name a",
            downloadUrl = "td.download a",
            magnetUrl = "td.magnet a[href^='magnet:']",
            size = "td.size",
            seeders = "td.seeders",
            leechers = "td.leechers"
        ),
        enabled = true,
        category = "adult"
    )
}

/**
 * JAVTorrent - Japanese Adult Video torrents
 */
class ProviderJAVTorrent : BaseIndexerProvider() {
    override val id = "javtorrent"
    override val name = "JAVTorrent"
    override val baseUrl = "https://www.javtorrent.com"
    override val description = "JAV torrents"
    override val category = listOf("Adult", "JAV", "Asian")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/{query}",
        selectors = selectors(
            container = "div.post",
            title = "h2.title a",
            torrentPageUrl = "h2.title a",
            magnetUrl = "a.magnet[href^='magnet:']",
            size = "span.size"
        ),
        enabled = true,
        category = "adult"
    )
}

/**
 * Hentai Torrents
 */
class ProviderHentaiTorrents : BaseIndexerProvider() {
    override val id = "hentai-torrents"
    override val name = "Hentai Torrents"
    override val baseUrl = "https://hentai-torrents.net"
    override val description = "Hentai anime torrents"
    override val category = listOf("Adult", "Hentai", "Anime")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search.php?s={query}",
        selectors = selectors(
            container = "table.table tbody tr",
            title = "td:nth-child(2) a",
            downloadUrl = "td:nth-child(1) a[href$='.torrent']",
            size = "td:nth-child(4)",
            seeders = "td:nth-child(5)",
            leechers = "td:nth-child(6)"
        ),
        enabled = true,
        category = "adult"
    )
}

/**
 * PornBits
 */
class ProviderPornBits : BaseIndexerProvider() {
    override val id = "pornbits"
    override val name = "PornBits"
    override val baseUrl = "https://pornbits.net"
    override val description = "Semi-private adult tracker"
    override val category = listOf("Adult")
    override val isPrivate = true
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/browse.php?search={query}",
        selectors = selectors(
            container = "table.torrents tr",
            title = "td.name a",
            downloadUrl = "td.quickdownload a",
            size = "td.size",
            seeders = "td.seeders",
            leechers = "td.leechers"
        ),
        enabled = false,
        category = "adult"
    )
}

/**
 * DMHY Adult
 */
class ProviderDMHYAdult : BaseIndexerProvider() {
    override val id = "dmhy-adult"
    override val name = "DMHY Adult"
    override val baseUrl = "https://share.dmhy.org"
    override val description = "Chinese adult anime tracker"
    override val category = listOf("Adult", "Anime", "Asian")
    override val language = "zh-CN"
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/topics/list?keyword={query}",
        selectors = selectors(
            container = "table.tablesorter tbody tr",
            title = "td:nth-child(3) a",
            torrentPageUrl = "td:nth-child(3) a",
            magnetUrl = "td:nth-child(4) a[href^='magnet:']",
            size = "td:nth-child(5)",
            seeders = "td:nth-child(6) span",
            leechers = "td:nth-child(7) span",
            publishDate = "td:nth-child(1)"
        ),
        enabled = true,
        category = "adult"
    )
}

/**
 * AVgle Torrents
 */
class ProviderAVgleTorrents : BaseIndexerProvider() {
    override val id = "avgle-torrents"
    override val name = "AVgle Torrents"
    override val baseUrl = "https://avgle.com"
    override val description = "Asian adult video torrents"
    override val category = listOf("Adult", "JAV", "Asian")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/{query}",
        selectors = selectors(
            container = "div.video-item",
            title = "h4.title a",
            torrentPageUrl = "h4.title a",
            publishDate = "span.upload-time"
        ),
        enabled = true,
        category = "adult"
    )
}

/**
 * PornoLab - Russian adult tracker
 */
class ProviderPornoLab : BaseIndexerProvider() {
    override val id = "pornolab"
    override val name = "PornoLab"
    override val baseUrl = "https://pornolab.net"
    override val description = "Russian adult tracker"
    override val category = listOf("Adult")
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
            size = "td.tor-size",
            seeders = "td.seedmed",
            leechers = "td.leechmed"
        ),
        enabled = false,
        category = "adult"
    )
}

/**
 * MPA Torrents - Adult music/performance
 */
class ProviderMPATorrents : BaseIndexerProvider() {
    override val id = "mpa-torrents"
    override val name = "MPA Torrents"
    override val baseUrl = "https://mpatorrents.com"
    override val description = "Adult music and performance"
    override val category = listOf("Adult", "Music")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/search/{query}",
        selectors = selectors(
            container = "table.torrents tr",
            title = "td.name a",
            downloadUrl = "td.download a",
            magnetUrl = "td.magnet a[href^='magnet:']",
            size = "td.size",
            seeders = "td.seeders",
            leechers = "td.leechers"
        ),
        enabled = true,
        category = "adult"
    )
}
