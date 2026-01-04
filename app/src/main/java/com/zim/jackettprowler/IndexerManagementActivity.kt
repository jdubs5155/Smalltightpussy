package com.zim.jackettprowler

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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

        loadIndexers()
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
        val url = "$baseUrl/api/v2.0/indexers/all/results/torznab/api?t=caps&apikey=$apiKey"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val indexers = mutableListOf<IndexerInfo>()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code}")
            }

            val xml = response.body?.string() ?: return indexers

            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(xml.reader())

            var event = parser.eventType
            val categories = mutableSetOf<String>()

            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (event) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "category" -> {
                                val catName = parser.getAttributeValue(null, "name")
                                if (catName != null) {
                                    categories.add(catName)
                                }
                            }
                        }
                    }
                }
                event = parser.next()
            }

            // For now, we'll create a single entry representing "all indexers"
            // In reality, Jackett/Prowlarr return a combined feed, so we treat it as one source
            indexers.add(IndexerInfo(
                id = "$source-all",
                name = "All ${source.replaceFirstChar { it.uppercase() }} Indexers",
                categories = categories.toList(),
                source = source
            ))
        }

        return indexers
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
