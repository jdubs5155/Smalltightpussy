/*
// DISABLED: This advanced custom sites editor is replaced by CustomURLsActivity
// Commenting out to avoid build errors with missing layouts
package com.zim.jackettprowler

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class CustomSitesActivity : AppCompatActivity() {
    private lateinit var customSiteManager: CustomSiteManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CustomSiteAdapter
    private lateinit var addButton: Button
    private lateinit var syncGitHubButton: Button
    private lateinit var importButton: Button
    private lateinit var exportButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_sites)
        
        customSiteManager = CustomSiteManager(this)
        
        setupViews()
        loadSites()
    }
    
    private fun setupViews() {
        recyclerView = findViewById(R.id.customSitesRecyclerView)
        addButton = findViewById(R.id.addSiteButton)
        syncGitHubButton = findViewById(R.id.syncGitHubButton)
        importButton = findViewById(R.id.importButton)
        exportButton = findViewById(R.id.exportButton)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CustomSiteAdapter(
            sites = mutableListOf(),
            onEdit = { site -> showEditDialog(site) },
            onDelete = { site -> deleteSite(site) },
            onToggle = { site, enabled -> toggleSite(site, enabled) },
            onTest = { site -> testSite(site) }
        )
        recyclerView.adapter = adapter
        
        addButton.setOnClickListener { showAddDialog() }
        syncGitHubButton.setOnClickListener { syncFromGitHub() }
        importButton.setOnClickListener { showImportDialog() }
        exportButton.setOnClickListener { exportSites() }
    }
    
    private fun loadSites() {
        val sites = customSiteManager.getSites()
        adapter.updateSites(sites)
    }
    
    private fun showAddDialog() {
        val dialog = SiteEditorDialog(
            context = this,
            site = null,
            onSave = { site ->
                customSiteManager.addSite(site)
                loadSites()
            }
        )
        dialog.show()
    }
    
    private fun showEditDialog(site: CustomSiteConfig) {
        val dialog = SiteEditorDialog(
            context = this,
            site = site,
            onSave = { updatedSite ->
                customSiteManager.updateSite(site.id, updatedSite)
                loadSites()
            }
        )
        dialog.show()
    }
    
    private fun deleteSite(site: CustomSiteConfig) {
        AlertDialog.Builder(this)
            .setTitle("Delete Site")
            .setMessage("Are you sure you want to delete ${site.name}?")
            .setPositiveButton("Delete") { _, _ ->
                customSiteManager.removeSite(site.id)
                loadSites()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun toggleSite(site: CustomSiteConfig, enabled: Boolean) {
        val updatedSite = site.copy(enabled = enabled)
        customSiteManager.updateSite(site.id, updatedSite)
        loadSites()
    }
    
    private fun testSite(site: CustomSiteConfig) {
        Toast.makeText(this, "Testing ${site.name}...", Toast.LENGTH_SHORT).show()
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val torProxy = if (site.requiresTor) TorProxyManager(this@CustomSitesActivity) else null
                val scraper = ScraperService(torProxy)
                
                val results = withContext(Dispatchers.IO) {
                    scraper.search(site, "test", limit = 5)
                }
                
                AlertDialog.Builder(this@CustomSitesActivity)
                    .setTitle("Test Results")
                    .setMessage("Found ${results.size} results\n\n${results.take(3).joinToString("\n") { it.title }}")
                    .setPositiveButton("OK", null)
                    .show()
            } catch (e: Exception) {
                AlertDialog.Builder(this@CustomSitesActivity)
                    .setTitle("Test Failed")
                    .setMessage("Error: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    private fun syncFromGitHub() {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Syncing from GitHub")
            .setMessage("Downloading scraper configurations...")
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val sync = GitHubScraperSync(customSiteManager)
                val result = withContext(Dispatchers.IO) {
                    sync.syncAllRepositories()
                }
                
                progressDialog.dismiss()
                
                AlertDialog.Builder(this@CustomSitesActivity)
                    .setTitle("Sync Complete")
                    .setMessage(result.getMessage())
                    .setPositiveButton("OK") { _, _ -> loadSites() }
                    .show()
            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(this@CustomSitesActivity, "Sync failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showImportDialog() {
        val input = EditText(this)
        input.hint = "Paste JSON configuration or GitHub URL"
        input.minLines = 5
        
        AlertDialog.Builder(this)
            .setTitle("Import Scrapers")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val text = input.text.toString()
                if (text.contains("github.com")) {
                    importFromGitHubUrl(text)
                } else {
                    importFromJson(text)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun importFromGitHubUrl(url: String) {
        // Parse GitHub URL: https://github.com/owner/repo/...
        val parts = url.split("/")
        if (parts.size < 5) {
            Toast.makeText(this, "Invalid GitHub URL", Toast.LENGTH_SHORT).show()
            return
        }
        
        val owner = parts[3]
        val repo = parts[4]
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val sync = GitHubScraperSync(customSiteManager)
                val result = withContext(Dispatchers.IO) {
                    sync.importFromCustomRepo(owner, repo)
                }
                
                Toast.makeText(this@CustomSitesActivity, result.getMessage(), Toast.LENGTH_LONG).show()
                loadSites()
            } catch (e: Exception) {
                Toast.makeText(this@CustomSitesActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun importFromJson(json: String) {
        try {
            val sync = GitHubScraperSync(customSiteManager)
            val result = sync.importScrapersFromJson(json)
            
            Toast.makeText(this, result.getMessage(), Toast.LENGTH_LONG).show()
            loadSites()
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun exportSites() {
        val sync = GitHubScraperSync(customSiteManager)
        val json = sync.exportScrapersToJson()
        
        // Show JSON in dialog for copying
        val input = EditText(this)
        input.setText(json)
        input.setSelection(0, json.length)
        input.minLines = 10
        
        AlertDialog.Builder(this)
            .setTitle("Export Scrapers")
            .setMessage("Copy this JSON to share your scraper configurations")
            .setView(input)
            .setPositiveButton("Close", null)
            .show()
    }
}

/**
 * RecyclerView adapter for custom sites
 */
class CustomSiteAdapter(
    private var sites: MutableList<CustomSiteConfig>,
    private val onEdit: (CustomSiteConfig) -> Unit,
    private val onDelete: (CustomSiteConfig) -> Unit,
    private val onToggle: (CustomSiteConfig, Boolean) -> Unit,
    private val onTest: (CustomSiteConfig) -> Unit
) : RecyclerView.Adapter<CustomSiteAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.siteNameText)
        val urlText: TextView = view.findViewById(R.id.siteUrlText)
        val statusText: TextView = view.findViewById(R.id.siteStatusText)
        val enabledSwitch: Switch = view.findViewById(R.id.siteEnabledSwitch)
        val editButton: Button = view.findViewById(R.id.editSiteButton)
        val deleteButton: Button = view.findViewById(R.id.deleteSiteButton)
        val testButton: Button = view.findViewById(R.id.testSiteButton)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_custom_site, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val site = sites[position]
        
        holder.nameText.text = site.name
        holder.urlText.text = site.baseUrl
        holder.statusText.text = when {
            site.isOnionSite || site.requiresTor -> "🧅 Onion Site"
            else -> "🌐 Clearnet"
        }
        
        holder.enabledSwitch.isChecked = site.enabled
        holder.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            onToggle(site, isChecked)
        }
        
        holder.editButton.setOnClickListener { onEdit(site) }
        holder.deleteButton.setOnClickListener { onDelete(site) }
        holder.testButton.setOnClickListener { onTest(site) }
    }
    
    override fun getItemCount() = sites.size
    
    fun updateSites(newSites: List<CustomSiteConfig>) {
        sites.clear()
        sites.addAll(newSites)
        notifyDataSetChanged()
    }
}
*/
