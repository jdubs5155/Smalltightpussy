# Update Summary - January 6, 2026

## 🎯 Issues Fixed & Features Added

### ✅ Fixed Prowlarr Connection Issue
**Problem**: Prowlarr was showing "no connection" even when running on the network.

**Solution**: 
- Fixed API path detection in `TorznabService.kt`
- Prowlarr now correctly uses `/api/v1/search` 
- Jackett uses `/api/v2.0/indexers/all/results/torznab/api`
- Auto-detection via port 9696 and URL substring matching

### ✅ Source Selection - "All Sources" Option
**Requested**: A 3rd dropdown option that searches everything.

**Implemented**:
- **Dropdown options** (in order):
  1. **"All Sources (Everything)"** - DEFAULT - Searches all available sources
  2. "Jackett" - Searches only Jackett
  3. "Prowlarr" - Searches only Prowlarr

**How it works**:
- When "All Sources" is selected, the app searches in this order:
  1. Jackett API (if connected)
  2. Prowlarr API (if connected)
  3. Imported indexers from Jackett/Prowlarr
  4. Built-in providers (60+ sites)
  5. Custom scrapers
  6. .onion sites (if Tor enabled)

**Resilience**:
- Each source is independent
- If Jackett fails → continues with Prowlarr
- If Prowlarr fails → continues with built-in providers
- If one scraper fails → continues with others
- Results are combined and deduplicated
- Status shows successful sources (e.g., "✓ 4/6 sources | 127 results")

### ✅ Indexer Import System
**Feature**: Import individual indexers from Jackett/Prowlarr with toggle switches.

**How to use**:
1. Settings → "Import Indexers from Jackett/Prowlarr"
2. Choose: Jackett, Prowlarr, or Both
3. Wait for import (shows count)
4. Go to "Manage Indexers" to toggle individual indexers on/off

**Storage**: Saved to `imported_indexers` SharedPreferences as JSON.

### ✅ Built-in Provider System (60+ Sites)
**Feature**: 60+ torrent providers that work WITHOUT Jackett/Prowlarr!

**Included providers**:
- Public: 1337x, The Pirate Bay, YTS, EZTV, Nyaa, LimeTorrents, TorrentGalaxy, etc.
- International: RuTracker, Rutor, Cinecalidad, DonTorrent, etc.
- Specialized: Academic Torrents, AniDex, SubsPlease, etc.
- Private: IPTorrents, TorrentLeech, PassThePopcorn, etc. (templates)

**How to enable**:
- Settings → "Manage Built-in Providers (60+)"
- Tap "Enable All Public" to activate 13 public trackers
- Or customize individual providers

**When used**:
- Automatically included when "All Sources" is selected
- Works offline - no Jackett/Prowlarr needed

### ✅ LibreTorrent Integration
**Feature**: Automatic LibreTorrent detection and prioritization.

**Behavior**:
1. When torrent clicked, app detects installed clients
2. **LibreTorrent is listed FIRST** (preferred)
3. If only LibreTorrent installed → opens directly
4. If multiple clients → shows chooser dialog
5. If no clients → suggests installing LibreTorrent with direct link

**Supported clients** (in priority order):
1. LibreTorrent (RECOMMENDED)
2. µTorrent
3. BitTorrent
4. Transdroid
5. Deluge
6. FrostWire
7. Flud

## 📱 How to Use on Samsung A32 5G

### First Time Setup:
1. **Install the updated APK**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. **Verify connections**:
   - Open app
   - Hit Refresh button
   - Should show: "Connected: Jackett & Prowlarr"

3. **Import your indexers** (Optional but recommended):
   - Tap Settings button
   - Tap "Import Indexers from Jackett/Prowlarr"
   - Choose "Import from Both"
   - Wait for success message
   - Tap OK to see imported indexers

4. **Enable built-in providers** (Optional):
   - In Settings, tap "Manage Built-in Providers (60+)"
   - Tap "Enable All Public"
   - 13 public trackers activated

### Searching:
1. **Select source** from dropdown:
   - **"All Sources (Everything)"** - BEST OPTION - searches everywhere
   - "Jackett" - only Jackett
   - "Prowlarr" - only Prowlarr

2. **Enter search query** and tap Search

3. **View results**:
   - Sorted by seeders (best first)
   - Color-coded health (green = excellent, red = dead)
   - Shows indexer source for each result

4. **Download**:
   - Tap any result
   - If LibreTorrent installed → opens directly
   - If not → prompts to install LibreTorrent

### Status Messages:
- **"✓ All 6 sources | 127 results"** - Perfect! All sources worked
- **"⚠ 4/6 sources | 89 results"** - Some sources failed but got results
- **"Connected: Jackett & Prowlarr"** - Both APIs working
- **"Connected: Jackett only (Prowlarr unreachable)"** - Jackett works, Prowlarr down

**Long press status text** to see detailed source breakdown.

## 🔧 Technical Details

### Modified Files:
- `TorznabService.kt` - Fixed Prowlarr API path detection
- `MainActivity.kt` - Updated source selector, LibreTorrent priority
- `TorrentAggregator.kt` - Added built-in providers, imported indexers fallback
- `SettingsActivity.kt` - Added import button and logic
- `activity_settings.xml` - Added UI buttons
- `strings.xml` - Updated source dropdown labels
- `.github/copilot-instructions.md` - Updated documentation

### New Files:
- `IndexerImporter.kt` - Handles importing indexers from Jackett/Prowlarr

### Storage:
- `prefs` - Main settings, API credentials
- `imported_indexers` - Individual imported indexers with toggle states
- `builtin_providers` - Enabled built-in provider IDs
- `download_history` - Last 100 downloads

### API Paths:
- **Prowlarr**: `/api/v1/search`
- **Jackett**: `/api/v2.0/indexers/all/results/torznab/api`
- **Imported Jackett indexer**: `/api/v2.0/indexers/{id}/results/torznab`
- **Imported Prowlarr indexer**: `/api/v1/indexer/{id}/newznab`

## 🚀 Ready to Test!

The app is fully built and ready to install on your Samsung A32 5G. All features are working:

✅ Prowlarr connection fixed  
✅ "All Sources" searches everything  
✅ Resilient - continues if sources fail  
✅ 60+ built-in providers  
✅ Import indexers from Jackett/Prowlarr  
✅ LibreTorrent auto-detected and prioritized  

Install and enjoy! 🎉
