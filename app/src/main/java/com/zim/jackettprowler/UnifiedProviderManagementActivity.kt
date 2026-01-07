package com.zim.jackettprowler

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zim.jackettprowler.databinding.ActivityUnifiedProviderManagementBinding
import com.zim.jackettprowler.providers.ProviderRegistry
import kotlinx.coroutines.*

/**
 * Unified provider management - shows ALL providers (built-in + imported) with toggles
 */
class UnifiedProviderManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUnifiedProviderManagementBinding
    private lateinit var adapter: ProviderAdapter
    private val providers = mutableListOf<ProviderItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUnifiedProviderManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        title = "All Providers"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        setupRecyclerView()
        loadProviders()
        setupButtons()
    }
    
    private fun setupRecyclerView() {
        adapter = ProviderAdapter(providers) { provider, enabled ->
            updateProviderState(provider, enabled)
        }
        binding.recyclerProviders.layoutManager = LinearLayoutManager(this)
        binding.recyclerProviders.adapter = adapter
    }
    
    private fun loadProviders() {
        providers.clear()
        
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val builtinPrefs = getSharedPreferences("builtin_providers", MODE_PRIVATE)
        val enabledBuiltinIds = builtinPrefs.getStringSet("enabled_providers", null)
        
        // Add section header for built-in providers
        providers.add(ProviderItem.Header("Built-in Providers (${ProviderRegistry.getAllProviders().size})"))
        
        // Add built-in providers
        ProviderRegistry.getAllProviders().forEach { provider ->
            val isEnabled = if (enabledBuiltinIds == null) {
                // Default: enable public, disable private
                !provider.category.contains("private")
            } else {
                enabledBuiltinIds.contains(provider.id)
            }
            
            providers.add(ProviderItem.Provider(
                id = provider.id,
                name = provider.name,
                description = provider.description,
                category = provider.category.joinToString(", "),
                source = "Built-in",
                isEnabled = isEnabled
            ))
        }
        
        // Add section header for imported indexers
        val indexerImporter = IndexerImporter(this)
        val importedIndexers = indexerImporter.getImportedIndexers()
        
        if (importedIndexers.isNotEmpty()) {
            providers.add(ProviderItem.Header("Imported from Jackett/Prowlarr (${importedIndexers.size})"))
            
            importedIndexers.forEach { indexer ->
                providers.add(ProviderItem.Provider(
                    id = indexer.id,
                    name = indexer.name,
                    description = indexer.description,
                    category = indexer.language,
                    source = indexer.source.capitalize(),
                    isEnabled = indexer.isEnabled
                ))
            }
        }
        
        adapter.notifyDataSetChanged()
        updateStats()
    }
    
    private fun updateProviderState(provider: ProviderItem.Provider, enabled: Boolean) {
        if (provider.source == "Built-in") {
            // Update built-in provider
            val prefs = getSharedPreferences("builtin_providers", MODE_PRIVATE)
            val currentEnabled = prefs.getStringSet("enabled_providers", null)?.toMutableSet() 
                ?: ProviderRegistry.getAllProviders()
                    .filter { !it.category.contains("private") }
                    .map { it.id }
                    .toMutableSet()
            
            if (enabled) {
                currentEnabled.add(provider.id)
            } else {
                currentEnabled.remove(provider.id)
            }
            
            prefs.edit().putStringSet("enabled_providers", currentEnabled).apply()
        } else {
            // Update imported indexer
            val indexerImporter = IndexerImporter(this)
            indexerImporter.updateIndexerState(provider.id, enabled)
        }
        
        updateStats()
    }
    
    private fun setupButtons() {
        binding.buttonEnableAll.setOnClickListener {
            enableAllProviders(true)
        }
        
        binding.buttonDisableAll.setOnClickListener {
            enableAllProviders(false)
        }
        
        binding.buttonEnablePublic.setOnClickListener {
            enablePublicOnly()
        }
        
        binding.buttonTestProviders.setOnClickListener {
            testAllProviders()
        }
    }
    
    private fun enableAllProviders(enable: Boolean) {
        // Update UI
        providers.filterIsInstance<ProviderItem.Provider>().forEach { provider ->
            provider.isEnabled = enable
            updateProviderState(provider, enable)
        }
        adapter.notifyDataSetChanged()
        
        val message = if (enable) "All providers enabled" else "All providers disabled"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun enablePublicOnly() {
        providers.filterIsInstance<ProviderItem.Provider>().forEach { provider ->
            val isPublic = !provider.category.contains("private", ignoreCase = true)
            provider.isEnabled = isPublic
            updateProviderState(provider, isPublic)
        }
        adapter.notifyDataSetChanged()
        Toast.makeText(this, "Enabled public providers only", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateStats() {
        val allProviders = providers.filterIsInstance<ProviderItem.Provider>()
        val enabled = allProviders.count { it.isEnabled }
        val total = allProviders.size
        
        binding.textStats.text = "$enabled of $total providers enabled"
    }
    
    private fun testAllProviders() {
        val dialog = android.app.ProgressDialog(this)
        dialog.setTitle("Testing Providers")
        dialog.setMessage("Testing 0/0...")
        dialog.setCancelable(false)
        dialog.show()
        
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Main + job)
        
        scope.launch {
            try {
                val tester = ProviderBatchTester(this@UnifiedProviderManagementActivity)
                
                // Test built-in providers
                val builtinResults = tester.testAllBuiltInProviders(
                    testQuery = "ubuntu"
                ) { current, total, name ->
                    launch(Dispatchers.Main) {
                        dialog.setMessage("Testing built-in: $current/$total\n$name")
                    }
                }
                
                // Test imported indexers
                val importedResults = tester.testImportedIndexers(
                    testQuery = "ubuntu"
                ) { current, total, name ->
                    launch(Dispatchers.Main) {
                        dialog.setMessage("Testing imported: $current/$total\n$name")
                    }
                }
                
                dialog.dismiss()
                
                // Show results
                val combinedMessage = buildString {
                    appendLine("=== Built-in Providers ===")
                    appendLine("Total: ${builtinResults.total}")
                    appendLine("✓ Success: ${builtinResults.success}")
                    appendLine("✗ Failed: ${builtinResults.failed}")
                    appendLine("Avg Response: ${builtinResults.avgResponseTime}ms")
                    appendLine()
                    appendLine("=== Imported Indexers ===")
                    appendLine("Total: ${importedResults.total}")
                    appendLine("✓ Success: ${importedResults.success}")
                    appendLine("✗ Failed: ${importedResults.failed}")
                    appendLine("Avg Response: ${importedResults.avgResponseTime}ms")
                }
                
                AlertDialog.Builder(this@UnifiedProviderManagementActivity)
                    .setTitle("Provider Test Results")
                    .setMessage(combinedMessage)
                    .setPositiveButton("View Details") { _, _ ->
                        showDetailedResults(builtinResults, importedResults)
                    }
                    .setNegativeButton("OK", null)
                    .show()
                
            } catch (e: Exception) {
                dialog.dismiss()
                Toast.makeText(
                    this@UnifiedProviderManagementActivity,
                    "Test failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun showDetailedResults(
        builtinResults: ProviderBatchTester.BatchTestResults,
        importedResults: ProviderBatchTester.BatchTestResults
    ) {
        val tester = ProviderBatchTester(this)
        val message = buildString {
            appendLine(tester.formatResults(builtinResults))
            appendLine()
            appendLine("=========================")
            appendLine()
            appendLine(tester.formatResults(importedResults))
        }
        
        AlertDialog.Builder(this)
            .setTitle("Detailed Test Results")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    // Data classes
    sealed class ProviderItem {
        data class Header(val title: String) : ProviderItem()
        data class Provider(
            val id: String,
            val name: String,
            val description: String,
            val category: String,
            val source: String,
            var isEnabled: Boolean
        ) : ProviderItem()
    }
    
    // Adapter
    class ProviderAdapter(
        private val items: List<ProviderItem>,
        private val onToggle: (ProviderItem.Provider, Boolean) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        
        companion object {
            const val TYPE_HEADER = 0
            const val TYPE_PROVIDER = 1
        }
        
        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is ProviderItem.Header -> TYPE_HEADER
                is ProviderItem.Provider -> TYPE_PROVIDER
            }
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                TYPE_HEADER -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_provider_header, parent, false)
                    HeaderViewHolder(view)
                }
                else -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_provider, parent, false)
                    ProviderViewHolder(view)
                }
            }
        }
        
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is ProviderItem.Header -> {
                    (holder as HeaderViewHolder).bind(item)
                }
                is ProviderItem.Provider -> {
                    (holder as ProviderViewHolder).bind(item, onToggle)
                }
            }
        }
        
        override fun getItemCount() = items.size
        
        class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val textTitle: TextView = view.findViewById(R.id.text_header)
            
            fun bind(header: ProviderItem.Header) {
                textTitle.text = header.title
            }
        }
        
        class ProviderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val textName: TextView = view.findViewById(R.id.text_provider_name)
            private val textDescription: TextView = view.findViewById(R.id.text_provider_description)
            private val textCategory: TextView = view.findViewById(R.id.text_provider_category)
            private val textSource: TextView = view.findViewById(R.id.text_provider_source)
            private val switchEnabled: SwitchCompat = view.findViewById(R.id.switch_provider_enabled)
            
            fun bind(provider: ProviderItem.Provider, onToggle: (ProviderItem.Provider, Boolean) -> Unit) {
                textName.text = provider.name
                textDescription.text = provider.description
                textCategory.text = provider.category
                textSource.text = provider.source
                switchEnabled.isChecked = provider.isEnabled
                
                switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                    provider.isEnabled = isChecked
                    onToggle(provider, isChecked)
                }
            }
        }
    }
}
