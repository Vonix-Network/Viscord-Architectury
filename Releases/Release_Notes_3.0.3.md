# Viscord 3.0.3 Release Notes

**Release Date:** March 25, 2026  
**Previous Public Release:** 2.4.9  
**Supported Minecraft Versions:** 1.18.2, 1.19.2, 1.20.1, 1.21.1  
**Supported Platforms:** Fabric, Forge, NeoForge

---

## 🎉 Overview

Viscord 3.0.3 is a major stability and feature release that includes a complete configuration system overhaul, significant Fluxer platform improvements, and critical bug fixes. This release marks the transition from JSON to TOML configuration and includes numerous stability improvements for multi-platform chat bridging.

---

## ⚠️ Breaking Changes

### Configuration System Migration (3.0.0)
- **Complete config format change**: JSON → TOML
- **New config location**: `config/viscord/viscord.toml` (replaces `viscord.json`)
- **Automatic migration**: Existing configs are automatically migrated with backup creation
- **Action required**: Review migrated config for correctness after first run

---

## 🌟 Major New Features

### 1. Complete TOML Configuration System (3.0.0)
- **Better organization**: Hierarchical config sections with cleaner naming
- **Automatic migration**: JSON configs auto-convert to TOML on first run
- **Backup safety**: Old configs saved as `.backup` files
- **Enhanced validation**: Built-in config spec correction
- **New dependency**: NightConfig library for robust TOML parsing

### 2. Fluxer Bot API Integration (2.4.10 - 2.4.11)
- **Bot API sending**: All messages now use Fluxer Bot REST API instead of webhooks
- **No port forwarding required**: WebSocket Gateway handles all receiving
- **Simplified config**: Just `api_key` + `channel_id` + `event_channel_id`
- **Channel-based routing**: Mirrors Discord's configuration pattern
- **Event formatting**: Plain-text bold messages for Fluxer (API v1 limitation)

### 3. Modern Python CLI Build Menu (2.4.12)
- **Rich terminal UI**: Beautiful progress bars and interactive menus
- **Java auto-detection**: Automatically finds Java 21+ installations
- **Multi-platform builds**: Support for Fabric, Forge, and NeoForge
- **Smart build types**: Clean Build vs Quick Build options
- **Visual feedback**: Real-time build progress with animated spinners

---

## 🐛 Critical Bug Fixes

### Server Restart Crash (3.0.3)
- **Fixed**: `UnsupportedOperationException: StampedConfig does not support valueMap()`
- **Root cause**: Config spec correction incompatible with NightConfig's concurrent config
- **Solution**: Disable autosave during config correction, then re-enable
- **Impact**: Server can now restart without config-related crashes

### Fluxer Bot Player Count Status (3.0.3)
- **Fixed**: Bot displaying "0/0" instead of actual player counts
- **Root cause**: Hardcoded status values in READY/RESUMED event handlers
- **Solution**: Removed hardcoded values, now uses `DiscordManager.updateBotStatus()`
- **Impact**: Status now shows real "Online: X/Y" values matching Discord

### Fluxer Gateway Connection Stability (2.4.9 - 2.4.15)
- **Fixed**: Bots appearing offline despite successful authentication
- **Fixed**: 4002 "Invalid identify payload" errors
- **Fixed**: Infinite reconnection loops causing bot oscillation
- **Improvements**:
  - Correct API version (`v=1` for Fluxer protocol)
  - Modern gateway properties (`os`, `browser`, `device` instead of `$os`, `$browser`, `$device`)
  - Added `GUILD_MESSAGES` intent for MESSAGE_CREATE events
  - Proper session resume support
  - Exponential backoff reconnection (2s → 4s → 8s up to 60s)
  - Max 10 reconnect attempts before giving up

### 1.21.1 Compilation Issues (3.0.2)
- **Fixed**: Java 21 compatibility requirements
- **Fixed**: NeoForge API migration (Forge → NeoForge)
- **Fixed**: Command API changes (`sendSuccess()` now requires `Supplier<Component>`)
- **Fixed**: Advancement API changes (`Advancement` → `AdvancementHolder`)
- **Fixed**: TextColor parsing with new DataResult handling

### Message Formatting Fixes (3.0.1)
- **Fixed**: Double prefix/username in Discord when receiving from Fluxer
- **Before**: "[Fluxer] OGPargon: [Fluxer] OGPargon: Hello"
- **After**: "[Fluxer]OGPargon: Hello"
- **Solution**: Proper prefix stripping before bridging

---

## 🔧 Configuration Changes

### New TOML Structure (3.0.0)
```toml
[general]
  enabled = true
  platform = "fluxer"
  debug = false

[server]
  name = "My Server"

[discord]
  bot_token = "YOUR_BOT_TOKEN"
  channel_id = "CHANNEL_ID"
  event_channel_id = "EVENT_CHANNEL_ID"
  webhook_url = "WEBHOOK_URL"

[fluxer]
  bot_token = "YOUR_FLUXER_API_KEY"
  channel_id = "CHANNEL_ID"
  event_channel_id = "EVENT_CHANNEL_ID"
  webhook_url = "WEBHOOK_URL"

[tridirectional]
  enabled = false
  discord_to_fluxer = true
  fluxer_to_discord = true
  show_source = true

[bot_status]
  enabled = true
  format = "Online: {online}/{max}"
```

### Removed Configuration (2.4.10)
The following deprecated fields were removed:
- `fluxerWebhookUrl` → Use Bot API
- `fluxerEventWebhookUrl` → Use Bot API
- `fluxerReceiverPort` → No longer needed (WebSocket)
- `fluxerReceiverPath` → No longer needed (WebSocket)
- `fluxerApplicationId` → No longer needed
- `fluxerClientSecret` → No longer needed
- `fluxerUseBotApi` → Now always uses Bot API

---

## 📊 Version Support Matrix

| Minecraft | Fabric | Forge | NeoForge | Status |
|-----------|--------|-------|----------|--------|
| 1.21.1 | ✅ | ❌ | ✅ | Active |
| 1.20.1 | ✅ | ✅ | ❌ | Active |
| 1.19.2 | ✅ | ✅ | ❌ | Active |
| 1.18.2 | ✅ | ✅ | ❌ | Active |

---

## 🔄 Migration Guide

### From 2.4.9 to 3.0.3

1. **Backup your config**: Copy `config/viscord/viscord.json` to a safe location
2. **Update the mod**: Install 3.0.3 JAR files
3. **First run**: Server will automatically migrate JSON → TOML
4. **Verify migration**: Check `config/viscord/viscord.toml` for correctness
5. **Fluxer users**: Update config to use new simplified format:
   - Old: `fluxer.api_key`, `fluxer.channel_id`
   - New: Same field names, but in TOML format under `[fluxer]` section

---

## 📋 Full Changelog Summary

### 3.0.3 (2026-03-25)
- Fixed Fluxer bot status showing actual player counts
- Fixed server restart crash with StampedConfig
- Added detailed diagnostic logging for status updates

### 3.0.2 (2026-03-25)
- Fixed 1.21.1 compilation with Java 21
- Migrated to NeoForge APIs
- Updated command and advancement APIs

### 3.0.1 (2026-03-25)
- Fixed double prefix in Discord for Fluxer messages

### 3.0.0 (2026-03-25)
- Complete config system rewrite: JSON → TOML
- Automatic migration with backup creation

### 2.4.15 (2026-03-25)
- Fixed Fluxer bot online status in tri-directional mode
- Added tri-directional setup documentation

### 2.4.14 (2026-03-25)
- Verified Fluxer bot online status
- Fixed gateway 4002 errors with modern property format

### 2.4.12 (2026-03-25)
- Added modern Python CLI build menu with Rich UI

### 2.4.11 (2026-03-25)
- Fixed missing config field compilation errors
- Fixed tri-directional chat echo loops
- Fixed Discord to Fluxer message sending

### 2.4.10 (2026-03-24)
- Fluxer Bot API integration
- Simplified configuration (removed webhooks)
- No port forwarding required

### 2.4.9 (2026-03-24)
- Fixed Fluxer Gateway protocol (v=1)
- Added GUILD_MESSAGES intent
- Fixed identify payload properties

---

## 📥 Download

Download the appropriate JAR for your Minecraft version from the GitHub Releases page:
- `viscord-1.21.1-[fabric|neoforge]-3.0.3.jar`
- `viscord-1.20.1-[fabric|forge]-3.0.3.jar`
- `viscord-1.19.2-[fabric|forge]-3.0.3.jar`
- `viscord-1.18.2-[fabric|forge]-3.0.3.jar`

---

## 🙏 Credits

Thank you to all users who reported issues and provided feedback during the 2.4.9 → 3.0.3 development cycle. Special thanks for the patience during the configuration system migration.

**Full commit history**: https://github.com/Vonix-Network/Viscord-Architectury/commits/main
