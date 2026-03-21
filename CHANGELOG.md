# Changelog

All notable changes to Viscord will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
