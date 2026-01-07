package com.zim.jackettprowler

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages torrent tracker lists for enhancing magnet links
 * Stores curated lists of high-performance trackers to improve download speeds
 */
class TrackerManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tracker_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val KEY_ENABLED_TRACKERS = "enabled_trackers"
        private const val KEY_CUSTOM_TRACKERS = "custom_trackers"
        private const val KEY_USE_TRACKERS = "use_trackers"
        
        /**
         * Curated list of super fast, reliable BitTorrent trackers
         * Updated from community sources (ngosang/trackerslist style)
         */
        val DEFAULT_FAST_TRACKERS = listOf(
            // Super fast trackers (top tier)
            "udp://tracker.openbittorrent.com:80",
            "udp://tracker.leechers-paradise.org:6969",
            "udp://tracker.coppersurfer.tk:6969",
            "udp://glotorrents.pw:6969",
            "udp://tracker.opentrackr.org:1337",
            "http://tracker2.istole.it:60500/announce",
            
            // High-performance UDP trackers
            "udp://9.rarbg.com:2710/announce",
            "udp://tracker.internetwarriors.net:1337/announce",
            "udp://tracker.tiny-vps.com:6969/announce",
            "udp://tracker.skyts.net:6969/announce",
            "udp://tracker.filetracker.pl:8089/announce",
            "udp://tracker.ex.ua:80/announce",
            "udp://tracker.yoshi210.com:6969/announce",
            "udp://p4p.arenabg.com:1337/announce",
            "udp://torrent.gresille.org:80/announce",
            "udp://tracker.piratepublic.com:1337/announce",
            "udp://tracker.kicks-ass.net:80/announce",
            "udp://tracker.aletorrenty.pl:2710/announce",
            "udp://tracker.sktorrent.net:6969/announce",
            "udp://zer0day.ch:1337/announce",
            "udp://mgtracker.org:2710/announce",
            "udp://91.218.230.81:6969/announce",
            "udp://tracker.grepler.com:6969/announce",
            "udp://tracker.flashtorrents.org:6969/announce",
            "udp://tracker.bittor.pw:1337/announce",
            "udp://tracker.kuroy.me:5944/announce",
            "udp://182.176.139.129:6969/announce",
            "udp://open.stealth.si:80/announce",
            "udp://208.67.16.113:8000/announce",
            
            // Reliable HTTP/HTTPS trackers
            "http://tracker.opentrackr.org:1337/announce",
            "http://explodie.org:6969/announce",
            "http://p4p.arenabg.com:1337/announce",
            "http://tracker.aletorrenty.pl:2710/announce",
            "http://tracker.bittorrent.am/announce",
            "http://tracker.kicks-ass.net/announce",
            "http://tracker.baravik.org:6970/announce",
            "http://torrent.gresille.org/announce",
            "http://tracker.skyts.net:6969/announce",
            "http://tracker.internetwarriors.net:1337/announce",
            "http://tracker.dutchtracking.nl/announce",
            "http://tracker.yoshi210.com:6969/announce",
            "http://tracker.tiny-vps.com:6969/announce",
            "http://www.wareztorrent.com/announce",
            "http://tracker.filetracker.pl:8089/announce",
            "http://tracker.ex.ua/announce",
            "http://tracker.calculate.ru:6969/announce",
            "http://tracker.tvunderground.org.ru:3218/announce",
            "http://tracker.grepler.com:6969/announce",
            "http://tracker.flashtorrents.org:6969/announce",
            "http://retracker.gorcomnet.ru/announce",
            "http://bt.pusacg.org:8080/announce",
            "http://87.248.186.252:8080/announce",
            "http://tracker.kuroy.me:5944/announce",
            "http://retracker.krs-ix.ru/announce",
            "http://open.acgtracker.com:1096/announce",
            "http://bt2.careland.com.cn:6969/announce",
            "http://open.lolicon.eu:7777/announce",
            "https://www.wareztorrent.com/announce",
            
            // Additional stable trackers
            "udp://213.163.67.56:1337/announce",
            "http://213.163.67.56:1337/announce",
            "udp://185.86.149.205:1337/announce",
            "http://74.82.52.209:6969/announce",
            "udp://94.23.183.33:6969/announce",
            "udp://74.82.52.209:6969/announce",
            "udp://151.80.120.114:2710/announce",
            "udp://109.121.134.121:1337/announce",
            "udp://168.235.67.63:6969/announce",
            "http://109.121.134.121:1337/announce",
            "udp://178.33.73.26:2710/announce",
            "http://178.33.73.26:2710/announce",
            "http://85.17.19.180/announce",
            "udp://85.17.19.180:80/announce",
            "http://210.244.71.25:6969/announce",
            "http://213.159.215.198:6970/announce",
            "udp://191.101.229.236:1337/announce",
            "http://178.175.143.27/announce",
            "udp://89.234.156.205:80/announce",
            "http://91.216.110.47/announce",
            "http://114.55.113.60:6969/announce",
            "http://195.123.209.37:1337/announce",
            "udp://114.55.113.60:6969/announce",
            "http://210.244.71.26:6969/announce",
            "http://81.169.145.151/announce",
            "udp://107.150.14.110:6969/announce",
            "udp://5.79.249.77:6969/announce",
            "udp://195.123.209.37:1337/announce",
            "udp://37.19.5.155:2710/announce",
            "http://107.150.14.110:6969/announce",
            "http://5.79.249.77:6969/announce",
            "udp://185.5.97.139:8089/announce",
            "udp://194.106.216.222:80/announce",
            "https://104.28.17.69/announce",
            "http://104.28.16.69/announce",
            "http://185.5.97.139:8089/announce",
            "http://194.106.216.222/announce",
            "http://80.246.243.18:6969/announce",
            "http://37.19.5.139:6969/announce",
            "udp://5.79.83.193:6969/announce",
            "udp://46.4.109.148:6969/announce",
            "udp://51.254.244.161:6969/announce",
            "udp://188.165.253.109:1337/announce",
            "http://91.217.91.21:3218/announce",
            "http://37.19.5.155:6881/announce",
            "http://46.4.109.148:6969/announce",
            "http://51.254.244.161:6969/announce",
            "http://104.28.1.30:8080/announce",
            "http://81.200.2.231/announce",
            "http://157.7.202.64:8080/announce",
            "udp://128.199.70.66:5944/announce",
            "http://128.199.70.66:5944/announce",
            "http://188.165.253.109:1337/announce",
            "http://93.92.64.5/announce",
            "http://173.254.204.71:1096/announce",
            "udp://195.123.209.40:80/announce",
            "udp://62.212.85.66:2710/announce",
            "http://125.227.35.196:6969/announce",
            "http://59.36.96.77:6969/announce",
            "http://87.253.152.137/announce",
            "http://158.69.146.212:7777/announce",
            
            // Legacy but reliable trackers
            "udp://p4p.arenabg.ch:1337",
            "http://bttracker.crunchbanglinux.org:6969/announce",
            "udp://tracker.trackerfix.com:80/announce",
            "udp://www.eddie4.nl:6969/announce",
            "http://retracker.kld.ru:2710/announce",
            "http://bt.careland.com.cn:6969/announce",
            "http://mgtracker.org:2710/announce",
            "http://tracker.best-torrents.net:6969/announce",
            "http://tracker.tfile.me/announce",
            "http://tracker.torrenty.org:6969/announce",
            "http://tracker1.wasabii.com.tw:6969/announce",
            "udp://9.rarbg.me:2710/announce",
            "udp://tracker.btzoo.eu:80/announce",
            "http://pow7.com/announce",
            "http://tracker.novalayer.org:6969/announce",
            "http://193.107.16.156:2710/announce",
            "http://cpleft.com:2710/announce",
            "http://retracker.hotplug.ru:2710/announce",
            "http://retracker.kld.ru/announce",
            "http://tracker.coppersurfer.tk:6969/announce",
            "http://inferno.demonoid.me:3414/announce",
            "http://announce.torrentsmd.com:6969/announce",
            "udp://coppersurfer.tk:6969/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://tracker.prq.to/announce",
            "http://exodus.desync.com/announce",
            "http://ipv4.tracker.harry.lu/announce",
            "http://tracker.torrentbay.to:6969/announce",
            "udp://11.rarbg.com/announce",
            "udp://tracker.1337x.org:80/announce",
            "udp://tracker.istole.it:80/announce",
            "udp://tracker.ccc.de:80/announce",
            "udp://fr33dom.h33t.com:3310/announce",
            "udp://tracker.publicbt.com:80/announce",
            
            // Additional working trackers from the list
            "http://182.176.139.129:6969/announce",
            "http://5.79.83.193:2710/announce",
            "http://atrack.pow7.com/announce",
            "http://bt.henbt.com:2710/announce",
            "http://mgtracker.org:6969/announce",
            "http://open.touki.ru/announce.php",
            "http://p4p.arenabg.ch:1337/announce",
            "http://pow7.com:80/announce",
            "http://retracker.krs-ix.ru:80/announce",
            "http://secure.pow7.com/announce",
            "http://t1.pow7.com/announce",
            "http://t2.pow7.com/announce",
            "http://thetracker.org:80/announce",
            "http://torrentsmd.com:8080/announce",
            "http://tracker.bittor.pw:1337/announce",
            "http://tracker.dutchtracking.com:80/announce",
            "http://tracker.dutchtracking.com/announce",
            "http://tracker.edoardocolombo.eu:6969/announce",
            "http://tracker.mg64.net:6881/announce",
            "http://tracker2.itzmx.com:6961/announce",
            "http://tracker2.wasabii.com.tw:6969/announce",
            "udp://62.138.0.158:6969/announce",
            "udp://eddie4.nl:6969/announce",
            "udp://explodie.org:6969/announce",
            "udp://shadowshq.eddie4.nl:6969/announce",
            "udp://shadowshq.yi.org:6969/announce",
            "udp://tracker.eddie4.nl:6969/announce",
            "udp://tracker.mg64.net:2710/announce",
            "udp://tracker.mg64.net:6969/announce",
            "udp://tracker2.indowebster.com:6969/announce",
            "udp://tracker4.piratux.com:6969/announce",
            "udp://bt.xxx-tracker.com:2710/announce",
            "http://tracker.dler.org:6969/announce"
        )
    }
    
    /**
     * Check if tracker enhancement is enabled
     */
    fun isTrackerEnhancementEnabled(): Boolean {
        return prefs.getBoolean(KEY_USE_TRACKERS, true)
    }
    
    /**
     * Enable or disable tracker enhancement
     */
    fun setTrackerEnhancementEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_TRACKERS, enabled).apply()
    }
    
    /**
     * Get list of enabled trackers
     */
    fun getEnabledTrackers(): List<String> {
        val json = prefs.getString(KEY_ENABLED_TRACKERS, null)
        return if (json != null) {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } else {
            // Return top 50 fastest trackers by default
            DEFAULT_FAST_TRACKERS.take(50)
        }
    }
    
    /**
     * Set custom enabled trackers
     */
    fun setEnabledTrackers(trackers: List<String>) {
        val json = gson.toJson(trackers)
        prefs.edit().putString(KEY_ENABLED_TRACKERS, json).apply()
    }
    
    /**
     * Get custom user-added trackers
     */
    fun getCustomTrackers(): List<String> {
        val json = prefs.getString(KEY_CUSTOM_TRACKERS, null)
        return if (json != null) {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } else {
            emptyList()
        }
    }
    
    /**
     * Add custom trackers
     */
    fun addCustomTrackers(trackers: List<String>) {
        val existing = getCustomTrackers().toMutableList()
        existing.addAll(trackers.filter { it !in existing })
        val json = gson.toJson(existing)
        prefs.edit().putString(KEY_CUSTOM_TRACKERS, json).apply()
    }
    
    /**
     * Remove custom tracker
     */
    fun removeCustomTracker(tracker: String) {
        val existing = getCustomTrackers().toMutableList()
        existing.remove(tracker)
        val json = gson.toJson(existing)
        prefs.edit().putString(KEY_CUSTOM_TRACKERS, json).apply()
    }
    
    /**
     * Reset to default trackers
     */
    fun resetToDefaults() {
        prefs.edit()
            .remove(KEY_ENABLED_TRACKERS)
            .remove(KEY_CUSTOM_TRACKERS)
            .apply()
    }
    
    /**
     * Enhance a magnet link by adding trackers
     * @param magnetUri Original magnet link
     * @param maxTrackers Maximum number of trackers to add (default 30)
     * @return Enhanced magnet link with tracker announces
     */
    fun enhanceMagnetLink(magnetUri: String, maxTrackers: Int = 30): String {
        if (!isTrackerEnhancementEnabled() || !magnetUri.startsWith("magnet:")) {
            return magnetUri
        }
        
        // Get trackers to add (enabled + custom)
        val trackers = (getEnabledTrackers() + getCustomTrackers()).distinct().take(maxTrackers)
        
        // Parse existing magnet URI to avoid duplicates
        val existingTrackers = extractTrackersFromMagnet(magnetUri)
        
        // Build tracker parameters
        val newTrackers = trackers
            .filter { it !in existingTrackers }
            .joinToString("") { "&tr=${java.net.URLEncoder.encode(it, "UTF-8")}" }
        
        return if (newTrackers.isNotEmpty()) {
            "$magnetUri$newTrackers"
        } else {
            magnetUri
        }
    }
    
    /**
     * Extract tracker URLs from existing magnet link
     */
    private fun extractTrackersFromMagnet(magnetUri: String): Set<String> {
        val trackers = mutableSetOf<String>()
        val params = magnetUri.substringAfter("magnet:?").split("&")
        
        for (param in params) {
            if (param.startsWith("tr=")) {
                val tracker = java.net.URLDecoder.decode(param.substringAfter("tr="), "UTF-8")
                trackers.add(tracker)
            }
        }
        
        return trackers
    }
    
    /**
     * Get tracker statistics from magnet link
     */
    fun getTrackerStats(magnetUri: String): TrackerStats {
        val trackers = extractTrackersFromMagnet(magnetUri)
        val udpCount = trackers.count { it.startsWith("udp://") }
        val httpCount = trackers.count { it.startsWith("http://") }
        val httpsCount = trackers.count { it.startsWith("https://") }
        
        return TrackerStats(
            total = trackers.size,
            udp = udpCount,
            http = httpCount,
            https = httpsCount
        )
    }
    
    data class TrackerStats(
        val total: Int,
        val udp: Int,
        val http: Int,
        val https: Int
    )
}
