package com.zim.jackettprowler

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.zim.jackettprowler.providers.ProviderRegistry
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParserFactory

class IndexerManagementActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)

    private lateinit var jackettIndexersLayout: LinearLayout
    private lateinit var prowlarrIndexersLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var buttonMassImportJackett: Button
    private lateinit var buttonMassImportProwlarr: Button
    private lateinit var buttonLoadBuiltInProviders: Button

    private val JACKETT_BASE_URL = "http://192.168.1.175:9117"
    private val JACKETT_API_KEY = "sfbizvj42r5h41a2aojb2t29zouqgd3s"
    private val PROWLARR_BASE_URL = "http://192.168.1.175:9696"
    private val PROWLARR_API_KEY = "11e5676f4c3444479cea3671a6c0c55b"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_indexer_management)
        title = "Manage Indexers"

        jackettIndexersLayout = findViewById(R.id.jackettIndexersLayout)
        prowlarrIndexersLayout = findViewById(R.id.prowlarrIndexersLayout)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        buttonMassImportJackett = findViewById(R.id.buttonMassImportJackett)
        buttonMassImportProwlarr = findViewById(R.id.buttonMassImportProwlarr)
        buttonLoadBuiltInProviders = findViewById(R.id.buttonLoadBuiltInProviders)

        buttonMassImportJackett.setOnClickListener {
            massImportIndexers("jackett")
        }

        buttonMassImportProwlarr.setOnClickListener {
            massImportIndexers("prowlarr")
        }

        buttonLoadBuiltInProviders.setOnClickListener {
            loadBuiltInProviders()
        }

        loadIndexers()
    }
    
    private fun loadBuiltInProviders() {
        AlertDialog.Builder(this)
            .setTitle("Load Built-in Providers")
            .setMessage("This will load 65+ built-in torrent providers including adult content sites. These work independently without Jackett/Prowlarr. Continue?")
            .setPositiveButton("Load All") { _, _ ->
                uiScope.launch {
                    try {
                        statusText.text = "Loading built-in providers..."
                        progressBar.visibility = View.VISIBLE
                        
                        val stats = ProviderRegistry.getStats()
                        val configs = ProviderRegistry.getAllConfigs()
                        
                        // Save all providers to CustomSiteManager
                        val customSiteManager = CustomSiteManager(this@IndexerManagementActivity)
                        val existing = customSiteManager.getSites()
                        val existingIds = existing.map { it.id }.toSet()
                        
                        // Add only new providers
                        val newProviders = configs.filter { it.id !in existingIds }
                        val allSites = existing + newProviders
                        customSiteManager.saveSites(allSites)
                        
                        // Enable built-in providers in preferences
                        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
                        prefs.edit().apply {
                            configs.forEach { config ->
                                putBoolean("indexer_${config.id}_enabled", config.enabled)
                            }
                            apply()
                        }
                        
                        progressBar.visibility = View.GONE
                        statusText.text = "Loaded ${stats.total} providers (${stats.public} public, ${stats.adult} adult, ${stats.international} international)"
                        
                        Toast.makeText(
                            this@IndexerManagementActivity,
                            "✅ Loaded ${newProviders.size} new providers!\nTotal: ${stats.total}\nPublic: ${stats.public}\nAdult: ${stats.adult}\nPrivate: ${stats.private}\nInternational: ${stats.international}",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Reload to show new indexers
                        loadIndexers()
                    } catch (e: Exception) {
                        progressBar.visibility = View.GONE
                        statusText.text = "Error loading providers: ${e.message}"
                        Toast.makeText(this@IndexerManagementActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun massImportIndexers(source: String) {
        progressBar.visibility = View.VISIBLE
        statusText.text = "Mass importing $source indexers..."
        
        uiScope.launch(Dispatchers.IO) {
            try {
                val baseUrl = if (source == "jackett") JACKETT_BASE_URL else PROWLARR_BASE_URL
                val apiKey = if (source == "jackett") JACKETT_API_KEY else PROWLARR_API_KEY
                
                val indexers = fetchIndexers(baseUrl, apiKey, source)
                
                launch(Dispatchers.Main) {
                    if (indexers.isNotEmpty()) {
                        // Enable all fetched indexers
                        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
                        prefs.edit().apply {
                            indexers.forEach { indexer ->
                                putBoolean("indexer_${indexer.id}_enabled", true)
                            }
                            apply()
                        }
                        
                        progressBar.visibility = View.GONE
                        statusText.text = "Mass imported ${indexers.size} indexers from $source"
                        Toast.makeText(
                            this@IndexerManagementActivity,
                            "✅ Imported ${indexers.size} indexers from $source",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Reload to show enabled state
                        loadIndexers()
                    } else {
                        progressBar.visibility = View.GONE
                        statusText.text = "No indexers found in $source"
                        Toast.makeText(this@IndexerManagementActivity, "No indexers found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    statusText.text = "Error importing: ${e.message}"
                    Toast.makeText(this@IndexerManagementActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadIndexers() {
        progressBar.visibility = View.VISIBLE
        statusText.text = "Loading indexers..."

        uiScope.launch(Dispatchers.IO) {
            try {
                val jackettIndexers = fetchIndexers(JACKETT_BASE_URL, JACKETT_API_KEY, "jackett")
                val prowlarrIndexers = fetchIndexers(PROWLARR_BASE_URL, PROWLARR_API_KEY, "prowlarr")

                launch(Dispatchers.Main) {
                    displayIndexers(jackettIndexers, jackettIndexersLayout, "jackett")
                    displayIndexers(prowlarrIndexers, prowlarrIndexersLayout, "prowlarr")
                    progressBar.visibility = View.GONE
                    statusText.text = "Found ${jackettIndexers.size} Jackett + ${prowlarrIndexers.size} Prowlarr indexers"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    statusText.text = "Error loading indexers: ${e.message}"
                }
            }
        }
    }

    private fun fetchIndexers(baseUrl: String, apiKey: String, source: String): List<IndexerInfo> {
        val indexers = mutableListOf<IndexerInfo>()
        
        try {
            if (source == "prowlarr") {
                // Prowlarr uses different API - fetch indexer list
                val url = "$baseUrl/api/v1/indexer?apikey=$apiKey"
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = response.body?.string() ?: "[]"
                        // Parse Prowlarr JSON response
                        parseProwlarrIndexers(json, indexers)
                    }
                }
            } else {
                // Jackett - fetch indexers via API
                val url = "$baseUrl/api/v2.0/indexers?configured=true&apikey=$apiKey"
                
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = response.body?.string() ?: "[]"
                        parseJackettIndexers(json, indexers)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Return empty list on error - will be handled by caller
        }

        return indexers
    }
    
    private fun parseProwlarrIndexers(json: String, indexers: MutableList<IndexerInfo>) {
        try {
            // Simple JSON parsing without external library
            val items = json.trim().removePrefix("[").removeSuffix("]").split("},")
            for (item in items) {
                val cleaned = item.trim().removePrefix("{").removeSuffix("}")
                val idMatch = Regex("\"id\":(\\d+)").find(cleaned)
                val nameMatch = Regex("\"name\":\"([^\"]+)\"").find(cleaned)
                val enableMatch = Regex("\"enable\":(true|false)").find(cleaned)
                
                if (idMatch != null && nameMatch != null) {
                    val id = idMatch.groupValues[1]
                    val name = nameMatch.groupValues[1]
                    val enabled = enableMatch?.groupValues?.get(1) == "true"
                    
                    indexers.add(IndexerInfo(
                        id = "prowlarr-$id",
                        name = name,
                        categories = listOf("Prowlarr Indexer"),
                        source = "prowlarr"
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun parseJackettIndexers(json: String, indexers: MutableList<IndexerInfo>) {
        try {
            // Simple JSON parsing
            val items = json.trim().removePrefix("[").removeSuffix("]").split("},")
            for (item in items) {
                val cleaned = item.trim().removePrefix("{").removeSuffix("}")
                val idMatch = Regex("\"id\":\"([^\"]+)\"").find(cleaned)
                val nameMatch = Regex("\"name\":\"([^\"]+)\"").find(cleaned)
                
                if (idMatch != null && nameMatch != null) {
                    val id = idMatch.groupValues[1]
                    val name = nameMatch.groupValues[1]
                    
                    indexers.add(IndexerInfo(
                        id = "jackett-$id",
                        name = name,
                        categories = listOf("Jackett Indexer"),
                        source = "jackett"
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun displayIndexers(indexers: List<IndexerInfo>, layout: LinearLayout, source: String) {
        layout.removeAllViews()
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)

        for (indexer in indexers) {
            val checkBox = CheckBox(this)
            checkBox.text = "${indexer.name} (${indexer.categories.size} categories)"
            checkBox.textSize = 14f
            checkBox.setPadding(16, 16, 16, 16)

            val isEnabled = prefs.getBoolean("indexer_${indexer.id}_enabled", true)
            checkBox.isChecked = isEnabled

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("indexer_${indexer.id}_enabled", isChecked).apply()
                statusText.text = if (isChecked) {
                    "✓ ${indexer.name} enabled"
                } else {
                    "✗ ${indexer.name} disabled"
                }
            }

            layout.addView(checkBox)
        }

        if (indexers.isEmpty()) {
            val textView = TextView(this)
            textView.text = "No indexers found for $source"
            textView.setPadding(16, 16, 16, 16)
            layout.addView(textView)
        }
    }

    data class IndexerInfo(
        val id: String,
        val name: String,
        val categories: List<String>,
        val source: String
    )

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
