package com.zim.jackettprowler.video

import android.app.ProgressDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zim.jackettprowler.R
import com.zim.jackettprowler.databinding.ActivityVideoSitesBinding
import kotlinx.coroutines.*

/**
 * Activity for managing clearnet video sites
 */
class VideoSitesActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityVideoSitesBinding
    private lateinit var videoService: VideoSearchService
    private lateinit var universalExtractor: UniversalVideoExtractor
    private lateinit var adapter: VideoSiteAdapter
    
    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + job)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoSitesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        title = "Clearnet Video Sites"
        
        videoService = VideoSearchService(this)
        universalExtractor = UniversalVideoExtractor(this)
        
        setupRecyclerView()
        setupButtons()
        refreshSiteList()
    }
    
    private fun setupRecyclerView() {
        adapter = VideoSiteAdapter(
            onToggle = { site, enabled ->
                videoService.toggleSite(site.id, enabled)
            },
            onDelete = { site ->
                confirmDelete(site)
            },
            onTest = { site ->
                testSite(site)
            }
        )
        
        binding.recyclerViewSites.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSites.adapter = adapter
    }
    
    private fun setupButtons() {
        binding.buttonAddSite.setOnClickListener {
            showAddSiteDialog()
        }
        
        binding.buttonAddPreset.setOnClickListener {
            showPresetSitesDialog()
        }
        
        binding.buttonTestAll.setOnClickListener {
            testAllSites()
        }
    }
    
    private fun refreshSiteList() {
        val sites = videoService.getAllSites()
        adapter.updateData(sites)
        
        binding.textSiteCount.text = "${sites.size} video sites configured (${sites.count { it.isEnabled }} enabled)"
        
        if (sites.isEmpty()) {
            binding.textEmptyMessage.visibility = View.VISIBLE
            binding.recyclerViewSites.visibility = View.GONE
        } else {
            binding.textEmptyMessage.visibility = View.GONE
            binding.recyclerViewSites.visibility = View.VISIBLE
        }
    }
    
    private fun showAddSiteDialog() {
        val editText = EditText(this).apply {
            hint = "Paste video site URL (e.g., https://yewtu.be)"
            setPadding(48, 32, 48, 32)
        }
        
        AlertDialog.Builder(this)
            .setTitle("🔍 Auto-Discover Video Site")
            .setMessage("Paste ANY video site URL - the app will deep analyze and auto-configure it!\n\n✓ Invidious/Piped instances\n✓ PeerTube instances\n✓ Any video platform\n✓ Custom streaming sites")
            .setView(editText)
            .setPositiveButton("Discover") { _, _ ->
                val url = editText.text.toString().trim()
                if (url.isNotEmpty()) {
                    deepDiscoverSite(url)
                }
            }
            .setNeutralButton("Simple Add") { _, _ ->
                val url = editText.text.toString().trim()
                if (url.isNotEmpty()) {
                    addSite(url)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deepDiscoverSite(url: String) {
        val progress = ProgressDialog.show(this, "🔍 Deep Analysis", "Probing site capabilities...\n\n• Detecting instance type\n• Finding API endpoints\n• Analyzing page structure\n• Learning selectors", true)
        
        uiScope.launch(Dispatchers.IO) {
            val discovered = universalExtractor.deepAnalyze(url)
            
            launch(Dispatchers.Main) {
                progress.dismiss()
                
                if (discovered != null) {
                    showDiscoveryResults(discovered)
                } else {
                    Toast.makeText(
                        this@VideoSitesActivity,
                        "Could not analyze site. Try adding manually.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun showDiscoveryResults(discovered: UniversalVideoExtractor.DiscoveredSite) {
        val featuresText = discovered.features.joinToString("\n") { "✓ $it" }
        val apisText = if (discovered.apiEndpoints.isNotEmpty()) {
            "\n\nAPI Endpoints Found:\n" + discovered.apiEndpoints.take(3).joinToString("\n") { "• ${it.takeLast(50)}" }
        } else ""
        
        val confidenceEmoji = when {
            discovered.confidence >= 0.8f -> "🟢"
            discovered.confidence >= 0.5f -> "🟡"
            else -> "🔴"
        }
        
        AlertDialog.Builder(this)
            .setTitle("$confidenceEmoji Site Discovered!")
            .setMessage("""
                |Name: ${discovered.config.name}
                |Type: ${discovered.config.siteType}
                |Confidence: ${(discovered.confidence * 100).toInt()}%
                |
                |Features:
                |$featuresText$apisText
            """.trimMargin())
            .setPositiveButton("Add Site") { _, _ ->
                videoService.saveSite(discovered.config)
                refreshSiteList()
                Toast.makeText(this, "Added: ${discovered.config.name}", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Test First") { _, _ ->
                testDiscoveredSite(discovered)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun testDiscoveredSite(discovered: UniversalVideoExtractor.DiscoveredSite) {
        val progress = ProgressDialog.show(this, "Testing", "Searching for 'test'...", true)
        
        uiScope.launch(Dispatchers.IO) {
            try {
                val results = universalExtractor.searchSite(discovered.config, "test")
                
                launch(Dispatchers.Main) {
                    progress.dismiss()
                    
                    if (results.isNotEmpty()) {
                        AlertDialog.Builder(this@VideoSitesActivity)
                            .setTitle("✅ Test Successful!")
                            .setMessage("Found ${results.size} videos!\n\nExample:\n${results.first().title.take(60)}...")
                            .setPositiveButton("Add Site") { _, _ ->
                                videoService.saveSite(discovered.config)
                                refreshSiteList()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    } else {
                        Toast.makeText(this@VideoSitesActivity, "No results found. Site may still work.", Toast.LENGTH_LONG).show()
                        showDiscoveryResults(discovered)
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    progress.dismiss()
                    Toast.makeText(this@VideoSitesActivity, "Test failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun addSite(url: String) {
        val progress = ProgressDialog.show(this, "Analyzing", "Detecting site configuration...", true)
        
        uiScope.launch(Dispatchers.IO) {
            val result = videoService.addSite(url)
            
            launch(Dispatchers.Main) {
                progress.dismiss()
                
                if (result.success && result.config != null) {
                    Toast.makeText(
                        this@VideoSitesActivity,
                        "Added: ${result.config.name} (${result.config.siteType})",
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshSiteList()
                } else {
                    Toast.makeText(
                        this@VideoSitesActivity,
                        "Failed: ${result.error ?: "Unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun showPresetSitesDialog() {
        val presets = arrayOf(
            "📺 YouTube (via Invidious)",
            "🎬 Dailymotion",
            "🎥 Vimeo",
            "🔊 Rumble",
            "🌊 Odysee",
            "💬 BitChute",
            "📚 Internet Archive",
            "──────────────",
            "🔗 Invidious Instances...",
            "🔗 PeerTube Instances...",
            "──────────────",
            "🔞 Adult Sites (18+)...",
            "──────────────",
            "⚡ Add All Public Sites"
        )
        
        AlertDialog.Builder(this)
            .setTitle("Add Video Sites")
            .setItems(presets) { _, which ->
                when (which) {
                    0 -> addPresetSite(VideoSiteType.YOUTUBE)
                    1 -> addPresetSite(VideoSiteType.DAILYMOTION)
                    2 -> addPresetSite(VideoSiteType.VIMEO)
                    3 -> addPresetSite(VideoSiteType.RUMBLE)
                    4 -> addPresetSite(VideoSiteType.ODYSEE)
                    5 -> addPresetSite(VideoSiteType.BITCHUTE)
                    6 -> addPresetSite(VideoSiteType.ARCHIVE_ORG)
                    8 -> showInvidiousInstancesDialog()
                    9 -> showPeerTubeInstancesDialog()
                    11 -> showAdultSitesDialog()
                    13 -> addAllPresets()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showAdultSitesDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Age Verification")
            .setMessage("You must be 18+ years old to add adult video sites.\n\nDo you confirm that you are of legal age in your jurisdiction?")
            .setPositiveButton("I am 18+") { _, _ ->
                showAdultSitesListDialog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showAdultSitesListDialog() {
        val categories = arrayOf(
            "🌐 Popular Sites (10)",
            "🎬 Premium/Studios (15)",
            "🌍 International (10)",
            "🔧 Specialty Sites (15)",
            "──────────────",
            "⚡ Add All 50+ Adult Sites"
        )
        
        AlertDialog.Builder(this)
            .setTitle("🔞 Adult Video Sites Database (50+)")
            .setItems(categories) { _, which ->
                when (which) {
                    0 -> showPopularAdultSites()
                    1 -> showPremiumAdultSites()
                    2 -> showInternationalAdultSites()
                    3 -> showSpecialtyAdultSites()
                    5 -> addAllAdultSitesComplete()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showPopularAdultSites() {
        val sites = arrayOf(
            "🔞 PornHub",
            "🔞 XVideos", 
            "🔞 xHamster",
            "🔞 XNXX",
            "🔞 YouPorn",
            "🔞 RedTube",
            "🔞 SpankBang",
            "🔞 Eporner",
            "🔞 Tube8",
            "🔞 Beeg",
            "──────────────",
            "⚡ Add All Popular"
        )
        
        AlertDialog.Builder(this)
            .setTitle("🌐 Popular Adult Sites")
            .setItems(sites) { _, which ->
                when (which) {
                    0 -> addAdultSite("pornhub", "PornHub", "https://www.pornhub.com", "/video/search?search={query}")
                    1 -> addAdultSite("xvideos", "XVideos", "https://www.xvideos.com", "/?k={query}")
                    2 -> addAdultSite("xhamster", "xHamster", "https://xhamster.com", "/search/{query}")
                    3 -> addAdultSite("xnxx", "XNXX", "https://www.xnxx.com", "/search/{query}")
                    4 -> addAdultSite("youporn", "YouPorn", "https://www.youporn.com", "/search/?query={query}")
                    5 -> addAdultSite("redtube", "RedTube", "https://www.redtube.com", "/?search={query}")
                    6 -> addAdultSite("spankbang", "SpankBang", "https://spankbang.com", "/s/{query}/")
                    7 -> addAdultSite("eporner", "Eporner", "https://www.eporner.com", "/search/{query}/")
                    8 -> addAdultSite("tube8", "Tube8", "https://www.tube8.com", "/searches?q={query}")
                    9 -> addAdultSite("beeg", "Beeg", "https://beeg.com", "/search?q={query}")
                    11 -> addAllPopularAdultSites()
                }
            }
            .setNegativeButton("Back") { _, _ -> showAdultSitesListDialog() }
            .show()
    }
    
    private fun showPremiumAdultSites() {
        val sites = arrayOf(
            "🔞 Brazzers",
            "🔞 RealityKings",
            "🔞 BangBros",
            "🔞 Naughty America",
            "🔞 Digital Playground",
            "🔞 Blacked",
            "🔞 Tushy",
            "🔞 Vixen",
            "🔞 Deeper",
            "🔞 Mofos",
            "🔞 TeamSkeet",
            "🔞 Babes",
            "🔞 Twistys",
            "🔞 PureTaboo",
            "🔞 AdultTime",
            "──────────────",
            "⚡ Add All Premium"
        )
        
        AlertDialog.Builder(this)
            .setTitle("🎬 Premium/Studios")
            .setItems(sites) { _, which ->
                when (which) {
                    0 -> addAdultSite("brazzers", "Brazzers", "https://www.brazzers.com", "/search?q={query}")
                    1 -> addAdultSite("realitykings", "RealityKings", "https://www.realitykings.com", "/search?q={query}")
                    2 -> addAdultSite("bangbros", "BangBros", "https://www.bangbros.com", "/search/{query}")
                    3 -> addAdultSite("naughtyamerica", "NaughtyAmerica", "https://www.naughtyamerica.com", "/search?q={query}")
                    4 -> addAdultSite("digitalplayground", "DigitalPlayground", "https://www.digitalplayground.com", "/search?q={query}")
                    5 -> addAdultSite("blacked", "Blacked", "https://www.blacked.com", "/videos?search={query}")
                    6 -> addAdultSite("tushy", "Tushy", "https://www.tushy.com", "/videos?search={query}")
                    7 -> addAdultSite("vixen", "Vixen", "https://www.vixen.com", "/videos?search={query}")
                    8 -> addAdultSite("deeper", "Deeper", "https://www.deeper.com", "/videos?search={query}")
                    9 -> addAdultSite("mofos", "Mofos", "https://www.mofos.com", "/search?q={query}")
                    10 -> addAdultSite("teamskeet", "TeamSkeet", "https://www.teamskeet.com", "/search?q={query}")
                    11 -> addAdultSite("babes", "Babes", "https://www.babes.com", "/search?q={query}")
                    12 -> addAdultSite("twistys", "Twistys", "https://www.twistys.com", "/search?q={query}")
                    13 -> addAdultSite("puretaboo", "PureTaboo", "https://www.puretaboo.com", "/search?q={query}")
                    14 -> addAdultSite("adulttime", "AdultTime", "https://www.adulttime.com", "/search?q={query}")
                    16 -> addAllPremiumAdultSites()
                }
            }
            .setNegativeButton("Back") { _, _ -> showAdultSitesListDialog() }
            .show()
    }
    
    private fun showInternationalAdultSites() {
        val sites = arrayOf(
            "🔞 JavHD (Japan)",
            "🔞 R18 (Japan)",
            "🔞 DMM (Japan)",
            "🔞 Txxx (Europe)",
            "🔞 Porn555 (Asia)",
            "🔞 Drtuber",
            "🔞 HClips",
            "🔞 Pornone",
            "🔞 Gotporn",
            "🔞 4tube",
            "──────────────",
            "⚡ Add All International"
        )
        
        AlertDialog.Builder(this)
            .setTitle("🌍 International Sites")
            .setItems(sites) { _, which ->
                when (which) {
                    0 -> addAdultSite("javhd", "JavHD", "https://www.javhd.com", "/search?q={query}")
                    1 -> addAdultSite("r18", "R18", "https://www.r18.com", "/videos/search/?q={query}")
                    2 -> addAdultSite("dmm", "DMM", "https://www.dmm.co.jp", "/search?q={query}")
                    3 -> addAdultSite("txxx", "Txxx", "https://www.txxx.com", "/search/?q={query}")
                    4 -> addAdultSite("porn555", "Porn555", "https://www.porn555.com", "/search/{query}")
                    5 -> addAdultSite("drtuber", "DrTuber", "https://www.drtuber.com", "/search/{query}")
                    6 -> addAdultSite("hclips", "HClips", "https://www.hclips.com", "/search/{query}")
                    7 -> addAdultSite("pornone", "Pornone", "https://www.pornone.com", "/search?q={query}")
                    8 -> addAdultSite("gotporn", "GotPorn", "https://www.gotporn.com", "/results?search={query}")
                    9 -> addAdultSite("4tube", "4tube", "https://www.4tube.com", "/search?q={query}")
                    11 -> addAllInternationalAdultSites()
                }
            }
            .setNegativeButton("Back") { _, _ -> showAdultSitesListDialog() }
            .show()
    }
    
    private fun showSpecialtyAdultSites() {
        val sites = arrayOf(
            "🔞 Porntrex",
            "🔞 Tnaflix",
            "🔞 Nudevista",
            "🔞 Pornmd",
            "🔞 Thumbzilla",
            "🔞 Fapster",
            "🔞 Fuq",
            "🔞 Xxxdan",
            "🔞 Sunporno",
            "🔞 Anyporn",
            "🔞 Porndig",
            "🔞 Sexvid",
            "🔞 Upornia",
            "🔞 Voyeurhit",
            "🔞 Analdin",
            "──────────────",
            "⚡ Add All Specialty"
        )
        
        AlertDialog.Builder(this)
            .setTitle("🔧 Specialty Sites")
            .setItems(sites) { _, which ->
                when (which) {
                    0 -> addAdultSite("porntrex", "Porntrex", "https://www.porntrex.com", "/search/{query}/")
                    1 -> addAdultSite("tnaflix", "TNAFlix", "https://www.tnaflix.com", "/search.php?what={query}")
                    2 -> addAdultSite("nudevista", "Nudevista", "https://www.nudevista.com", "/?q={query}")
                    3 -> addAdultSite("pornmd", "PornMD", "https://www.pornmd.com", "/{query}")
                    4 -> addAdultSite("thumbzilla", "Thumbzilla", "https://www.thumbzilla.com", "/video/search?search={query}")
                    5 -> addAdultSite("fapster", "Fapster", "https://www.fapster.xxx", "/search/{query}")
                    6 -> addAdultSite("fuq", "Fuq", "https://www.fuq.com", "/search/{query}/")
                    7 -> addAdultSite("xxxdan", "Xxxdan", "https://xxxdan.com", "/search/{query}")
                    8 -> addAdultSite("sunporno", "Sunporno", "https://www.sunporno.com", "/search/{query}/")
                    9 -> addAdultSite("anyporn", "Anyporn", "https://www.anyporn.com", "/search/?q={query}")
                    10 -> addAdultSite("porndig", "Porndig", "https://www.porndig.com", "/search/{query}")
                    11 -> addAdultSite("sexvid", "Sexvid", "https://www.sexvid.xxx", "/search/{query}")
                    12 -> addAdultSite("upornia", "Upornia", "https://www.upornia.com", "/search/{query}/")
                    13 -> addAdultSite("voyeurhit", "Voyeurhit", "https://www.voyeurhit.com", "/search/{query}")
                    14 -> addAdultSite("analdin", "Analdin", "https://www.analdin.com", "/search/{query}/")
                    16 -> addAllSpecialtyAdultSites()
                }
            }
            .setNegativeButton("Back") { _, _ -> showAdultSitesListDialog() }
            .show()
    }
    
    private fun addAdultSite(id: String, name: String, baseUrl: String, searchPath: String) {
        val config = VideoSiteConfig(
            id = "adult_$id",
            name = "$name (18+)",
            baseUrl = baseUrl,
            siteType = VideoSiteType.GENERIC,
            searchPath = searchPath,
            selectors = VideoSelectors(
                videoContainer = ".video-wrapper, .thumb-block, .mozaique .thumb, .video-box, .video-item, .thumb, .thumbs li",
                videoTitle = ".title a, .thumb-under a, a[title], .video-title, h3 a, .name",
                videoUrl = "a[href*='/video'], a[href*='/view_video'], a[href*='watch'], a[href*='/v/']",
                thumbnail = "img[data-src], img[src*='thumb'], .thumb img, img.lazy, img[data-original]",
                duration = ".duration, .video-duration, span.duration, .time, .length",
                views = ".views, .video-views, .metadata .views, .info .views"
            ),
            isAdult = true
        )
        
        videoService.saveSite(config)
        refreshSiteList()
        Toast.makeText(this, "Added: ${config.name}", Toast.LENGTH_SHORT).show()
    }
    
    private fun addAllPopularAdultSites() {
        addAdultSite("pornhub", "PornHub", "https://www.pornhub.com", "/video/search?search={query}")
        addAdultSite("xvideos", "XVideos", "https://www.xvideos.com", "/?k={query}")
        addAdultSite("xhamster", "xHamster", "https://xhamster.com", "/search/{query}")
        addAdultSite("xnxx", "XNXX", "https://www.xnxx.com", "/search/{query}")
        addAdultSite("youporn", "YouPorn", "https://www.youporn.com", "/search/?query={query}")
        addAdultSite("redtube", "RedTube", "https://www.redtube.com", "/?search={query}")
        addAdultSite("spankbang", "SpankBang", "https://spankbang.com", "/s/{query}/")
        addAdultSite("eporner", "Eporner", "https://www.eporner.com", "/search/{query}/")
        addAdultSite("tube8", "Tube8", "https://www.tube8.com", "/searches?q={query}")
        addAdultSite("beeg", "Beeg", "https://beeg.com", "/search?q={query}")
        Toast.makeText(this, "Added 10 popular adult sites", Toast.LENGTH_SHORT).show()
    }
    
    private fun addAllPremiumAdultSites() {
        addAdultSite("brazzers", "Brazzers", "https://www.brazzers.com", "/search?q={query}")
        addAdultSite("realitykings", "RealityKings", "https://www.realitykings.com", "/search?q={query}")
        addAdultSite("bangbros", "BangBros", "https://www.bangbros.com", "/search/{query}")
        addAdultSite("naughtyamerica", "NaughtyAmerica", "https://www.naughtyamerica.com", "/search?q={query}")
        addAdultSite("digitalplayground", "DigitalPlayground", "https://www.digitalplayground.com", "/search?q={query}")
        addAdultSite("blacked", "Blacked", "https://www.blacked.com", "/videos?search={query}")
        addAdultSite("tushy", "Tushy", "https://www.tushy.com", "/videos?search={query}")
        addAdultSite("vixen", "Vixen", "https://www.vixen.com", "/videos?search={query}")
        addAdultSite("deeper", "Deeper", "https://www.deeper.com", "/videos?search={query}")
        addAdultSite("mofos", "Mofos", "https://www.mofos.com", "/search?q={query}")
        addAdultSite("teamskeet", "TeamSkeet", "https://www.teamskeet.com", "/search?q={query}")
        addAdultSite("babes", "Babes", "https://www.babes.com", "/search?q={query}")
        addAdultSite("twistys", "Twistys", "https://www.twistys.com", "/search?q={query}")
        addAdultSite("puretaboo", "PureTaboo", "https://www.puretaboo.com", "/search?q={query}")
        addAdultSite("adulttime", "AdultTime", "https://www.adulttime.com", "/search?q={query}")
        Toast.makeText(this, "Added 15 premium studio sites", Toast.LENGTH_SHORT).show()
    }
    
    private fun addAllInternationalAdultSites() {
        addAdultSite("javhd", "JavHD", "https://www.javhd.com", "/search?q={query}")
        addAdultSite("r18", "R18", "https://www.r18.com", "/videos/search/?q={query}")
        addAdultSite("dmm", "DMM", "https://www.dmm.co.jp", "/search?q={query}")
        addAdultSite("txxx", "Txxx", "https://www.txxx.com", "/search/?q={query}")
        addAdultSite("porn555", "Porn555", "https://www.porn555.com", "/search/{query}")
        addAdultSite("drtuber", "DrTuber", "https://www.drtuber.com", "/search/{query}")
        addAdultSite("hclips", "HClips", "https://www.hclips.com", "/search/{query}")
        addAdultSite("pornone", "Pornone", "https://www.pornone.com", "/search?q={query}")
        addAdultSite("gotporn", "GotPorn", "https://www.gotporn.com", "/results?search={query}")
        addAdultSite("4tube", "4tube", "https://www.4tube.com", "/search?q={query}")
        Toast.makeText(this, "Added 10 international sites", Toast.LENGTH_SHORT).show()
    }
    
    private fun addAllSpecialtyAdultSites() {
        addAdultSite("porntrex", "Porntrex", "https://www.porntrex.com", "/search/{query}/")
        addAdultSite("tnaflix", "TNAFlix", "https://www.tnaflix.com", "/search.php?what={query}")
        addAdultSite("nudevista", "Nudevista", "https://www.nudevista.com", "/?q={query}")
        addAdultSite("pornmd", "PornMD", "https://www.pornmd.com", "/{query}")
        addAdultSite("thumbzilla", "Thumbzilla", "https://www.thumbzilla.com", "/video/search?search={query}")
        addAdultSite("fapster", "Fapster", "https://www.fapster.xxx", "/search/{query}")
        addAdultSite("fuq", "Fuq", "https://www.fuq.com", "/search/{query}/")
        addAdultSite("xxxdan", "Xxxdan", "https://xxxdan.com", "/search/{query}")
        addAdultSite("sunporno", "Sunporno", "https://www.sunporno.com", "/search/{query}/")
        addAdultSite("anyporn", "Anyporn", "https://www.anyporn.com", "/search/?q={query}")
        addAdultSite("porndig", "Porndig", "https://www.porndig.com", "/search/{query}")
        addAdultSite("sexvid", "Sexvid", "https://www.sexvid.xxx", "/search/{query}")
        addAdultSite("upornia", "Upornia", "https://www.upornia.com", "/search/{query}/")
        addAdultSite("voyeurhit", "Voyeurhit", "https://www.voyeurhit.com", "/search/{query}")
        addAdultSite("analdin", "Analdin", "https://www.analdin.com", "/search/{query}/")
        Toast.makeText(this, "Added 15 specialty sites", Toast.LENGTH_SHORT).show()
    }
    
    private fun addAllAdultSitesComplete() {
        val progress = ProgressDialog.show(this, "Adding Sites", "Adding 50+ adult video sites...", true)
        
        uiScope.launch(Dispatchers.IO) {
            // Popular (10)
            addAdultSiteSilent("pornhub", "PornHub", "https://www.pornhub.com", "/video/search?search={query}")
            addAdultSiteSilent("xvideos", "XVideos", "https://www.xvideos.com", "/?k={query}")
            addAdultSiteSilent("xhamster", "xHamster", "https://xhamster.com", "/search/{query}")
            addAdultSiteSilent("xnxx", "XNXX", "https://www.xnxx.com", "/search/{query}")
            addAdultSiteSilent("youporn", "YouPorn", "https://www.youporn.com", "/search/?query={query}")
            addAdultSiteSilent("redtube", "RedTube", "https://www.redtube.com", "/?search={query}")
            addAdultSiteSilent("spankbang", "SpankBang", "https://spankbang.com", "/s/{query}/")
            addAdultSiteSilent("eporner", "Eporner", "https://www.eporner.com", "/search/{query}/")
            addAdultSiteSilent("tube8", "Tube8", "https://www.tube8.com", "/searches?q={query}")
            addAdultSiteSilent("beeg", "Beeg", "https://beeg.com", "/search?q={query}")
            
            // Premium (15)
            addAdultSiteSilent("brazzers", "Brazzers", "https://www.brazzers.com", "/search?q={query}")
            addAdultSiteSilent("realitykings", "RealityKings", "https://www.realitykings.com", "/search?q={query}")
            addAdultSiteSilent("bangbros", "BangBros", "https://www.bangbros.com", "/search/{query}")
            addAdultSiteSilent("naughtyamerica", "NaughtyAmerica", "https://www.naughtyamerica.com", "/search?q={query}")
            addAdultSiteSilent("digitalplayground", "DigitalPlayground", "https://www.digitalplayground.com", "/search?q={query}")
            addAdultSiteSilent("blacked", "Blacked", "https://www.blacked.com", "/videos?search={query}")
            addAdultSiteSilent("tushy", "Tushy", "https://www.tushy.com", "/videos?search={query}")
            addAdultSiteSilent("vixen", "Vixen", "https://www.vixen.com", "/videos?search={query}")
            addAdultSiteSilent("deeper", "Deeper", "https://www.deeper.com", "/videos?search={query}")
            addAdultSiteSilent("mofos", "Mofos", "https://www.mofos.com", "/search?q={query}")
            addAdultSiteSilent("teamskeet", "TeamSkeet", "https://www.teamskeet.com", "/search?q={query}")
            addAdultSiteSilent("babes", "Babes", "https://www.babes.com", "/search?q={query}")
            addAdultSiteSilent("twistys", "Twistys", "https://www.twistys.com", "/search?q={query}")
            addAdultSiteSilent("puretaboo", "PureTaboo", "https://www.puretaboo.com", "/search?q={query}")
            addAdultSiteSilent("adulttime", "AdultTime", "https://www.adulttime.com", "/search?q={query}")
            
            // International (10)
            addAdultSiteSilent("javhd", "JavHD", "https://www.javhd.com", "/search?q={query}")
            addAdultSiteSilent("r18", "R18", "https://www.r18.com", "/videos/search/?q={query}")
            addAdultSiteSilent("dmm", "DMM", "https://www.dmm.co.jp", "/search?q={query}")
            addAdultSiteSilent("txxx", "Txxx", "https://www.txxx.com", "/search/?q={query}")
            addAdultSiteSilent("porn555", "Porn555", "https://www.porn555.com", "/search/{query}")
            addAdultSiteSilent("drtuber", "DrTuber", "https://www.drtuber.com", "/search/{query}")
            addAdultSiteSilent("hclips", "HClips", "https://www.hclips.com", "/search/{query}")
            addAdultSiteSilent("pornone", "Pornone", "https://www.pornone.com", "/search?q={query}")
            addAdultSiteSilent("gotporn", "GotPorn", "https://www.gotporn.com", "/results?search={query}")
            addAdultSiteSilent("4tube", "4tube", "https://www.4tube.com", "/search?q={query}")
            
            // Specialty (15)
            addAdultSiteSilent("porntrex", "Porntrex", "https://www.porntrex.com", "/search/{query}/")
            addAdultSiteSilent("tnaflix", "TNAFlix", "https://www.tnaflix.com", "/search.php?what={query}")
            addAdultSiteSilent("nudevista", "Nudevista", "https://www.nudevista.com", "/?q={query}")
            addAdultSiteSilent("pornmd", "PornMD", "https://www.pornmd.com", "/{query}")
            addAdultSiteSilent("thumbzilla", "Thumbzilla", "https://www.thumbzilla.com", "/video/search?search={query}")
            addAdultSiteSilent("fapster", "Fapster", "https://www.fapster.xxx", "/search/{query}")
            addAdultSiteSilent("fuq", "Fuq", "https://www.fuq.com", "/search/{query}/")
            addAdultSiteSilent("xxxdan", "Xxxdan", "https://xxxdan.com", "/search/{query}")
            addAdultSiteSilent("sunporno", "Sunporno", "https://www.sunporno.com", "/search/{query}/")
            addAdultSiteSilent("anyporn", "Anyporn", "https://www.anyporn.com", "/search/?q={query}")
            addAdultSiteSilent("porndig", "Porndig", "https://www.porndig.com", "/search/{query}")
            addAdultSiteSilent("sexvid", "Sexvid", "https://www.sexvid.xxx", "/search/{query}")
            addAdultSiteSilent("upornia", "Upornia", "https://www.upornia.com", "/search/{query}/")
            addAdultSiteSilent("voyeurhit", "Voyeurhit", "https://www.voyeurhit.com", "/search/{query}")
            addAdultSiteSilent("analdin", "Analdin", "https://www.analdin.com", "/search/{query}/")
            
            launch(Dispatchers.Main) {
                progress.dismiss()
                refreshSiteList()
                Toast.makeText(this@VideoSitesActivity, "✓ Added 50 adult video sites (18+)", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun addAdultSiteSilent(id: String, name: String, baseUrl: String, searchPath: String) {
        val config = VideoSiteConfig(
            id = "adult_$id",
            name = "$name (18+)",
            baseUrl = baseUrl,
            siteType = VideoSiteType.GENERIC,
            searchPath = searchPath,
            selectors = VideoSelectors(
                videoContainer = ".video-wrapper, .thumb-block, .mozaique .thumb, .video-box, .video-item, .thumb, .thumbs li",
                videoTitle = ".title a, .thumb-under a, a[title], .video-title, h3 a, .name",
                videoUrl = "a[href*='/video'], a[href*='/view_video'], a[href*='watch'], a[href*='/v/']",
                thumbnail = "img[data-src], img[src*='thumb'], .thumb img, img.lazy, img[data-original]",
                duration = ".duration, .video-duration, span.duration, .time, .length",
                views = ".views, .video-views, .metadata .views, .info .views"
            ),
            isAdult = true
        )
        videoService.saveSite(config)
    }
    
    // Keep old method for compatibility
    private fun addAllAdultSites() {
        addAllPopularAdultSites()
    }
    
    private fun showInvidiousInstancesDialog() {
        val instances = arrayOf(
            "yewtu.be (Germany)",
            "vid.puffyan.us (USA)",
            "invidious.snopyta.org (Finland)",
            "invidious.kavin.rocks (India)",
            "inv.riverside.rocks (USA)",
            "invidious.namazso.eu (Germany)",
            "invidio.xamh.de (Germany)",
            "inv.bp.projectsegfau.lt (France)",
            "invidious.osi.kr (South Korea)",
            "invidious.slipfox.xyz (USA)"
        )
        
        val instanceUrls = listOf(
            "https://yewtu.be",
            "https://vid.puffyan.us",
            "https://invidious.snopyta.org",
            "https://invidious.kavin.rocks",
            "https://inv.riverside.rocks",
            "https://invidious.namazso.eu",
            "https://invidio.xamh.de",
            "https://inv.bp.projectsegfau.lt",
            "https://invidious.osi.kr",
            "https://invidious.slipfox.xyz"
        )
        
        AlertDialog.Builder(this)
            .setTitle("🔗 Invidious Instances (Privacy YouTube)")
            .setItems(instances) { _, which ->
                val url = instanceUrls[which]
                val name = instances[which].substringBefore(" (")
                addInvidiousInstance(url, name)
            }
            .setNeutralButton("Add Custom") { _, _ ->
                showCustomInstanceDialog("Invidious")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showPeerTubeInstancesDialog() {
        val instances = arrayOf(
            "framatube.org (France)",
            "peertube.social (Germany)",
            "video.blender.org (Netherlands)",
            "tilvids.com (USA)",
            "tube.tchncs.de (Germany)",
            "diode.zone (France)",
            "videos.pair2jeux.tube (France)",
            "peertube.uno (Spain)",
            "video.liberta.vip (Italy)"
        )
        
        val instanceUrls = listOf(
            "https://framatube.org",
            "https://peertube.social",
            "https://video.blender.org",
            "https://tilvids.com",
            "https://tube.tchncs.de",
            "https://diode.zone",
            "https://videos.pair2jeux.tube",
            "https://peertube.uno",
            "https://video.liberta.vip"
        )
        
        AlertDialog.Builder(this)
            .setTitle("🔗 PeerTube Instances (Federated Video)")
            .setItems(instances) { _, which ->
                val url = instanceUrls[which]
                val name = instances[which].substringBefore(" (")
                addPeerTubeInstance(url, name)
            }
            .setNeutralButton("Add Custom") { _, _ ->
                showCustomInstanceDialog("PeerTube")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showCustomInstanceDialog(type: String) {
        val editText = EditText(this).apply {
            hint = "https://instance.example.com"
            setPadding(48, 32, 48, 32)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Add Custom $type Instance")
            .setMessage("Enter the base URL of the $type instance:")
            .setView(editText)
            .setPositiveButton("Add") { _, _ ->
                val url = editText.text.toString().trim()
                if (url.isNotEmpty()) {
                    deepDiscoverSite(url)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun addInvidiousInstance(url: String, name: String) {
        val config = VideoSiteConfig(
            id = "invidious_${name.lowercase().replace(".", "_")}",
            name = "Invidious ($name)",
            baseUrl = url,
            siteType = VideoSiteType.YOUTUBE,
            instanceUrl = url,
            apiEndpoint = "$url/api/v1/search",
            searchPath = "/search?q={query}"
        )
        
        videoService.saveSite(config)
        refreshSiteList()
        Toast.makeText(this, "Added: ${config.name}", Toast.LENGTH_SHORT).show()
    }
    
    private fun addPeerTubeInstance(url: String, name: String) {
        val config = VideoSiteConfig(
            id = "peertube_${name.lowercase().replace(".", "_")}",
            name = "PeerTube ($name)",
            baseUrl = url,
            siteType = VideoSiteType.PEERTUBE,
            instanceUrl = url,
            apiEndpoint = "$url/api/v1/search/videos",
            searchPath = "/search?search={query}"
        )
        
        videoService.saveSite(config)
        refreshSiteList()
        Toast.makeText(this, "Added: ${config.name}", Toast.LENGTH_SHORT).show()
    }
    
    private fun addPresetSite(type: VideoSiteType) {
        val config = when (type) {
            VideoSiteType.YOUTUBE -> VideoSiteConfig(
                id = "youtube",
                name = "YouTube",
                baseUrl = "https://www.youtube.com",
                siteType = VideoSiteType.YOUTUBE,
                searchPath = "/results?search_query={query}"
            )
            VideoSiteType.DAILYMOTION -> VideoSiteConfig(
                id = "dailymotion",
                name = "Dailymotion",
                baseUrl = "https://www.dailymotion.com",
                siteType = VideoSiteType.DAILYMOTION,
                apiEndpoint = "https://api.dailymotion.com/videos"
            )
            VideoSiteType.VIMEO -> VideoSiteConfig(
                id = "vimeo",
                name = "Vimeo",
                baseUrl = "https://vimeo.com",
                siteType = VideoSiteType.VIMEO,
                searchPath = "/search?q={query}"
            )
            VideoSiteType.RUMBLE -> VideoSiteConfig(
                id = "rumble",
                name = "Rumble",
                baseUrl = "https://rumble.com",
                siteType = VideoSiteType.RUMBLE,
                searchPath = "/search/video?q={query}"
            )
            VideoSiteType.ODYSEE -> VideoSiteConfig(
                id = "odysee",
                name = "Odysee",
                baseUrl = "https://odysee.com",
                siteType = VideoSiteType.ODYSEE,
                apiEndpoint = "https://lighthouse.odysee.com/search"
            )
            VideoSiteType.BITCHUTE -> VideoSiteConfig(
                id = "bitchute",
                name = "BitChute",
                baseUrl = "https://www.bitchute.com",
                siteType = VideoSiteType.BITCHUTE,
                searchPath = "/search/?query={query}&kind=video"
            )
            VideoSiteType.ARCHIVE_ORG -> VideoSiteConfig(
                id = "archive_org",
                name = "Internet Archive",
                baseUrl = "https://archive.org",
                siteType = VideoSiteType.ARCHIVE_ORG,
                apiEndpoint = "https://archive.org/advancedsearch.php"
            )
            else -> return
        }
        
        videoService.saveSite(config)
        refreshSiteList()
        Toast.makeText(this, "Added: ${config.name}", Toast.LENGTH_SHORT).show()
    }
    
    private fun addAllPresets() {
        listOf(
            VideoSiteType.YOUTUBE,
            VideoSiteType.DAILYMOTION,
            VideoSiteType.VIMEO,
            VideoSiteType.RUMBLE,
            VideoSiteType.ODYSEE,
            VideoSiteType.BITCHUTE,
            VideoSiteType.ARCHIVE_ORG
        ).forEach { addPresetSite(it) }
        
        Toast.makeText(this, "Added all preset video sites", Toast.LENGTH_SHORT).show()
    }
    
    private fun confirmDelete(site: VideoSiteConfig) {
        AlertDialog.Builder(this)
            .setTitle("Delete Site")
            .setMessage("Remove ${site.name}?")
            .setPositiveButton("Delete") { _, _ ->
                videoService.removeSite(site.id)
                refreshSiteList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun testSite(site: VideoSiteConfig) {
        val progress = ProgressDialog.show(this, "Testing", "Testing ${site.name}...", true)
        
        uiScope.launch(Dispatchers.IO) {
            try {
                val results = videoService.searchSite(site, "test", 5)
                
                launch(Dispatchers.Main) {
                    progress.dismiss()
                    
                    if (results.isNotEmpty()) {
                        Toast.makeText(
                            this@VideoSitesActivity,
                            "✓ ${site.name}: ${results.size} results",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@VideoSitesActivity,
                            "✗ ${site.name}: No results",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    progress.dismiss()
                    Toast.makeText(
                        this@VideoSitesActivity,
                        "✗ ${site.name}: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun testAllSites() {
        val sites = videoService.getAllSites()
        if (sites.isEmpty()) {
            Toast.makeText(this, "No sites to test", Toast.LENGTH_SHORT).show()
            return
        }
        
        val progress = ProgressDialog(this).apply {
            setTitle("Testing Sites")
            setMessage("Testing ${sites.size} sites...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = sites.size
            show()
        }
        
        uiScope.launch(Dispatchers.IO) {
            val results = mutableMapOf<String, Boolean>()
            
            sites.forEachIndexed { index, site ->
                launch(Dispatchers.Main) {
                    progress.progress = index + 1
                    progress.setMessage("Testing ${site.name}...")
                }
                
                try {
                    val searchResults = videoService.searchSite(site, "test", 3)
                    results[site.name] = searchResults.isNotEmpty()
                } catch (e: Exception) {
                    results[site.name] = false
                }
            }
            
            launch(Dispatchers.Main) {
                progress.dismiss()
                
                val successful = results.count { it.value }
                val message = buildString {
                    appendLine("Test Results: $successful/${sites.size} working")
                    appendLine()
                    results.forEach { (name, success) ->
                        appendLine("${if (success) "✓" else "✗"} $name")
                    }
                }
                
                AlertDialog.Builder(this@VideoSitesActivity)
                    .setTitle("Test Results")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
    
    // =================== Adapter ===================
    
    inner class VideoSiteAdapter(
        private val onToggle: (VideoSiteConfig, Boolean) -> Unit,
        private val onDelete: (VideoSiteConfig) -> Unit,
        private val onTest: (VideoSiteConfig) -> Unit
    ) : RecyclerView.Adapter<VideoSiteAdapter.ViewHolder>() {
        
        private var sites = listOf<VideoSiteConfig>()
        
        fun updateData(newSites: List<VideoSiteConfig>) {
            sites = newSites
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_video_site, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(sites[position])
        }
        
        override fun getItemCount() = sites.size
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textName = itemView.findViewById<android.widget.TextView>(R.id.textSiteName)
            private val textType = itemView.findViewById<android.widget.TextView>(R.id.textSiteType)
            private val textUrl = itemView.findViewById<android.widget.TextView>(R.id.textSiteUrl)
            private val switchEnabled = itemView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchEnabled)
            private val buttonTest = itemView.findViewById<android.widget.ImageButton>(R.id.buttonTest)
            private val buttonDelete = itemView.findViewById<android.widget.ImageButton>(R.id.buttonDelete)
            
            fun bind(site: VideoSiteConfig) {
                textName.text = site.name
                textType.text = site.siteType.name
                textUrl.text = site.baseUrl
                
                switchEnabled.setOnCheckedChangeListener(null)
                switchEnabled.isChecked = site.isEnabled
                switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                    onToggle(site, isChecked)
                }
                
                buttonTest.setOnClickListener { onTest(site) }
                buttonDelete.setOnClickListener { onDelete(site) }
            }
        }
    }
}
