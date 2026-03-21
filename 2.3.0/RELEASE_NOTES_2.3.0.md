# 🎉 Viscord 2.3.0 - Clean Configuration Release

## 🚀 What's New in 2.3.0

### 🌟 Clean Configuration Structure

Version 2.3.0 introduces a completely reorganized configuration system for better readability and maintainability.

#### New Section Names
| Old Name | New Name | Description |
|----------|----------|-------------|
| `server_identity` | `server` | Server identity settings |
| `message_formats` | `formats` | Message formatting templates |
| `loop_prevention` | `filters` | Chat filtering and loop prevention |
| `bot_status` | `bot` | Discord bot presence settings |
| `account_linking` | `linking` | Account linking configuration |

#### Simplified Config Keys
Config keys within sections no longer have redundant prefixes:
- ✅ `tridirectional.enabled` (not `tridirectional.enabled`)
- ✅ `discord.bot_token` (not `discord.bot_token`)
- ✅ `server.prefix` (not `server.prefix`)
- ✅ `formats.discord_to_minecraft` (not `formats.discord_to_minecraft`)

#### Improved Defaults
- Better out-of-box experience with sensible defaults
- Filter settings optimized for most server setups
- Bot status enabled by default for player count display

## 📋 Complete Config Example

```json
{
  "general": {
    "enabled": false,
    "platform": "discord"
  },
  
  "tridirectional": {
    "enabled": false,
    "discord_to_fluxer": true,
    "fluxer_to_discord": true,
    "show_source": true
  },
  
  "discord": {
    "bot_token": "YOUR_BOT_TOKEN_HERE",
    "channel_id": "YOUR_CHANNEL_ID_HERE",
    "webhook_url": "YOUR_WEBHOOK_URL_HERE",
    "webhook_id": "",
    "invite_url": ""
  },
  
  "fluxer": {
    "webhook_url": "",
    "event_webhook_url": "",
    "api_key": "YOUR_API_KEY_HERE",
    "port": 8080,
    "path": "/webhook"
  },
  
  "server": {
    "prefix": "[MC]",
    "name": "Minecraft Server",
    "avatar_url": ""
  },
  
  "formats": {
    "discord_to_minecraft": "[Discord] {username}: {message}",
    "minecraft_to_discord": "{message}",
    "webhook_username": "{prefix}{username}",
    "avatar_url": "https://minotar.net/armor/bust/{username}/100.png"
  },
  
  "events": {
    "send_join": true,
    "send_leave": true,
    "send_death": true,
    "send_advancement": true,
    "event_channel_id": "",
    "event_webhook_url": ""
  },
  
  "filters": {
    "ignore_bots": true,
    "ignore_webhooks": true,
    "filter_by_prefix": true,
    "show_other_server_events": true,
    "enable_chat_filter": false,
    "chat_filter_prefix": "!"
  },
  
  "bot": {
    "enabled": true,
    "format": "{online}/{max} players"
  },
  
  "linking": {
    "enabled": true,
    "code_expiry": 300
  },
  
  "advanced": {
    "debug_logging": false,
    "queue_size": 100,
    "rate_limit": 1000
  }
}
```

## 📦 Distribution

### Supported Versions
- **Minecraft 1.21.1** - Fabric + NeoForge
- **Minecraft 1.20.1** - Fabric + Forge  
- **Minecraft 1.19.2** - Fabric + Forge
- **Minecraft 1.18.2** - Fabric + Forge

### File Naming Convention
```
Viscord-2.3.0-Fabric-1.21.1.jar
Viscord-2.3.0-NeoForge-1.21.1.jar
Viscord-2.3.0-Fabric-1.20.1.jar
Viscord-2.3.0-Forge-1.20.1.jar
Viscord-2.3.0-Fabric-1.19.2.jar
Viscord-2.3.0-Forge-1.19.2.jar
Viscord-2.3.0-Fabric-1.18.2.jar
Viscord-2.3.0-Forge-1.18.2.jar
```

## 🔧 Installation & Setup

### Quick Start
1. Download Viscord 2.3.0 for your Minecraft version
2. Install to your server's `mods` folder
3. Restart server to generate new clean config
4. Configure your preferred platform
5. Enable with `enabled: true`
6. Restart and enjoy!

### Migration from 2.2.0
- Config files are auto-generated on first run
- Old configs will be backed up automatically
- Simply replace the JAR and restart

## 🎯 Benefits

### For Server Owners
- **Cleaner config files** - Easier to read and maintain
- **Better organization** - Logical section grouping
- **Simplified troubleshooting** - Clear structure

### For Developers
- **Consistent naming** - Standardized conventions
- **Better documentation** - Clear comments
- **Easier maintenance** - Logical structure

## 🔍 Technical Details

### Config Structure Changes
- Removed redundant key prefixes within sections
- Standardized section naming convention
- Improved default values
- Better inline documentation

### Backward Compatibility
- Config auto-regenerates on version change
- Old values are migrated automatically
- No manual intervention required

## 🐛 Known Issues & Fixes

This is a configuration cleanup release. Report any issues with config generation.

## 🙏 Acknowledgments

Thanks to our community for feedback on config organization!

## 🔮 Looking Forward

Viscord 2.3.0 provides the foundation for future feature additions with a clean, maintainable config structure.

---

**Viscord 2.3.0** - Clean config, better experience! 🎨✨

*Upgrade today and enjoy the improved configuration system!*
