# 🎉 Viscord 2.4.0 - Production Ready Release

## 🚀 What's New in 2.4.0

### 🌟 Full Reload Capability
Version 2.4.0 introduces the long-awaited `/viscord reload` command for administrators.

**Features:**
- **Full Reconnect**: Disconnects and reconnects Discord/Fluxer bot with new configuration
- **Async Operation**: Runs entirely on background thread - no server lag
- **Timeout Protection**: 30-second timeout prevents hanging
- **Graceful Error Handling**: Failed reloads don't break existing connections

**Usage:**
```
/viscord reload
```

### 📊 Status Command
New `/viscord status` command for administrators to check Viscord health.

**Displays:**
- Connection status (Running/Stopped)
- Configured platform (Discord/Fluxer)
- Enabled/disabled state
- Player count (if running)

**Usage:**
```
/viscord status
```

### 📁 Config Directory Reorganization
All Viscord files are now organized in `config/viscord/` subdirectory for better separation.

**New Structure:**
```
config/
└── viscord/
    ├── viscord.json          (main configuration)
    ├── player_preferences.json
    └── linked_accounts.json  (if account linking enabled)
```

**Migration:**
- Existing configs in `config/viscord.json` will need to be moved manually
- New installations automatically use the new location
- Player preferences will be regenerated in new location

### 🔄 Command Rebrand
All `/vonix` commands have been rebranded to `/viscord` for consistency.

**Mapping:**
| Old Command | New Command |
|------------|-------------|
| `/vonix discord link` | `/viscord discord link` |
| `/vonix discord unlink` | `/viscord discord unlink` |
| `/vonix discord messages` | `/viscord discord messages` |
| `/vonix discord events` | `/viscord discord events` |
| `/vonix discord help` | `/viscord discord help` |
| N/A (new) | `/viscord reload` |
| N/A (new) | `/viscord status` |

**Backward Compatibility:**
- `/vonix` still works as a deprecated alias
- Shows warning message directing users to new commands
- Will be removed in version 3.0.0

### ⚡ Async Safety Improvements
Critical fix for server startup freezing.

**Before:**
- Discord initialization blocked main thread for up to 10 seconds
- Could cause "Server not responding" messages
- Timeout exceptions were common

**After:**
- Fully async initialization
- Server continues booting immediately
- Non-blocking connection handling
- Better timeout management

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
Viscord-2.4.0-Fabric-1.21.1.jar
Viscord-2.4.0-NeoForge-1.21.1.jar
Viscord-2.4.0-Fabric-1.20.1.jar
Viscord-2.4.0-Forge-1.20.1.jar
Viscord-2.4.0-Fabric-1.19.2.jar
Viscord-2.4.0-Forge-1.19.2.jar
Viscord-2.4.0-Fabric-1.18.2.jar
Viscord-2.4.0-Forge-1.18.2.jar
```

## 🔧 Installation & Setup

### Quick Start
1. Download Viscord 2.4.0 for your Minecraft version
2. Install to your server's `mods` folder
3. Restart server to generate `config/viscord/` directory
4. Configure your preferred platform in `config/viscord/viscord.json`
5. Enable with `enabled: true`
6. Restart and enjoy!

### Migration from 2.2.0/2.3.0
1. Stop your server
2. Replace the JAR file
3. Move `config/viscord.json` to `config/viscord/viscord.json`
4. Restart server
5. Player preferences will be regenerated automatically

## 🎯 Benefits

### For Server Owners
- **No more startup freezes** - Server boots normally even with slow Discord connections
- **Live configuration** - Reload config without server restart
- **Better organization** - All Viscord files in dedicated subdirectory
- **Status monitoring** - Check connection health at any time

### For Developers
- **Clean async patterns** - All Discord operations non-blocking
- **Consistent API** - Rebranded commands follow naming conventions
- **Better error handling** - Graceful degradation on connection issues

## 🔍 Technical Details

### Async Architecture
```java
// Discord initialization now runs on async executor
Viscord.ASYNC_EXECUTOR.submit(() -> {
    DiscordManager.getInstance().initialize(server);
});
```

### Config Loading
```java
// Config now loaded from subdirectory
Path configDir = Platform.getConfigFolder().resolve("viscord");
Path configPath = configDir.resolve("viscord.json");
```

### Reload Process
```java
// 1. Shutdown existing connection
DiscordManager.getInstance().shutdown();

// 2. Reload config from disk
SimpleConfigManager.load(configPath, ViscordConfig.SPEC);

// 3. Re-initialize with new settings
DiscordManager.getInstance().initialize(server);
```

## 🐛 Known Issues & Fixes

This is a production-ready release. All known issues from 2.2.0 have been addressed:
- ✅ Fixed startup freezing
- ✅ Fixed config organization
- ✅ Added reload capability
- ✅ Rebranded commands

## 🙏 Acknowledgments

Thanks to our community for:
- Reporting the startup freeze issue
- Requesting reload functionality
- Feedback on command naming
- Testing prerelease builds

## 🔮 Looking Forward

Viscord 2.4.0 provides a solid foundation for:
- Future platform integrations
- Advanced filtering options
- Enhanced formatting features
- Performance optimizations

---

**Viscord 2.4.0** - Production Ready! 🚀✨

*Upgrade today and experience the most stable Viscord release yet!*
