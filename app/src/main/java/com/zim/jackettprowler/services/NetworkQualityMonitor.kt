package com.zim.jackettprowler.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Network Quality Monitor
 * 
 * Real-time network quality monitoring with:
 * - Connection type detection (WiFi, Cellular, VPN)
 * - Latency measurement
 * - Bandwidth estimation
 * - Network change callbacks
 * - Offline detection
 */
class NetworkQualityMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "NetworkQuality"
        
        // Test endpoints for latency measurement
        private val LATENCY_TEST_URLS = listOf(
            "https://www.google.com/generate_204",
            "https://connectivitycheck.gstatic.com/generate_204",
            "https://www.cloudflare.com/cdn-cgi/trace"
        )
        
        // Quality thresholds (in milliseconds)
        const val LATENCY_EXCELLENT = 100L
        const val LATENCY_GOOD = 300L
        const val LATENCY_FAIR = 600L
        const val LATENCY_POOR = 1000L
    }
    
    enum class ConnectionType {
        WIFI, CELLULAR, ETHERNET, VPN, UNKNOWN, OFFLINE
    }
    
    enum class NetworkQuality {
        EXCELLENT, GOOD, FAIR, POOR, OFFLINE
    }
    
    data class NetworkStatus(
        val connectionType: ConnectionType,
        val quality: NetworkQuality,
        val latencyMs: Long,
        val isMetered: Boolean,
        val hasInternet: Boolean,
        val signalStrength: Int, // 0-100
        val lastChecked: Long
    )
    
    interface NetworkChangeListener {
        fun onNetworkChanged(status: NetworkStatus)
        fun onNetworkLost()
        fun onNetworkAvailable()
    }
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val listeners = mutableListOf<NetworkChangeListener>()
    private val isMonitoring = AtomicBoolean(false)
    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    
    private var lastStatus: NetworkStatus? = null
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available")
            scope.launch {
                val status = measureNetworkQuality()
                lastStatus = status
                withContext(Dispatchers.Main) {
                    listeners.forEach { it.onNetworkAvailable() }
                    listeners.forEach { it.onNetworkChanged(status) }
                }
            }
        }
        
        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost")
            lastStatus = NetworkStatus(
                connectionType = ConnectionType.OFFLINE,
                quality = NetworkQuality.OFFLINE,
                latencyMs = -1,
                isMetered = false,
                hasInternet = false,
                signalStrength = 0,
                lastChecked = System.currentTimeMillis()
            )
            scope.launch(Dispatchers.Main) {
                listeners.forEach { it.onNetworkLost() }
            }
        }
        
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            Log.d(TAG, "Network capabilities changed")
            scope.launch {
                val status = measureNetworkQuality()
                if (status != lastStatus) {
                    lastStatus = status
                    withContext(Dispatchers.Main) {
                        listeners.forEach { it.onNetworkChanged(status) }
                    }
                }
            }
        }
    }
    
    /**
     * Start monitoring network quality
     */
    fun startMonitoring() {
        if (isMonitoring.getAndSet(true)) return
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            Log.d(TAG, "Network monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}")
        }
        
        // Start periodic quality checks
        monitorJob = scope.launch {
            while (isActive) {
                delay(TimeUnit.MINUTES.toMillis(2))
                val status = measureNetworkQuality()
                if (status != lastStatus) {
                    lastStatus = status
                    withContext(Dispatchers.Main) {
                        listeners.forEach { it.onNetworkChanged(status) }
                    }
                }
            }
        }
    }
    
    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        if (!isMonitoring.getAndSet(false)) return
        
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering callback: ${e.message}")
        }
        
        monitorJob?.cancel()
        Log.d(TAG, "Network monitoring stopped")
    }
    
    /**
     * Add listener for network changes
     */
    fun addListener(listener: NetworkChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }
    
    /**
     * Remove listener
     */
    fun removeListener(listener: NetworkChangeListener) {
        listeners.remove(listener)
    }
    
    /**
     * Get current network status
     */
    fun getCurrentStatus(): NetworkStatus {
        return lastStatus ?: runBlocking { measureNetworkQuality() }
    }
    
    /**
     * Check if network is available
     */
    fun isNetworkAvailable(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    /**
     * Measure current network quality
     */
    suspend fun measureNetworkQuality(): NetworkStatus {
        val connectionType = getConnectionType()
        
        if (connectionType == ConnectionType.OFFLINE) {
            return NetworkStatus(
                connectionType = ConnectionType.OFFLINE,
                quality = NetworkQuality.OFFLINE,
                latencyMs = -1,
                isMetered = false,
                hasInternet = false,
                signalStrength = 0,
                lastChecked = System.currentTimeMillis()
            )
        }
        
        val latency = measureLatency()
        val quality = latencyToQuality(latency)
        val isMetered = isConnectionMetered()
        
        return NetworkStatus(
            connectionType = connectionType,
            quality = quality,
            latencyMs = latency,
            isMetered = isMetered,
            hasInternet = latency >= 0,
            signalStrength = estimateSignalStrength(latency),
            lastChecked = System.currentTimeMillis()
        )
    }
    
    // Private helpers
    
    private fun getConnectionType(): ConnectionType {
        val activeNetwork = connectivityManager.activeNetwork ?: return ConnectionType.OFFLINE
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return ConnectionType.OFFLINE
        
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionType.VPN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
            else -> ConnectionType.UNKNOWN
        }
    }
    
    private suspend fun measureLatency(): Long = withContext(Dispatchers.IO) {
        for (url in LATENCY_TEST_URLS) {
            try {
                val startTime = System.currentTimeMillis()
                val request = Request.Builder().url(url).head().build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code == 204) {
                        return@withContext System.currentTimeMillis() - startTime
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Latency test to $url failed: ${e.message}")
            }
        }
        return@withContext -1L // No connectivity
    }
    
    private fun latencyToQuality(latencyMs: Long): NetworkQuality {
        return when {
            latencyMs < 0 -> NetworkQuality.OFFLINE
            latencyMs <= LATENCY_EXCELLENT -> NetworkQuality.EXCELLENT
            latencyMs <= LATENCY_GOOD -> NetworkQuality.GOOD
            latencyMs <= LATENCY_FAIR -> NetworkQuality.FAIR
            else -> NetworkQuality.POOR
        }
    }
    
    private fun isConnectionMetered(): Boolean {
        return connectivityManager.isActiveNetworkMetered
    }
    
    private fun estimateSignalStrength(latencyMs: Long): Int {
        return when {
            latencyMs < 0 -> 0
            latencyMs <= LATENCY_EXCELLENT -> 100
            latencyMs <= LATENCY_GOOD -> 75
            latencyMs <= LATENCY_FAIR -> 50
            latencyMs <= LATENCY_POOR -> 25
            else -> 10
        }
    }
    
    /**
     * Get recommended concurrent connection count based on network quality
     */
    fun getRecommendedConcurrentConnections(): Int {
        val status = getCurrentStatus()
        return when (status.quality) {
            NetworkQuality.EXCELLENT -> 10
            NetworkQuality.GOOD -> 6
            NetworkQuality.FAIR -> 4
            NetworkQuality.POOR -> 2
            NetworkQuality.OFFLINE -> 0
        }
    }
    
    /**
     * Get recommended timeout based on network quality
     */
    fun getRecommendedTimeoutMs(): Long {
        val status = getCurrentStatus()
        return when (status.quality) {
            NetworkQuality.EXCELLENT -> 10_000L
            NetworkQuality.GOOD -> 15_000L
            NetworkQuality.FAIR -> 20_000L
            NetworkQuality.POOR -> 30_000L
            NetworkQuality.OFFLINE -> 5_000L
        }
    }
    
    fun destroy() {
        stopMonitoring()
        scope.cancel()
    }
}
