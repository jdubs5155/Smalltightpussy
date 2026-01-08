package com.zim.jackettprowler.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zim.jackettprowler.R
import com.zim.jackettprowler.automation.ConnectionStabilityManager
import com.zim.jackettprowler.automation.ProviderHealthMonitor
import com.zim.jackettprowler.providers.ProviderRegistry
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * Background Service Daemon Manager
 * 
 * Manages all background services and daemon processes:
 * - Provider health monitoring (auto-disable dead providers)
 * - Connection stability checking
 * - Search result caching
 * - Auto-update provider configurations
 * - Network quality monitoring
 * 
 * Runs as a foreground service for reliability on Android 8+
 */
class BackgroundServiceDaemon : Service() {
    
    companion object {
        private const val TAG = "BackgroundDaemon"
        private const val CHANNEL_ID = "torrent_search_daemon"
        private const val NOTIFICATION_ID = 1337
        
        // Service intervals
        private const val HEALTH_CHECK_INTERVAL = 5 * 60 * 1000L // 5 minutes
        private const val CONNECTION_CHECK_INTERVAL = 2 * 60 * 1000L // 2 minutes
        private const val CACHE_CLEANUP_INTERVAL = 30 * 60 * 1000L // 30 minutes
        
        // Start the service
        fun start(context: Context) {
            val intent = Intent(context, BackgroundServiceDaemon::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        // Stop the service
        fun stop(context: Context) {
            context.stopService(Intent(context, BackgroundServiceDaemon::class.java))
        }
        
        // Check if daemon is enabled in settings
        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences("daemon_settings", Context.MODE_PRIVATE)
                .getBoolean("daemon_enabled", true)
        }
        
        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences("daemon_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("daemon_enabled", enabled).apply()
            
            if (enabled) start(context) else stop(context)
        }
    }
    
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    
    private lateinit var healthMonitor: ProviderHealthMonitor
    private lateinit var connectionManager: ConnectionStabilityManager
    private lateinit var prefs: android.content.SharedPreferences
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 Background Daemon Service starting...")
        
        healthMonitor = ProviderHealthMonitor(this)
        connectionManager = ConnectionStabilityManager(this)
        prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Monitoring providers..."))
        
        startBackgroundTasks()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Daemon service onStartCommand")
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 Background Daemon Service stopping...")
        serviceJob.cancel()
    }
    
    /**
     * Start all background monitoring tasks
     */
    private fun startBackgroundTasks() {
        // Health check daemon
        serviceScope.launch {
            while (isActive) {
                try {
                    performHealthCheck()
                } catch (e: Exception) {
                    Log.e(TAG, "Health check failed: ${e.message}")
                }
                delay(HEALTH_CHECK_INTERVAL)
            }
        }
        
        // Connection stability daemon
        serviceScope.launch {
            while (isActive) {
                try {
                    checkConnectionStability()
                } catch (e: Exception) {
                    Log.e(TAG, "Connection check failed: ${e.message}")
                }
                delay(CONNECTION_CHECK_INTERVAL)
            }
        }
        
        // Cache cleanup daemon
        serviceScope.launch {
            while (isActive) {
                try {
                    cleanupCaches()
                } catch (e: Exception) {
                    Log.e(TAG, "Cache cleanup failed: ${e.message}")
                }
                delay(CACHE_CLEANUP_INTERVAL)
            }
        }
        
        Log.d(TAG, "✅ All background daemons started")
    }
    
    /**
     * Perform periodic health check on all providers
     */
    private suspend fun performHealthCheck() {
        Log.d(TAG, "🏥 Running scheduled health check...")
        
        val jackettUrl = prefs.getString("jackett_url", null)
        val jackettKey = prefs.getString("jackett_api_key", null)
        val prowlarrUrl = prefs.getString("prowlarr_url", null)
        val prowlarrKey = prefs.getString("prowlarr_api_key", null)
        
        val report = healthMonitor.checkAllProviders(
            jackettUrl = jackettUrl,
            jackettKey = jackettKey,
            prowlarrUrl = prowlarrUrl,
            prowlarrKey = prowlarrKey
        )
        
        // Update notification with health status
        val healthStatus = "${report.healthyProviders}/${report.totalProviders} providers healthy"
        updateNotification(healthStatus)
        
        // Auto-disable consistently failing providers
        if (report.providersToDisable.isNotEmpty()) {
            Log.w(TAG, "Auto-disabling unhealthy providers: ${report.providersToDisable}")
            disableUnhealthyProviders(report.providersToDisable)
        }
        
        // Log stats
        saveHealthStats(report)
    }
    
    /**
     * Check connection stability to configured endpoints
     */
    private suspend fun checkConnectionStability() {
        Log.d(TAG, "🔌 Checking connection stability...")
        
        val jackettUrl = prefs.getString("jackett_url", null)
        val jackettKey = prefs.getString("jackett_api_key", null)
        
        if (jackettUrl != null && jackettKey != null) {
            val config = ConnectionStabilityManager.ConnectionConfig(
                url = jackettUrl,
                apiKey = jackettKey,
                type = "jackett"
            )
            val health = connectionManager.testConnection(config)
            
            if (!health.isHealthy) {
                Log.w(TAG, "⚠️ Jackett connection unhealthy: ${health.consecutiveFailures} failures")
            }
        }
        
        val prowlarrUrl = prefs.getString("prowlarr_url", null)
        val prowlarrKey = prefs.getString("prowlarr_api_key", null)
        
        if (prowlarrUrl != null && prowlarrKey != null) {
            val config = ConnectionStabilityManager.ConnectionConfig(
                url = prowlarrUrl,
                apiKey = prowlarrKey,
                type = "prowlarr"
            )
            val health = connectionManager.testConnection(config)
            
            if (!health.isHealthy) {
                Log.w(TAG, "⚠️ Prowlarr connection unhealthy: ${health.consecutiveFailures} failures")
            }
        }
    }
    
    /**
     * Cleanup old caches and temporary data
     */
    private fun cleanupCaches() {
        Log.d(TAG, "🧹 Cleaning up caches...")
        
        // Clean search result cache
        val searchCachePrefs = getSharedPreferences("search_cache", Context.MODE_PRIVATE)
        val cacheEntries = searchCachePrefs.all.keys.toList()
        val now = System.currentTimeMillis()
        val maxAge = TimeUnit.HOURS.toMillis(1) // Cache valid for 1 hour
        
        var cleanedCount = 0
        cacheEntries.forEach { key ->
            if (key.endsWith("_timestamp")) {
                val timestamp = searchCachePrefs.getLong(key, 0)
                if (now - timestamp > maxAge) {
                    val dataKey = key.removeSuffix("_timestamp")
                    searchCachePrefs.edit()
                        .remove(key)
                        .remove(dataKey)
                        .apply()
                    cleanedCount++
                }
            }
        }
        
        if (cleanedCount > 0) {
            Log.d(TAG, "Cleaned $cleanedCount expired cache entries")
        }
    }
    
    /**
     * Disable providers that are consistently failing
     */
    private fun disableUnhealthyProviders(providerIds: List<String>) {
        val disabledProviders = prefs.getStringSet("disabled_providers", emptySet())?.toMutableSet() ?: mutableSetOf()
        disabledProviders.addAll(providerIds)
        prefs.edit().putStringSet("disabled_providers", disabledProviders).apply()
    }
    
    /**
     * Save health statistics for later analysis
     */
    private fun saveHealthStats(report: ProviderHealthMonitor.HealthReport) {
        val statsPrefs = getSharedPreferences("health_stats", Context.MODE_PRIVATE)
        statsPrefs.edit()
            .putInt("last_total_providers", report.totalProviders)
            .putInt("last_healthy_providers", report.healthyProviders)
            .putLong("last_avg_response_time", report.avgResponseTime)
            .putLong("last_check_time", System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Create notification channel for Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Torrent Search Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background monitoring for torrent search providers"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create foreground notification
     */
    private fun createNotification(status: String): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JackettProwlarr Active")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    /**
     * Update notification with new status
     */
    private fun updateNotification(status: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(status))
    }
}

/**
 * Daemon Controller - Static methods to control the background service
 */
object DaemonController {
    
    private const val TAG = "DaemonController"
    
    /**
     * Initialize and start background services if enabled
     */
    fun initialize(context: Context) {
        if (BackgroundServiceDaemon.isEnabled(context)) {
            Log.d(TAG, "Starting background daemon...")
            BackgroundServiceDaemon.start(context)
        }
    }
    
    /**
     * Get current daemon status
     */
    fun getStatus(context: Context): DaemonStatus {
        val prefs = context.getSharedPreferences("health_stats", Context.MODE_PRIVATE)
        val daemonPrefs = context.getSharedPreferences("daemon_settings", Context.MODE_PRIVATE)
        
        return DaemonStatus(
            isEnabled = BackgroundServiceDaemon.isEnabled(context),
            lastHealthCheck = prefs.getLong("last_check_time", 0),
            totalProviders = prefs.getInt("last_total_providers", 0),
            healthyProviders = prefs.getInt("last_healthy_providers", 0),
            avgResponseTime = prefs.getLong("last_avg_response_time", 0)
        )
    }
    
    data class DaemonStatus(
        val isEnabled: Boolean,
        val lastHealthCheck: Long,
        val totalProviders: Int,
        val healthyProviders: Int,
        val avgResponseTime: Long
    )
}
