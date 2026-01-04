# JackettProwlarrClient

A powerful Android BitTorrent client that uses the Torznab API to search and download torrents from Jackett and Prowlarr indexers.

## Features

### 🔍 **Torznab API Integration**
- Full Torznab API support for both Jackett and Prowlarr
- Search across all configured indexers simultaneously
- Support for multiple search types:
  - General search (`t=search`)
  - TV show search (`t=tvsearch`)
  - Movie search (`t=movie`)
  - Music search (`t=music`)
  - Book search (`t=book`)

### 📊 **Advanced Torrent Details**
- **Health Status**: Visual health indicators (Excellent, Good, Fair, Poor, Dead)
- **Seeder/Leecher Count**: Real-time peer information with color-coded display
- **Size Information**: Human-readable file sizes (GB, MB, KB)
- **Indexer Source**: See which indexer provided each result
- **Category Information**: Torrent categories when available
- **Publication Date**: When the torrent was uploaded
- **Magnet Link Support**: Direct magnet link handling

### 🎯 **Smart Search**
- **Keyword Extraction**: Automatically searches for individual keywords
- **Duplicate Removal**: Intelligent deduplication of results
- **Relevance Sorting**: Results sorted by seeder count

### 📥 **Multi-Client Download Support**
- **qBittorrent Integration**: Send torrents directly to qBittorrent
- **Automatic Client Detection**: Finds installed torrent clients:
  - µTorrent
  - BitTorrent
  - LibreTorrent
  - Flud
  - Transdroid
  - Deluge
  - FrostWire
- **Magnet Link Support**: Opens magnet links in compatible apps
- **Download History**: Track all downloaded torrents with timestamps

### 🛠️ **Configuration**
- Manage individual indexers (enable/disable)
- Configure qBittorrent server connection
- Connection status monitoring
- Support for both HTTP and HTTPS

## Technical Implementation

### Torznab Service Layer
The app includes a comprehensive `TorznabService` class that handles all API communication:

```kotlin
val service = TorznabService(baseUrl, apiKey)

// Simple search
val results = service.search("ubuntu", SearchType.SEARCH)

// TV search with metadata
val tvResults = service.searchTv("Breaking Bad", season = 1, episode = 1)

// Movie search
val movieResults = service.searchMovie("Inception", imdbId = "tt1375666")

// Check capabilities
val caps = service.getCapabilities()
```

### Enhanced Data Model
`TorrentResult` includes comprehensive torrent information:
- Title, size, seeders, leechers, peers
- Download URL, magnet URL, info hash
- Indexer source, category, publication date
- IMDB ID support
- Health calculation and status indicators

### Download History
Powered by `DownloadHistoryManager`:
- JSON-based persistent storage
- Last 100 downloads tracked
- Search and filter capabilities
- Timestamp tracking

## Setup

1. **Configure Jackett/Prowlarr**
   - Update `JACKETT_BASE_URL` and `JACKETT_API_KEY` in MainActivity.kt
   - Update `PROWLARR_BASE_URL` and `PROWLARR_API_KEY` in MainActivity.kt

2. **Optional: Configure qBittorrent**
   - Go to Settings in the app
   - Enable qBittorrent integration
   - Enter your qBittorrent Web UI URL, username, and password

3. **Build and Install**
   ```bash
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## Requirements

- Android 8.0 (API 26) or higher
- Active Jackett or Prowlarr server
- Network connectivity to indexer servers

## Architecture

- **Language**: Kotlin
- **UI**: Material Design with ViewBinding
- **Networking**: OkHttp
- **Async**: Kotlin Coroutines
- **XML Parsing**: Android XmlPullParser
- **JSON**: Gson

## Torznab API Compliance

This app implements the Torznab specification:
- RSS/XML feed parsing
- Torznab namespace attributes (`torznab:attr`)
- All standard search parameters
- Capabilities endpoint (`t=caps`)
- Enclosure support for direct downloads

## Screenshots

The app displays:
- Torrent title (up to 3 lines)
- File size with unit
- Health status (color-coded)
- Seeders (↑ green) and Leechers (↓ orange)
- Source indexer name
- Optional category badge

## Future Enhancements

- [ ] Custom search filters (category, size range, min seeders)
- [ ] Save favorite searches
- [ ] RSS feed subscriptions
- [ ] Automatic download scheduling
- [ ] VPN integration check
- [ ] Advanced sorting options
- [ ] Dark mode

## License

This project is open source. Use responsibly and respect copyright laws in your jurisdiction.
