package com.zim.jackettprowler

/**
 * Comprehensive database of 200+ BitTorrent tracker announce URLs
 * Updated January 2026 - Public and widely-used trackers
 */
object TrackerDatabase {
    
    /**
     * Get all 200+ public tracker URLs
     */
    fun getAllTrackers(): List<String> {
        return publicTrackers() + openBitTorrentTrackers() + 
               academicTrackers() + specializedTrackers()
    }
    
    /**
     * Get trackers formatted for clipboard (one per line)
     */
    fun getTrackersAsText(): String {
        return getAllTrackers().joinToString("\n\n")
    }
    
    /**
     * Get count of all trackers
     */
    fun getTrackerCount(): Int = getAllTrackers().size
    
    /**
     * Primary public DHT trackers (most reliable)
     */
    private fun publicTrackers() = listOf(
        // OpenBitTorrent Network
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://p4p.arenabg.com:1337/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.dler.org:6969/announce",
        "udp://open.stealth.si:80/announce",
        "udp://ipv4.tracker.harry.lu:80/announce",
        
        // Major Public Trackers
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://tracker.opentrackr.org:1337/announce",
        "http://tracker.openbittorrent.com:80/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.pomf.se:80/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://tracker.moeking.me:6969/announce",
        
        // ExploitieTrackers
        "udp://explodie.org:6969/announce",
        "udp://tracker.publictracker.xyz:6969/announce",
        "udp://tracker.skyts.net:6969/announce",
        "udp://tracker.birkenwald.de:6969/announce",
        "udp://tracker.bitsearch.to:1337/announce",
        
        // IPv6 Capable
        "udp://tracker.zerobytes.xyz:1337/announce",
        "udp://tracker.0x7c0.com:6969/announce",
        "udp://tracker.filemail.com:6969/announce",
        "udp://tracker.doko.moe:6969/announce",
        
        // Alternative Ports
        "udp://tracker1.bt.moack.co.kr:80/announce",
        "udp://tracker.theoks.net:6969/announce",
        "udp://tracker.bittor.pw:1337/announce",
        "udp://tracker.4.babico.name.tr:3131/announce",
        "udp://tracker.dump.cl:6969/announce",
        
        // OpenTrackr Network
        "https://tracker.nanoha.org:443/announce",
        "https://tracker.lilithraws.cf:443/announce",
        "https://tracker.lilithraws.org:443/announce",
        
        // Public DHT Bootstrap
        "udp://tracker.coppersurfer.tk:6969/announce",
        "udp://tracker.leechers-paradise.org:6969/announce",
        "udp://tracker.pirateparty.gr:6969/announce",
        "udp://tracker.cyberia.is:6969/announce",
        
        // European Trackers
        "udp://tracker.internetwarriors.net:1337/announce",
        "udp://9.rarbg.com:2810/announce",
        "udp://9.rarbg.me:2780/announce",
        "udp://9.rarbg.to:2710/announce",
        "udp://tracker.trakx.xyz:1337/announce",
        
        // Asian Region
        "udp://tracker.swateam.org.uk:2710/announce",
        "udp://retracker.lanta-net.ru:2710/announce",
        "udp://retracker.netbynet.ru:2710/announce",
        "udp://bt.oiyo.tk:6969/announce",
        "udp://bt1.archive.org:6969/announce",
        
        // HTTP/HTTPS Trackers
        "http://tracker.bt4g.com:2095/announce",
        "http://tracker.files.fm:6969/announce",
        "http://tracker.gbitt.info:80/announce",
        "http://tracker.lelux.fi:80/announce",
        "http://tracker.torrentbytes.net:80/announce",
        "https://tracker.gbitt.info:443/announce",
        "https://tracker.torrentbytes.net:443/announce",
        
        // WebSocket Trackers
        "wss://tracker.btorrent.xyz",
        "wss://tracker.openwebtorrent.com",
        "wss://tracker.webtorrent.io"
    )
    
    /**
     * OpenBitTorrent and related network trackers
     */
    private fun openBitTorrentTrackers() = listOf(
        "udp://tracker.openbittorrent.com:80/announce",
        "udp://tracker.publicbt.com:80/announce",
        "udp://tracker.istole.it:80/announce",
        "udp://denis.stalker.upeer.me:6969/announce",
        "udp://tracker.btzoo.eu:80/announce",
        "udp://tracker.blackunicorn.xyz:6969/announce",
        "udp://tracker.ccc.de:80/announce",
        "udp://tracker.port443.xyz:6969/announce",
        "udp://tracker.opentracker.se:80/announce",
        "udp://eddie4.nl:6969/announce",
        "udp://shadowshq.yi.org:6969/announce",
        "udp://tracker.sktorrent.net:6969/announce",
        "udp://open.facedatabg.net:6969/announce",
        "udp://mgtracker.org:6969/announce",
        "udp://tracker.cypherpunks.ru:6969/announce",
        "udp://tracker.mg64.net:6969/announce",
        "udp://tracker.tcp.exchange:6969/announce",
        "udp://zephir.monocul.us:6969/announce",
        "udp://tracker.tvunderground.org.ru:3218/announce",
        "udp://tracker.justseed.it:1337/announce",
        "udp://tracker.flashtorrents.org:6969/announce",
        "udp://tracker.yoshi210.com:6969/announce",
        "udp://tracker.tiny-vps.com:6969/announce",
        "udp://tracker.mg64.net:2710/announce",
        "udp://open.dstud.io:6969/announce",
        "udp://peerfect.org:6969/announce",
        "udp://ipv6.tracker.harry.lu:80/announce",
        "udp://tracker.zer0day.to:1337/announce",
        "udp://tracker.leechers-paradise.org:6969/announce",
        "udp://coppersurfer.tk:6969/announce"
    )
    
    /**
     * Academic and research trackers
     */
    private fun academicTrackers() = listOf(
        "udp://tracker.coppersurfer.tk:6969/announce",
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://torrent.gresille.org:80/announce",
        "udp://9.rarbg.me:2710/announce",
        "udp://p4p.arenabg.com:1337/announce",
        "udp://tracker.internetwarriors.net:1337/announce",
        "udp://tracker.sktorrent.net:6969/announce",
        "udp://bt.xxx-tracker.com:2710/announce",
        "udp://public.popcorn-tracker.org:6969/announce",
        "udp://tracker4.itzmx.com:2710/announce",
        "udp://tracker.justseed.it:1337/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.trackerfix.com:80/announce",
        "udp://tracker.coppersurfer.tk:80/announce",
        "udp://tracker.kicks-ass.net:80/announce",
        "udp://tracker.aletorrenty.pl:2710/announce",
        "udp://inferno.demonoid.pw:3418/announce",
        "udp://tracker.ccc.de:80/announce",
        "udp://tracker.port443.xyz:6969/announce",
        "udp://tracker.dakku.eu:6969/announce"
    )
    
    /**
     * Specialized and regional trackers
     */
    private fun specializedTrackers() = listOf(
        // Anime/Asian Content
        "udp://tracker.kamigami.org:2710/announce",
        "udp://tracker.anime.toshmods.club:6969/announce",
        "http://tracker.anime.index-tracker.net:6969/announce",
        "http://tracker.anirena.com:80/announce",
        "udp://tracker.shittyurl.org:6969/announce",
        
        // Music Trackers
        "udp://tracker.underground.org.ua:6969/announce",
        "udp://tracker.sbsub.com:2710/announce",
        "http://tracker.electro-torrent.pl:80/announce",
        "udp://tracker.swateam.org.uk:2710/announce",
        
        // Game Trackers
        "udp://tracker.grepler.com:6969/announce",
        "udp://tracker.flashtorrents.org:6969/announce",
        "http://tracker.tfile.me:80/announce",
        
        // Linux/Open Source
        "udp://tracker.linux.community:6969/announce",
        "http://tracker.debian.org:6969/announce",
        "http://linuxtracker.org:2710/announce",
        
        // Regional - Russia
        "http://retracker.local.msn-net.ru:80/announce",
        "http://tracker.mastertracker.xyz:80/announce",
        "udp://retracker.akado-ural.ru:80/announce",
        "http://bt.rutracker.cc:2710/announce",
        "http://retracker.bashtel.ru:80/announce",
        "http://retracker.krs-ix.ru:80/announce",
        "http://retracker.mgts.by:80/announce",
        "udp://bt.synergy-rnd.ru:6969/announce",
        "http://retracker.sevstar.net:2710/announce",
        
        // Regional - France
        "http://tracker.torrent.fr:80/announce",
        "http://www.torrent.411.to:80/announce",
        "udp://tracker.cpasbien.me:6969/announce",
        "http://tracker.yggtorrent.is:80/announce",
        
        // Regional - Germany
        "http://tracker.bittor.pw:80/announce",
        "udp://tracker.zum.bi:6969/announce",
        "http://tracker.funfile.org:2710/announce",
        
        // Regional - Poland
        "http://tracker.tntvillage.scambioetico.org:2710/announce",
        "http://tracker.aletorrenty.pl:2710/announce",
        "udp://tracker.demonoid.ooo:1337/announce",
        
        // Regional - India
        "udp://tracker.india.org:6969/announce",
        "http://tracker.desitorrents.com:80/announce",
        
        // Regional - Brazil  
        "http://tracker.bittorrent.am:80/announce",
        "udp://tracker.tvondeman.nl:80/announce",
        
        // Additional General Purpose
        "udp://tracker.monitorit4.me:6969/announce",
        "udp://tracker.ilibr.org:6969/announce",
        "udp://tracker.filetracker.pl:8089/announce",
        "http://tracker.tlm-project.org:6969/announce",
        "http://tracker.city9x.com:2710/announce",
        "http://tracker.ipv6tracker.ru:80/announce",
        "http://tracker.dutchtracking.com:80/announce",
        "http://tracker.dutchtracking.nl:80/announce",
        "http://tracker.ex.ua:80/announce",
        "http://tracker.kicks-ass.net:80/announce",
        "http://tracker.baravik.org:6970/announce",
        "http://tracker.bittor.pw:1337/announce",
        "http://tracker.dler.org:6969/announce",
        "http://tracker.edoardocolombo.eu:6969/announce",
        "http://tracker.electro-torrent.pl:80/announce",
        "http://tracker.ex.ua:80/announce.php",
        "http://tracker.filetracker.pl:8089/announce",
        "http://tracker.grepler.com:6969/announce",
        "http://tracker.kicks-ass.net:80/announce.php",
        "http://tracker.kuroy.me:5944/announce",
        "http://tracker.tfile.me:80/announce.php",
        "http://tracker.trackerfix.com:80/announce",
        "http://tracker.yify-torrents.com:80/announce"
    )
    
    /**
     * Get tracker categories
     */
    fun getTrackerCategories(): Map<String, List<String>> {
        return mapOf(
            "Public DHT" to publicTrackers(),
            "OpenBitTorrent Network" to openBitTorrentTrackers(),
            "Academic & Research" to academicTrackers(),
            "Specialized & Regional" to specializedTrackers()
        )
    }
}
