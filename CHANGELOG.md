# Changelog

All notable changes to Viscord will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.4.0] - 2026-03-21

### ⚠️ Compatibility Notes
- **Minecraft 1.18.2**: Due to major API changes in this version, 1.18.2 builds are based on 2.3.0 codebase. Full 2.4.0 features (reload command, command rebrand) are not available for 1.18.2.

### 🌟 Added
- **Full Reload Capability** - New `/viscord reload` command for administrators
  - Disconnects and reconnects Discord/Fluxer bot with new config
  - Full async operation to prevent server lag
  - 30-second timeout protection
  - Graceful error handling with rollback
- **Status Command** - New `/viscord status` command for administrators
  - Shows current connection status (Running/Stopped)
  - Displays configured platform (Discord/Fluxer)
  - Shows if Viscord is enabled/disabled in config
- **Commands Documentation** - New commands section in web docs
  - Complete command reference for admins and players
  - Permission level documentation
  - Usage examples

### 🔄 Changed
- **Command Rebrand** - All `/vonix` commands rebranded to `/viscord`
  - `/vonix discord link` → `/viscord discord link`
  - `/vonix discord unlink` → `/viscord discord unlink`
  - `/vonix discord messages` → `/viscord discord messages`
  - `/vonix discord events` → `/viscord discord events`
  - `/vonix discord help` → `/viscord discord help`
  - `/vonix reload` (new) → `/viscord reload`
  - Backward compatibility alias maintained for `/vonix`
- **Documentation Updates** - Web docs updated to version 2.4.0
  - Added Commands tab with full reference
  - Version badges updated throughout
  - Footer version corrected

### 🔧 Technical Improvements
- **Config Directory Reorganization** - All Viscord files now stored in `config/viscord/`
  - Main config: `config/viscord/viscord.json`
  - Player preferences: `config/viscord/player_preferences.json`
  - Account links: `config/viscord/linked_accounts.json`
  - Better organization and separation from other mods
- **Async Safety** - Fixed blocking `.get()` call in Discord initialization
  - Prevents 10-second server freeze during startup
  - All Discord operations now fully async
  - Better error handling for connection failures
- **Production Readiness Review** - Full codebase review completed
  - Core mod structure verified
  - Discord integration layer optimized
  - Command system enhanced

### ⚡ Performance
- Discord initialization no longer blocks main thread
- Config reload runs entirely on async executor
- Message processing maintains async safety

## [2.2.0] - 2026-03-21

### 🌟 Added
- **Discord Formatting Support** - Minecraft formatting codes now convert to Discord markdown
- **Dual Code Support** - Both § codes and & codes are supported for maximum compatibility
- **Rich Text Formatting** - Bold (§l), Italic (§o), Underline (§n), Strikethrough (§m) support
- **Color Indicators** - Minecraft colors converted to emoji indicators since Discord doesn't support text colors
- **Magic/Obfuscated** - Converted to sparkle emoji (✨) for visual indication
- **Reset Codes** - Properly handled to close formatting tags
- **Cross-Platform Formatting** - Works in tridirectional chat and all message routing

### 🎨 Formatting Conversions
- **§l / &l** → **Bold text** (Discord: `**text**`)
- **§o / &o** → *Italic text* (Discord: `*text*`)
- **§n / &n** → __Underlined text__ (Discord: `__text__`)
- **§m / &m** → ~~Strikethrough text~~ (Discord: `~~text~~`)
- **§k / &k** → ✨Magic text✨ (Discord: sparkle emoji)
- **§r / &r** → Reset all formatting
- **Colors (0-9, a-f)** → Color emoji indicators (⚫🟦🟩🟨🟥🟪🟧⚪🔵🟢🔷🔴🟠🟡)

### 🔧 Technical Implementation
- **DiscordFormatter Utility** - New utility class for formatting conversion
- **Smart Tag Management** - Proper opening and closing of Discord markdown tags
- **Nested Formatting** - Supports multiple formatting codes in same message
- **Performance Optimized** - Efficient regex-based parsing
- **Error Resilient** - Graceful handling of malformed formatting codes

### 📝 Usage Examples
- `§6§lGolden§r text` → 🟡 **Golden** text
- `§oItalic §nand §lunderlined§r` → *Italic __and **underlined**__*
- `§kMagic text§r` → ✨Magic text✨

### 🌐 Integration
- **Minecraft → Discord**: Full formatting conversion
- **Tridirectional Chat**: Formatting preserved across platforms
- **Event Messages**: System messages also support formatting
- **Backward Compatible**: Existing messages without formatting work unchanged

### Changed
- Updated version to 2.2.0 to reflect major formatting enhancement
- Enhanced message processing pipeline for formatting support

## [2.3.0] - 2026-03-21

### 🌟 Added
- **Clean Configuration Structure** - Reorganized config sections for better readability
  - Renamed `server_identity` → `server`
  - Renamed `message_formats` → `formats`
  - Renamed `loop_prevention` → `filters`
  - Renamed `bot_status` → `bot`
  - Renamed `account_linking` → `linking`
- **Simplified Config Keys** - Removed redundant prefixes within sections
- **Improved Defaults** - Better out-of-box experience with sensible defaults
- **Standardized Naming** - Consistent section naming convention across all config files

### Changed
- Updated version to 2.3.0 for configuration improvements
- Enhanced config documentation with clearer comments
- Improved config file organization and readability

## [2.1.0] - 2026-03-21

### 🌟 Added
- **Revolutionary Tridirectional Chat System** - Complete 3-way message synchronization between Discord ↔ Minecraft ↔ Fluxer
- **Platform Source Identification** - Optional tags showing message origin ([Discord], [Fluxer], [Minecraft])
- **Configurable Message Bridging** - Fine-grained control over which platforms bridge to each other
- **Real-time Cross-Platform Communication** - Messages flow seamlessly across all connected platforms
- **Advanced Tridirectional Configuration** - New config section for 3-way chat settings
- **Mobile Accessibility** - Participate in server chat via Fluxer when away from PC
- **Unified Chat Experience** - Type anywhere, appear everywhere across all platforms

### 🔧 Technical Changes
- **Enhanced DiscordManager** - Added bridging methods for cross-platform message routing
- **Improved Configuration System** - New tridirectional chat settings with detailed explanations
- **Message Formatting System** - Platform-aware message formatting with source identification
- **Configuration Validation** - Checks for proper Discord and Fluxer setup before enabling bridging
- **Error Handling** - Robust error handling for cross-platform message failures

### 📋 Configuration Options
- `tridirectional.enabled` - Enable/disable 3-way chat synchronization
- `tridirectional.discord_to_fluxer` - Bridge Discord messages to Fluxer
- `tridirectional.fluxer_to_discord` - Bridge Fluxer messages to Discord  
- `tridirectional.show_source` - Show platform source tags in messages

### 🎯 Use Cases Enabled
- **Multi-platform communities** - Engage users wherever they are
- **Server management** - Monitor chat from Discord while away from game
- **Mobile gaming** - Use Fluxer app when away from computer
- **Community bridging** - Connect different Discord servers via Fluxer
- **Stream integration** - Let viewers participate from multiple platforms

### ⚠️ Requirements
- Both Discord and Fluxer must be properly configured for tridirectional chat
- Requires webhooks for both platforms to be functional
- Recommended for servers with active multi-platform communities

### Changed
- Updated version to 2.1.0 to reflect major new feature addition

### Added
- Fluxer webhook service support as alternative to Discord
- Configurable HTTP receiver for bidirectional Fluxer communication
- Platform selection in configuration (`platform: "discord"` or `"fluxer"`)
- Separate Fluxer configuration section with webhook URLs and API key
- Configurable receiver port and path for Fluxer messages
- Simplified configuration structure with cleaner field names
- Professional documentation with README and setup guides

### Changed
- Renamed Discord config fields to be more explicit (e.g., `bot_token` → `discord.bot_token`)
- Simplified configuration comments and structure for better user experience
- Updated config generation to be more user-friendly with quick start guide
- Improved error handling and logging for platform initialization

### Fixed
- Fixed config file generation issues across all Minecraft versions
- Resolved dependency bundling problems with Jackson and other libraries
- Fixed platform-specific initialization logic

## [2.0.0] - 2024-03-21

### Added
- Dual platform support (Discord and Fluxer)
- HTTP server for receiving Fluxer webhook messages
- Configurable receiver port and path
- Platform-specific initialization
- Cleaner configuration structure
- Professional documentation

### Changed
- Complete configuration restructure for better UX
- Updated all field names to be platform-specific
- Simplified setup process with quick start guide
- Improved error messages and logging

### Fixed
- Config file generation across all versions
- Dependency bundling issues
- Platform initialization bugs

## [1.5.0] - 2024-03-20

### Added
- Multi-server support with unique prefixes
- Account linking system (Discord only)
- Player message filtering preferences
- Advanced chat filtering with prefix support
- Bot status updates with player count
- Debug logging mode
- Performance optimizations with message queuing

### Changed
- Refined message formatting system
- Improved event notification handling
- Better webhook error handling
- Updated dependency management

### Fixed
- Message loop prevention in multi-server setups
- Rate limiting issues with Discord API
- Avatar URL template handling
- Event embed processing

## [1.4.0] - 2024-03-15

### Added
- Event notifications (join/leave/death/advancement)
- Separate event channel and webhook support
- Customizable message formats
- Server identity configuration
- Loop prevention settings
- Webhook username formatting

### Changed
- Restructured configuration for better organization
- Improved message processing pipeline
- Enhanced embed handling for Discord messages

### Fixed
- Discord message parsing issues
- Webhook ID extraction from URLs
- Avatar display problems

## [1.3.0] - 2024-03-10

### Added
- Discord message receiving via bot
- Webhook message sending
- Basic bidirectional chat functionality
- Discord bot connection management
- Message filtering options

### Changed
- Initial architecture setup
- Basic configuration structure

### Fixed
- Initial stability issues
- Connection timeout problems

## [1.2.0] - 2024-03-05

### Added
- Basic Discord webhook support
- Minecraft to Discord message sending
- Simple configuration system
- Server prefix support

### Changed
- Core architecture implementation
- Initial feature set

## [1.1.0] - 2024-03-01

### Added
- Project initialization
- Architectury framework integration
- Multi-version support (1.18.2, 1.19.2, 1.20.1, 1.21.1)
- Fabric and Forge/NeoForge compatibility

## [1.0.0] - 2024-02-28

### Added
- Initial release
- Basic mod structure
- Configuration system foundation

---

## Version Support Matrix

| Version | Fabric | Forge | NeoForge | Status |
|---------|--------|-------|----------|---------|
| 1.21.1 | ✅ | ❌ | ✅ | Active |
| 1.20.1 | ✅ | ✅ | ❌ | Active |
| 1.19.2 | ✅ | ✅ | ❌ | Active |
| 1.18.2 | ✅ | ✅ | ❌ | Active |

## Migration Guide

### From 1.x to 2.0

**Configuration Changes:**
The configuration has been completely restructured. You will need to recreate your config file.

**Old format:**
```json
{
  "bot_token": "...",
  "channel_id": "...",
  "webhook_url": "..."
}
```

**New format:**
```json
{
  "platform": "discord",
  "discord": {
    "bot_token": "...",
    "channel_id": "...",
    "webhook_url": "..."
  }
}
```

**Key Changes:**
1. Add `platform` field to choose between "discord" or "fluxer"
2. Move Discord settings under `discord` section
3. Add Fluxer settings under `fluxer` section if using Fluxer
4. All other settings remain the same but may be in different sections

### Fluxer Setup

New in 2.0, you can use Fluxer instead of Discord:

1. Set `platform: "fluxer"`
2. Configure Fluxer webhook URLs
3. Set receiver port (default: 8080)
4. Point Fluxer to `http://your-server:8080/webhook`

## Breaking Changes

### 2.0.0
- Configuration file format completely changed
- Field names renamed for clarity
- Platform selection now required

### 1.5.0
- No breaking changes

### 1.4.0
- No breaking changes

### 1.3.0
- No breaking changes

## Deprecations

### Deprecated in 2.0.0
- Old configuration field names
- Direct platform detection (now explicit via config)

### To be Removed in 3.0.0
- None planned

---

**For more detailed information about each release, please check the GitHub releases page.**
