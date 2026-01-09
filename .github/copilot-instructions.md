# JackettProwlarrClient - AI Coding Agent Instructions

## Project Overview
Android BitTorrent client that searches torrents via **Torznab API** (Jackett/Prowlarr), custom web scrapers, and Tor-enabled .onion sites. Uses Kotlin with coroutines for async operations. **NEW**: Supports importing individual indexers from Jackett/Prowlarr, includes 60+ built-in torrent providers as fallback, and now has **clearnet video search** with support for 10+ video platforms.

## Architecture

### Core Components
- **TorznabService**: Torznab API client for Jackett/Prowlarr - auto-detects API paths (/api/v1/search for Prowlarr, /api/v2.0 for Jackett)
- **IndexerImporter**: Imports individual indexers from Jackett/Prowlarr with their unique Torznab URLs - allows per-indexer toggling
- **ProviderRegistry**: 60+ built-in torrent providers (1337x, TPB, YTS, EZTV, Nyaa, etc.) - works without Jackett/Prowlarr
- **ScraperService**: HTML scraping engine using Jsoup - extracts torrent data from custom sites via CSS/XPath selectors
- **TorrentAggregator**: Orchestrates parallel searches across: Jackett/Prowlarr APIs → Imported indexers → Built-in providers → Custom scrapers → Onion sites
- **TorProxyManager**: SOCKS proxy manager for Orbot/Tor integration to access .onion sites
- **QbittorrentClient**: qBittorrent Web UI API client for sending torrents to remote clients
- **DownloadHistoryManager**: JSON-persisted download tracking (last 100 downloads)
- **VideoSearchService**: Clearnet video search with 10+ platforms (YouTube via Invidious, Dailymotion, Vimeo, Rumble, Odysee, BitChute, PeerTube, Archive.org, Twitch)
- **VideoSiteInfiltrator**: Auto-detects and configures video sites from URL (like torrent site infiltration)

### Data Flow
1. User enters search query in MainActivity
2. **Mode Toggle**: Torrents mode → TorznabService/TorrentAggregator | Videos mode → VideoSearchService
3. For torrents: If Torznab APIs unavailable, searches fallback to: imported indexers → built-in providers → custom scrapers
4. For videos: Searches all enabled video sites in parallel
5. Results parsed into `TorrentResult` or `VideoResult` objects
6. TorrentAdapter/VideoResultAdapter displays results in RecyclerView
7. Torrent download via qBittorrent API or intent | Video opens in browser or compatible app

### Indexer Management System
- **Import from Jackett/Prowlarr**: SettingsActivity → "Import Indexers" button fetches all configured indexers
- **Per-indexer Torznab URLs**: Each indexer gets its own Torznab endpoint (e.g., `/api/v2.0/indexers/1337x/results/torznab`)
- **Toggle system**: IndexerManagementActivity allows enabling/disabling individual imported indexers
- **Built-in providers**: 60+ providers in ProviderRegistry - enabled by default for public trackers
- **Storage**: Imported indexers saved to SharedPreferences as JSON via IndexerImporter

### Video Search System (NEW)
- **VideoSiteConfig**: Data class for video site configuration with selectors
- **VideoSiteType**: Enum for known platforms (YOUTUBE, DAILYMOTION, VIMEO, RUMBLE, ODYSEE, BITCHUTE, PEERTUBE, ARCHIVE_ORG, TWITCH, GENERIC)
- **VideoSearchService**: Searches configured video sites in parallel, handles API/scraping differences per platform
- **VideoSiteInfiltrator**: Analyzes URLs to auto-detect video site type and create configurations
- **VideoSitesActivity**: UI to manage video sites - add, delete, test, toggle enabled state
- **Storage**: Video sites saved to `video_sites` SharedPreferences as JSON

## Critical Conventions

### API Configuration (MainActivity.kt)
Hard-coded API keys and URLs are at class level - **change these for local development**. Now also saved to SharedPreferences for SettingsActivity access:
```kotlin
private val JACKETT_BASE_URL = "http://192.168.1.175:9117"
private val JACKETT_API_KEY = "sfbizvj42r5h41a2aojb2t29zouqgd3s"
private val PROWLARR_BASE_URL = "http://192.168.1.175:9696"
private val PROWLARR_API_KEY = "11e5676f4c3444479cea3671a6c0c55b"
```

### Prowlarr vs Jackett API Paths
**CRITICAL FIX**: TorznabService now auto-detects API type:
- Prowlarr uses: `/api/v1/search`
- Jackett uses: `/api/v2.0/indexers/all/results/torznab/api`
Detection via port (9696) or URL substring, with fallback test request.

### TorrentResult Health Calculation
Health status uses seeder thresholds (see [TorrentResult.kt](app/src/main/java/com/zim/jackettprowler/TorrentResult.kt)):
- `≥50 seeders` = Excellent (green)
- `≥20 seeders` = Good (light green)
- `≥5 seeders` = Fair (yellow)
- `<5 seeders` = Poor (orange)
- `0 seeders` = Dead (red)

**When modifying health logic, update both `getHealthStatus()` and `getHealthColor()` methods together.**

### Coroutine Pattern
All network operations use `CoroutineScope(Dispatchers.Main + job)` in Activities:
```kotlin
uiScope.launch(Dispatchers.IO) {
    val results = service.search(query)
    launch(Dispatchers.Main) {
        adapter?.updateData(results)
    }
}
```
**Never block Main thread** - wrap OkHttp calls in `Dispatchers.IO`.

### XML Parsing (Torznab)
TorznabService uses XmlPullParser for RSS/Atom feeds. Handles Torznab namespace attributes:
```kotlin
parser.getAttributeValue(torznabNamespace, "attr")
```
**Test with both Jackett AND Prowlarr** - they format XML differently.

## Development Workflows

### Build & Install
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```
Requires Android SDK 26+ (minSdk), targets SDK 34.

### Testing Indexer Import
1. Ensure Jackett/Prowlarr running and accessible on network
2. Open app → Settings → "Import Indexers from Jackett/Prowlarr"
3. Choose import source (Jackett, Prowlarr, or Both)
4. Verify indexers appear in "Manage Indexers" with toggle switches
5. Test search with "All Sources" to use imported + built-in providers

### Testing Built-in Providers
- Settings → "Manage Built-in Providers (60+)" shows provider count
- "Enable All Public" activates 13 public trackers by default
- Private trackers require authentication (included for reference only)
- Built-in providers work offline - no Jackett/Prowlarr needed

### Debugging Connection Issues
Check connection status in MainActivity status text. Common issues:
- `HTTP 401/403`: Invalid API key - check SharedPreferences values
- "Prowlarr unreachable": Wrong API path - verify `/api/v1/search` detection
- Empty results with imported indexers: Check per-indexer toggle state in IndexerManagementActivity
- Built-in providers not searching: Verify enabled in `builtin_providers` SharedPreferences

### Testing Video Search
1. Toggle to "Videos" mode using the circle buttons in MainActivity
2. Settings → "Manage Video Sites (10+)" to add/configure video sites
3. Add sites by URL - VideoSiteInfiltrator auto-detects type
4. "Test All Sites" verifies connectivity and search functionality
5. Search returns VideoResult objects with title, channel, duration, views, thumbnail
6. Click video to open in browser or compatible video app

### Adding Custom Video Sites
1. Go to Settings → "Manage Video Sites (10+)"
2. Enter site URL (e.g., `https://yewtu.be` for Invidious instance)
3. VideoSiteInfiltrator auto-detects site type and creates config
4. For unknown sites, GENERIC type uses CSS selectors for scraping
5. Test site with "Test" button before enabling

### Adding Custom Scrapers
1. Create `CustomSiteConfig` with CSS/XPath selectors in [CustomSiteConfig.kt](app/src/main/java/com/zim/jackettprowler/CustomSiteConfig.kt)
2. Test selectors with ScraperService in isolation
3. Add rate limiting (default 1000ms between requests)
4. For .onion sites, set `requiresTor: true` and `isOnionSite: true`

## Integration Points

### External Dependencies
- **OkHttp**: All HTTP requests - configured with 30s timeouts in TorznabService
- **Gson**: JSON persistence (SharedPreferences) - DownloadHistoryManager, CustomSiteManager, IndexerImporter
- **Jsoup**: HTML parsing for web scrapers
- **Orbot (optional)**: Tor proxy for .onion sites - checks via `TorProxyManager.isTorAvailable()`

### Android Intents
Magnet links open via `Intent.ACTION_VIEW` to find installed torrent clients. Priority chain:
1. qBittorrent (if configured)
2. Installed apps matching `magnet:` intent filter
3. Fallback to web browser

### Storage
- **SharedPreferences**: Settings, download history, Tor config, custom sites, imported indexers, built-in provider toggles
- **No Room/SQLite**: All persistence is JSON via Gson
- Locations:
  - `prefs`: Main settings, API credentials
  - `imported_indexers`: Imported Jackett/Prowlarr indexers
  - `builtin_providers`: Enabled provider IDs
  - `download_history`: Recent downloads

## Key Files Reference
- [TorznabService.kt](app/src/main/java/com/zim/jackettprowler/TorznabService.kt) - Torznab API with Prowlarr/Jackett detection
- [IndexerImporter.kt](app/src/main/java/com/zim/jackettprowler/IndexerImporter.kt) - Import individual indexers from Jackett/Prowlarr
- [ProviderRegistry.kt](app/src/main/java/com/zim/jackettprowler/providers/ProviderRegistry.kt) - 60+ built-in providers
- [TorrentAggregator.kt](app/src/main/java/com/zim/jackettprowler/TorrentAggregator.kt) - Multi-source search with fallback chain
- [MainActivity.kt](app/src/main/java/com/zim/jackettprowler/MainActivity.kt) - Main search UI with Torrent/Video mode toggle
- [SettingsActivity.kt](app/src/main/java/com/zim/jackettprowler/SettingsActivity.kt) - Import button, provider management, video sites
- [ScraperService.kt](app/src/main/java/com/zim/jackettprowler/ScraperService.kt) - HTML scraping engine
- [VideoSearchService.kt](app/src/main/java/com/zim/jackettprowler/video/VideoSearchService.kt) - Video site search with 10+ platforms
- [VideoSiteInfiltrator.kt](app/src/main/java/com/zim/jackettprowler/video/VideoSiteInfiltrator.kt) - Auto-detect video site type from URL
- [VideoSitesActivity.kt](app/src/main/java/com/zim/jackettprowler/video/VideoSitesActivity.kt) - Manage video sites
- [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) - Detailed feature changelog

## Common Pitfalls
- **Prowlarr API path wrong**: Use `/api/v1/search` not `/api/v2.0/indexers...`
- **Imported indexers not searchable**: Check `isEnabled` flag in IndexerImporter storage
- **Built-in providers always searching**: Disable via `builtin_providers` SharedPreferences
- **Don't parse XML manually in Activities** - use TorznabService layer
- **Always sort results by seeders descending** - users expect best health first
- **Rate limit scraper requests** - default 1000ms minimum between same-site requests
- **Test with actual Jackett/Prowlarr instances** - XML structure varies by version
- **Magnet links vs download URLs** - check `isMagnetLink()` before choosing download method
- **Per-indexer API keys**: Imported indexers inherit parent API key - store in `ImportedIndexer.apiKey`
- **Video mode confusion**: When `isVideoMode=true`, use VideoSearchService not TorznabService
- **YouTube via Invidious**: Direct YouTube API requires API key; use Invidious instances for free access
- **AlertDialog setMessage+setItems conflict**: Use only setItems() OR setMessage(), not both together
