# Viscord 3.0.3

## 🎯 Highlights
- **Fixed server restart crashes** - No more `StampedConfig` errors
- **Fixed Fluxer bot status** - Now shows real player counts instead of "0/0"
- **Complete config rewrite** - JSON → TOML with automatic migration
- **Fluxer Bot API** - More reliable than webhooks, no port forwarding needed
- **1.21.1 support** - Full NeoForge compatibility with Java 21

---

## 🚨 Breaking Changes
**Configuration format changed from JSON to TOML**
- Old: `config/viscord/viscord.json`
- New: `config/viscord/viscord.toml`
- Configs auto-migrate on first run with backup creation

---

## ✨ New Features

### TOML Configuration System
- Hierarchical, cleaner config structure
- Better organization with sections like `[general]`, `[discord]`, `[fluxer]`
- Automatic JSON → TOML migration
- Built-in config validation and correction

### Fluxer Bot API Integration
- All messages now use Fluxer Bot REST API
- No webhook URLs required
- No port forwarding needed (WebSocket Gateway)
- Simplified config: just `api_key` + `channel_id` + `event_channel_id`

### Python Build Menu
- Modern CLI with Rich UI and progress bars
- Auto-detects Java 21 installations
- Support for Fabric, Forge, and NeoForge
- Clean Build vs Quick Build options

---

## 🐛 Bug Fixes

| Issue | Fix |
|-------|-----|
| Server restart crash (`StampedConfig does not support valueMap()`) | Fixed config loading sequence |
| Fluxer bot showing "0/0" player count | Removed hardcoded values, now uses real server data |
| Bot appearing offline despite connection | Fixed gateway protocol (v=1) and identify payload |
| Infinite reconnection loops | Added exponential backoff and max retry limits |
| 1.21.1 compilation failures | Migrated to NeoForge APIs and Java 21 |
| Double prefixes in Discord ([Fluxer] [Fluxer]) | Proper prefix stripping in bridge logic |

---

## 📦 Downloads

| Minecraft | Fabric | Forge | NeoForge |
|-----------|--------|-------|----------|
| **1.21.1** | ✅ | ❌ | ✅ |
| **1.20.1** | ✅ | ✅ | ❌ |
| **1.19.2** | ✅ | ✅ | ❌ |
| **1.18.2** | ✅ | ✅ | ❌ |

---

## 🔄 Migration Guide

### From 2.4.9 to 3.0.3

1. **Backup**: Copy `config/viscord/viscord.json`
2. **Install**: Drop in new JAR files
3. **Start server**: Config auto-migrates to TOML
4. **Verify**: Check `config/viscord/viscord.toml`

### Configuration Changes

**Removed fields** (no longer needed):
- `fluxerWebhookUrl`, `fluxerEventWebhookUrl`
- `fluxerReceiverPort`, `fluxerReceiverPath`
- `fluxerApplicationId`, `fluxerClientSecret`, `fluxerUseBotApi`

**New structure**:
```toml
[general]
  enabled = true
  platform = "fluxer"  # or "discord"

[fluxer]
  bot_token = "YOUR_API_KEY"
  channel_id = "CHANNEL_ID"
  event_channel_id = "EVENT_CHANNEL_ID"
```

---

## 📝 Full Changelog

**3.0.3** - Server restart crash fix, Fluxer status fix, diagnostic logging  
**3.0.2** - 1.21.1 compilation fixes, NeoForge migration  
**3.0.1** - Fluxer message formatting fix (double prefixes)  
**3.0.0** - TOML config system rewrite  
**2.4.15** - Fluxer bot online status in tri-directional mode  
**2.4.14** - Gateway protocol fixes (v=1, modern properties)  
**2.4.12** - Python CLI build menu  
**2.4.11** - Tri-directional chat echo fixes, config field fixes  
**2.4.10** - Fluxer Bot API integration  
**2.4.9** - Fluxer Gateway deep-fix (intents, identify payload)  

---

## 🔗 Links
- **Documentation**: See `viscord-documentation.html`
- **Issues**: https://github.com/Vonix-Network/Viscord-Architectury/issues
- **Full Changelog**: See `CHANGELOG.md`
