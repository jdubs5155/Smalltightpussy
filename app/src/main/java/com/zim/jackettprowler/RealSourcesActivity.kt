package com.zim.jackettprowler

import android.app.ProgressDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zim.jackettprowler.services.RealSourceManager
import com.zim.jackettprowler.services.VerifiedSiteRegistry
import kotlinx.coroutines.*

/**
 * Activity to manage REAL verified torrent sources.
 * 
 * This shows ONLY sources that have been:
 * 1. Auto-configured from live site analysis
 * 2. Verified to return real torrent data
 * 3. Tested and working
 * 
 * NO PRESETS - ALL REAL DATA!
 */
class RealSourcesActivity : AppCompatActivity() {
    
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private lateinit var realSourceManager: RealSourceManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RealSourceAdapter
    private lateinit var statusText: android.widget.TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_real_sources)
        
        title = "🔒 Real Verified Sources"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        realSourceManager = RealSourceManager(this)
        
        setupViews()
        loadSources()
    }
    
    private fun setupViews() {
        recyclerView = findViewById(R.id.recyclerSources)
        statusText = findViewById(R.id.textStatus)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = RealSourceAdapter(
            onToggle = { sourceId, enabled ->
                realSourceManager.toggleSource(sourceId, enabled)
            },
            onDelete = { sourceId ->
                confirmDelete(sourceId)
            },
            onTest = { source ->
                testSource(source)
            }
        )
        recyclerView.adapter = adapter
        
        // Add source by URL button
        findViewById<android.widget.Button>(R.id.buttonAddByUrl)?.setOnClickListener {
            showAddByUrlDialog()
        }
        
        // Add verified sources button
        findViewById<android.widget.Button>(R.id.buttonAddVerified)?.setOnClickListener {
            showAddVerifiedDialog()
        }
        
        // Re-verify all button
        findViewById<android.widget.Button>(R.id.buttonReverify)?.setOnClickListener {
            reverifyAllSources()
        }
        
        // Initialize sources if empty
        findViewById<android.widget.Button>(R.id.buttonInitialize)?.setOnClickListener {
            initializeSources()
        }
    }
    
    private fun loadSources() {
        val sources = realSourceManager.getAllSources()
        adapter.updateSources(sources)
        
        val stats = realSourceManager.getSourceStats()
        statusText.text = buildString {
            append("📊 ${stats.total} sources | ")
            append("✓ ${stats.working} working | ")
            append("🌐 ${stats.withMagnets} with magnets\n")
            if (stats.needsReverification) {
                append("⚠️ Re-verification recommended")
            } else {
                append("✓ All sources recently verified")
            }
        }
        
        // Show/hide initialize button based on whether sources exist
        findViewById<android.widget.Button>(R.id.buttonInitialize)?.visibility = 
            if (sources.isEmpty()) View.VISIBLE else View.GONE
    }
    
    private fun showAddByUrlDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "https://example.com"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        
        AlertDialog.Builder(this)
            .setTitle("🔍 Add Source by URL")
            .setMessage("Enter a torrent site URL.\nThe app will automatically:\n• Analyze the site structure\n• Detect selectors\n• Test data extraction\n• Save only if working")
            .setView(input)
            .setPositiveButton("Analyze & Add") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    addSourceByUrl(url)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun addSourceByUrl(url: String) {
        val progress = ProgressDialog.show(
            this,
            "🔍 Analyzing Site",
            "Connecting and analyzing...\n• Fetching HTML\n• Detecting structure\n• Testing selectors",
            true
        )
        
        uiScope.launch(Dispatchers.IO) {
            val result = realSourceManager.addSourceByUrl(url)
            
            launch(Dispatchers.Main) {
                progress.dismiss()
                
                if (result.success) {
                    Toast.makeText(
                        this@RealSourcesActivity,
                        "✓ ${result.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    loadSources()
                } else {
                    AlertDialog.Builder(this@RealSourcesActivity)
                        .setTitle("❌ Failed to Add Source")
                        .setMessage(result.message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }
    
    private fun showAddVerifiedDialog() {
        val verifiedSites = VerifiedSiteRegistry.getVerifiedSites()
        val siteNames = verifiedSites.map { 
            "${it.config.name}\n  ${it.description}"
        }.toTypedArray()
        
        val checkedItems = BooleanArray(verifiedSites.size) { false }
        
        AlertDialog.Builder(this)
            .setTitle("🔒 Add Verified Sources")
            .setMultiChoiceItems(siteNames, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Add Selected") { _, _ ->
                val selectedUrls = verifiedSites
                    .filterIndexed { index, _ -> checkedItems[index] }
                    .map { it.config.baseUrl }
                
                if (selectedUrls.isNotEmpty()) {
                    addMultipleSources(selectedUrls)
                }
            }
            .setNeutralButton("Add All") { _, _ ->
                addMultipleSources(verifiedSites.map { it.config.baseUrl })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun addMultipleSources(urls: List<String>) {
        val progress = ProgressDialog(this).apply {
            setTitle("🔍 Adding Sources")
            setMessage("Analyzing and verifying...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = urls.size
            show()
        }
        
        uiScope.launch(Dispatchers.IO) {
            var successCount = 0
            
            urls.forEachIndexed { index, url ->
                launch(Dispatchers.Main) {
                    progress.progress = index + 1
                    progress.setMessage("Analyzing: $url")
                }
                
                val result = realSourceManager.addSourceByUrl(url)
                if (result.success) successCount++
            }
            
            launch(Dispatchers.Main) {
                progress.dismiss()
                Toast.makeText(
                    this@RealSourcesActivity,
                    "✓ Added $successCount of ${urls.size} sources",
                    Toast.LENGTH_LONG
                ).show()
                loadSources()
            }
        }
    }
    
    private fun confirmDelete(sourceId: String) {
        val source = realSourceManager.getAllSources().find { it.config.id == sourceId }
        
        AlertDialog.Builder(this)
            .setTitle("Delete Source?")
            .setMessage("Remove ${source?.config?.name ?: sourceId}?\n\nYou can re-add it later.")
            .setPositiveButton("Delete") { _, _ ->
                realSourceManager.removeSource(sourceId)
                loadSources()
                Toast.makeText(this, "Source removed", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun testSource(source: RealSourceManager.VerifiedSource) {
        val progress = ProgressDialog.show(
            this,
            "🧪 Testing Source",
            "Testing ${source.config.name}...",
            true
        )
        
        uiScope.launch(Dispatchers.IO) {
            val torProxyManager = TorProxyManager(this@RealSourcesActivity)
            val verificationService = com.zim.jackettprowler.services.SiteVerificationService(
                this@RealSourcesActivity,
                torProxyManager
            )
            val result = verificationService.verifySite(source.config)
            
            launch(Dispatchers.Main) {
                progress.dismiss()
                
                val message = buildString {
                    if (result.isWorking) {
                        appendLine("✅ Source is WORKING!")
                        appendLine()
                        appendLine("Results found: ${result.resultCount}")
                        appendLine("Has magnets: ${if (result.hasMagnets) "Yes ✓" else "No"}")
                        appendLine("Has seeders: ${if (result.hasSeeders) "Yes ✓" else "No"}")
                        appendLine("Response time: ${result.responseTimeMs}ms")
                        appendLine()
                        appendLine("Sample titles:")
                        result.sampleTitles.take(3).forEach { 
                            appendLine("• ${it.take(50)}...")
                        }
                    } else {
                        appendLine("❌ Source NOT working")
                        appendLine()
                        appendLine("Error: ${result.errorMessage}")
                    }
                }
                
                AlertDialog.Builder(this@RealSourcesActivity)
                    .setTitle(if (result.isWorking) "✅ Test Passed" else "❌ Test Failed")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    private fun reverifyAllSources() {
        val sources = realSourceManager.getAllSources()
        if (sources.isEmpty()) {
            Toast.makeText(this, "No sources to verify", Toast.LENGTH_SHORT).show()
            return
        }
        
        val progress = ProgressDialog(this).apply {
            setTitle("🔄 Re-verifying All Sources")
            setMessage("Testing sources...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = sources.size
            show()
        }
        
        uiScope.launch(Dispatchers.IO) {
            val result = realSourceManager.reverifyAllSources { current, total, verifyResult ->
                launch(Dispatchers.Main) {
                    progress.progress = current
                    progress.setMessage("Testing ${verifyResult.siteName}...")
                }
            }
            
            launch(Dispatchers.Main) {
                progress.dismiss()
                
                AlertDialog.Builder(this@RealSourcesActivity)
                    .setTitle("✅ Re-verification Complete")
                    .setMessage(buildString {
                        appendLine("Total sources: ${result.total}")
                        appendLine("Working: ${result.working} ✓")
                        appendLine("Broken: ${result.broken} ✗")
                    })
                    .setPositiveButton("OK", null)
                    .show()
                
                loadSources()
            }
        }
    }
    
    private fun initializeSources() {
        AlertDialog.Builder(this)
            .setTitle("Initialize Default Sources?")
            .setMessage("This will add and verify the following default sources:\n\n• 1337x\n• Nyaa\n• EZTV\n• TorrentGalaxy\n• BT4G\n\nOnly working sources will be added.")
            .setPositiveButton("Initialize") { _, _ ->
                doInitialize()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun doInitialize() {
        val progress = ProgressDialog.show(
            this,
            "🚀 Initializing",
            "Adding and verifying default sources...",
            true
        )
        
        uiScope.launch(Dispatchers.IO) {
            val count = realSourceManager.initializeDefaultSources()
            
            launch(Dispatchers.Main) {
                progress.dismiss()
                Toast.makeText(
                    this@RealSourcesActivity,
                    "✓ Initialized $count verified sources",
                    Toast.LENGTH_LONG
                ).show()
                loadSources()
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
    
    // RecyclerView Adapter
    class RealSourceAdapter(
        private val onToggle: (String, Boolean) -> Unit,
        private val onDelete: (String) -> Unit,
        private val onTest: (RealSourceManager.VerifiedSource) -> Unit
    ) : RecyclerView.Adapter<RealSourceAdapter.ViewHolder>() {
        
        private var sources: List<RealSourceManager.VerifiedSource> = emptyList()
        
        fun updateSources(newSources: List<RealSourceManager.VerifiedSource>) {
            sources = newSources
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_real_source, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(sources[position])
        }
        
        override fun getItemCount() = sources.size
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val textName: android.widget.TextView = view.findViewById(R.id.textSourceName)
            private val textUrl: android.widget.TextView = view.findViewById(R.id.textSourceUrl)
            private val textStatus: android.widget.TextView = view.findViewById(R.id.textSourceStatus)
            private val switchEnabled: android.widget.Switch = view.findViewById(R.id.switchEnabled)
            private val buttonTest: android.widget.Button = view.findViewById(R.id.buttonTest)
            private val buttonDelete: android.widget.ImageButton = view.findViewById(R.id.buttonDelete)
            
            fun bind(source: RealSourceManager.VerifiedSource) {
                val config = source.config
                
                textName.text = config.name
                textUrl.text = config.baseUrl
                
                // Status based on verification
                val statusIcon = when {
                    source.failureCount >= 3 -> "❌"
                    source.failureCount > 0 -> "⚠️"
                    source.hasMagnets && source.hasSeeders -> "✅"
                    source.hasMagnets -> "✓"
                    else -> "◯"
                }
                
                textStatus.text = buildString {
                    append(statusIcon)
                    append(" ${source.lastResultCount} results")
                    if (source.isAutoConfigured) append(" | Auto-configured")
                    if (source.failureCount > 0) append(" | ${source.failureCount} failures")
                }
                
                switchEnabled.isChecked = config.enabled
                switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                    onToggle(config.id, isChecked)
                }
                
                buttonTest.setOnClickListener {
                    onTest(source)
                }
                
                buttonDelete.setOnClickListener {
                    onDelete(config.id)
                }
            }
        }
    }
}
