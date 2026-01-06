package com.zim.jackettprowler.providers

import com.zim.jackettprowler.CustomSiteConfig

/**
 * IPTorrents - Major private tracker
 */
class ProviderIPTorrents : BaseIndexerProvider() {
    override val id = "iptorrents"
    override val name = "IPTorrents"
    override val baseUrl = "https://iptorrents.com"
    override val description = "IPTorrents - Major private tracker"
    override val isPrivate = true
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/t?q={query}",
        selectors = selectors(
            container = "table.torrents tr.t-row",
            title = "td:nth-child(2) a.t_title",
            downloadUrl = "td:nth-child(4) a",
            size = "td:nth-child(6)",
            seeders = "td:nth-child(8)",
            leechers = "td:nth-child(9)"
        )
    )
}

/**
 * TorrentLeech - Premium private tracker
 */
class ProviderTorrentLeech : BaseIndexerProvider() {
    override val id = "torrentleech"
    override val name = "TorrentLeech"
    override val baseUrl = "https://www.torrentleech.org"
    override val description = "TorrentLeech - High quality private tracker"
    override val isPrivate = true
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrents/browse/index/query/{query}",
        selectors = selectors(
            container = "table.table tbody tr",
            title = "td.name a.tt-name",
            downloadUrl = "td.quickdownload a",
            size = "td.size",
            seeders = "td.seeders",
            leechers = "td.leechers",
            publishDate = "td.added"
        )
    )
}

/**
 * AlphaRatio - Private general tracker
 */
class ProviderAlphaRatio : BaseIndexerProvider() {
    override val id = "alpharatio"
    override val name = "AlphaRatio"
    override val baseUrl = "https://alpharatio.cc"
    override val description = "AlphaRatio - Private general tracker"
    override val isPrivate = true
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrents.php?searchstr={query}",
        selectors = selectors(
            container = "table.torrent_table tbody tr.torrent",
            title = "td.big_info a[href^='torrents.php?id=']",
            downloadUrl = "td a[href^='torrents.php?action=download']",
            size = "td:nth-last-child(4)",
            seeders = "td:nth-last-child(3)",
            leechers = "td:nth-last-child(2)"
        )
    )
}

/**
 * BroadcastTheNet (BTN) - Premier TV private tracker
 */
class ProviderBroadcastTheNet : BaseIndexerProvider() {
    override val id = "broadcastthenet"
    override val name = "BroadcastTheNet"
    override val baseUrl = "https://broadcasthe.net"
    override val description = "Premier TV private tracker"
    override val isPrivate = true
    override val category = listOf("TV")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrents.php?searchstr={query}",
        selectors = selectors(
            container = "table.torrent_table tbody tr",
            title = "td a[href^='torrents.php?id=']",
            downloadUrl = "td a[href^='torrents.php?action=download']",
            size = "td:nth-last-child(4)",
            seeders = "td:nth-last-child(3)",
            leechers = "td:nth-last-child(2)"
        ),
        enabled = false
    )
}

/**
 * AnimeBytes - Premier anime private tracker
 */
class ProviderAnimeBytes : BaseIndexerProvider() {
    override val id = "animebytes"
    override val name = "AnimeBytes"
    override val baseUrl = "https://animebytes.tv"
    override val description = "Premier anime private tracker"
    override val isPrivate = true
    override val category = listOf("Anime")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrents.php?searchstr={query}",
        selectors = selectors(
            container = "table.torrent_table tbody tr",
            title = "td.big_info a",
            downloadUrl = "td a[href^='torrents.php?action=download']",
            size = "td:nth-last-child(4)",
            seeders = "td:nth-last-child(3)",
            leechers = "td:nth-last-child(2)"
        ),
        enabled = false
    )
}

/**
 * MoreThanTV - TV focused private tracker
 */
class ProviderMoreThanTV : BaseIndexerProvider() {
    override val id = "morethantv"
    override val name = "MoreThanTV"
    override val baseUrl = "https://www.morethantv.me"
    override val description = "MoreThanTV - TV focused private tracker"
    override val isPrivate = true
    override val category = listOf("TV")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrents.php?searchstr={query}",
        selectors = selectors(
            container = "table.torrent_table tbody tr.torrent",
            title = "td.torrent_name a",
            downloadUrl = "td a[title='Download']",
            size = "td.number_column:nth-child(4)",
            seeders = "td.number_column:nth-child(6)",
            leechers = "td.number_column:nth-child(7)"
        ),
        category = "tv"
    )
}

/**
 * BroadcasTheNet - Elite TV tracker
 */
class ProviderBroadcasTheNet : BaseIndexerProvider() {
    override val id = "broadcasthenet"
    override val name = "BroadcasTheNet"
    override val baseUrl = "https://broadcasthe.net"
    override val description = "BroadcasTheNet - Elite TV private tracker"
    override val isPrivate = true
    override val category = listOf("TV")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrents.php?searchstr={query}",
        selectors = selectors(
            container = "table.torrent_table tbody tr",
            title = "td.name a",
            downloadUrl = "td a[href^='torrents.php?action=download']",
            size = "td.size",
            seeders = "td.seeders",
            leechers = "td.leechers"
        ),
        category = "tv"
    )
}

/**
 * PassThePopcorn - Elite movie tracker
 */
class ProviderPassThePopcorn : BaseIndexerProvider() {
    override val id = "passthepopcorn"
    override val name = "PassThePopcorn"
    override val baseUrl = "https://passthepopcorn.me"
    override val description = "PassThePopcorn - Elite movie private tracker"
    override val isPrivate = true
    override val category = listOf("Movies")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrents.php?searchstr={query}",
        selectors = selectors(
            container = "div.movie_info",
            title = "h2 a.title",
            torrentPageUrl = "h2 a.title",
            publishDate = "div.time"
        ),
        category = "movies"
    )
}

/**
 * Redacted (RED) - Elite music tracker
 */
class ProviderRedacted : BaseIndexerProvider() {
    override val id = "redacted"
    override val name = "Redacted"
    override val baseUrl = "https://redacted.ch"
    override val description = "Redacted (RED) - Elite music private tracker"
    override val isPrivate = true
    override val category = listOf("Music")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrents.php?searchstr={query}",
        selectors = selectors(
            container = "table.torrent_table tbody tr.torrent",
            title = "td:nth-child(2) a",
            downloadUrl = "td a[href^='torrents.php?action=download']",
            size = "td.number_column:nth-child(4)",
            seeders = "td.number_column:nth-child(6)",
            leechers = "td.number_column:nth-child(7)"
        ),
        category = "music"
    )
}

/**
 * Orpheus - Music tracker
 */
class ProviderOrpheus : BaseIndexerProvider() {
    override val id = "orpheus"
    override val name = "Orpheus"
    override val baseUrl = "https://orpheus.network"
    override val description = "Orpheus - Music private tracker"
    override val isPrivate = true
    override val category = listOf("Music")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrents.php?searchstr={query}",
        selectors = selectors(
            container = "table.torrent_table tbody tr.torrent",
            title = "td:nth-child(2) a",
            downloadUrl = "td a[title='Download']",
            size = "td.number_column",
            seeders = "td:nth-child(6)",
            leechers = "td:nth-child(7)"
        ),
        category = "music"
    )
}

/**
 * MyAnonaMouse - E-books and audiobooks
 */
class ProviderMyAnonaMouse : BaseIndexerProvider() {
    override val id = "myanonamouse"
    override val name = "MyAnonaMouse"
    override val baseUrl = "https://www.myanonamouse.net"
    override val description = "MyAnonaMouse - E-books and audiobooks private tracker"
    override val isPrivate = true
    override val category = listOf("Books")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/tor/browse.php?search={query}",
        selectors = selectors(
            container = "table.coltable tbody tr",
            title = "td.tn_name a",
            downloadUrl = "td a[href^='/tor/download.php']",
            size = "td.tn_size",
            seeders = "td.tn_seeders",
            leechers = "td.tn_leechers"
        ),
        category = "books"
    )
}

/**
 * GazelleGames - Games private tracker
 */
class ProviderGazelleGames : BaseIndexerProvider() {
    override val id = "gazellegames"
    override val name = "GazelleGames"
    override val baseUrl = "https://gazellegames.net"
    override val description = "GazelleGames - Gaming private tracker"
    override val isPrivate = true
    override val category = listOf("Games")
    
    override fun toConfig() = CustomSiteConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        searchPath = "/torrents.php?searchstr={query}",
        selectors = selectors(
            container = "table.torrent_table tbody tr.torrent",
            title = "td:nth-child(2) a",
            downloadUrl = "td a[href^='torrents.php?action=download']",
            size = "td.number_column",
            seeders = "td:nth-child(6)",
            leechers = "td:nth-child(7)"
        ),
        category = "games"
    )
}
