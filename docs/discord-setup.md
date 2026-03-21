# Discord Setup Guide

This guide walks you through setting up Viscord with Discord for full bidirectional chat integration.

## 📋 Prerequisites

- Minecraft server with Viscord installed
- Discord server with admin permissions
- Basic understanding of Discord applications

## 🚀 Step 1: Create Discord Application

1. Go to [Discord Developer Portal](https://discord.com/developers/applications)
2. Click **"New Application"**
3. Give your application a name (e.g., "Viscord Bot")
4. Agree to terms and click **"Create"**

## 🤖 Step 2: Create Bot

1. In your application, go to **"Bot"** tab
2. Click **"Add Bot"**
3. Confirm by clicking **"Yes, do it!"**
4. Configure your bot:
   - **Name**: Your bot's display name
   - **Icon**: Upload a bot avatar (optional)
   - **Description**: Brief description (optional)

## ⚙️ Step 3: Configure Bot Permissions

### Enable Message Content Intent
1. Scroll down to **"Privileged Gateway Intents"**
2. **Enable** the **"Message Content Intent"** toggle
   - This is **required** for reading Discord messages
3. Click **"Save Changes"**

### Bot Permissions
Your bot needs these permissions:
- **Read Messages/View Channels**
- **Send Messages**
- **Embed Links** (for rich event messages)
- **Read Message History**
- **Use External Emojis** (optional)

## 🔑 Step 4: Get Bot Token

1. In the **"Bot"** tab, click **"Reset Token"** (or "View Token")
2. **Copy the token** - this is your `bot_token`
3. **Keep this secret!** Never share your bot token

## 📢 Step 5: Create Webhook

1. Go to your Discord server
2. Navigate to the channel you want to link
3. Click **Channel Settings** (gear icon)
4. Go to **"Integrations"**
5. Click **"Webhooks"**
6. Click **"New Webhook"**
7. Configure webhook:
   - **Name**: Viscord Webhook (or your preference)
   - **Avatar**: Upload webhook avatar (optional)
8. Click **"Copy Webhook URL"** - this is your `webhook_url`
9. Click **"Save"**

## 📝 Step 6: Get Channel ID

1. In Discord, enable **Developer Mode**:
   - User Settings → Advanced → Developer Mode
2. Right-click the channel you want to link
3. Select **"Copy Channel ID"** - this is your `channel_id`

## 🔗 Step 7: Invite Bot to Server

1. Go back to Discord Developer Portal
2. Select your application
3. Go to **"OAuth2" → "URL Generator"**
4. Select these scopes:
   - `bot`
   - `applications.commands`
5. Select these bot permissions:
   - Send Messages
   - Read Messages/View Channels
   - Embed Links
   - Read Message History
   - Use External Emojis
6. Copy the generated URL
7. Paste URL in browser and invite bot to your server

## ⚙️ Step 8: Configure Viscord

Edit `config/viscord.json`:

```json
{
  "enabled": true,
  "platform": "discord",
  "discord": {
    "bot_token": "YOUR_BOT_TOKEN_HERE",
    "channel_id": "YOUR_CHANNEL_ID_HERE",
    "webhook_url": "YOUR_WEBHOOK_URL_HERE",
    "invite_url": "https://discord.gg/yourserver"
  },
  "server": {
    "prefix": "[MC]",
    "name": "My Minecraft Server"
  }
}
```

Replace the placeholder values:
- `YOUR_BOT_TOKEN_HERE` - Token from Step 4
- `YOUR_CHANNEL_ID_HERE` - Channel ID from Step 6
- `YOUR_WEBHOOK_URL_HERE` - Webhook URL from Step 5

## 🚀 Step 9: Test Integration

1. Restart your Minecraft server
2. Check server logs for successful initialization:
   ```
   [Discord] Integration initialized.
   [Discord] Bot connected, sending startup embed
   ```
3. Test in Discord:
   - Send a message in the linked channel
   - Should appear in Minecraft as `[Discord] Username: message`
4. Test in Minecraft:
   - Send a chat message
   - Should appear in Discord via webhook

## 🎯 Optional Configurations

### Server Events
Enable event notifications:

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

### Custom Message Formats
Personalize message appearance:

```json
{
  "formats": {
    "discord_to_minecraft": "§6[Discord] §f{username}: §7{message}",
    "minecraft_to_discord": "**{username}**: {message}",
    "webhook_username": "{prefix}{username}"
  }
}
```

### Separate Event Channel
Send events to a different channel:

```json
{
  "events": {
    "event_channel_id": "EVENT_CHANNEL_ID_HERE",
    "event_webhook_url": "EVENT_WEBHOOK_URL_HERE"
  }
}
```

## 🔧 Troubleshooting

### Bot Not Connecting
- Verify bot token is correct
- Check bot has Message Content Intent enabled
- Ensure bot is properly invited to server

### Messages Not Appearing
- Check channel ID matches exactly
- Verify webhook URL is correct
- Ensure bot has permissions in the channel

### Rate Limiting
- Increase `rate_limit_delay` in advanced settings
- Check for spam or rapid message sending

### Debug Mode
Enable detailed logging:

```json
{
  "advanced": {
    "debug_logging": true
  }
}
```

## 📚 Additional Features

### Account Linking
Allow players to link Discord accounts:

```json
{
  "linking": {
    "enabled": true
  }
}
```

Players can use:
- `/link` in-game to get a code
- `/link <code>` in Discord to complete linking

### Bot Status
Show player count in Discord status:

```json
{
  "status": {
    "enabled": true,
    "format": "{online}/{max} players online"
  }
}
```

## 🛡️ Security Tips

1. **Never share your bot token** - treat it like a password
2. **Use application commands** for sensitive operations
3. **Limit bot permissions** to only what's needed
4. **Regularly rotate tokens** if compromised
5. **Monitor bot activity** in Discord audit logs

## ✅ Checklist

- [ ] Discord application created
- [ ] Bot created with Message Content Intent
- [ ] Bot token copied and secured
- [ ] Webhook created in target channel
- [ ] Channel ID obtained
- [ ] Bot invited to server with proper permissions
- [ ] Viscord configured with correct values
- [ ] Server restarted and logs checked
- [ ] Bidirectional communication tested
- [ ] Events and optional features configured

---

**Need help?** Check the [main README](../README.md) or open an issue on GitHub.
