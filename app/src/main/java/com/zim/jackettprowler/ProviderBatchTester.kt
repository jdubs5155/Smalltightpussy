package com.zim.jackettprowler

import android.content.Context
import com.zim.jackettprowler.providers.ProviderRegistry
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Batch testing tool for validating multiple providers at once
 */
class ProviderBatchTester(private val context: Context) {
    
    data class TestResult(
        val providerId: String,
        val providerName: String,
        val success: Boolean,
        val resultCount: Int,
        val responseTime: Long,
        val error: String? = null,
        val sampleResults: List<String> = emptyList()
    )
    
    data class BatchTestResults(
        val total: Int,
        val success: Int,
        val failed: Int,
        val avgResponseTime: Long,
        val results: List<TestResult>
    )
    
    /**
     * Test all enabled built-in providers
     */
    suspend fun testAllBuiltInProviders(
        testQuery: String = "ubuntu",
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): BatchTestResults = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("builtin_providers", Context.MODE_PRIVATE)
        val enabledIds = prefs.getStringSet("enabled_providers", null)
        
        val configs = if (enabledIds == null) {
            ProviderRegistry.getPublicConfigs()
        } else {
            ProviderRegistry.getAllConfigs().filter { it.id in enabledIds }
        }
        
        testProviders(configs, testQuery, onProgress)
    }
    
    /**
     * Test all imported indexers
     */
    suspend fun testImportedIndexers(
        testQuery: String = "ubuntu",
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): BatchTestResults = withContext(Dispatchers.IO) {
        val indexerImporter = IndexerImporter(context)
        val indexers = indexerImporter.getEnabledIndexers()
        
        val results = mutableListOf<TestResult>()
        val completed = AtomicInteger(0)
        val total = indexers.size
        
        for (indexer in indexers) {
            try {
                val current = completed.incrementAndGet()
                onProgress(current, total, indexer.name)
                
                val startTime = System.currentTimeMillis()
                val service = TorznabService(indexer.torznabUrl, indexer.apiKey)
                val torrents = service.search(testQuery, TorznabService.SearchType.SEARCH, limit = 10)
                val responseTime = System.currentTimeMillis() - startTime
                
                results.add(TestResult(
                    providerId = indexer.id,
                    providerName = indexer.name,
                    success = torrents.isNotEmpty(),
                    resultCount = torrents.size,
                    responseTime = responseTime,
                    sampleResults = torrents.take(3).map { it.title }
                ))
            } catch (e: Exception) {
                results.add(TestResult(
                    providerId = indexer.id,
                    providerName = indexer.name,
                    success = false,
                    resultCount = 0,
                    responseTime = 0,
                    error = e.message
                ))
            }
        }
        
        BatchTestResults(
            total = total,
            success = results.count { it.success },
            failed = results.count { !it.success },
            avgResponseTime = if (results.isNotEmpty()) results.map { it.responseTime }.average().toLong() else 0,
            results = results
        )
    }
    
    /**
     * Test specific custom site configs
     */
    suspend fun testProviders(
        configs: List<CustomSiteConfig>,
        testQuery: String = "ubuntu",
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): BatchTestResults = withContext(Dispatchers.IO) {
        val torProxyManager = TorProxyManager(context)
        val scraperService = ScraperService(torProxyManager, context)
        
        val results = mutableListOf<TestResult>()
        val completed = AtomicInteger(0)
        val total = configs.size
        
        // Test in batches to avoid overwhelming the system
        val batchSize = 5
        for (batch in configs.chunked(batchSize)) {
            val batchJobs = batch.map { config ->
                async {
                    try {
                        val current = completed.incrementAndGet()
                        onProgress(current, total, config.name)
                        
                        val startTime = System.currentTimeMillis()
                        val torrents = scraperService.search(config, testQuery, limit = 10)
                        val responseTime = System.currentTimeMillis() - startTime
                        
                        TestResult(
                            providerId = config.id,
                            providerName = config.name,
                            success = torrents.isNotEmpty(),
                            resultCount = torrents.size,
                            responseTime = responseTime,
                            sampleResults = torrents.take(3).map { it.title }
                        )
                    } catch (e: Exception) {
                        TestResult(
                            providerId = config.id,
                            providerName = config.name,
                            success = false,
                            resultCount = 0,
                            responseTime = 0,
                            error = e.message
                        )
                    }
                }
            }
            
            results.addAll(batchJobs.awaitAll())
            delay(1000) // Rate limiting between batches
        }
        
        BatchTestResults(
            total = total,
            success = results.count { it.success },
            failed = results.count { !it.success },
            avgResponseTime = if (results.isNotEmpty()) results.map { it.responseTime }.average().toLong() else 0,
            results = results
        )
    }
    
    /**
     * Quick test of a single provider
     */
    suspend fun quickTest(config: CustomSiteConfig, testQuery: String = "ubuntu"): TestResult = withContext(Dispatchers.IO) {
        try {
            val torProxyManager = TorProxyManager(context)
            val scraperService = ScraperService(torProxyManager, context)
            
            val startTime = System.currentTimeMillis()
            val torrents = scraperService.search(config, testQuery, limit = 10)
            val responseTime = System.currentTimeMillis() - startTime
            
            TestResult(
                providerId = config.id,
                providerName = config.name,
                success = torrents.isNotEmpty(),
                resultCount = torrents.size,
                responseTime = responseTime,
                sampleResults = torrents.take(3).map { it.title }
            )
        } catch (e: Exception) {
            TestResult(
                providerId = config.id,
                providerName = config.name,
                success = false,
                resultCount = 0,
                responseTime = 0,
                error = e.message
            )
        }
    }
    
    /**
     * Format batch test results as readable string
     */
    fun formatResults(results: BatchTestResults): String {
        return buildString {
            appendLine("=== Provider Test Results ===")
            appendLine()
            appendLine("Total: ${results.total}")
            appendLine("✓ Success: ${results.success}")
            appendLine("✗ Failed: ${results.failed}")
            appendLine("Success Rate: ${(results.success * 100.0 / results.total).toInt()}%")
            appendLine("Avg Response: ${results.avgResponseTime}ms")
            appendLine()
            appendLine("=== Detailed Results ===")
            appendLine()
            
            // Show successful providers first
            results.results.filter { it.success }.forEach { result ->
                appendLine("✓ ${result.providerName}")
                appendLine("  Results: ${result.resultCount}")
                appendLine("  Time: ${result.responseTime}ms")
                if (result.sampleResults.isNotEmpty()) {
                    appendLine("  Sample: ${result.sampleResults.first().take(50)}...")
                }
                appendLine()
            }
            
            // Then show failed providers
            if (results.failed > 0) {
                appendLine("=== Failed Providers ===")
                appendLine()
                results.results.filter { !it.success }.forEach { result ->
                    appendLine("✗ ${result.providerName}")
                    appendLine("  Error: ${result.error ?: "Unknown error"}")
                    appendLine()
                }
            }
        }
    }
}
