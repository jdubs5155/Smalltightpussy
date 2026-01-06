package com.zim.jackettprowler

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.*

/**
 * Manager for Tor/SOCKS proxy connections to access .onion sites
 */
class TorProxyManager(private val context: Context) {
    private var proxyHost: String = "127.0.0.1"
    private var proxyPort: Int = 9050 // Default Orbot SOCKS port
    private var isOrbotInstalled: Boolean = false
    private var isTorEnabled: Boolean = false
    
    companion object {
        const val ORBOT_PACKAGE = "org.torproject.android"
        const val ORBOT_MARKET_URI = "market://details?id=$ORBOT_PACKAGE"
    }
    
    init {
        checkOrbotInstalled()
        loadSettings()
    }
    
    /**
     * Check if Orbot is installed
     */
    private fun checkOrbotInstalled() {
        isOrbotInstalled = try {
            context.packageManager.getPackageInfo(ORBOT_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Load Tor settings from SharedPreferences
     */
    private fun loadSettings() {
        val prefs = context.getSharedPreferences("tor_settings", Context.MODE_PRIVATE)
        isTorEnabled = prefs.getBoolean("tor_enabled", false)
        proxyHost = prefs.getString("proxy_host", "127.0.0.1") ?: "127.0.0.1"
        proxyPort = prefs.getInt("proxy_port", 9050)
    }
    
    /**
     * Save Tor settings
     */
    fun saveSettings(enabled: Boolean, host: String = proxyHost, port: Int = proxyPort) {
        val prefs = context.getSharedPreferences("tor_settings", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("tor_enabled", enabled)
            putString("proxy_host", host)
            putInt("proxy_port", port)
            apply()
        }
        isTorEnabled = enabled
        proxyHost = host
        proxyPort = port
    }
    
    /**
     * Check if Tor is enabled and configured
     */
    fun isTorAvailable(): Boolean {
        return isTorEnabled && isProxyReachable()
    }
    
    /**
     * Test if SOCKS proxy is reachable
     */
    private fun isProxyReachable(): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(proxyHost, proxyPort), 5000)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Test Tor connection by checking Tor check service
     */
    suspend fun testTorConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val testUrl = "https://check.torproject.org/api/ip"
            val response = fetchViaProxy(testUrl)
            response.contains("\"IsTor\":true")
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Fetch content via SOCKS proxy (Tor)
     */
    suspend fun fetchViaProxy(
        urlString: String,
        headers: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        if (!isTorEnabled) {
            throw IOException("Tor is not enabled")
        }
        
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyHost, proxyPort))
        val url = URL(urlString)
        val connection = url.openConnection(proxy) as HttpURLConnection
        
        try {
            connection.connectTimeout = 60000 // Onion sites can be slow
            connection.readTimeout = 60000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0")
            
            // Add custom headers
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                throw IOException("HTTP error code: $responseCode")
            }
            
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
                response.append('\n')
            }
            
            reader.close()
            response.toString()
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * Start Orbot (if installed)
     */
    fun startOrbot(): Boolean {
        if (!isOrbotInstalled) {
            return false
        }
        
        try {
            val intent = Intent("org.torproject.android.intent.action.START")
            intent.setPackage(ORBOT_PACKAGE)
            context.sendBroadcast(intent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Stop Orbot
     */
    fun stopOrbot(): Boolean {
        if (!isOrbotInstalled) {
            return false
        }
        
        try {
            val intent = Intent("org.torproject.android.intent.action.STOP")
            intent.setPackage(ORBOT_PACKAGE)
            context.sendBroadcast(intent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Request new Tor identity (new circuit)
     */
    fun requestNewIdentity(): Boolean {
        if (!isOrbotInstalled) {
            return false
        }
        
        try {
            val intent = Intent("org.torproject.android.intent.action.REQUEST_NEWNYM")
            intent.setPackage(ORBOT_PACKAGE)
            context.sendBroadcast(intent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Open Orbot app
     */
    fun openOrbotApp() {
        if (isOrbotInstalled) {
            val intent = context.packageManager.getLaunchIntentForPackage(ORBOT_PACKAGE)
            intent?.let { context.startActivity(it) }
        } else {
            // Open Play Store to install Orbot
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ORBOT_MARKET_URI))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
    
    /**
     * Get connection status
     */
    fun getConnectionStatus(): TorConnectionStatus {
        return when {
            !isTorEnabled -> TorConnectionStatus.DISABLED
            !isOrbotInstalled -> TorConnectionStatus.ORBOT_NOT_INSTALLED
            !isProxyReachable() -> TorConnectionStatus.PROXY_UNREACHABLE
            else -> TorConnectionStatus.CONNECTED
        }
    }
    
    /**
     * Check if URL is an onion address
     */
    fun isOnionUrl(url: String): Boolean {
        return url.contains(".onion")
    }
}

/**
 * Tor connection status
 */
enum class TorConnectionStatus {
    DISABLED,
    ORBOT_NOT_INSTALLED,
    PROXY_UNREACHABLE,
    CONNECTED
}
