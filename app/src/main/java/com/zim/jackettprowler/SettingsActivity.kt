package com.zim.jackettprowler

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.zim.jackettprowler.databinding.ActivitySettingsBinding
import kotlinx.coroutines.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.settings_title)

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
            prefs.edit()
                // Save Jackett/Prowlarr settings
                .putString("jackett_url", binding.editJackettUrl.text.toString().trim())
                .putString("jackett_api_key", binding.editJackettApiKey.text.toString().trim())
                .putString("prowlarr_url", binding.editProwlarrUrl.text.toString().trim())
                .putString("prowlarr_api_key", binding.editProwlarrApiKey.text.toString().trim())
                // Save qBittorrent settings
                .putBoolean(getString(R.string.pref_qb_enabled), binding.checkEnableQb.isChecked)
                .putString(
                    getString(R.string.pref_qb_base_url),
                    binding.editQbBaseUrl.text.toString().trim()
                )
                .putString(
                    getString(R.string.pref_qb_username),
                    binding.editQbUsername.text.toString().trim()
                )
                .putString(
                    getString(R.string.pref_qb_password),
                    binding.editQbPassword.text.toString()
                )
                .apply()
            
            Toast.makeText(this, "Settings saved! Restart search to apply changes.", Toast.LENGTH_LONG).show()
            finish()
        }

        binding.buttonImportIndexers.setOnClickListener {
            showImportDialog()
        }

        binding.buttonManageIndexers.setOnClickListener {
            val intent = Intent(this, IndexerManagementActivity::class.java)
            startActivity(intent)
        }

        binding.buttonAddCustomURLs.setOnClickListener {
            val intent = Intent(this, CustomURLsActivity::class.java)
            startActivity(intent)
        }
        
        binding.buttonManageBuiltInProviders.setOnClickListener {
            showBuiltInProvidersDialog()
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
            "Import from Jackett",
            "Import from Prowlarr", 
            "Import from Both"
        )
        
        AlertDialog.Builder(this)
            .setTitle("Import Indexers")
            .setMessage("This will import all configured indexers and their Torznab URLs")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> performImport("jackett", jackettUrl, jackettApiKey)
                    1 -> performImport("prowlarr", prowlarrUrl, prowlarrApiKey)
                    2 -> performImport("both", jackettUrl, jackettApiKey, prowlarrUrl, prowlarrApiKey)
                }
            }
            .setNegativeButton("Cancel", null)
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
    
    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}