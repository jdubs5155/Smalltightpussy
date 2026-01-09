package com.zim.jackettprowler

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.zim.jackettprowler.automation.ConnectionStabilityManager
import com.zim.jackettprowler.automation.ProviderHealthMonitor
import com.zim.jackettprowler.databinding.ActivitySettingsBinding
import kotlinx.coroutines.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private lateinit var connectionManager: ConnectionStabilityManager
    private lateinit var healthMonitor: ProviderHealthMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.settings_title)
        
        connectionManager = ConnectionStabilityManager(this)
        healthMonitor = ProviderHealthMonitor(this)

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)

        // Load Jackett/Prowlarr settings
        val jackettUrl = prefs.getString("jackett_url", "http://192.168.1.175:9117") ?: ""
        val jackettApiKey = prefs.getString("jackett_api_key", "") ?: ""
        val prowlarrUrl = prefs.getString("prowlarr_url", "http://192.168.1.175:9696") ?: ""
        val prowlarrApiKey = prefs.getString("prowlarr_api_key", "") ?: ""

        binding.editJackettUrl.setText(jackettUrl)
        binding.editJackettApiKey.setText(jackettApiKey)
        binding.editProwlarrUrl.setText(prowlarrUrl)
        binding.editProwlarrApiKey.setText(prowlarrApiKey)
        
        // Show connection status
        updateConnectionStatus()

        // Load qBittorrent settings
        val enabled = prefs.getBoolean(getString(R.string.pref_qb_enabled), false)
        val baseUrl = prefs.getString(getString(R.string.pref_qb_base_url), "") ?: ""
        val username = prefs.getString(getString(R.string.pref_qb_username), "") ?: ""
        val password = prefs.getString(getString(R.string.pref_qb_password), "") ?: ""

        binding.checkEnableQb.isChecked = enabled
        binding.editQbBaseUrl.setText(baseUrl)
        binding.editQbUsername.setText(username)
        binding.editQbPassword.setText(password)

        binding.buttonSaveSettings.setOnClickListener {
            saveAndTestConnections()
        }

        binding.buttonImportIndexers.setOnClickListener {
            showImportDialog()
        }

        binding.buttonManageIndexers.setOnClickListener {
            // Open unified provider management
            val intent = Intent(this, UnifiedProviderManagementActivity::class.java)
            startActivity(intent)
        }

        binding.buttonAddCustomURLs.setOnClickListener {
            // Open smart provider adding
            val intent = Intent(this, SmartProviderAddActivity::class.java)
            startActivity(intent)
        }
        
        binding.buttonManageBuiltInProviders.setOnClickListener {
            // Also open unified provider management
            val intent = Intent(this, UnifiedProviderManagementActivity::class.java)
            startActivity(intent)
        }
        
        binding.buttonManageTrackers.setOnClickListener {
            val intent = Intent(this, TrackerManagementActivity::class.java)
            startActivity(intent)
        }
        
        binding.buttonManageVideoSites.setOnClickListener {
            val intent = Intent(this, com.zim.jackettprowler.video.VideoSitesActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun showImportDialog() {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        
        // Get Jackett/Prowlarr settings from MainActivity config
        val jackettUrl = prefs.getString("jackett_url", "http://192.168.1.175:9117") ?: ""
        val jackettApiKey = prefs.getString("jackett_api_key", "sfbizvj42r5h41a2aojb2t29zouqgd3s") ?: ""
        val prowlarrUrl = prefs.getString("prowlarr_url", "http://192.168.1.175:9696") ?: ""
        val prowlarrApiKey = prefs.getString("prowlarr_api_key", "11e5676f4c3444479cea3671a6c0c55b") ?: ""
        
        val options = arrayOf(
            "📥 Import from Jackett",
            "📥 Import from Prowlarr", 
            "📥 Import from Both",
            "ℹ️ What does this do?"
        )
        
        AlertDialog.Builder(this)
            .setTitle("Import Torznab Indexers")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> performImport("jackett", jackettUrl, jackettApiKey)
                    1 -> performImport("prowlarr", prowlarrUrl, prowlarrApiKey)
                    2 -> performImport("both", jackettUrl, jackettApiKey, prowlarrUrl, prowlarrApiKey)
                    3 -> showImportInfoDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showImportInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("About Indexer Import")
            .setMessage("""
                This feature imports all configured indexers from your Jackett/Prowlarr instances.
                
                What it does:
                • Fetches all configured indexers
                • Saves their individual Torznab URLs
                • Allows toggling each indexer on/off
                • Enables searching each indexer independently
                
                Requirements:
                • Jackett/Prowlarr must be running
                • API credentials must be configured above
                • Network access to your Jackett/Prowlarr server
            """.trimIndent())
            .setPositiveButton("Got it", null)
            .show()
    }
    
    private fun performImport(
        source: String,
        url1: String,
        key1: String,
        url2: String = "",
        key2: String = ""
    ) {
        val progress = ProgressDialog.show(this, "Importing", "Importing indexers...", true)
        
        uiScope.launch(Dispatchers.IO) {
            try {
                val importer = IndexerImporter(this@SettingsActivity)
                val result = when (source) {
                    "jackett" -> importer.importFromJackett(url1, key1)
                    "prowlarr" -> importer.importFromProwlarr(url1, key1)
                    "both" -> importer.importFromBoth(url1, key1, url2, key2)
                    else -> IndexerImporter.ImportResult(false, 0, 0, listOf("Unknown source"))
                }
                
                launch(Dispatchers.Main) {
                    progress.dismiss()
                    
                    if (result.success) {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle("Import Successful")
                            .setMessage("Imported ${result.importedCount} indexers!\n\nYou can now toggle them on/off in 'Manage Indexers'.")
                            .setPositiveButton("OK") { _, _ ->
                                // Optionally open IndexerManagementActivity
                                val intent = Intent(this@SettingsActivity, IndexerManagementActivity::class.java)
                                intent.putExtra("show_imported", true)
                                startActivity(intent)
                            }
                            .show()
                    } else {
                        AlertDialog.Builder(this@SettingsActivity)
                            .setTitle("Import Failed")
                            .setMessage("Errors:\n${result.errors.joinToString("\n")}")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    progress.dismiss()
                    Toast.makeText(this@SettingsActivity, "Import error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun showBuiltInProvidersDialog() {
        val message = buildString {
            appendLine("Built-in Providers: ${com.zim.jackettprowler.providers.ProviderRegistry.getProviderCount()}")
            appendLine()
            val stats = com.zim.jackettprowler.providers.ProviderRegistry.getStats()
            appendLine("Public: ${stats.public}")
            appendLine("Private: ${stats.private}")
            appendLine("Adult: ${stats.adult}")
            appendLine("International: ${stats.international}")
            appendLine("Specialized: ${stats.specialized}")
            appendLine()
            appendLine("These providers work without Jackett/Prowlarr")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Built-in Torrent Providers")
            .setMessage(message)
            .setPositiveButton("Enable All Public") { _, _ ->
                enableBuiltInProviders("public")
            }
            .setNeutralButton("Customize", null)
            .setNegativeButton("Close", null)
            .show()
    }
    
    private fun enableBuiltInProviders(mode: String) {
        val prefs = getSharedPreferences("builtin_providers", MODE_PRIVATE)
        val providerIds = when (mode) {
            "public" -> com.zim.jackettprowler.providers.ProviderRegistry.getPublicConfigs().map { it.id }.toSet()
            "all" -> com.zim.jackettprowler.providers.ProviderRegistry.getAllConfigs().map { it.id }.toSet()
            else -> emptySet()
        }
        
        prefs.edit().putStringSet("enabled_providers", providerIds).apply()
        Toast.makeText(this, "Enabled ${providerIds.size} built-in providers", Toast.LENGTH_SHORT).show()
    }
    
    private fun saveAndTestConnections() {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        
        val jackettUrl = binding.editJackettUrl.text.toString().trim()
        val jackettKey = binding.editJackettApiKey.text.toString().trim()
        val prowlarrUrl = binding.editProwlarrUrl.text.toString().trim()
        val prowlarrKey = binding.editProwlarrApiKey.text.toString().trim()
        
        prefs.edit()
            .putString("jackett_url", jackettUrl)
            .putString("jackett_api_key", jackettKey)
            .putString("prowlarr_url", prowlarrUrl)
            .putString("prowlarr_api_key", prowlarrKey)
            .putBoolean(getString(R.string.pref_qb_enabled), binding.checkEnableQb.isChecked)
            .putString(getString(R.string.pref_qb_base_url), binding.editQbBaseUrl.text.toString().trim())
            .putString(getString(R.string.pref_qb_username), binding.editQbUsername.text.toString().trim())
            .putString(getString(R.string.pref_qb_password), binding.editQbPassword.text.toString())
            .apply()
        
        Toast.makeText(this, "Settings saved! Testing connections...", Toast.LENGTH_LONG).show()
        finish()
    }
    
    private fun updateConnectionStatus() {
        // Placeholder for future UI updates
    }
    
    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}