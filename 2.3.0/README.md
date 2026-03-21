# Viscord 2.3.0 - Quick Start

## 🚀 Installation

1. **Choose the correct JAR** for your Minecraft version and mod loader
2. **Place JAR** in your server's `mods` folder
3. **Restart server** to generate configuration
4. **Configure** Discord or Fluxer in `config/viscord.json`
5. **Set `enabled: true`** and restart again

## 📦 Files Included

- `Viscord-2.3.0-Fabric-1.21.1.jar` - For Fabric 1.21.1
- `Viscord-2.3.0-NeoForge-1.21.1.jar` - For NeoForge 1.21.1
- `Viscord-2.3.0-Fabric-1.20.1.jar` - For Fabric 1.20.1
- `Viscord-2.3.0-Forge-1.20.1.jar` - For Forge 1.20.1
- `Viscord-2.3.0-Fabric-1.19.2.jar` - For Fabric 1.19.2
- `Viscord-2.3.0-Forge-1.19.2.jar` - For Forge 1.19.2
- `Viscord-2.3.0-Fabric-1.18.2.jar` - For Fabric 1.18.2
- `Viscord-2.3.0-Forge-1.18.2.jar` - For Forge 1.18.2
- `RELEASE_NOTES_2.3.0.md` - Detailed release notes

## 🎨 New in 2.3.0

- **Clean Configuration Structure** - Reorganized config sections:
  - `server` (was `server_identity`)
  - `formats` (was `message_formats`)
  - `filters` (was `loop_prevention`)
  - `bot` (was `bot_status`)
  - `linking` (was `account_linking`)
- **Simplified Config Keys** - Removed redundant prefixes within sections
- **Improved Defaults** - Better out-of-box experience

## 🔗 Documentation

Open `Web Docs/localwebdocs.html` for comprehensive documentation.

## ⚡ Quick Test

Try this in chat: `§6§lHello World!§r`
Should appear in Discord as: 🟡 **Hello World!**

## 🆘 Need Help?

- Check the web documentation
- Review the release notes
- Enable debug mode in config for troubleshooting

---
**Viscord 2.3.0** - Clean config, better experience! 🎨✨
