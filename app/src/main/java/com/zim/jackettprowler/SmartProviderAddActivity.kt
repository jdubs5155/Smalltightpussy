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
import com.zim.jackettprowler.services.LiveSiteConfigBuilder
import com.zim.jackettprowler.services.RealSourceManager
import com.zim.jackettprowler.services.SiteVerificationService
import com.zim.jackettprowler.services.VerifiedSiteRegistry
import kotlinx.coroutines.*

/**
 * Smart provider adding - just paste a URL and the app figures out the rest!
 * 
 * NOW WITH REAL AUTO-CONFIGURATION:
 * - Actually fetches and analyzes live sites
 * - Tests selectors against real HTML
 * - Verifies data extraction works
 * - Saves ONLY working configurations
 * 
 * NO PRESETS - everything is real and verified!
 */
class SmartProviderAddActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySmartProviderAddBinding
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    private lateinit var urlConverter: URLToConfigConverter
    private lateinit var infiltrator: SiteInfiltrator
    private lateinit var realSourceManager: RealSourceManager
    private lateinit var torProxyManager: TorProxyManager
    private lateinit var liveSiteConfigBuilder: LiveSiteConfigBuilder
    private var lastConversionResult: URLToConfigConverter.ConversionResult? = null
    private var lastRealConfigResult: LiveSiteConfigBuilder.ConfigBuildResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySmartProviderAddBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        title = "Add Real Torrent Source"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Initialize real source system
        torProxyManager = TorProxyManager(this)
        realSourceManager = RealSourceManager(this)
        liveSiteConfigBuilder = LiveSiteConfigBuilder(this, torProxyManager)
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
            // Use the NEW real auto-config system
            analyzeAndConfigureRealSite(url)
        }
        
        binding.buttonSave.setOnClickListener {
            // Use the real source manager to save verified configs
            if (lastRealConfigResult?.success == true && lastRealConfigResult?.config != null) {
                saveRealSource(lastRealConfigResult!!.config!!)
            } else if (lastConversionResult?.success == true && lastConversionResult?.config != null) {
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
        
        // Add verified sources button
        binding.root.findViewById<android.widget.Button>(R.id.buttonAddPresets)?.setOnClickListener {
            showVerifiedSourcesDialog()
        }
    }
    
    /**
     * NEW: Use real live site analysis - NO PRESETS!
     */
    private fun analyzeAndConfigureRealSite(url: String) {
        val progressDialog = ProgressDialog.show(
            this,
            "🔍 Real Site Analysis",
            "Connecting to live site...\n• Fetching actual HTML\n• Detecting real structure\n• Testing selectors\n• Verifying data extraction",
            true,
            false
        )
        
        uiScope.launch(Dispatchers.IO) {
            try {
                // Use the REAL live site config builder
                val result = liveSiteConfigBuilder.buildConfig(url)
                lastRealConfigResult = result
                
                launch(Dispatchers.Main) {
                    progressDialog.dismiss()
                    displayRealConfigResult(result)
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(
                        this@SmartProviderAddActivity,
                        "Analysis failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * Display results from REAL site analysis
     */
    private fun displayRealConfigResult(result: LiveSiteConfigBuilder.ConfigBuildResult) {
        binding.textResults.text = buildString {
            appendLine("🔬 REAL SITE ANALYSIS RESULTS")
            appendLine("═".repeat(40))
            appendLine()
            
            if (result.success && result.config != null) {
                val config = result.config
                
                appendLine("✅ Site successfully analyzed and configured!")
                appendLine()
                appendLine("📍 VERIFIED SITE INFO:")
                appendLine("   Name: ${config.name}")
                appendLine("   Base URL: ${config.baseUrl}")
                appendLine("   Search URL: ${config.searchPath}")
                appendLine()
                appendLine("📊 VERIFICATION RESULTS:")
                appendLine("   Test Results Found: ${result.testResultCount}")
                appendLine("   Confidence Score: ${(result.confidence * 100).toInt()}%")
                appendLine()
                appendLine("🔍 DETECTED SELECTORS (REAL):")
                appendLine("─".repeat(35))
                appendLine("   Container: ${config.selectors.resultContainer}")
                appendLine("   ├─ Title: ${config.selectors.title}")
                config.selectors.magnetUrl?.let { 
                    appendLine("   ├─ Magnet: $it ✓ (verified)") 
                } ?: appendLine("   ├─ Magnet: ⚠️ Not found")
                config.selectors.downloadUrl?.let { 
                    appendLine("   ├─ Download: $it ✓ (verified)") 
                }
                config.selectors.seeders?.let { 
                    appendLine("   ├─ Seeders: $it ✓ (verified)") 
                } ?: appendLine("   ├─ Seeders: ⚠️ Not found")
                config.selectors.leechers?.let { 
                    appendLine("   ├─ Leechers: $it ✓") 
                }
                config.selectors.size?.let { 
                    appendLine("   └─ Size: $it ✓") 
                } ?: appendLine("   └─ Size: ⚠️ Not found")
                appendLine()
                appendLine("═".repeat(40))
                appendLine("✓ ${result.message}")
                appendLine()
                appendLine("This is a REAL verified config - NOT a preset!")
                appendLine("Click SAVE to add this working source.")
                
                binding.buttonSave.isEnabled = true
                binding.buttonTestSearch.isEnabled = true
            } else {
                appendLine("✗ Could not configure site")
                appendLine()
                appendLine("Message: ${result.message}")
                appendLine()
                appendLine("The site could not be automatically configured.")
                appendLine("Possible reasons:")
                appendLine("• Site is protected by Cloudflare/CAPTCHA")
                appendLine("• Site requires login")
                appendLine("• Site structure is unusual")
                appendLine("• Site is currently down")
                appendLine()
                appendLine("Try a different URL or mirror.")
                
                binding.buttonSave.isEnabled = false
                binding.buttonTestSearch.isEnabled = false
            }
        }
    }
    
    /**
     * Show verified sources that can be added (these are TESTED, not presets)
     */
    private fun showVerifiedSourcesDialog() {
        val verifiedSites = VerifiedSiteRegistry.getVerifiedSites()
        val categories = verifiedSites.groupBy { 
            when {
                it.config.category == "anime" -> "🎌 Anime"
                it.config.category == "tv" -> "📺 TV Shows"
                it.config.category == "movies" -> "🎬 Movies"
                it.config.category == "adult" -> "🔞 Adult"
                it.config.category == "legal" -> "📚 Legal/Academic"
                it.config.category == "dht" -> "🌐 DHT/Meta Search"
                it.config.category == "international" -> "🌍 International"
                else -> "📁 General"
            }
        }
        
        val categoryNames = categories.keys.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("🔒 Add Verified Sources")
            .setItems(categoryNames) { _, which ->
                showVerifiedSitesForCategory(categoryNames[which], categories[categoryNames[which]]!!)
            }
            .setNeutralButton("Add All Verified") { _, _ ->
                addAllVerifiedSources()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showVerifiedSitesForCategory(
        categoryName: String,
        sites: List<VerifiedSiteRegistry.VerifiedSiteInfo>
    ) {
        val siteNames = sites.map { "• ${it.config.name}\n  ${it.description}" }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle(categoryName)
            .setItems(siteNames) { _, which ->
                addVerifiedSource(sites[which])
            }
            .setNeutralButton("Add All") { _, _ ->
                addAllInCategory(sites)
            }
            .setNegativeButton("Back") { _, _ ->
                showVerifiedSourcesDialog()
            }
            .show()
    }
    
    private fun addVerifiedSource(siteInfo: VerifiedSiteRegistry.VerifiedSiteInfo) {
        val progress = ProgressDialog.show(
            this,
            "🧪 Verifying Source",
            "Testing ${siteInfo.config.name} with live search...",
            true
        )
        
        uiScope.launch(Dispatchers.IO) {
            // Actually verify before adding!
            val result = realSourceManager.addSourceByUrl(siteInfo.config.baseUrl)
            
            launch(Dispatchers.Main) {
                progress.dismiss()
                
                if (result.success) {
                    AlertDialog.Builder(this@SmartProviderAddActivity)
                        .setTitle("✅ ${siteInfo.config.name} Added!")
                        .setMessage("Verification passed!\n\n${result.message}\n\nLast verified: ${siteInfo.lastVerified}")
                        .setPositiveButton("Great!", null)
                        .show()
                } else {
                    AlertDialog.Builder(this@SmartProviderAddActivity)
                        .setTitle("⚠️ Verification Failed")
                        .setMessage("Could not verify ${siteInfo.config.name}:\n\n${result.message}\n\nThe site may be temporarily down or blocked.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }
    
    private fun addAllInCategory(sites: List<VerifiedSiteRegistry.VerifiedSiteInfo>) {
        val progress = ProgressDialog(this).apply {
            setTitle("🧪 Verifying Sources")
            setMessage("Testing sites...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = sites.size
            show()
        }
        
        uiScope.launch(Dispatchers.IO) {
            var addedCount = 0
            var workingCount = 0
            
            sites.forEachIndexed { index, site ->
                launch(Dispatchers.Main) {
                    progress.progress = index + 1
                    progress.setMessage("Testing ${site.config.name}...")
                }
                
                val result = realSourceManager.addSourceByUrl(site.config.baseUrl)
                addedCount++
                if (result.success) workingCount++
            }
            
            launch(Dispatchers.Main) {
                progress.dismiss()
                Toast.makeText(
                    this@SmartProviderAddActivity,
                    "✓ Verified and added $workingCount of $addedCount sources",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun addAllVerifiedSources() {
        val allSites = VerifiedSiteRegistry.getVerifiedSites()
        
        AlertDialog.Builder(this)
            .setTitle("Add All ${allSites.size} Verified Sources?")
            .setMessage("This will test and add all verified torrent sources.\n\nOnly sources that pass live verification will be added.\n\nThis may take a few minutes.")
            .setPositiveButton("Start") { _, _ ->
                addAllInCategory(allSites)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Save using the REAL source manager (with verification)
     */
    private fun saveRealSource(config: CustomSiteConfig) {
        val progress = ProgressDialog.show(
            this,
            "🔒 Saving & Verifying",
            "Final verification before save...",
            true
        )
        
        uiScope.launch(Dispatchers.IO) {
            val result = realSourceManager.addSourceByUrl(config.baseUrl)
            
            launch(Dispatchers.Main) {
                progress.dismiss()
                
                if (result.success) {
                    Toast.makeText(
                        this@SmartProviderAddActivity,
                        "✓ ${config.name} verified and saved!",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    AlertDialog.Builder(this@SmartProviderAddActivity)
                        .setTitle("✅ Source Added!")
                        .setMessage("${config.name} has been verified and saved.\n\n${result.message}\n\nThis is a REAL working source!")
                        .setPositiveButton("Add Another") { _, _ ->
                            clearForm()
                        }
                        .setNegativeButton("Done") { _, _ ->
                            finish()
                        }
                        .show()
                } else {
                    AlertDialog.Builder(this@SmartProviderAddActivity)
                        .setTitle("⚠️ Verification Failed")
                        .setMessage("${result.message}\n\nThe source was not saved because it couldn't be verified.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }
    
    private fun clearForm() {
        binding.editUrl.text?.clear()
        binding.textResults.text = ""
        binding.buttonSave.isEnabled = false
        binding.buttonTestSearch.isEnabled = false
        lastConversionResult = null
        lastRealConfigResult = null
    }
    
    /**
     * Show dialog with TESTED torrent site presets (legacy - now shows verified instead)
     */
    private fun showTorrentPresetsDialog() {
        // Redirect to new verified sources dialog
        showVerifiedSourcesDialog()
    }
    
    private fun showPresetsForCategory(category: com.zim.jackettprowler.providers.TorrentSitePresetRegistry.TorrentPresetCategory) {
        val presetNames = category.presets.map { "• ${it.name} - ${it.description}" }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("${category.icon} ${category.name}")
            .setItems(presetNames) { _, which ->
                addTorrentPresetWithTesting(category.presets[which].id)
            }
            .setNeutralButton("Add All") { _, _ ->
                addAllPresetsInCategory(category)
            }
            .setNegativeButton("Back") { _, _ ->
                showTorrentPresetsDialog()
            }
            .show()
    }
    
    private fun addTorrentPresetWithTesting(presetId: String) {
        val config = com.zim.jackettprowler.providers.TorrentSitePresetRegistry.getPresetConfig(presetId)
        if (config == null) {
            Toast.makeText(this, "Unknown preset: $presetId", Toast.LENGTH_SHORT).show()
            return
        }
        
        val progress = ProgressDialog.show(this, "🧪 Testing Preset", "Verifying ${config.name} is working...", true)
        
        uiScope.launch(Dispatchers.IO) {
            val testResult = com.zim.jackettprowler.providers.TorrentSitePresetRegistry.testPreset(this@SmartProviderAddActivity, presetId)
            
            launch(Dispatchers.Main) {
                progress.dismiss()
                
                val customSiteManager = CustomSiteManager(this@SmartProviderAddActivity)
                
                if (testResult.success) {
                    customSiteManager.addSite(config)
                    AlertDialog.Builder(this@SmartProviderAddActivity)
                        .setTitle("✅ ${config.name} Added!")
                        .setMessage("Test successful!\n\n• Found ${testResult.resultCount} torrents\n• Example: ${testResult.sampleTitle?.take(50) ?: "N/A"}...\n\nSite is ready to use.")
                        .setPositiveButton("Great!", null)
                        .show()
                } else {
                    AlertDialog.Builder(this@SmartProviderAddActivity)
                        .setTitle("⚠️ Test Inconclusive")
                        .setMessage("Could not verify ${config.name}:\n\n${testResult.error ?: "No results found"}\n\nThe site may still work. Add anyway?")
                        .setPositiveButton("Add Anyway") { _, _ ->
                            customSiteManager.addSite(config)
                            Toast.makeText(this@SmartProviderAddActivity, "Added: ${config.name}", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
    }
    
    private fun addAllPresetsInCategory(category: com.zim.jackettprowler.providers.TorrentSitePresetRegistry.TorrentPresetCategory) {
        val progress = ProgressDialog(this).apply {
            setTitle("🧪 Testing Presets")
            setMessage("Testing ${category.presets.size} sites...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = category.presets.size
            show()
        }
        
        uiScope.launch(Dispatchers.IO) {
            val customSiteManager = CustomSiteManager(this@SmartProviderAddActivity)
            var addedCount = 0
            var workingCount = 0
            
            category.presets.forEachIndexed { index, preset ->
                launch(Dispatchers.Main) {
                    progress.progress = index + 1
                    progress.setMessage("Testing ${preset.name}...")
                }
                
                val config = com.zim.jackettprowler.providers.TorrentSitePresetRegistry.getPresetConfig(preset.id)
                if (config != null) {
                    val testResult = com.zim.jackettprowler.providers.TorrentSitePresetRegistry.testPreset(this@SmartProviderAddActivity, preset.id)
                    customSiteManager.addSite(config)
                    addedCount++
                    if (testResult.success) workingCount++
                }
            }
            
            launch(Dispatchers.Main) {
                progress.dismiss()
                Toast.makeText(this@SmartProviderAddActivity, "✓ Added $addedCount sites ($workingCount verified working)", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun addAllPublicTorrentPresets() {
        val publicPresets = com.zim.jackettprowler.providers.TorrentSitePresetRegistry.getPublicPresets()
        
        val progress = ProgressDialog(this).apply {
            setTitle("🧪 Testing Public Trackers")
            setMessage("Testing ${publicPresets.size} sites...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = publicPresets.size
            show()
        }
        
        uiScope.launch(Dispatchers.IO) {
            val customSiteManager = CustomSiteManager(this@SmartProviderAddActivity)
            var addedCount = 0
            var workingCount = 0
            val results = mutableMapOf<String, Boolean>()
            
            publicPresets.forEachIndexed { index, preset ->
                launch(Dispatchers.Main) {
                    progress.progress = index + 1
                    progress.setMessage("Testing ${preset.name}...")
                }
                
                val config = com.zim.jackettprowler.providers.TorrentSitePresetRegistry.getPresetConfig(preset.id)
                if (config != null) {
                    val testResult = com.zim.jackettprowler.providers.TorrentSitePresetRegistry.testPreset(this@SmartProviderAddActivity, preset.id)
                    customSiteManager.addSite(config)
                    addedCount++
                    results[preset.name] = testResult.success
                    if (testResult.success) workingCount++
                }
            }
            
            launch(Dispatchers.Main) {
                progress.dismiss()
                
                val message = buildString {
                    appendLine("Added $addedCount sites ($workingCount verified working)")
                    appendLine()
                    results.forEach { (name, success) ->
                        appendLine("${if (success) "✓" else "⚠"} $name")
                    }
                }
                
                AlertDialog.Builder(this@SmartProviderAddActivity)
                    .setTitle("✅ Public Trackers Added")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
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
