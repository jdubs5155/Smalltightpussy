# Tracker Enhancement - User Guide

## What Are Trackers?

BitTorrent trackers are servers that help you find other people (peers) who have the files you want to download. More trackers = more peers = faster downloads!

## How It Works

### Automatic Enhancement ⚡
When you download a torrent, the app automatically adds 30 super-fast trackers to your magnet link:

**Before:**
```
magnet:?xt=urn:btih:ABC123DEF456...
```

**After (Enhanced):**
```
magnet:?xt=urn:btih:ABC123DEF456...
&tr=udp://tracker.openbittorrent.com:80
&tr=udp://tracker.coppersurfer.tk:6969
&tr=udp://tracker.leechers-paradise.org:6969
... (27 more trackers)
```

## Quick Start

### Step 1: Verify It's Enabled
1. Open app → **Settings**
2. Scroll to "📡 Tracker Management"
3. Tap "⚡ Manage Super Fast Trackers"
4. Check that switch is **ON** (enabled by default)

### Step 2: Download a Torrent
1. Search for anything (movie, software, etc.)
2. Tap a result to download
3. Look at status bar at bottom
4. You'll see: **"📡 Enhanced with 30 trackers (20 UDP, 10 HTTP)"**

That's it! Your download now has 30 additional ways to find peers.

## Customization Options

### Use Top 50 Trackers (Default - Recommended)
**Settings → Tracker Management → Top 50**
- Best balance of speed and compatibility
- 50 fastest, most reliable trackers
- Works with all torrent clients

### Use All 200+ Trackers (Maximum Coverage)
**Settings → Tracker Management → Use All**
- Every available tracker
- Maximum peer discovery
- Best for rare/old torrents
- May be slower to process

### Add Your Own Trackers
**Settings → Tracker Management → Add**
1. Tap "Add" button
2. Paste tracker URL
3. Examples:
   - `udp://tracker.example.com:1337/announce`
   - `http://tracker.example.com:6969/announce`
4. Tap "Add"

### Turn Off Tracker Enhancement
**Settings → Tracker Management → Toggle Switch OFF**
- Magnet links will not be modified
- Original behavior restored

## Understanding the Status Messages

### "📡 Enhanced with 30 trackers"
- Your magnet link now has 30 additional trackers
- This is GOOD - more trackers = more peers

### "📡 Enhanced with 30 trackers (20 UDP, 10 HTTP)"
- **UDP (20)**: Fast, connectionless protocol - best for speed
- **HTTP (10)**: Reliable, works through firewalls - best for compatibility
- **HTTPS**: Secure, encrypted tracker connections

### "No trackers added"
- Either enhancement is disabled
- Or the torrent already has many trackers
- Check Settings → Tracker Management

## Protocol Types Explained

### 📡 UDP Trackers (Fastest)
```
udp://tracker.openbittorrent.com:80
udp://tracker.coppersurfer.tk:6969
```
- **Speed**: ⚡⚡⚡ Very Fast
- **Reliability**: ⭐⭐⭐ Good
- **Firewall**: May be blocked by some networks

### 🌐 HTTP Trackers (Most Compatible)
```
http://tracker.opentrackr.org:1337/announce
http://explodie.org:6969/announce
```
- **Speed**: ⚡⚡ Fast
- **Reliability**: ⭐⭐⭐⭐ Very Good
- **Firewall**: Works on most networks

### 🔒 HTTPS Trackers (Secure)
```
https://www.wareztorrent.com/announce
```
- **Speed**: ⚡ Moderate
- **Reliability**: ⭐⭐⭐⭐⭐ Excellent
- **Firewall**: Works on all networks

## Troubleshooting

### ❌ "Trackers not working"
**Solution:**
1. Some trackers may be offline (this is normal)
2. The app uses 200+ trackers, so if 5-10 are dead, you still have 190+ working ones
3. To update: Settings → Tracker Management → Reset to Defaults

### ❌ "Downloads still slow"
**Possible Reasons:**
1. **Few seeders**: Trackers can only find peers that exist
2. **ISP throttling**: Some ISPs limit torrent speeds
3. **Client settings**: Check your torrent client's upload/download limits
4. **Old/rare torrent**: Try searching for newer releases

**Solutions:**
- Use "Use All" to enable all 200+ trackers
- Try different search terms
- Check torrent has seeders (green number in search results)

### ❌ "Status doesn't show tracker count"
**Check:**
1. Is it a magnet link? (Feature only works with magnet://)
2. Is enhancement enabled? (Settings → Toggle switch ON)
3. Try restarting the app

### ❌ "Tracker Management button missing"
**Solution:**
1. Update app to latest version
2. Or: Settings → scroll to bottom → look for "📡 Tracker Management"

## Advanced Tips

### For Best Performance
1. **Use Top 50** - Balanced speed and compatibility
2. **Enable UDP trackers** - Fastest protocol
3. **Check seeders** - Always choose torrents with 20+ seeders (green in results)

### For Maximum Compatibility
1. **Use All trackers** - 200+ options increase chances
2. **Prefer HTTP trackers** - Works through corporate firewalls
3. **Enable HTTPS trackers** - Works on restricted networks

### For Privacy
1. **Use HTTPS trackers only** - Encrypted tracker communication
2. **Disable UDP** - Less visible to ISPs
3. **Use Tor mode** - Enable in Settings (if you have Orbot installed)

## Real-World Benefits

### Example 1: Popular Movie
- **Without trackers**: 50 peers found
- **With 30 trackers**: 200+ peers found
- **Result**: 4x faster download

### Example 2: Old Software
- **Without trackers**: 2 peers, slow download
- **With 200 trackers**: 15 peers found across multiple trackers
- **Result**: Download completes successfully

### Example 3: Rare Content
- **Without trackers**: No peers found, stuck at 0%
- **With all trackers**: Found 3 peers on obscure international trackers
- **Result**: Download possible!

## FAQ

**Q: Does this use more data/battery?**
A: No, tracker URLs are just text added to the magnet link. Your torrent client handles the communication.

**Q: Will this work with any torrent client?**
A: Yes! All modern torrent clients (LibreTorrent, µTorrent, qBittorrent, etc.) support multiple trackers.

**Q: Are these trackers legal?**
A: Yes, trackers are just directories. They don't host content, they only help peers find each other.

**Q: Can I use my own tracker list?**
A: Yes! Settings → Tracker Management → Add → paste your tracker URLs.

**Q: Does this work offline?**
A: The tracker list is stored locally, but you need internet to actually download torrents.

**Q: Will this slow down my phone?**
A: No, this feature only adds text to magnet links. Zero performance impact.

**Q: How often should I update trackers?**
A: The default list has 200+ trackers, so updates aren't critical. Reset to defaults once a year if needed.

---

## Quick Reference Card

| Action | Steps |
|--------|-------|
| **Enable/Disable** | Settings → Tracker Management → Toggle switch |
| **Use Top 50** | Settings → Tracker Management → "Top 50" button |
| **Use All 200+** | Settings → Tracker Management → "Use All" button |
| **Add Custom** | Settings → Tracker Management → "Add" button |
| **Reset** | Settings → Tracker Management → "Reset to Defaults" |
| **View Active** | Settings → Tracker Management → Scroll through list |
| **Remove Tracker** | Settings → Tracker Management → Long press on tracker |

---

**Enjoy faster torrents! 🚀**

*This feature adds zero bloat, uses zero battery, and costs zero data - just makes your torrents better.*
