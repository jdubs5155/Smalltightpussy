package com.zim.jackettprowler

import android.app.ProgressDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.zim.jackettprowler.automation.SiteConfigValidator
import com.zim.jackettprowler.automation.SiteInfiltrator
import com.zim.jackettprowler.automation.URLToConfigConverter
import com.zim.jackettprowler.databinding.ActivitySmartProviderAddBinding
import kotlinx.coroutines.*

/**
 * Smart provider adding - just paste a URL and the app figures out the rest!
 * Now with full site infiltration, deep parsing analysis, and validation!
 */
class SmartProviderAddActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySmartProviderAddBinding
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private lateinit var urlConverter: URLToConfigConverter
    private lateinit var infiltrator: SiteInfiltrator
    private var lastConversionResult: URLToConfigConverter.ConversionResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySmartProviderAddBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        title = "Add Custom Provider"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        urlConverter = URLToConfigConverter(this)
        infiltrator = SiteInfiltrator()
        
        setupListeners()
    }
    
    private fun setupListeners() {
        binding.buttonAnalyze.setOnClickListener {
            val url = binding.editUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            analyzeSite(url)
        }
        
        binding.buttonSave.setOnClickListener {
            if (lastConversionResult?.success == true && lastConversionResult?.config != null) {
                saveProvider(lastConversionResult!!.config!!)
            } else {
                Toast.makeText(this, "Please analyze the site first", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.buttonTestSearch.setOnClickListener {
            val testQuery = binding.editTestQuery.text.toString().trim()
            if (testQuery.isEmpty()) {
                Toast.makeText(this, "Please enter a test query", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            testSearch(testQuery)
        }
    }
    
    private fun analyzeSite(url: String) {
        val progressDialog = ProgressDialog.show(
            this,
            "🎯 Infiltrating Site",
            "Using advanced reconnaissance...\n• Detecting site structure\n• Finding APIs\n• Analyzing protection\n• Generating config",
            true,
            false
        )
        
        uiScope.launch(Dispatchers.IO) {
            try {
                // Use the powerful URL converter that does EVERYTHING
                val result = urlConverter.convertAndSave(
                    url = url,
                    autoSave = false, // Don't auto-save yet, let user review
                    testQuery = binding.editTestQuery.text.toString().trim().takeIf { it.isNotEmpty() } ?: "ubuntu"
                )
                lastConversionResult = result
                
                launch(Dispatchers.Main) {
                    progressDialog.dismiss()
                    displayConversionResult(result)
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@SmartProviderAddActivity,
                        "Infiltration failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun displayConversionResult(result: URLToConfigConverter.ConversionResult) {
        binding.textResults.text = buildString {
            appendLine("🔬 DEEP ANALYSIS RESULTS")
            appendLine("═".repeat(40))
            appendLine()
            
            if (result.success && result.config != null) {
                val config = result.config
                val infiltration = result.infiltrationData
                val deepAnalysis = result.deepAnalysis
                val details = result.detectionDetails
                
                appendLine("✅ Site successfully analyzed!")
                appendLine()
                appendLine("📍 SITE INFO:")
                appendLine("   Name: ${config.name}")
                appendLine("   Base URL: ${config.baseUrl}")
                appendLine("   Search URL: ${config.searchPath}")
                appendLine()
                
                // Show deep analysis results
                if (deepAnalysis != null) {
                    appendLine("📊 CONFIDENCE SCORE: ${(deepAnalysis.confidence * 100).toInt()}%")
                    appendLine()
                }
                
                // Show detection details
                if (details != null) {
                    appendLine("🔍 AUTO-DETECTED SELECTORS:")
                    appendLine("─".repeat(35))
                    appendLine("   Container: ${details.containerSelector}")
                    appendLine("   ├─ Title: ${details.titleSelector}")
                    appendLine("   │  (confidence: ${(details.titleConfidence * 100).toInt()}%)")
                    details.magnetSelector?.let { 
                        appendLine("   ├─ Magnet: $it ✓") 
                    } ?: appendLine("   ├─ Magnet: ⚠️ Not detected")
                    details.torrentSelector?.let { 
                        appendLine("   ├─ Torrent: $it ✓") 
                    }
                    details.seedersSelector?.let { 
                        appendLine("   ├─ Seeders: $it ✓") 
                    } ?: appendLine("   ├─ Seeders: ⚠️ Not detected")
                    details.leechersSelector?.let { 
                        appendLine("   ├─ Leechers: $it ✓") 
                    }
                    details.sizeSelector?.let { 
                        appendLine("   └─ Size: $it ✓") 
                    } ?: appendLine("   └─ Size: ⚠️ Not detected")
                    appendLine()
                }
                
                // Show protection status
                if (infiltration != null) {
                    appendLine("🛡️ PROTECTION STATUS:")
                    appendLine("─".repeat(35))
                    appendLine("   Cloudflare: ${if (infiltration.cloudflareProtected) "⚠️ DETECTED" else "✓ None"}")
                    appendLine("   reCAPTCHA: ${if (infiltration.reCaptchaPresent) "⚠️ DETECTED" else "✓ None"}")
                    appendLine("   Login Required: ${if (infiltration.authRequired) "⚠️ YES" else "✓ No"}")
                    appendLine()
                }
                
                // Show detected APIs
                if (details?.detectedAPIs?.isNotEmpty() == true) {
                    appendLine("🔗 DETECTED APIs:")
                    appendLine("─".repeat(35))
                    details.detectedAPIs.forEach { api ->
                        appendLine("   • $api")
                    }
                    appendLine()
                }
                
                // Show recommendations
                if (details?.recommendations?.isNotEmpty() == true) {
                    appendLine("💡 RECOMMENDATIONS:")
                    appendLine("─".repeat(35))
                    details.recommendations.forEach { rec ->
                        appendLine("   $rec")
                    }
                    appendLine()
                }
                
                appendLine("═".repeat(40))
                appendLine("✓ Configuration ready! Click SAVE to add.")
                
                binding.buttonSave.isEnabled = true
                binding.buttonTestSearch.isEnabled = true
            } else {
                appendLine("✗ Infiltration unsuccessful")
                appendLine()
                appendLine("Message: ${result.message}")
                appendLine()
                appendLine("The site structure could not be automatically detected.")
                appendLine("You may need to configure it manually or try a different URL.")
                
                binding.buttonSave.isEnabled = false
                binding.buttonTestSearch.isEnabled = false
            }
        }
    }
    
    private fun displayAnalysisResult(result: SmartSiteAnalyzer.SiteAnalysisResult) {
        binding.textResults.text = buildString {
            appendLine("📊 Analysis Results")
            appendLine()
            appendLine("Site Name: ${result.siteName}")
            appendLine("Base URL: ${result.baseUrl}")
            appendLine("Confidence: ${(result.confidence * 100).toInt()}%")
            appendLine()
            
            if (result.success) {
                appendLine("✓ Site structure detected successfully!")
                appendLine()
                appendLine("Detected Configuration:")
                result.detectedConfig?.let { config ->
                    appendLine("• Search path: ${config.searchPath}")
                    appendLine("• Title selector: ${config.selectors.title}")
                    appendLine("• Magnet selector: ${config.selectors.magnetUrl ?: "N/A"}")
                    appendLine("• Download selector: ${config.selectors.downloadUrl ?: "N/A"}")
                    appendLine("• Seeders selector: ${config.selectors.seeders ?: "N/A"}")
                    appendLine("• Leechers selector: ${config.selectors.leechers ?: "N/A"}")
                    appendLine("• Size selector: ${config.selectors.size ?: "N/A"}")
                }
                appendLine()
                appendLine("✓ Ready to save!")
                
                binding.buttonSave.isEnabled = true
                binding.buttonTestSearch.isEnabled = true
            } else {
                appendLine("✗ Could not detect site structure")
                appendLine()
                if (result.issues.isNotEmpty()) {
                    appendLine("Issues:")
                    result.issues.forEach { issue ->
                        appendLine("• $issue")
                    }
                }
                appendLine()
                appendLine("You may need to configure this site manually.")
                
                binding.buttonSave.isEnabled = false
                binding.buttonTestSearch.isEnabled = false
            }
        }
    }
    
    private fun testSearch(query: String) {
        val config = lastConversionResult?.config
        if (config == null) {
            Toast.makeText(this, "No configuration available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val progressDialog = ProgressDialog.show(
            this,
            "🧪 Validating Configuration",
            "Running comprehensive validation...\n• Testing search URL\n• Validating selectors\n• Performing test scrape",
            true,
            false
        )
        
        uiScope.launch(Dispatchers.IO) {
            try {
                // Use the new SiteConfigValidator for comprehensive testing
                val validator = SiteConfigValidator(this@SmartProviderAddActivity)
                val validationResult = validator.validate(config, query)
                
                launch(Dispatchers.Main) {
                    progressDialog.dismiss()
                    showValidationResults(validationResult)
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@SmartProviderAddActivity,
                        "Validation failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun showValidationResults(result: SiteConfigValidator.ValidationResult) {
        val validator = SiteConfigValidator(this)
        val message: CharSequence = validator.formatResult(result)
        
        AlertDialog.Builder(this)
            .setTitle(if (result.isValid) "✅ Validation Passed" else "⚠️ Validation Issues")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showSearchResults(results: List<TorrentResult>) {
        val message = buildString {
            appendLine("Test Search Results:")
            appendLine()
            appendLine("Found ${results.size} torrents")
            appendLine()
            if (results.isNotEmpty()) {
                appendLine("Sample results:")
                results.take(5).forEach { torrent ->
                    appendLine("• ${torrent.title}")
                    appendLine("  Seeds: ${torrent.seeders} | Leechers: ${torrent.leechers}")
                    appendLine("  Size: ${formatSize(torrent.sizeBytes)}")
                    appendLine()
                }
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Search Test Results")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun saveProvider(config: CustomSiteConfig) {
        val customSiteManager = CustomSiteManager(this)
        customSiteManager.addSite(config)
        
        Toast.makeText(this, "Provider \"${config.name}\" saved successfully!", Toast.LENGTH_LONG).show()
        
        // Show success dialog with option to add more or go back
        AlertDialog.Builder(this)
            .setTitle("Provider Added")
            .setMessage("\"${config.name}\" has been added to your custom providers.\n\nYou can now use it in searches!")
            .setPositiveButton("Add Another") { _, _ ->
                // Clear form
                binding.editUrl.text?.clear()
                binding.textResults.text = ""
                binding.buttonSave.isEnabled = false
                binding.buttonTestSearch.isEnabled = false
                lastConversionResult = null
            }
            .setNegativeButton("Done") { _, _ ->
                finish()
            }
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
    
    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "Unknown"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> "%.2f GB".format(gb)
            mb >= 1 -> "%.2f MB".format(mb)
            kb >= 1 -> "%.2f KB".format(kb)
            else -> "$bytes B"
        }
    }
}
