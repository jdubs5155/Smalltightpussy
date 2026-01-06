# JackettProwlarrClient - AI Coding Agent Instructions

## Project Overview
Android BitTorrent client that searches torrents via **Torznab API** (Jackett/Prowlarr), custom web scrapers, and Tor-enabled .onion sites. Uses Kotlin with coroutines for async operations.

## Architecture

### Core Components
- **TorznabService**: Torznab API client for Jackett/Prowlarr - handles search, TV/movie queries, capabilities detection
- **ScraperService**: HTML scraping engine using Jsoup - extracts torrent data from custom sites via CSS/XPath selectors
- **TorrentAggregator**: Orchestrates parallel searches across multiple sources (Torznab + scrapers + onion sites)
- **TorProxyManager**: SOCKS proxy manager for Orbot/Tor integration to access .onion sites
- **QbittorrentClient**: qBittorrent Web UI API client for sending torrents to remote clients
- **DownloadHistoryManager**: JSON-persisted download tracking (last 100 downloads)

### Data Flow
1. User enters search query in MainActivity
2. Query flows to TorznabService (Jackett/Prowlarr) OR TorrentAggregator (multi-source)
3. Results parsed into `TorrentResult` objects with health metrics (seeders/leechers ratio)
4. TorrentAdapter displays results in RecyclerView with color-coded health status
5. Download via qBittorrent API or intent to local torrent clients (uTorrent, LibreTorrent, etc.)

### Provider System
- **IndexerProvider interface**: Base for all torrent site providers
- **Concrete providers**: PublicIndexers.kt, PrivateIndexers.kt, InternationalIndexers.kt
- **CustomSiteConfig**: JSON-based scraper definitions stored in `custom_sites.json`
- **GitHubScraperSync**: Auto-sync scraper configs from GitHub repos

## Critical Conventions

### API Configuration (MainActivity.kt)
Hard-coded API keys and URLs are at class level - **change these for local development**:
```kotlin
private val JACKETT_BASE_URL = "http://192.168.1.175:9117"
private val JACKETT_API_KEY = "sfbizvj42r5h41a2aojb2t29zouqgd3s"
private val PROWLARR_BASE_URL = "http://192.168.1.175:9696"
private val PROWLARR_API_KEY = "11e5676f4c3444479cea3671a6c0c55b"
```

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

### Testing Network Features
- **Local Jackett/Prowlarr**: Must be running on accessible network address
- **qBittorrent testing**: Enable in SettingsActivity, requires Web UI credentials
- **Tor/.onion sites**: Install Orbot app on device/emulator first

### Debugging Torznab Issues
Check connection status in MainActivity status text. Common issues:
- `HTTP 401`: Invalid API key
- `HTTP 403`: API key missing or wrong format
- Empty results: Check Torznab caps with `service.getCapabilities()` first

### Adding Custom Scrapers
1. Create `CustomSiteConfig` with CSS/XPath selectors in [CustomSiteConfig.kt](app/src/main/java/com/zim/jackettprowler/CustomSiteConfig.kt)
2. Test selectors with ScraperService in isolation
3. Add rate limiting (default 1000ms between requests)
4. For .onion sites, set `requiresTor: true` and `isOnionSite: true`

## Integration Points

### External Dependencies
- **OkHttp**: All HTTP requests - configured with 30s timeouts in TorznabService
- **Gson**: JSON persistence (SharedPreferences) - DownloadHistoryManager, CustomSiteManager
- **Jsoup**: HTML parsing for web scrapers
- **Orbot (optional)**: Tor proxy for .onion sites - checks via `TorProxyManager.isTorAvailable()`

### Android Intents
Magnet links open via `Intent.ACTION_VIEW` to find installed torrent clients. Priority chain:
1. qBittorrent (if configured)
2. Installed apps matching `magnet:` intent filter
3. Fallback to web browser

### Storage
- **SharedPreferences**: Settings, download history, Tor config, custom sites
- **No Room/SQLite**: All persistence is JSON via Gson
- Location: `context.getSharedPreferences("download_history", MODE_PRIVATE)`

## Key Files Reference
- [TorznabService.kt](app/src/main/java/com/zim/jackettprowler/TorznabService.kt) - Torznab API implementation
- [TorrentAggregator.kt](app/src/main/java/com/zim/jackettprowler/TorrentAggregator.kt) - Multi-source search orchestration
- [MainActivity.kt](app/src/main/java/com/zim/jackettprowler/MainActivity.kt) - Main search UI and lifecycle
- [ScraperService.kt](app/src/main/java/com/zim/jackettprowler/ScraperService.kt) - HTML scraping engine
- [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) - Detailed feature changelog

## Common Pitfalls
- **Don't parse XML manually in Activities** - use TorznabService layer
- **Always sort results by seeders descending** - users expect best health first
- **Rate limit scraper requests** - default 1000ms minimum between same-site requests
- **Test with actual Jackett/Prowlarr instances** - XML structure varies by version
- **Magnet links vs download URLs** - check `isMagnetLink()` before choosing download method
