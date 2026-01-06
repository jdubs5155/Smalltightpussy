# JackettProwlarrClient v1.5.0 Release Notes

**Release Date:** January 6, 2026  
**Build Size:** 7.0MB  
**Target Android:** 8.0+ (SDK 26)

---

## 🎉 Major Features

### 1. **70 Built-in Torrent Providers**
Expanded from 65 to 70 providers with comprehensive coverage across all categories:
- **16 Public Indexers:** 1337x, The Pirate Bay, YTS, EZTV, Torlock, SolidTorrents, GloTorrents, LimeTorrents, Nyaa, TorrentGalaxy, Kickass, Zooqle, RARBG Mirror, Torrentz2, TorrentFunk, TorrentDownloads
- **8 Private Trackers:** IPTorrents, TorrentLeech, AlphaRatio, BroadcastTheNet, PassThePopcorn, RED, Orpheus, AnimeBytes
- **16 Adult Content Sites:** Empornium, PornLeech, Sukebei, and 13 more specialized adult trackers
- **8 International Sites:** Rutracker, Torrent9, MagnetDL, Glodls, IsoHunt, Demonoid, BTDigg, TorrentProject
- **18 Specialized DHT Engines:** TorrentGuru, Snowfl, BitSearch, Idope, TorrentAPI, and 13 more meta-search engines

### 2. **Editable Host Settings**
Complete flexibility for Jackett/Prowlarr server configuration:
- ✨ Edit Jackett URL and API key in Settings
- ✨ Edit Prowlarr URL and API key in Settings
- ✨ Comprehensive setup instructions with examples
- ✨ Support for custom ports and network configurations
- ✨ Automatic service reload when returning from Settings

### 3. **Enhanced Configuration UI**
User-friendly Settings screen with helpful guidance:
- 📋 Step-by-step instructions for finding API keys
- 📋 Multiple URL format examples (same network, localhost, custom ports)
- 📋 Visual formatting with bold labels and helper text
- 📋 Real-world examples you can copy and modify

### 4. **IndexerImporter System**
Mass import individual indexers from Jackett/Prowlarr:
- 🔄 Import from Jackett, Prowlarr, or both simultaneously
- 🔄 Each indexer gets its own toggle switch
- 🔄 Per-indexer Torznab URLs with independent API keys
- 🔄 Access via Settings → "Import Indexers from Jackett/Prowlarr"
- 🔄 Manage imported indexers in dedicated management screen

### 5. **"All Sources" Default Search**
Comprehensive search across everything:
- 🔍 Searches Jackett API → Prowlarr API → Imported Indexers → Built-in Providers → Custom Scrapers → Onion Sites
- 🔍 Set as default option in source dropdown (position 0)
- 🔍 Resilient error handling - continues even if some sources fail
- 🔍 Results aggregated and sorted by seeders

---

## 🐛 Bug Fixes

### Fixed Prowlarr Connection Issues
- ✅ **API Path Detection:** Prowlarr now correctly uses `/api/v1/search` instead of Jackett's `/api/v2.0/indexers/all/results/torznab/api`
- ✅ **Auto-Detection:** Service type detection by port (9696) or URL substring with fallback test request
- ✅ **Error Handling:** Better connection status messages in MainActivity

### LibreTorrent Prioritization
- ✅ Moved LibreTorrent to first position in torrent client detection
- ✅ Auto-transfers to LibreTorrent when available

---

## 🔧 Technical Improvements

### Architecture Enhancements
- **SharedPreferences Configuration:** Runtime host settings stored persistently
- **Service Reload Pattern:** MainActivity.onResume() automatically reloads services after Settings changes
- **Fallback Chain:** 6-tier search cascade with independent try-catch blocks per source
- **Provider Registry:** Centralized 70-provider registry with category management

### Performance Optimizations
- **Parallel Searches:** Coroutines launch searches across multiple sources simultaneously
- **Error Isolation:** Failed sources don't block other searches
- **Result Deduplication:** Aggregator removes duplicate torrents by info hash

### Code Quality
- **Kotlin Coroutines:** All network operations on Dispatchers.IO
- **ViewBinding:** Modern view access pattern
- **OkHttp 4.12:** Latest HTTP client with connection pooling
- **Gson 2.10:** Fast JSON serialization for settings persistence

---

## 📦 Installation

### From GitHub Release (Recommended)
1. Navigate to: https://github.com/zimbiss/JackettProwlarrClient/releases/tag/v1.5.0
2. Download `app-debug.apk` from Assets
3. Install on Android device (enable "Install from Unknown Sources")

### Via GitHub Actions Artifacts
1. Visit: https://github.com/zimbiss/JackettProwlarrClient/actions
2. Select latest successful "Build APK" workflow
3. Download `JackettProwlarr-debug-apk` artifact (90-day retention)

### Build from Source
```bash
git clone https://github.com/zimbiss/JackettProwlarrClient.git
cd JackettProwlarrClient
git checkout v1.5.0
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## 🚀 Configuration Guide

### First-Time Setup

#### 1. Configure Jackett (Optional)
Open app → Settings → Jackett Configuration:
- **Jackett URL:** Your server URL with port (e.g., `http://192.168.1.175:9117`)
  - Same network: `http://<PC_IP>:9117`
  - Localhost (if on same device): `http://localhost:9117`
  - Custom port: `http://<IP>:<PORT>`
- **API Key:** Found in Jackett web interface (top-right corner → API Key button)
  - Example: `sfbizvj42r5h41a2aojb2t29zouqgd3s`

#### 2. Configure Prowlarr (Optional)
Open app → Settings → Prowlarr Configuration:
- **Prowlarr URL:** Default port is 9696 (e.g., `http://192.168.1.175:9696`)
  - Same network: `http://<PC_IP>:9696`
  - Localhost: `http://localhost:9696`
  - Custom port: `http://<IP>:<PORT>`
- **API Key:** Prowlarr → Settings → General → Security → API Key
  - Example: `11e5676f4c3444479cea3671a6c0c55b`

#### 3. Import Indexers (Optional)
If you want per-indexer control:
1. Settings → "Import Indexers from Jackett/Prowlarr"
2. Choose source: Jackett, Prowlarr, or Both
3. Wait for import to complete
4. Settings → "Manage Indexers" to toggle individual indexers

#### 4. Enable Built-in Providers (Already Active)
Built-in providers work without any configuration:
- 13 public trackers enabled by default
- No API keys or servers needed
- Works offline
- Settings → "Manage Built-in Providers (70+)" to customize

### Search Configuration

**Recommended:** Use "All Sources (Everything)" dropdown option
- Searches: Jackett + Prowlarr + Imported Indexers + 70 Built-in Providers + Custom Sites
- Most comprehensive results
- Automatic fallback if servers unavailable

**Alternative Options:**
- **Jackett:** Only Jackett-configured indexers
- **Prowlarr:** Only Prowlarr-configured indexers

---

## 🔒 Privacy & Security

### Network Requirements
- Jackett/Prowlarr: Local network or VPN access to server
- Built-in Providers: Direct HTTPS connections to public sites
- Tor Sites: Requires Orbot installed and running
- No tracking or analytics - all searches local/direct

### Permissions Required
- **Internet:** Search torrent sites and APIs
- **Network State:** Check connection status
- **Storage:** Save download history and settings

### Data Storage
All data stored locally in SharedPreferences:
- API credentials (plain text - secure your device)
- Download history (last 100 items)
- Imported indexer configurations
- Custom site selectors

---

## 🎯 Usage Tips

### Maximizing Results
1. **Enable everything:** Use "All Sources" for most torrents
2. **Import indexers:** Get per-site control with Import feature
3. **Configure qBittorrent:** Set up qBittorrent Web UI in Settings for one-tap downloads
4. **Check connection:** Status line shows Jackett/Prowlarr availability

### Troubleshooting

**"Prowlarr unreachable" but it's running:**
- Verify URL has correct port (9696 not 9117)
- Check Settings shows correct Prowlarr URL
- Test in browser: `http://<YOUR_IP>:9696/api/v1/search?query=test`

**No results from built-in providers:**
- Check internet connection
- Try "All Sources" dropdown option
- Some sites may be blocked by ISP (use VPN)

**Imported indexers not searching:**
- Settings → Manage Indexers → verify enabled
- Check original Jackett/Prowlarr still has indexers configured

**LibreTorrent not auto-opening:**
- Ensure LibreTorrent installed
- App detects: LibreTorrent → uTorrent → Flud → tTorrent → qBittorrent (Android)

---

## 📝 Changelog

### v1.5.0 (2026-01-06)
- **Added:** 5 new providers (Torlock, GloTorrents, SolidTorrents, TorrentGuru, Snowfl) → Total 70
- **Added:** Editable Jackett/Prowlarr URLs and API keys in Settings
- **Added:** Comprehensive configuration instructions with examples
- **Added:** IndexerImporter for mass importing from Jackett/Prowlarr
- **Added:** "All Sources" default search option with 6-tier fallback
- **Fixed:** Prowlarr API path detection (now uses `/api/v1/search`)
- **Fixed:** LibreTorrent prioritization in client list
- **Improved:** Settings UI with helper text and real-world examples
- **Improved:** Automatic service reload on Settings changes (onResume pattern)
- **Improved:** Resilient error handling with per-source try-catch isolation

---

## 🔗 Links

- **Repository:** https://github.com/zimbiss/JackettProwlarrClient
- **Issues:** https://github.com/zimbiss/JackettProwlarrClient/issues
- **Releases:** https://github.com/zimbiss/JackettProwlarrClient/releases
- **Actions:** https://github.com/zimbiss/JackettProwlarrClient/actions

---

## 👥 Credits

**Developer:** zimbiss  
**License:** MIT (check repository for details)  
**Built with:** Kotlin, OkHttp, Gson, Jsoup, AndroidX

---

## 🐛 Known Issues

- Some private trackers require authentication (templates included for reference)
- Adult content providers not filtered (app targets technical users)
- Download history limited to 100 items (configurable in future versions)
- API credentials stored unencrypted (secure your device with lock screen)

---

## 🔮 Future Plans

- [ ] Custom categories for provider organization
- [ ] Download speed tracking via qBittorrent API
- [ ] Enhanced Tor integration with circuit selection
- [ ] Provider health monitoring and auto-disable
- [ ] Export/import settings for backup
- [ ] Material 3 UI redesign
- [ ] Release signed APK builds

---

**Enjoy comprehensive torrent searching with 70+ providers! 🚀**
