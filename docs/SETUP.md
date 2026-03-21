# Setup Guides

This directory contains detailed setup guides for different configurations and use cases of Viscord.

## 📚 Available Guides

- [Discord Setup](./discord-setup.md) - Complete Discord bot and webhook configuration
- [Fluxer Setup](./fluxer-setup.md) - Fluxer webhook service configuration
- [Multi-Server Setup](./multi-server-setup.md) - Connecting multiple servers to one Discord
- [Advanced Configuration](./advanced-config.md) - Performance tuning and advanced features

## 🚀 Quick Start

1. **Choose Your Platform**: Decide between Discord (full-featured) or Fluxer (simple webhooks)
2. **Follow Platform Guide**: Complete the setup for your chosen platform
3. **Configure Basic Settings**: Set server name, prefix, and message formats
4. **Test Integration**: Send test messages to verify bidirectional communication

## 🔧 Configuration Examples

### Minimal Discord Setup
```json
{
  "enabled": true,
  "platform": "discord",
  "discord": {
    "bot_token": "YOUR_BOT_TOKEN",
    "channel_id": "YOUR_CHANNEL_ID",
    "webhook_url": "YOUR_WEBHOOK_URL"
  }
}
```

### Minimal Fluxer Setup
```json
{
  "enabled": true,
  "platform": "fluxer",
  "fluxer": {
    "webhook_url": "https://fluxer.example.com/webhook",
    "api_key": "YOUR_FLUXER_API_KEY",
    "port": 8080,
    "path": "webhook"
  }
}
```

### Production Setup with Events
```json
{
  "enabled": true,
  "platform": "discord",
  "discord": {
    "bot_token": "YOUR_BOT_TOKEN",
    "channel_id": "YOUR_CHANNEL_ID",
    "webhook_url": "YOUR_WEBHOOK_URL",
    "invite_url": "https://discord.gg/yourserver"
  },
  "events": {
    "send_join": true,
    "send_leave": true,
    "send_death": true,
    "send_advancement": true,
    "event_channel_id": "123456789012345678"
  },
  "server": {
    "prefix": "[SMP]",
    "name": "My Survival Server"
  }
}
```

## 🐛 Troubleshooting

### Common Issues and Solutions

| Issue | Solution |
|-------|----------|
| Config not generating | Check file permissions and restart server |
| Messages not appearing | Verify platform-specific settings (bot token, webhook URL) |
| Rate limiting errors | Increase `rate_limit_delay` in advanced settings |
| Message loops | Enable `filter_by_prefix` and ensure unique server prefixes |

### Debug Mode

Enable detailed logging to troubleshoot issues:

```json
{
  "advanced": {
    "debug_logging": true
  }
}
```

Check your server logs for detailed information about:
- Configuration loading
- Platform initialization
- Message processing
- Connection status

## 📖 Additional Resources

- [Main README](../README.md) - General information and overview
- [Configuration Reference](../README.md#-configuration) - Full config documentation
- [CHANGELOG](../CHANGELOG.md) - Version history and updates

## 💡 Tips

1. **Start Simple**: Begin with minimal configuration, then add features
2. **Test Thoroughly**: Verify both directions of message flow
3. **Monitor Performance**: Use debug mode to check for issues
4. **Backup Config**: Keep a copy of your working configuration
5. **Stay Updated**: Check CHANGELOG for new features and changes

---

Need help? Check the individual setup guides or open an issue on GitHub.
