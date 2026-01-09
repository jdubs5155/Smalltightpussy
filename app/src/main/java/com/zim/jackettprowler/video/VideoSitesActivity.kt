package com.zim.jackettprowler.video

import android.app.ProgressDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zim.jackettprowler.R
import com.zim.jackettprowler.databinding.ActivityVideoSitesBinding
import kotlinx.coroutines.*

/**
 * Activity for managing clearnet video sites
 */
class VideoSitesActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityVideoSitesBinding
    private lateinit var videoService: VideoSearchService
    private lateinit var adapter: VideoSiteAdapter
    
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoSitesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        title = "Clearnet Video Sites"
        
        videoService = VideoSearchService(this)
        
        setupRecyclerView()
        setupButtons()
        refreshSiteList()
    }
    
    private fun setupRecyclerView() {
        adapter = VideoSiteAdapter(
            onToggle = { site, enabled ->
                videoService.toggleSite(site.id, enabled)
            },
            onDelete = { site ->
                confirmDelete(site)
            },
            onTest = { site ->
                testSite(site)
            }
        )
        
        binding.recyclerViewSites.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSites.adapter = adapter
    }
    
    private fun setupButtons() {
        binding.buttonAddSite.setOnClickListener {
            showAddSiteDialog()
        }
        
        binding.buttonAddPreset.setOnClickListener {
            showPresetSitesDialog()
        }
        
        binding.buttonTestAll.setOnClickListener {
            testAllSites()
        }
    }
    
    private fun refreshSiteList() {
        val sites = videoService.getAllSites()
        adapter.updateData(sites)
        
        binding.textSiteCount.text = "${sites.size} video sites configured (${sites.count { it.isEnabled }} enabled)"
        
        if (sites.isEmpty()) {
            binding.textEmptyMessage.visibility = View.VISIBLE
            binding.recyclerViewSites.visibility = View.GONE
        } else {
            binding.textEmptyMessage.visibility = View.GONE
            binding.recyclerViewSites.visibility = View.VISIBLE
        }
    }
    
    private fun showAddSiteDialog() {
        val editText = EditText(this).apply {
            hint = "Paste video site URL (e.g., https://youtube.com)"
            setPadding(48, 32, 48, 32)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Add Video Site")
            .setMessage("Paste a URL from any video site. The app will automatically detect and configure it.\n\nSupported: YouTube, Dailymotion, Vimeo, Rumble, Odysee, BitChute, PeerTube instances, Archive.org, and more!")
            .setView(editText)
            .setPositiveButton("Add") { _, _ ->
                val url = editText.text.toString().trim()
                if (url.isNotEmpty()) {
                    addSite(url)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun addSite(url: String) {
        val progress = ProgressDialog.show(this, "Analyzing", "Detecting site configuration...", true)
        
        uiScope.launch(Dispatchers.IO) {
            val result = videoService.addSite(url)
            
            launch(Dispatchers.Main) {
                progress.dismiss()
                
                if (result.success && result.config != null) {
                    Toast.makeText(
                        this@VideoSitesActivity,
                        "Added: ${result.config.name} (${result.config.siteType})",
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshSiteList()
                } else {
                    Toast.makeText(
                        this@VideoSitesActivity,
                        "Failed: ${result.error ?: "Unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun showPresetSitesDialog() {
        val presets = arrayOf(
            "YouTube (via Invidious)",
            "Dailymotion",
            "Vimeo",
            "Rumble",
            "Odysee",
            "BitChute",
            "Internet Archive",
            "Add All Public Sites"
        )
        
        AlertDialog.Builder(this)
            .setTitle("Add Preset Video Sites")
            .setItems(presets) { _, which ->
                when (which) {
                    0 -> addPresetSite(VideoSiteType.YOUTUBE)
                    1 -> addPresetSite(VideoSiteType.DAILYMOTION)
                    2 -> addPresetSite(VideoSiteType.VIMEO)
                    3 -> addPresetSite(VideoSiteType.RUMBLE)
                    4 -> addPresetSite(VideoSiteType.ODYSEE)
                    5 -> addPresetSite(VideoSiteType.BITCHUTE)
                    6 -> addPresetSite(VideoSiteType.ARCHIVE_ORG)
                    7 -> addAllPresets()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun addPresetSite(type: VideoSiteType) {
        val config = when (type) {
            VideoSiteType.YOUTUBE -> VideoSiteConfig(
                id = "youtube",
                name = "YouTube",
                baseUrl = "https://www.youtube.com",
                siteType = VideoSiteType.YOUTUBE,
                searchPath = "/results?search_query={query}"
            )
            VideoSiteType.DAILYMOTION -> VideoSiteConfig(
                id = "dailymotion",
                name = "Dailymotion",
                baseUrl = "https://www.dailymotion.com",
                siteType = VideoSiteType.DAILYMOTION,
                apiEndpoint = "https://api.dailymotion.com/videos"
            )
            VideoSiteType.VIMEO -> VideoSiteConfig(
                id = "vimeo",
                name = "Vimeo",
                baseUrl = "https://vimeo.com",
                siteType = VideoSiteType.VIMEO,
                searchPath = "/search?q={query}"
            )
            VideoSiteType.RUMBLE -> VideoSiteConfig(
                id = "rumble",
                name = "Rumble",
                baseUrl = "https://rumble.com",
                siteType = VideoSiteType.RUMBLE,
                searchPath = "/search/video?q={query}"
            )
            VideoSiteType.ODYSEE -> VideoSiteConfig(
                id = "odysee",
                name = "Odysee",
                baseUrl = "https://odysee.com",
                siteType = VideoSiteType.ODYSEE,
                apiEndpoint = "https://lighthouse.odysee.com/search"
            )
            VideoSiteType.BITCHUTE -> VideoSiteConfig(
                id = "bitchute",
                name = "BitChute",
                baseUrl = "https://www.bitchute.com",
                siteType = VideoSiteType.BITCHUTE,
                searchPath = "/search/?query={query}&kind=video"
            )
            VideoSiteType.ARCHIVE_ORG -> VideoSiteConfig(
                id = "archive_org",
                name = "Internet Archive",
                baseUrl = "https://archive.org",
                siteType = VideoSiteType.ARCHIVE_ORG,
                apiEndpoint = "https://archive.org/advancedsearch.php"
            )
            else -> return
        }
        
        videoService.saveSite(config)
        refreshSiteList()
        Toast.makeText(this, "Added: ${config.name}", Toast.LENGTH_SHORT).show()
    }
    
    private fun addAllPresets() {
        listOf(
            VideoSiteType.YOUTUBE,
            VideoSiteType.DAILYMOTION,
            VideoSiteType.VIMEO,
            VideoSiteType.RUMBLE,
            VideoSiteType.ODYSEE,
            VideoSiteType.BITCHUTE,
            VideoSiteType.ARCHIVE_ORG
        ).forEach { addPresetSite(it) }
        
        Toast.makeText(this, "Added all preset video sites", Toast.LENGTH_SHORT).show()
    }
    
    private fun confirmDelete(site: VideoSiteConfig) {
        AlertDialog.Builder(this)
            .setTitle("Delete Site")
            .setMessage("Remove ${site.name}?")
            .setPositiveButton("Delete") { _, _ ->
                videoService.removeSite(site.id)
                refreshSiteList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun testSite(site: VideoSiteConfig) {
        val progress = ProgressDialog.show(this, "Testing", "Testing ${site.name}...", true)
        
        uiScope.launch(Dispatchers.IO) {
            try {
                val results = videoService.searchSite(site, "test", 5)
                
                launch(Dispatchers.Main) {
                    progress.dismiss()
                    
                    if (results.isNotEmpty()) {
                        Toast.makeText(
                            this@VideoSitesActivity,
                            "✓ ${site.name}: ${results.size} results",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@VideoSitesActivity,
                            "✗ ${site.name}: No results",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    progress.dismiss()
                    Toast.makeText(
                        this@VideoSitesActivity,
                        "✗ ${site.name}: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun testAllSites() {
        val sites = videoService.getAllSites()
        if (sites.isEmpty()) {
            Toast.makeText(this, "No sites to test", Toast.LENGTH_SHORT).show()
            return
        }
        
        val progress = ProgressDialog(this).apply {
            setTitle("Testing Sites")
            setMessage("Testing ${sites.size} sites...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = sites.size
            show()
        }
        
        uiScope.launch(Dispatchers.IO) {
            val results = mutableMapOf<String, Boolean>()
            
            sites.forEachIndexed { index, site ->
                launch(Dispatchers.Main) {
                    progress.progress = index + 1
                    progress.setMessage("Testing ${site.name}...")
                }
                
                try {
                    val searchResults = videoService.searchSite(site, "test", 3)
                    results[site.name] = searchResults.isNotEmpty()
                } catch (e: Exception) {
                    results[site.name] = false
                }
            }
            
            launch(Dispatchers.Main) {
                progress.dismiss()
                
                val successful = results.count { it.value }
                val message = buildString {
                    appendLine("Test Results: $successful/${sites.size} working")
                    appendLine()
                    results.forEach { (name, success) ->
                        appendLine("${if (success) "✓" else "✗"} $name")
                    }
                }
                
                AlertDialog.Builder(this@VideoSitesActivity)
                    .setTitle("Test Results")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
    
    // =================== Adapter ===================
    
    inner class VideoSiteAdapter(
        private val onToggle: (VideoSiteConfig, Boolean) -> Unit,
        private val onDelete: (VideoSiteConfig) -> Unit,
        private val onTest: (VideoSiteConfig) -> Unit
    ) : RecyclerView.Adapter<VideoSiteAdapter.ViewHolder>() {
        
        private var sites = listOf<VideoSiteConfig>()
        
        fun updateData(newSites: List<VideoSiteConfig>) {
            sites = newSites
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_video_site, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(sites[position])
        }
        
        override fun getItemCount() = sites.size
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textName = itemView.findViewById<android.widget.TextView>(R.id.textSiteName)
            private val textType = itemView.findViewById<android.widget.TextView>(R.id.textSiteType)
            private val textUrl = itemView.findViewById<android.widget.TextView>(R.id.textSiteUrl)
            private val switchEnabled = itemView.findViewById<android.widget.Switch>(R.id.switchEnabled)
            private val buttonTest = itemView.findViewById<android.widget.ImageButton>(R.id.buttonTest)
            private val buttonDelete = itemView.findViewById<android.widget.ImageButton>(R.id.buttonDelete)
            
            fun bind(site: VideoSiteConfig) {
                textName.text = site.name
                textType.text = site.siteType.name
                textUrl.text = site.baseUrl
                
                switchEnabled.setOnCheckedChangeListener(null)
                switchEnabled.isChecked = site.isEnabled
                switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                    onToggle(site, isChecked)
                }
                
                buttonTest.setOnClickListener { onTest(site) }
                buttonDelete.setOnClickListener { onDelete(site) }
            }
        }
    }
}
