# Multi-Server Setup Guide

This guide explains how to connect multiple Minecraft servers to a single Discord channel using Viscord.

## 🎯 Use Cases

- **Network Hub**: Connect survival, creative, and minigame servers
- **Cross-Version**: Link different Minecraft versions
- **Cluster Setup**: Multiple servers in different locations
- **Development**: Connect test and production servers

## 📋 Prerequisites

- Multiple Minecraft servers with Viscord installed
- One Discord server with admin permissions
- Unique server prefixes for identification

## 🏗️ Architecture Overview

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Server 1  │    │   Server 2  │    │   Server 3  │
│   [SURVIVAL] │    │  [CREATIVE] │    │ [MINIGAMES] │
└──────┬──────┘    └──────┬──────┘    └──────┬──────┘
       │                  │                  │
       └──────────────────┼──────────────────┘
                          │
                   ┌──────▼──────┐
                   │   Discord   │
                   │   Channel   │
                   └─────────────┘
```

## ⚙️ Step 1: Discord Setup

### Single Bot Approach (Recommended)

1. **Create one Discord bot** following the [Discord Setup Guide](./discord-setup.md)
2. **Create one webhook** in the target Discord channel
3. **Get the webhook URL** - all servers will use this

### Multiple Webhooks Approach (Advanced)

1. Create separate webhooks for each server
2. Use different webhook usernames/avatars
3. Configure each server with its webhook URL

## 🔧 Step 2: Configure Each Server

### Server 1 - Survival

Edit `config/viscord.json`:

```json
{
  "enabled": true,
  "platform": "discord",
  "discord": {
    "bot_token": "YOUR_BOT_TOKEN",
    "channel_id": "YOUR_CHANNEL_ID",
    "webhook_url": "YOUR_WEBHOOK_URL"
  },
  "server": {
    "prefix": "[SURVIVAL]",
    "name": "Survival Server"
  },
  "prevention": {
    "filter_by_prefix": true,
    "show_other_server_events": true
  }
}
```

### Server 2 - Creative

```json
{
  "enabled": true,
  "platform": "discord",
  "discord": {
    "bot_token": "YOUR_BOT_TOKEN",
    "channel_id": "YOUR_CHANNEL_ID",
    "webhook_url": "YOUR_WEBHOOK_URL"
  },
  "server": {
    "prefix": "[CREATIVE]",
    "name": "Creative Server"
  },
  "prevention": {
    "filter_by_prefix": true,
    "show_other_server_events": true
  }
}
```

### Server 3 - Minigames

```json
{
  "enabled": true,
  "platform": "discord",
  "discord": {
    "bot_token": "YOUR_BOT_TOKEN",
    "channel_id": "YOUR_CHANNEL_ID",
    "webhook_url": "YOUR_WEBHOOK_URL"
  },
  "server": {
    "prefix": "[GAMES]",
    "name": "Minigame Server"
  },
  "prevention": {
    "filter_by_prefix": true,
    "show_other_server_events": true
  }
}
```

## 🎨 Step 3: Customization Options

### Unique Server Identities

Make each server visually distinct:

```json
{
  "server": {
    "prefix": "[SURVIVAL]",
    "name": "🏝️ Survival Island",
    "avatar_url": "https://example.com/survival-icon.png"
  },
  "formats": {
    "discord_to_minecraft": "§2[SURVIVAL] §f{username}: §7{message}",
    "webhook_username": "{prefix}{username}"
  }
}
```

### Event Channel Separation

Send events to a separate channel:

```json
{
  "events": {
    "send_join": true,
    "send_leave": true,
    "send_death": true,
    "event_channel_id": "EVENTS_CHANNEL_ID",
    "event_webhook_url": "EVENTS_WEBHOOK_URL"
  }
}
```

### Server-Specific Commands

Different command prefixes per server:

```json
{
  "prevention": {
    "enable_chat_filter": true,
    "chat_filter_prefix": "!"
  }
}
```

Players can use `!help` for server-specific help that stays in-game.

## 🔄 Step 4: Message Flow Examples

### Normal Chat Flow

```
Server 1 (PlayerA): Hello everyone!
↓
Discord: [SURVIVAL] PlayerA: Hello everyone!
↓
Server 2: §2[SURVIVAL] PlayerA: Hello everyone!
↓
Server 3: §2[SURVIVAL] PlayerA: Hello everyone!
```

### Event Flow

```
Server 1: PlayerB joined the game
↓
Discord: 🎮 [SURVIVAL] PlayerB joined the game
↓
Server 2: §a[SURVIVAL] PlayerB joined the game (if show_other_server_events: true)
↓
Server 3: §a[SURVIVAL] PlayerB joined the game (if show_other_server_events: true)
```

## 🛡️ Step 5: Prevention Settings

### Loop Prevention

Essential for multi-server setups:

```json
{
  "prevention": {
    "filter_by_prefix": true,
    "show_other_server_events": true,
    "ignore_bots": false,
    "ignore_webhooks": false
  }
}
```

**Explanation:**
- `filter_by_prefix`: Prevents echoing messages from same server
- `show_other_server_events`: Shows events from other servers
- `ignore_bots`: Set to `false` to allow cross-server messages

### Chat Filtering

Create server-specific commands:

```json
{
  "prevention": {
    "enable_chat_filter": true,
    "chat_filter_prefix": "!"
  }
}
```

Messages starting with `!` stay in-game only.

## 🚀 Step 6: Testing

### Test Message Flow

1. **Server 1**: Send "Hello from Survival!"
2. **Expected in Discord**: `[SURVIVAL] PlayerName: Hello from Survival!`
3. **Expected in Server 2**: `§2[SURVIVAL] PlayerName: Hello from Survival!`
4. **Expected in Server 3**: `§2[SURVIVAL] PlayerName: Hello from Survival!`

### Test Events

1. **Join Server 1**
2. **Expected**: Event appears in Discord and other servers
3. **Test filtering**: Try same prefix - should be filtered

### Test Filtering

1. **Send `!server help` in any server**
2. **Expected**: Stays in-game, doesn't go to Discord

## 🔧 Advanced Configurations

### Load Balancing

Distribute webhook calls:

```json
{
  "advanced": {
    "message_queue_size": 200,
    "rate_limit_delay": 500
  }
}
```

### Conditional Event Sharing

Only share certain events:

```json
{
  "events": {
    "send_join": false,
    "send_leave": false,
    "send_death": true,
    "send_advancement": true
  },
  "prevention": {
    "show_other_server_events": true
  }
}
```

### Server Roles

Different server types with different behaviors:

**Hub Server** (relays all messages):
```json
{
  "prevention": {
    "filter_by_prefix": false,
    "show_other_server_events": true
  }
}
```

**Game Server** (only shows its own events):
```json
{
  "prevention": {
    "filter_by_prefix": true,
    "show_other_server_events": false
  }
}
```

## 🐛 Troubleshooting

### Message Loops

**Symptoms**: Messages repeating infinitely
**Solution**: 
1. Ensure `filter_by_prefix: true`
2. Check all servers have unique prefixes
3. Verify no duplicate bot tokens

### Missing Messages

**Symptoms**: Messages not appearing in some servers
**Solution**:
1. Check all servers use same `channel_id`
2. Verify webhook URL is accessible from all servers
3. Check network connectivity between servers

### Event Spam

**Symptoms**: Too many event messages
**Solution**:
1. Set `show_other_server_events: false` on busy servers
2. Disable certain event types
3. Use separate event channel

## 📊 Performance Monitoring

### Debug Mode

Enable on one server to monitor:

```json
{
  "advanced": {
    "debug_logging": true
  }
}
```

### Message Queue Monitoring

Watch queue sizes:

```json
{
  "advanced": {
    "message_queue_size": 100,
    "rate_limit_delay": 1000
  }
}
```

## ✅ Checklist

- [ ] Discord bot created with proper permissions
- [ ] Webhook created in target channel
- [ ] All servers configured with unique prefixes
- [ ] Loop prevention enabled on all servers
- [ ] Event sharing preferences set
- [ ] Message flow tested between all servers
- [ ] Event notifications tested
- [ ] Chat filtering tested
- [ ] Performance settings tuned
- [ ] Backup configurations created

## 🎯 Best Practices

1. **Use consistent prefixes** - `[SURVIVAL]`, `[CREATIVE]`, etc.
2. **Test with 2 servers first** before adding more
3. **Monitor Discord rate limits** with many servers
4. **Use separate event channels** for high-traffic networks
5. **Document your setup** for easy troubleshooting
6. **Regular backups** of configuration files
7. **Test failover** - what happens if one server goes down

## 📚 Related Guides

- [Discord Setup Guide](./discord-setup.md) - Basic Discord configuration
- [Advanced Configuration](./advanced-config.md) - Performance and features
- [Troubleshooting](../README.md#-troubleshooting) - Common issues

---

**Need help?** Check the [main README](../README.md) or open an issue on GitHub.
