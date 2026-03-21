# Viscord

[![Minecraft](https://img.shields.io/badge/Minecraft-1.18.2%20%7C%201.19.2%20%7C%201.20.1%20%7C%201.21.1-brightgreen.svg)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-API-blue.svg)](https://fabricmc.net)
[![Forge](https://img.shields.io/badge/Forge-orange.svg)](https://mcforge.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-purple.svg)](https://neoforged.net)

**Viscord** is a powerful bidirectional chat integration mod that connects your Minecraft server to Discord or Fluxer. Bridge the gap between your in-game community and your preferred chat platform with real-time message synchronization.

## ✨ Features

- **Dual Platform Support**: Choose between Discord (full bot integration) or Fluxer (simple webhook service)
- **Bidirectional Chat**: Messages flow seamlessly between Minecraft and your chosen platform
- **Event Notifications**: Share player joins, leaves, deaths, and advancements
- **Customizable Formatting**: Tailor message appearance to match your server's style
- **Multi-Server Support**: Connect multiple servers with unique prefixes
- **Account Linking** (Discord only): Link Minecraft and Discord accounts
- **Player Preferences**: Let users control which messages they see
- **Performance Optimized**: Efficient message handling with rate limiting

## 🚀 Quick Start

### 1. Installation

1. Download the appropriate JAR for your Minecraft version and mod loader
2. Place the JAR in your server's `mods` folder
3. Restart the server

### 2. Configuration

Edit `config/viscord.json`:

```json
{
  "enabled": true,
  "platform": "discord",
  "discord": {
    "bot_token": "YOUR_BOT_TOKEN_HERE",
    "channel_id": "YOUR_CHANNEL_ID_HERE",
    "webhook_url": "YOUR_WEBHOOK_URL_HERE"
  }
}
```

### 3. Platform Setup

#### Discord Setup
1. Create a Discord application at [https://discord.com/developers/applications](https://discord.com/developers/applications)
2. Create a bot and enable **Message Content Intent**
3. Invite the bot to your server with required permissions
4. Create a webhook in your target channel
5. Fill in the configuration values

#### Fluxer Setup
1. Get your webhook URL from the Fluxer dashboard
2. Configure Fluxer to send messages to: `http://your-server-ip:8080/webhook`
3. Fill in the Fluxer configuration values

## 📖 Configuration

### Basic Settings

| Setting | Description | Default |
|---------|-------------|---------|
| `enabled` | Master toggle for all features | `false` |
| `platform` | Chat platform: `"discord"` or `"fluxer"` | `"discord"` |

### Discord Settings

| Setting | Description | Required |
|---------|-------------|----------|
| `bot_token` | Discord bot token | ✅ |
| `channel_id` | Target channel ID | ✅ |
| `webhook_url` | Webhook URL for sending messages | ✅ |
| `webhook_id` | Webhook ID (auto-extracted if empty) | ❌ |
| `invite_url` | Server invite URL for `/discord` command | ❌ |

### Fluxer Settings

| Setting | Description | Required |
|---------|-------------|----------|
| `webhook_url` | Fluxer webhook URL | ✅ |
| `event_webhook_url` | Separate webhook for events | ❌ |
| `api_key` | Fluxer API key | ✅ |
| `port` | Receiver port for incoming messages | `8080` |
| `path` | Receiver endpoint path | `"webhook"` |

### Server Identity

| Setting | Description | Default |
|---------|-------------|---------|
| `server.prefix` | Server prefix in messages | `"[MC]"` |
| `server.name` | Server name | `"Minecraft Server"` |
| `server.avatar_url` | Server avatar URL | `""` |

### Message Formats

| Setting | Description | Default |
|---------|-------------|---------|
| `formats.discord_to_minecraft` | Discord → Minecraft format | `"§b[Discord] §f{username}: {message}"` |
| `formats.minecraft_to_discord` | Minecraft → Discord format | `"{message}"` |
| `formats.webhook_username` | Webhook username format | `"{prefix}{username}"` |
| `formats.avatar_url` | Player avatar URL | `"https://minotar.net/armor/bust/{username}/100.png"` |

### Event Notifications

| Setting | Description | Default |
|---------|-------------|---------|
| `events.send_join` | Send player join notifications | `true` |
| `events.send_leave` | Send player leave notifications | `true` |
| `events.send_death` | Send player death messages | `true` |
| `events.send_advancement` | Send advancement notifications | `true` |
| `events.event_channel_id` | Separate channel for events | `""` |
| `events.event_webhook_url` | Separate webhook for events | `""` |

## 🔧 Advanced Features

### Multi-Server Setup

Connect multiple servers to the same Discord channel:

1. Set unique server prefixes in each server's config
2. Enable `prevention.filter_by_prefix` to prevent message loops
3. Configure `prevention.show_other_server_events` to control event visibility

### Chat Filtering

Create in-game-only messages:

```json
{
  "prevention": {
    "enable_chat_filter": true,
    "chat_filter_prefix": "!"
  }
}
```

Messages starting with `!` will stay in-game only.

### Account Linking (Discord only)

Allow players to link their Minecraft and Discord accounts:

1. Enable `linking.enabled` in config
2. Players use `/link` in-game to get a code
3. They use `/link <code>` in Discord to complete linking

## 🛠️ Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/discord` | Shows Discord invite URL | All players |
| `/link` | Generate account linking code | All players |
| `/unlink` | Unlink account | All players |

## 📊 Performance

- **Low Memory Usage**: Efficient message processing
- **Rate Limiting**: Built-in protection against API limits
- **Async Operations**: Non-blocking message handling
- **Configurable Queues**: Adjust message queue size for your needs

## 🔐 Security

- **Token Protection**: Bot tokens and API keys are never logged
- **Webhook Validation**: Validates incoming webhook payloads
- **Firewall Friendly**: Only requires one open port for Fluxer
- **Permission System**: Fine-grained control over features

## 🐛 Troubleshooting

### Common Issues

**Q: Config file isn't generating**
- Ensure you have write permissions in the `config` folder
- Check the server logs for any errors

**Q: Discord messages aren't appearing in Minecraft**
- Verify bot has Message Content Intent enabled
- Check that the bot is in the correct channel
- Ensure `enabled` is set to `true`

**Q: Minecraft messages aren't appearing in Discord**
- Verify webhook URL is correct
- Check that the webhook has permissions to send messages
- Ensure the channel ID matches your webhook's channel

**Q: Fluxer isn't receiving messages**
- Confirm port 8080 is open in your firewall
- Check that Fluxer is pointing to `http://your-server-ip:8080/webhook`
- Verify your server's public IP address

### Debug Mode

Enable detailed logging:

```json
{
  "advanced": {
    "debug_logging": true
  }
}
```

## 📝 Changelog

See [CHANGELOG.md](CHANGELOG.md) for version history and updates.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Credits

- **Architectury API** - Cross-platform mod development
- **Javacord** - Discord API library
- **Jackson** - JSON processing
- **OkHttp** - HTTP client library

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/your-repo/viscord/issues)
- **Discord**: [Join our Discord](https://discord.gg/your-invite)

---

**Enjoy seamless chat integration with Viscord! 🎮💬**
