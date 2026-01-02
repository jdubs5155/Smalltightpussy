package com.zim.jackettprowler

import android.content.Intent
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
            openTorrentLink(result)
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

        binding.textStatus.text = "Searching $source for \"$query\"..."
        adapter?.updateData(emptyList())

        uiScope.launch(Dispatchers.IO) {
            try {
                val xml = fetchTorznabResults(query, source)
                val results = parseTorznab(xml)

                saveSearchQuery(query)

                launch(Dispatchers.Main) {
                    adapter?.updateData(results)
                    binding.textStatus.text =
                        "Search OK on $source | ${results.size} result(s)."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    binding.textStatus.text = "Search error on $source: ${e.message}"
                }
            }
        }
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

    private fun openTorrentLink(result: TorrentResult) {
        saveDownloadHistory(result)

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val enabled = prefs.getBoolean(getString(R.string.pref_qb_enabled), false)

        if (enabled) {
            val baseUrl = prefs.getString(getString(R.string.pref_qb_base_url), "") ?: ""
            val username = prefs.getString(getString(R.string.pref_qb_username), "") ?: ""
            val password = prefs.getString(getString(R.string.pref_qb_password), "") ?: ""

            if (baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                GlobalScope.launch(Dispatchers.IO) {
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