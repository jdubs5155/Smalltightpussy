package com.zim.jackettprowler

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manager for syncing scraper configurations from GitHub repositories
 */
class GitHubScraperSync(
    private val customSiteManager: CustomSiteManager
) {
    private val gson = Gson()
    
    // Popular scraper repositories
    private val scraperRepos = listOf(
        GitHubRepo(
            owner = "jesec",
            repo = "flood",
            path = "scrapers",
            description = "Flood torrent scrapers"
        ),
        GitHubRepo(
            owner = "ngosang",
            repo = "trackerslist",
            path = "trackers_all.txt",
            description = "Updated torrent trackers"
        )
    )
    
    /**
     * Fetch scraper configurations from a GitHub repository
     */
    suspend fun fetchScrapersFromGitHub(
        owner: String,
        repo: String,
        path: String = ""
    ): List<CustomSiteConfig> = withContext(Dispatchers.IO) {
        try {
            val apiUrl = "https://api.github.com/repos/$owner/$repo/contents/$path"
            val response = fetchFromGitHub(apiUrl)
            
            // Parse GitHub API response
            parseScraperConfigs(response)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Sync all scraper repositories
     */
    suspend fun syncAllRepositories(): SyncResult = withContext(Dispatchers.IO) {
        var totalAdded = 0
        var totalFailed = 0
        val errors = mutableListOf<String>()
        
        for (repo in scraperRepos) {
            try {
                val configs = fetchScrapersFromGitHub(repo.owner, repo.repo, repo.path)
                configs.forEach { config ->
                    try {
                        customSiteManager.addSite(config)
                        totalAdded++
                    } catch (e: Exception) {
                        totalFailed++
                        errors.add("${config.name}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                totalFailed++
                errors.add("${repo.description}: ${e.message}")
            }
        }
        
        SyncResult(totalAdded, totalFailed, errors)
    }
    
    /**
     * Fetch content from GitHub API
     */
    private fun fetchFromGitHub(apiUrl: String): String {
        val url = URL(apiUrl)
        val connection = url.openConnection() as HttpURLConnection
        
        return try {
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "JackettProwlarrClient")
            
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                throw Exception("GitHub API error: $responseCode")
            }
            
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            
            reader.close()
            response.toString()
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * Parse scraper configurations from JSON
     */
    private fun parseScraperConfigs(json: String): List<CustomSiteConfig> {
        return try {
            // Try to parse as array of GitHub file objects
            val type = object : TypeToken<List<GitHubFile>>() {}.type
            val files: List<GitHubFile> = gson.fromJson(json, type)
            
            files.filter { it.name.endsWith(".json") }
                .mapNotNull { file ->
                    try {
                        // Download and parse each scraper config
                        val content = fetchFromGitHub(file.download_url)
                        gson.fromJson(content, CustomSiteConfig::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Import scrapers from a custom GitHub repository
     */
    suspend fun importFromCustomRepo(
        owner: String,
        repo: String,
        branch: String = "main",
        path: String = ""
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            val configs = fetchScrapersFromGitHub(owner, repo, path)
            var added = 0
            var failed = 0
            val errors = mutableListOf<String>()
            
            configs.forEach { config ->
                try {
                    customSiteManager.addSite(config)
                    added++
                } catch (e: Exception) {
                    failed++
                    errors.add("${config.name}: ${e.message}")
                }
            }
            
            SyncResult(added, failed, errors)
        } catch (e: Exception) {
            SyncResult(0, 1, listOf(e.message ?: "Unknown error"))
        }
    }
    
    /**
     * Export current scrapers to JSON format (for sharing)
     */
    fun exportScrapersToJson(): String {
        val sites = customSiteManager.getSites()
        return gson.toJson(sites)
    }
    
    /**
     * Import scrapers from JSON string
     */
    fun importScrapersFromJson(json: String): SyncResult {
        return try {
            val type = object : TypeToken<List<CustomSiteConfig>>() {}.type
            val sites: List<CustomSiteConfig> = gson.fromJson(json, type)
            
            var added = 0
            var failed = 0
            val errors = mutableListOf<String>()
            
            sites.forEach { site ->
                try {
                    customSiteManager.addSite(site)
                    added++
                } catch (e: Exception) {
                    failed++
                    errors.add("${site.name}: ${e.message}")
                }
            }
            
            SyncResult(added, failed, errors)
        } catch (e: Exception) {
            SyncResult(0, 1, listOf(e.message ?: "Invalid JSON format"))
        }
    }
}

/**
 * GitHub repository information
 */
data class GitHubRepo(
    val owner: String,
    val repo: String,
    val path: String,
    val description: String
)

/**
 * GitHub API file response
 */
data class GitHubFile(
    val name: String,
    val path: String,
    val download_url: String,
    val type: String
)

/**
 * Sync result summary
 */
data class SyncResult(
    val added: Int,
    val failed: Int,
    val errors: List<String>
) {
    fun isSuccess(): Boolean = added > 0 && failed == 0
    
    fun getMessage(): String {
        return if (isSuccess()) {
            "Successfully added $added scraper(s)"
        } else {
            "Added: $added, Failed: $failed\n${errors.joinToString("\n")}"
        }
    }
}
