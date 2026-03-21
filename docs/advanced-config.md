# Advanced Configuration Guide

This guide covers advanced configuration options, performance tuning, and expert features of Viscord.

## 📊 Performance Optimization

### Message Queue Management

For high-traffic servers with lots of chat activity:

```json
{
  "advanced": {
    "message_queue_size": 200,
    "rate_limit_delay": 500
  }
}
```

**Recommendations by Server Size:**
- **Small (1-10 players)**: `queue_size: 50`, `delay: 1000`
- **Medium (10-50 players)**: `queue_size: 100`, `delay: 750`
- **Large (50+ players)**: `queue_size: 200`, `delay: 500`
- **Network (100+ players)**: `queue_size: 500`, `delay: 250`

### Memory Usage Optimization

Reduce memory footprint:

```json
{
  "advanced": {
    "message_queue_size": 50,
    "debug_logging": false
  },
  "prevention": {
    "show_other_server_events": false
  }
}
```

### CPU Usage Tuning

Minimize CPU impact:

```json
{
  "advanced": {
    "rate_limit_delay": 2000,
    "message_queue_size": 25
  },
  "events": {
    "send_advancement": false
  }
}
```

## 🔧 Debugging & Monitoring

### Comprehensive Debug Mode

```json
{
  "advanced": {
    "debug_logging": true
  }
}
```

**Debug logs show:**
- Configuration loading
- Platform initialization
- Message processing details
- Webhook send/receive status
- Error stack traces

### Performance Monitoring

Monitor key metrics:

```json
{
  "advanced": {
    "debug_logging": true,
    "message_queue_size": 100
  }
}
```

Watch for these log patterns:
```
[Discord] Message queue size: 15/100
[Discord] Rate limit delay: 1000ms
[Discord] Webhook sent successfully
```

## 🎨 Advanced Formatting

### Rich Message Formatting

#### Discord → Minecraft
```json
{
  "formats": {
    "discord_to_minecraft": "§6[Discord] §b{username}§7: §f{message}"
  }
}
```

**Color Codes:**
- `§0` - Black
- `§1` - Dark Blue
- `§2` - Dark Green
- `§3` - Dark Cyan
- `§4` - Dark Red
- `§5` - Dark Purple
- `§6` - Gold
- `§7` - Gray
- `§8` - Dark Gray
- `§9` - Blue
- `§a` - Green
- `§b` - Cyan
- `§c` - Red
- `§d` - Light Purple
- `§e` - Yellow
- `§f` - White

#### Minecraft → Discord
```json
{
  "formats": {
    "minecraft_to_discord": "**{username}**: {message}"
  }
}
```

**Discord Markdown:**
- `**bold**` for bold text
- `*italic*` for italic text
- `~~strikethrough~~` for strikethrough
- `__underline__` for underline
- `||spoiler||` for spoiler

### Conditional Formatting

Different formats for different users:

```json
{
  "formats": {
    "discord_to_minecraft": "§6[Discord] {admin_color}{username}§7: §f{message}",
    "webhook_username": "{prefix}{username}"
  }
}
```

### Avatar URL Templates

#### Minotar (Default)
```json
{
  "formats": {
    "avatar_url": "https://minotar.net/armor/bust/{username}/100.png"
  }
}
```

#### Crafatar
```json
{
  "formats": {
    "avatar_url": "https://crafatar.com/avatars/{uuid}?size=100&overlay"
  }
}
```

#### Custom Service
```json
{
  "formats": {
    "avatar_url": "https://your-avatar-service.com/{username}.png"
  }
}
```

## 🔐 Security Configuration

### Webhook Security

#### Fluxer API Key Rotation
```json
{
  "fluxer": {
    "api_key": "NEW_API_KEY_HERE"
  }
}
```

#### Custom Receiver Path
```json
{
  "fluxer": {
    "path": "secret-webhook-abc123"
  }
}
```

### Rate Limiting Protection

#### Aggressive Rate Limiting
```json
{
  "advanced": {
    "rate_limit_delay": 3000
  }
}
```

#### Conservative Rate Limiting
```json
{
  "advanced": {
    "rate_limit_delay": 500
  }
}
```

### Message Filtering

#### Advanced Chat Filter
```json
{
  "prevention": {
    "enable_chat_filter": true,
    "chat_filter_prefix": "!",
    "filter_by_prefix": true,
    "ignore_bots": true,
    "ignore_webhooks": false
  }
}
```

#### Custom Filter Rules
```json
{
  "prevention": {
    "enable_chat_filter": true,
    "chat_filter_prefix": "!"
  }
}
```

Players can use:
- `!help` - Server help (in-game only)
- `!staff` - Contact staff (in-game only)
- `!rules` - Server rules (in-game only)

## 🌐 Network Configuration

### Multi-Server Network

#### Hub Server Configuration
```json
{
  "enabled": true,
  "platform": "discord",
  "server": {
    "prefix": "[HUB]",
    "name": "Network Hub"
  },
  "prevention": {
    "filter_by_prefix": false,
    "show_other_server_events": true
  },
  "events": {
    "send_join": false,
    "send_leave": false,
    "send_death": false,
    "send_advancement": false
  }
}
```

#### Game Server Configuration
```json
{
  "enabled": true,
  "platform": "discord",
  "server": {
    "prefix": "[SURVIVAL]",
    "name": "Survival World"
  },
  "prevention": {
    "filter_by_prefix": true,
    "show_other_server_events": true
  },
  "events": {
    "send_join": true,
    "send_leave": true,
    "send_death": true,
    "send_advancement": true
  }
}
```

### Cross-Version Compatibility

Different configurations per Minecraft version:

#### 1.18.2 Server
```json
{
  "formats": {
    "discord_to_minecraft": "§2[1.18.2] §f{username}: {message}"
  }
}
```

#### 1.21.1 Server
```json
{
  "formats": {
    "discord_to_minecraft": "§3[1.21.1] §f{username}: {message}"
  }
}
```

## 🎮 Event Customization

### Selective Event Broadcasting

#### Survival Server Events
```json
{
  "events": {
    "send_join": true,
    "send_leave": true,
    "send_death": true,
    "send_advancement": true
  }
}
```

#### Creative Server Events
```json
{
  "events": {
    "send_join": false,
    "send_leave": false,
    "send_death": false,
    "send_advancement": true
  }
}
```

#### Minigame Server Events
```json
{
  "events": {
    "send_join": true,
    "send_leave": true,
    "send_death": false,
    "send_advancement": false
  }
}
```

### Custom Event Messages

#### Death Message Filtering
Only send certain death messages:

```json
{
  "events": {
    "send_death": true
  },
  "prevention": {
    "enable_chat_filter": true,
    "chat_filter_prefix": "!"
  }
}
```

### Event Channel Configuration

#### Separate Event Channel
```json
{
  "events": {
    "event_channel_id": "123456789012345678",
    "event_webhook_url": "https://discord.com/api/webhooks/EVENT_WEBHOOK"
  }
}
```

## 🔧 Bot Behavior

### Custom Bot Status

#### Dynamic Status Updates
```json
{
  "status": {
    "enabled": true,
    "format": "{online}/{max} players | {server_name}"
  }
}
```

#### Time-Based Status
```json
{
  "status": {
    "enabled": true,
    "format": "{online} online | {server_name} | {time}"
  }
}
```

### Account Linking (Discord Only)

#### Strict Linking Requirements
```json
{
  "linking": {
    "enabled": true,
    "code_expiry_seconds": 120
  }
}
```

#### Relaxed Linking
```json
{
  "linking": {
    "enabled": true,
    "code_expiry_seconds": 600
  }
}
```

## 📈 Scaling Considerations

### Large Server Networks

#### Distributed Load
- Use separate event channels
- Implement server-specific filtering
- Monitor queue sizes closely

#### Resource Allocation
```json
{
  "advanced": {
    "message_queue_size": 500,
    "rate_limit_delay": 250
  },
  "prevention": {
    "show_other_server_events": false
  }
}
```

### High-Frequency Events

#### Event Throttling
```json
{
  "events": {
    "send_join": false,
    "send_leave": false,
    "send_death": true,
    "send_advancement": false
  }
}
```

#### Batch Processing
```json
{
  "advanced": {
    "message_queue_size": 1000,
    "rate_limit_delay": 100
  }
}
```

## 🛠️ Maintenance

### Configuration Backup

定期备份配置文件：

```bash
# Backup script example
cp config/viscord.json backups/viscord-$(date +%Y%m%d).json
```

### Health Monitoring

Monitor these indicators:
- Queue size vs capacity
- Rate limit hit frequency
- Error rate percentage
- Memory usage trends

### Performance Metrics

Track these metrics:
```json
{
  "advanced": {
    "debug_logging": true
  }
}
```

Look for patterns in logs:
- `[Discord] Queue full: dropping message`
- `[Discord] Rate limit hit, delaying`
- `[Discord] Webhook failed`

## 🔍 Troubleshooting Advanced

### Memory Leaks

**Symptoms**: Gradual memory increase over time
**Solutions**:
1. Reduce `message_queue_size`
2. Disable debug logging
3. Restart server periodically

### Performance Degradation

**Symptoms**: Increasing message delays
**Solutions**:
1. Increase `rate_limit_delay`
2. Check Discord API status
3. Monitor network connectivity

### Connection Issues

**Symptoms**: Intermittent disconnections
**Solutions**:
1. Check network stability
2. Verify webhook URLs
3. Monitor bot token validity

## ✅ Advanced Checklist

- [ ] Performance settings tuned for server size
- [ ] Debug mode configured for monitoring
- [ ] Security measures implemented
- [ ] Multi-server filtering configured
- [ ] Event customization optimized
- [ ] Backup procedures established
- [ ] Monitoring systems in place
- [ ] Performance benchmarks documented

## 📚 Additional Resources

- [Multi-Server Setup](./multi-server-setup.md) - Network configuration
- [Discord Setup](./discord-setup.md) - Basic Discord configuration
- [Fluxer Setup](./fluxer-setup.md) - Fluxer webhook configuration
- [Main README](../README.md) - General documentation

---

**Need help?** Check the troubleshooting section in the main README or open an issue on GitHub.
