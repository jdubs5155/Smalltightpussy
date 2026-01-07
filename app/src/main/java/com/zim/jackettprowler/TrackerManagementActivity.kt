package com.zim.jackettprowler

import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zim.jackettprowler.databinding.ActivityTrackerManagementBinding

/**
 * Activity for managing torrent tracker lists
 * Allows users to view, enable/disable, and add custom trackers
 */
class TrackerManagementActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityTrackerManagementBinding
    private lateinit var trackerManager: TrackerManager
    private lateinit var adapter: TrackerAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackerManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        title = "Tracker Management"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        trackerManager = TrackerManager(this)
        
        setupUI()
        setupListeners()
        updateStats()
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
