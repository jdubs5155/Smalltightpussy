package com.zim.jackettprowler

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zim.jackettprowler.automation.TrackerSiteScanner
import com.zim.jackettprowler.databinding.ActivityTrackerManagementBinding
import kotlinx.coroutines.*

/**
 * Activity for managing torrent tracker lists
 * Allows users to view, enable/disable, and add custom trackers
 * NEW: Scan 200+ trackers and auto-configure search interfaces!
 */
class TrackerManagementActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityTrackerManagementBinding
    private lateinit var trackerManager: TrackerManager
    private lateinit var trackerScanner: TrackerSiteScanner
    private lateinit var adapter: TrackerAdapter
    
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackerManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        title = "Tracker Management (${TrackerDatabase.getTrackerCount()}+ Available)"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        trackerManager = TrackerManager(this)
        trackerScanner = TrackerSiteScanner(this)
        
        setupUI()
        setupListeners()
        updateStats()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
    
    private fun setupUI() {
        // Setup RecyclerView for tracker list
        adapter = TrackerAdapter(
            trackers = trackerManager.getEnabledTrackers(),
            onRemove = { tracker -> removeTracker(tracker) }
        )
        
        binding.recyclerTrackers.layoutManager = LinearLayoutManager(this)
        binding.recyclerTrackers.adapter = adapter
        
        // Enable/disable switch
        binding.switchEnableTrackers.isChecked = trackerManager.isTrackerEnhancementEnabled()
        
        // Show tracker count
        val enabledCount = trackerManager.getEnabledTrackers().size
        val defaultCount = TrackerManager.DEFAULT_FAST_TRACKERS.size
        binding.textTrackerInfo.text = "$enabledCount trackers active (${defaultCount} available)"
    }
    
    private fun setupListeners() {
        binding.switchEnableTrackers.setOnCheckedChangeListener { _, isChecked ->
            trackerManager.setTrackerEnhancementEnabled(isChecked)
            Toast.makeText(this, 
                if (isChecked) "Tracker enhancement enabled" else "Tracker enhancement disabled",
                Toast.LENGTH_SHORT
            ).show()
        }
        
        binding.buttonAddTracker.setOnClickListener {
            showAddTrackerDialog()
        }
        
        binding.buttonResetDefaults.setOnClickListener {
            showResetDialog()
        }
        
        binding.buttonUseTop50.setOnClickListener {
            useTopTrackers(50)
        }
        
        binding.buttonUseAll.setOnClickListener {
            useAllTrackers()
        }
        
        // NEW: Copy all 200+ trackers to clipboard
        binding.buttonCopyAllTrackers.setOnClickListener {
            copyAllTrackersToClipboard()
        }
        
        // NEW: Scan trackers for search capability
        binding.buttonScanTrackers.setOnClickListener {
            scanTrackersForSearchCapability()
        }
        
        // NEW: Show tracker database
        binding.buttonViewDatabase.setOnClickListener {
            showTrackerDatabase()
        }
    }
    
    private fun showAddTrackerDialog() {
        val input = EditText(this)
        input.hint = "udp://tracker.example.com:1337/announce"
        
        AlertDialog.Builder(this)
            .setTitle("Add Custom Tracker")
            .setMessage("Enter tracker URL (UDP/HTTP/HTTPS):")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val trackerUrl = input.text.toString().trim()
                if (trackerUrl.isNotEmpty() && isValidTrackerUrl(trackerUrl)) {
                    trackerManager.addCustomTrackers(listOf(trackerUrl))
                    refreshTrackerList()
                    Toast.makeText(this, "Tracker added", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Invalid tracker URL", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showResetDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset to Defaults")
            .setMessage("This will remove all custom trackers and reset to the default list of fast trackers.")
            .setPositiveButton("Reset") { _, _ ->
                trackerManager.resetToDefaults()
                refreshTrackerList()
                Toast.makeText(this, "Reset to defaults", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun useTopTrackers(count: Int) {
        val topTrackers = TrackerManager.DEFAULT_FAST_TRACKERS.take(count)
        trackerManager.setEnabledTrackers(topTrackers)
        refreshTrackerList()
        Toast.makeText(this, "Using top $count trackers", Toast.LENGTH_SHORT).show()
    }
    
    private fun useAllTrackers() {
        trackerManager.setEnabledTrackers(TrackerManager.DEFAULT_FAST_TRACKERS)
        refreshTrackerList()
        Toast.makeText(this, "Using all ${TrackerManager.DEFAULT_FAST_TRACKERS.size} trackers", Toast.LENGTH_SHORT).show()
    }
    
    private fun removeTracker(tracker: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove Tracker")
            .setMessage("Remove this tracker?\n\n$tracker")
            .setPositiveButton("Remove") { _, _ ->
                trackerManager.removeCustomTracker(tracker)
                
                // Also remove from enabled list
                val enabled = trackerManager.getEnabledTrackers().toMutableList()
                enabled.remove(tracker)
                trackerManager.setEnabledTrackers(enabled)
                
                refreshTrackerList()
                Toast.makeText(this, "Tracker removed", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun refreshTrackerList() {
        adapter.updateTrackers(trackerManager.getEnabledTrackers())
        updateStats()
    }
    
    private fun updateStats() {
        val enabledCount = trackerManager.getEnabledTrackers().size
        val customCount = trackerManager.getCustomTrackers().size
        val defaultCount = TrackerManager.DEFAULT_FAST_TRACKERS.size
        
        binding.textTrackerInfo.text = buildString {
            append("$enabledCount active trackers")
            if (customCount > 0) {
                append(" ($customCount custom)")
            }
            append("\n${defaultCount} total available")
        }
        
        // Show protocol breakdown
        val trackers = trackerManager.getEnabledTrackers()
        val udpCount = trackers.count { it.startsWith("udp://") }
        val httpCount = trackers.count { it.startsWith("http://") }
        val httpsCount = trackers.count { it.startsWith("https://") }
        
        binding.textTrackerStats.text = "UDP: $udpCount | HTTP: $httpCount | HTTPS: $httpsCount"
    }
    
    /**
     * Copy all 200+ trackers from TrackerDatabase to clipboard
     */
    private fun copyAllTrackersToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val trackerText = TrackerDatabase.getTrackersAsText()
        val clip = ClipData.newPlainText("BitTorrent Trackers", trackerText)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(
            this,
            "✅ Copied ${TrackerDatabase.getTrackerCount()} trackers to clipboard!\n\n" +
                    "📋 Paste into:\n" +
                    "• qBittorrent: Tools → Options → BitTorrent → Add trackers\n" +
                    "• LibreTorrent: Settings → Torrent → Add trackers\n" +
                    "• Deluge: Preferences → Network → Add trackers",
            Toast.LENGTH_LONG
        ).show()
    }
    
    /**
     * Scan 200+ trackers for search capability and auto-add to built-in providers
     * Uses Tool-X integration for automatic configuration detection
     */
    private fun scanTrackersForSearchCapability() {
        val options = arrayOf(
            "🚀 Quick Scan (Known Sites Only)",
            "🔍 Full Scan (All ${TrackerDatabase.getTrackerCount()} Trackers)",
            "📖 Learn More About Tool-X"
        )
        
        AlertDialog.Builder(this)
            .setTitle("🔍 Scan Trackers for Search APIs\n\n" +
                    "Auto-detect search interfaces,\n" +
                    "configure selectors & add providers")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> performQuickScan()
                    1 -> performFullScan()
                    2 -> showToolXIntegrationInfo()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Quick scan - only check known search interfaces
     */
    private fun performQuickScan() {
        val progressDialog = ProgressDialog(this).apply {
            setTitle("🚀 Quick Scan")
            setMessage("Scanning known search interfaces...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            show()
        }
        
        uiScope.launch(Dispatchers.IO) {
            val result = trackerScanner.quickScan { current, total, message ->
                launch(Dispatchers.Main) {
                    progressDialog.progress = (current * 100) / total
                    progressDialog.setMessage(message)
                }
            }
            
            launch(Dispatchers.Main) {
                progressDialog.dismiss()
                showScanResults(result)
            }
        }
    }
    
    /**
     * Full scan - scan all 200+ trackers
     */
    private fun performFullScan() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Full Scan Warning")
            .setMessage("Full scan will check all ${TrackerDatabase.getTrackerCount()} trackers.\n\n" +
                    "This may take 5-10 minutes and use network data.\n\n" +
                    "Continue?")
            .setPositiveButton("Start Scan") { _, _ ->
                val progressDialog = ProgressDialog(this).apply {
                    setTitle("🔍 Full Tracker Scan")
                    setMessage("Scanning ${TrackerDatabase.getTrackerCount()} trackers...")
                    setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
                    max = TrackerDatabase.getTrackerCount()
                    setCancelable(false)
                    show()
                }
                
                uiScope.launch(Dispatchers.IO) {
                    val result = trackerScanner.scanAllTrackers { current, total, message ->
                        launch(Dispatchers.Main) {
                            progressDialog.progress = current
                            progressDialog.setMessage("($current/$total) $message")
                        }
                    }
                    
                    launch(Dispatchers.Main) {
                        progressDialog.dismiss()
                        showScanResults(result)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Show scan results
     */
    private fun showScanResults(result: TrackerSiteScanner.BatchScanResult) {
        val successfulConfigs = result.results.filter { it.autoConfigured }
        
        val message = buildString {
            appendLine("📊 SCAN RESULTS")
            appendLine("═".repeat(30))
            appendLine()
            appendLine("Trackers Scanned: ${result.totalScanned}")
            appendLine("Search Interfaces Found: ${result.searchInterfacesFound}")
            appendLine("Auto-Configured: ${result.autoConfigured}")
            appendLine()
            
            if (successfulConfigs.isNotEmpty()) {
                appendLine("✅ ADDED TO PROVIDERS:")
                successfulConfigs.forEach { config ->
                    appendLine("  • ${config.config?.name ?: config.searchUrl}")
                }
                appendLine()
                appendLine("These sites are now searchable via 'Everything' button!")
            } else {
                appendLine("No new providers were auto-configured.")
                appendLine("Try Quick Scan for pre-configured sites.")
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("✓ Scan Complete")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("View Details") { _, _ ->
                showDetailedResults(result)
            }
            .show()
    }
    
    /**
     * Show detailed scan results
     */
    private fun showDetailedResults(result: TrackerSiteScanner.BatchScanResult) {
        val detailMessage = result.results
            .filter { it.hasSearchInterface || it.error != null }
            .take(50)
            .joinToString("\n") { scanResult ->
                when {
                    scanResult.autoConfigured -> "✅ ${scanResult.searchUrl}"
                    scanResult.hasSearchInterface -> "⚠️ ${scanResult.searchUrl} (manual config needed)"
                    scanResult.error != null -> "❌ ${scanResult.trackerUrl}: ${scanResult.error}"
                    else -> "○ ${scanResult.trackerUrl}"
                }
            }
        
        AlertDialog.Builder(this)
            .setTitle("Detailed Results (First 50)")
            .setMessage(detailMessage)
            .setPositiveButton("OK", null)
            .show()
    }
    
    /**
     * Show Tool-X integration information
     */
    private fun showToolXIntegrationInfo() {
        AlertDialog.Builder(this)
            .setTitle("🛠️ Tool-X Auto-Configuration")
            .setMessage("Tool-X will analyze each tracker:\n\n" +
                    "1️⃣ HTTP/HTTPS Detection\n" +
                    "   • Test if tracker has web interface\n" +
                    "   • Extract domain and path patterns\n\n" +
                    "2️⃣ API Discovery\n" +
                    "   • Probe for Torznab endpoints\n" +
                    "   • Check RSS feed availability\n" +
                    "   • Test JSON-RPC APIs\n\n" +
                    "3️⃣ HTML Scraping Setup\n" +
                    "   • Auto-generate CSS selectors\n" +
                    "   • Map search result structure\n" +
                    "   • Extract magnet/download links\n\n" +
                    "4️⃣ Configuration Storage\n" +
                    "   • Save to CustomSiteConfig\n" +
                    "   • Add to built-in providers\n" +
                    "   • Enable for 'Everything' search\n\n" +
                    "All configuration happens automatically in the background!")
            .setPositiveButton("Got It", null)
            .show()
    }
    
    /**
     * Show complete tracker database with categories
     */
    private fun showTrackerDatabase() {
        val categories = TrackerDatabase.getTrackerCategories()
        val categoryNames = categories.keys.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("📊 Tracker Database (${TrackerDatabase.getTrackerCount()} Total)")
            .setItems(categoryNames) { _, which ->
                val selectedCategory = categoryNames[which]
                val trackers = categories[selectedCategory] ?: emptyList()
                showCategoryTrackers(selectedCategory, trackers)
            }
            .setNeutralButton("Copy All ${TrackerDatabase.getTrackerCount()}", null)
            .setNegativeButton("Close", null)
            .show()
            .getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                copyAllTrackersToClipboard()
            }
    }
    
    /**
     * Show trackers from specific category
     */
    private fun showCategoryTrackers(category: String, trackers: List<String>) {
        val trackerList = trackers.mapIndexed { index, tracker ->
            "${index + 1}. $tracker"
        }.joinToString("\n\n")
        
        AlertDialog.Builder(this)
            .setTitle("$category (${trackers.size} trackers)")
            .setMessage(trackerList)
            .setPositiveButton("Copy These ${trackers.size}") { _, _ ->
                copyTrackersToClipboard(trackers, category)
            }
            .setNegativeButton("Back", null)
            .show()
    }
    
    /**
     * Copy specific tracker category to clipboard
     */
    private fun copyTrackersToClipboard(trackers: List<String>, category: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val trackerText = trackers.joinToString("\n\n")
        val clip = ClipData.newPlainText("$category Trackers", trackerText)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(
            this,
            "✅ Copied ${trackers.size} $category trackers to clipboard!",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    private fun isValidTrackerUrl(url: String): Boolean {
        return url.matches(Regex("^(udp|http|https)://.*")) &&
               (url.contains("/announce") || url.contains(":"))
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    /**
     * Simple adapter for displaying tracker list
     */
    private class TrackerAdapter(
        private var trackers: List<String>,
        private val onRemove: (String) -> Unit
    ) : RecyclerView.Adapter<TrackerAdapter.ViewHolder>() {
        
        class ViewHolder(val textView: android.widget.TextView) : RecyclerView.ViewHolder(textView)
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val textView = android.widget.TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
                setPadding(32, 24, 32, 24)
                textSize = 12f
                setTextColor(android.graphics.Color.WHITE)
            }
            return ViewHolder(textView)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val tracker = trackers[position]
            holder.textView.text = tracker
            
            // Add protocol icon
            val icon = when {
                tracker.startsWith("udp://") -> "📡"
                tracker.startsWith("https://") -> "🔒"
                tracker.startsWith("http://") -> "🌐"
                else -> "❓"
            }
            holder.textView.text = "$icon $tracker"
            
            holder.textView.setOnLongClickListener {
                onRemove(tracker)
                true
            }
        }
        
        override fun getItemCount() = trackers.size
        
        fun updateTrackers(newTrackers: List<String>) {
            trackers = newTrackers
            notifyDataSetChanged()
        }
    }
}
