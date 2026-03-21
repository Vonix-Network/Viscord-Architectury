# Viscord 2.4.1 Release Notes

Release Date: 2026-03-21

## 🔗 Account Linking System

### Major New Features
- **Full /link Command Implementation** - Complete Discord-Minecraft account linking
  - Discord-side: `/link <code>` command for verification
  - Minecraft-side: `/viscord discord link` for code generation
  - 6-digit unique codes with configurable expiry time
  - Double-link prevention (MC & Discord accounts)
  - JSON persistence with automatic cleanup
  - Full error handling and user feedback

### Link Management
- **Link Management** - `/viscord discord unlink` command
- **Account Security** - Prevents multiple links per account
- **Data Persistence** - Links stored in `viscord-links.json`

## 📦 Available Downloads

This release includes the following jar files:

### Minecraft 1.18.2
- `viscord-1.18.2-fabric-2.4.1.jar` - Fabric loader
- `viscord-1.18.2-forge-2.4.1.jar` - Forge loader

### Minecraft 1.19.2
- `viscord-1.19.2-fabric-2.4.1.jar` - Fabric loader
- `viscord-1.19.2-forge-2.4.1.jar` - Forge loader

### Minecraft 1.20.1
- `viscord-1.20.1-fabric-2.4.1.jar` - Fabric loader
- `viscord-1.20.1-forge-2.4.1.jar` - Forge loader

### Minecraft 1.21.1
- `viscord-1.21.1-fabric-2.4.1.jar` - Fabric loader
- `viscord-1.21.1-neoforge-2.4.1.jar` - NeoForge loader

## 📋 Installation Instructions

1. Download the appropriate jar file for your Minecraft version and mod loader
2. Place the jar file in your server's `mods` folder
3. Restart your server
4. Configure the plugin in `config/viscord-common.toml`

## ⚠️ Important Notes

- Full 2.4.1 features are available for all supported Minecraft versions: 1.18.2, 1.19.2, 1.20.1, and 1.21.1
- **Minecraft 1.18.2**: Now available with full account linking support!
- Make sure to backup your existing configuration before updating

## 🐛 Bug Fixes

- Fixed various compilation issues across different Minecraft versions
- Improved error handling in account linking system
- Enhanced stability for Discord webhook connections

## 📚 Documentation

Full documentation is available in the Web Docs folder included with this release.

---

**Thank you for using Viscord!**
