package com.zim.jackettprowler

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.zim.jackettprowler.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val client = OkHttpClient()

    private var adapter: TorrentAdapter? = null
    private var lastQuery: String? = null
    private var lastSource: Source? = null

    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)

    // Hard-coded from your setup
    private val JACKETT_BASE_URL = "http://192.168.1.175:9117"
    private val JACKETT_API_KEY = "sfbizvj42r5h41a2aojb2t29zouqgd3s"

    private val PROWLARR_BASE_URL = "http://192.168.1.175:9696"
    private val PROWLARR_API_KEY = "11e5676f4c3444479cea3671a6c0c55b"

    enum class Source {
        JACKETT,
        PROWLARR
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        checkConnections()
    }

    private fun setupRecyclerView() {
        adapter = TorrentAdapter(emptyList()) { result ->
            showTorrentClientChooser(result)
        }
        binding.recyclerViewResults.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewResults.adapter = adapter
    }

    private fun setupListeners() {
        binding.buttonSearch.setOnClickListener {
            val query = binding.editTextQuery.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query, getSelectedSource())
            }
        }

        binding.buttonRefresh.setOnClickListener {
            binding.textStatus.text = "Rechecking connections..."
            adapter?.updateData(emptyList())

            uiScope.launch(Dispatchers.IO) {
                val status = getConnectionStatus()
                launch(Dispatchers.Main) {
                    binding.textStatus.text = status
                }

                val query = lastQuery
                val source = lastSource
                if (!query.isNullOrEmpty() && source != null) {
                    try {
                        val xml = fetchTorznabResults(query, source)
                        val results = parseTorznab(xml)
                        launch(Dispatchers.Main) {
                            adapter?.updateData(results)
                            binding.textStatus.text =
                                "$status | Refreshed: ${results.size} result(s)."
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        launch(Dispatchers.Main) {
                            binding.textStatus.text =
                                "$status | Refresh search error: ${e.message}"
                        }
                    }
                }
            }
        }

        binding.editTextQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.editTextQuery.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query, getSelectedSource())
                }
                true
            } else {
                false
            }
        }

        binding.buttonSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun getSelectedSource(): Source {
        val pos = binding.spinnerSource.selectedItemPosition
        return if (pos == 0) Source.JACKETT else Source.PROWLARR
    }

    private fun performSearch(query: String, source: Source) {
        lastQuery = query
        lastSource = source

        // Check if this source's indexers are enabled
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val sourceKey = if (source == Source.JACKETT) "jackett" else "prowlarr"
        val isSourceEnabled = prefs.getBoolean("indexer_${sourceKey}-all_enabled", true)
        
        if (!isSourceEnabled) {
            binding.textStatus.text = "$source indexers are disabled. Enable them in Settings > Manage Indexers"
            return
        }

        val useSmartSearch = binding.toggleDescriptiveSearch.isChecked
        val searchType = if (useSmartSearch) "Smart Search" else "Standard"
        
        binding.textStatus.text = "Searching $source ($searchType) for \"$query\"..."
        adapter?.updateData(emptyList())

        uiScope.launch(Dispatchers.IO) {
            try {
                val allResults = mutableSetOf<TorrentResult>()
                
                // Primary search
                val xml = fetchTorznabResults(query, source)
                allResults.addAll(parseTorznab(xml))
                
                // If smart search is enabled, also search for keywords
                if (useSmartSearch) {
                    val keywords = extractKeywords(query)
                    for (keyword in keywords) {
                        if (keyword.length > 2) { // Only search meaningful keywords
                            try {
                                val kwXml = fetchTorznabResults(keyword, source)
                                allResults.addAll(parseTorznab(kwXml))
                            } catch (_: Exception) {
                                // Ignore errors from individual keyword searches
                            }
                        }
                    }
                }

                saveSearchQuery(query)
                val results = allResults.toList().sortedByDescending { it.seeders }

                launch(Dispatchers.Main) {
                    adapter?.updateData(results)
                    binding.textStatus.text =
                        "Search OK on $source ($searchType) | ${results.size} result(s)."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    binding.textStatus.text = "Search error on $source: ${e.message}"
                }
            }
        }
    }

    private fun extractKeywords(query: String): List<String> {
        // Remove common words and split into keywords
        val stopWords = setOf("the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "from", "when", "gets", "is", "are", "was", "were")
        return query.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it !in stopWords }
            .distinct()
    }

    private fun checkConnections() {
        binding.textStatus.text = "Checking Jackett & Prowlarr..."
        uiScope.launch(Dispatchers.IO) {
            val status = getConnectionStatus()
            launch(Dispatchers.Main) {
                binding.textStatus.text = status
            }
        }
    }

    private fun getConnectionStatus(): String {
        val jackettOk = try {
            pingService(JACKETT_BASE_URL, JACKETT_API_KEY)
        } catch (_: Exception) {
            false
        }

        val prowlarrOk = try {
            pingService(PROWLARR_BASE_URL, PROWLARR_API_KEY)
        } catch (_: Exception) {
            false
        }

        return when {
            jackettOk && prowlarrOk -> "Connected: Jackett & Prowlarr"
            jackettOk && !prowlarrOk -> "Connected: Jackett only (Prowlarr unreachable)"
            !jackettOk && prowlarrOk -> "Connected: Prowlarr only (Jackett unreachable)"
            else -> "No connection to Jackett or Prowlarr"
        }
    }

    private fun pingService(baseUrl: String, apiKey: String): Boolean {
        val url = baseUrl.trimEnd('/') +
                "/api/v2.0/indexers/all/results/torznab/api" +
                "?t=caps&apikey=$apiKey"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            return response.isSuccessful
        }
    }

    private fun fetchTorznabResults(query: String, source: Source): String {
        val (baseUrl, apiKey) = when (source) {
            Source.JACKETT -> JACKETT_BASE_URL to JACKETT_API_KEY
            Source.PROWLARR -> PROWLARR_BASE_URL to PROWLARR_API_KEY
        }

        val url = baseUrl.trimEnd('/') +
                "/api/v2.0/indexers/all/results/torznab/api" +
                "?t=search&q=${Uri.encode(query)}&apikey=$apiKey"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code}")
            }
            return response.body?.string() ?: throw RuntimeException("Empty response")
        }
    }

    private fun parseTorznab(xml: String): List<TorrentResult> {
        val results = mutableListOf<TorrentResult>()

        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())

        var event = parser.eventType

        var title = ""
        var link = ""
        var size = 0L
        var seeders = 0
        var indexer: String? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "item" -> {
                            title = ""
                            link = ""
                            size = 0L
                            seeders = 0
                            indexer = null
                        }

                        "title" -> {
                            title = parser.nextText()
                        }

                        "link" -> {
                            link = parser.nextText()
                        }

                        "size" -> {
                            size = parser.nextText().toLongOrNull() ?: 0L
                        }

                        "attr" -> {
                            val nameAttr = parser.getAttributeValue(null, "name")
                            val valueAttr = parser.getAttributeValue(null, "value")
                            when (nameAttr) {
                                "seeders" -> seeders = valueAttr.toIntOrNull() ?: 0
                                "indexer", "jackett_indexer" -> indexer = valueAttr
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "item") {
                        if (title.isNotEmpty() && link.isNotEmpty()) {
                            results.add(
                                TorrentResult(
                                    title = title,
                                    link = link,
                                    sizeBytes = size,
                                    seeders = seeders,
                                    indexer = indexer
                                )
                            )
                        }
                    }
                }
            }
            event = parser.next()
        }

        return results
    }

    private fun showTorrentClientChooser(result: TorrentResult) {
        saveDownloadHistory(result)

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val qbEnabled = prefs.getBoolean(getString(R.string.pref_qb_enabled), false)
        
        val torrentClients = mutableListOf<TorrentClientOption>()
        
        // Add qBittorrent if configured
        if (qbEnabled) {
            val baseUrl = prefs.getString(getString(R.string.pref_qb_base_url), "") ?: ""
            val username = prefs.getString(getString(R.string.pref_qb_username), "") ?: ""
            val password = prefs.getString(getString(R.string.pref_qb_password), "") ?: ""
            
            if (baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                torrentClients.add(TorrentClientOption("qBittorrent (Configured)", "qbittorrent", baseUrl, username, password))
            }
        }
        
        // Detect installed torrent clients
        val installedClients = detectInstalledTorrentClients()
        torrentClients.addAll(installedClients)
        
        if (torrentClients.isEmpty()) {
            // No clients found, show install options
            showNoClientsDialog(result)
            return
        }
        
        if (torrentClients.size == 1) {
            // Only one client, use it directly
            downloadWithClient(result, torrentClients[0])
            return
        }
        
        // Multiple clients, show chooser
        val clientNames = torrentClients.map { it.displayName }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Download with:")
            .setItems(clientNames) { _, which ->
                downloadWithClient(result, torrentClients[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun detectInstalledTorrentClients(): List<TorrentClientOption> {
        val clients = mutableListOf<TorrentClientOption>()
        val knownClients = listOf(
            "com.utorrent.client" to "µTorrent",
            "com.bittorrent.client" to "BitTorrent",
            "org.transdroid.full" to "Transdroid",
            "com.deluge.android" to "Deluge",
            "org.proninyaroslav.libretorrent" to "LibreTorrent",
            "com.frostwire.android" to "FrostWire",
            "com.flxrs.danmaku.flinger" to "Flud"
        )
        
        val pm = packageManager
        for ((packageName, displayName) in knownClients) {
            try {
                pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
                clients.add(TorrentClientOption(displayName, packageName, null, null, null))
            } catch (_: PackageManager.NameNotFoundException) {
                // Client not installed
            }
        }
        
        return clients
    }

    private fun showNoClientsDialog(result: TorrentResult) {
        val recommendations = listOf(
            "LibreTorrent - Free, open source",
            "µTorrent - Popular choice",
            "Flud - Simple and effective"
        )
        
        AlertDialog.Builder(this)
            .setTitle("No Torrent Clients Found")
            .setMessage("You need to install a torrent client app to download torrents.\n\nRecommended clients:\n${recommendations.joinToString("\n")}")
            .setPositiveButton("Open Play Store") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=torrent+client&c=apps"))
                    startActivity(intent)
                } catch (_: Exception) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=torrent+client&c=apps"))
                    startActivity(intent)
                }
            }
            .setNeutralButton("Copy Link") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Torrent Link", result.link)
                clipboard.setPrimaryClip(clip)
                binding.textStatus.text = "Link copied to clipboard"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun downloadWithClient(result: TorrentResult, client: TorrentClientOption) {
        when (client.type) {
            "qbittorrent" -> {
                uiScope.launch(Dispatchers.IO) {
                    try {
                        val qb = QbittorrentClient(client.baseUrl!!, client.username!!, client.password!!)
                        qb.addTorrentFromUrl(result.link)
                        launch(Dispatchers.Main) {
                            binding.textStatus.text = "✓ Downloading in qBittorrent"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        launch(Dispatchers.Main) {
                            binding.textStatus.text = "qB error: ${e.message}"
                        }
                    }
                }
            }
            else -> {
                // Use Android intent for other clients
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.link))
                    intent.setPackage(client.type)
                    startActivity(intent)
                    binding.textStatus.text = "✓ Opening in ${client.displayName}"
                } catch (e: Exception) {
                    // Fallback to generic intent
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.link))
                        startActivity(intent)
                        binding.textStatus.text = "✓ Opening in default app"
                    } catch (e2: Exception) {
                        binding.textStatus.text = "Error: No app can handle this link"
                    }
                }
            }
        }
    }

    data class TorrentClientOption(
        val displayName: String,
        val type: String,
        val baseUrl: String?,
        val username: String?,
        val password: String?
    )

    private fun openTorrentLink(result: TorrentResult) {
        saveDownloadHistory(result)

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val enabled = prefs.getBoolean(getString(R.string.pref_qb_enabled), false)

        if (enabled) {
            val baseUrl = prefs.getString(getString(R.string.pref_qb_base_url), "") ?: ""
            val username = prefs.getString(getString(R.string.pref_qb_username), "") ?: ""
            val password = prefs.getString(getString(R.string.pref_qb_password), "") ?: ""

            if (baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                uiScope.launch(Dispatchers.IO) {
                    try {
                        val qb = QbittorrentClient(baseUrl, username, password)
                        qb.addTorrentFromUrl(result.link)
                        launch(Dispatchers.Main) {
                            binding.textStatus.text = "Sent to qBittorrent."
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        launch(Dispatchers.Main) {
                            binding.textStatus.text =
                                "qB error: ${e.message}. Falling back to local app..."
                            openTorrentLinkExternal(result.link)
                        }
                    }
                }
                return
            }
        }

        openTorrentLinkExternal(result.link)
    }

    private fun openTorrentLinkExternal(link: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            startActivity(intent)
        } catch (e: Exception) {
            binding.textStatus.text = "No app to handle this link."
        }
    }

    private fun saveSearchQuery(query: String) {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val existing = prefs.getStringSet("saved_searches", mutableSetOf())?.toMutableSet()
            ?: mutableSetOf()
        if (!existing.contains(query)) {
            existing.add(query)
            prefs.edit().putStringSet("saved_searches", existing).apply()
        }
    }

    private fun saveDownloadHistory(result: TorrentResult) {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val existing = prefs.getStringSet("download_history", mutableSetOf())?.toMutableSet()
            ?: mutableSetOf()
        val entry = "${result.title}|||${result.indexer ?: "unknown"}"
        existing.add(entry)
        prefs.edit().putStringSet("download_history", existing).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}