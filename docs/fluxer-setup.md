# Fluxer Setup Guide

This guide walks you through setting up Viscord with Fluxer for simple webhook-based chat integration.

## 📋 Prerequisites

- Minecraft server with Viscord installed
- Fluxer account and dashboard access
- Server with port forwarding capabilities (for receiving messages)

## 🚀 Step 1: Get Fluxer Credentials

1. Log in to your [Fluxer Dashboard](https://fluxer.example.com)
2. Navigate to **"Webhooks"** section
3. Create a new webhook or use existing one
4. Note down:
   - **Webhook URL** - for sending Minecraft messages TO Fluxer
   - **API Key** - for authentication

## 🌐 Step 2: Configure Server Network

### Port Forwarding
Fluxer needs to send messages back to your server:

1. **Choose a port** (default: 8080)
2. **Forward the port** in your router/firewall:
   - External Port: 8080
   - Internal Port: 8080
   - Internal IP: Your server's local IP
3. **Test the port** using an online port checker

### Find Your Public IP
1. Visit [whatismyip.com](https://whatismyip.com)
2. Note your public IP address
3. Your receiver URL will be: `http://YOUR_PUBLIC_IP:8080/webhook`

## 🔧 Step 3: Configure Fluxer

In your Fluxer dashboard:

1. **Set Outgoing Webhook**:
   - URL: `http://YOUR_PUBLIC_IP:8080/webhook`
   - Method: POST
   - Content-Type: application/json

2. **Configure Message Format** (if available):
   ```json
   {
     "username": "{sender}",
     "message": "{content}",
     "avatar_url": "{avatar}"
   }
   ```

3. **Test the webhook** to ensure Fluxer can reach your server

## ⚙️ Step 4: Configure Viscord

Edit `config/viscord.json`:

```json
{
  "enabled": true,
  "platform": "fluxer",
  "fluxer": {
    "webhook_url": "YOUR_FLUXER_WEBHOOK_URL",
    "api_key": "YOUR_FLUXER_API_KEY",
    "port": 8080,
    "path": "webhook"
  },
  "server": {
    "prefix": "[MC]",
    "name": "My Minecraft Server"
  }
}
```

Replace the placeholder values:
- `YOUR_FLUXER_WEBHOOK_URL` - From Step 1
- `YOUR_FLUXER_API_KEY` - From Step 1
- `port` - Same port you forwarded (default: 8080)
- `path` - Endpoint path (default: "webhook")

## 🚀 Step 5: Test Integration

1. **Restart your Minecraft server**
2. **Check server logs** for successful initialization:
   ```
   [Fluxer] HTTP server started on port 8080 at path /webhook
   [Fluxer] Fluxer integration initialized with webhook and receiver
   ```

3. **Test Fluxer → Minecraft**:
   - Send a message through Fluxer
   - Should appear in Minecraft as `[Fluxer] Username: message`

4. **Test Minecraft → Fluxer**:
   - Send a chat message in Minecraft
   - Should appear in Fluxer

## 🎯 Optional Configurations

### Separate Event Webhook
Send server events to a different Fluxer channel:

```json
{
  "fluxer": {
    "webhook_url": "https://fluxer.example.com/webhook/main",
    "event_webhook_url": "https://fluxer.example.com/webhook/events",
    "api_key": "YOUR_FLUXER_API_KEY"
  }
}
```

### Custom Port and Path
Use different port/path for security or multiple instances:

```json
{
  "fluxer": {
    "port": 9090,
    "path": "my-secret-webhook",
    "webhook_url": "YOUR_FLUXER_WEBHOOK_URL"
  }
}
```

Fluxer URL would then be: `http://YOUR_PUBLIC_IP:9090/my-secret-webhook`

### Custom Message Format
Change how messages appear in Minecraft:

```json
{
  "formats": {
    "discord_to_minecraft": "§a[Fluxer] §f{username}: §7{message}"
  }
}
```

## 🔧 Troubleshooting

### Fluxer Messages Not Reaching Minecraft

**Check Network:**
1. Verify port is forwarded correctly
2. Test port with online checker
3. Check firewall isn't blocking the port
4. Ensure server's public IP is correct

**Check Fluxer Config:**
1. Verify webhook URL format: `http://IP:PORT/PATH`
2. Ensure using `http://` not `https://`
3. Check Fluxer dashboard for error logs

**Check Viscord Logs:**
```json
{
  "advanced": {
    "debug_logging": true
  }
}
```
Look for:
```
[Fluxer] Received POST request from...
[Fluxer] Received webhook payload: {...}
```

### Minecraft Messages Not Reaching Fluxer

**Check Webhook URL:**
1. Verify Fluxer webhook URL is correct
2. Test URL in browser (should show Fluxer page)
3. Check API key is valid

**Check Viscord Config:**
1. Ensure `platform` is set to `"fluxer"`
2. Verify `webhook_url` is not empty
3. Check `api_key` is correct

### Port Already in Use

**Change Port:**
1. Pick a different port (e.g., 8081, 9090)
2. Update port forwarding
3. Update Viscord config:
```json
{
  "fluxer": {
    "port": 8081
  }
}
```
4. Update Fluxer webhook URL

## 🛡️ Security Considerations

### Protect Your Webhook Endpoint

1. **Use non-standard path**:
   ```json
   {
     "fluxer": {
       "path": "random-string-12345"
     }
   }
   ```

2. **Use firewall rules** to only allow Fluxer IPs

3. **Monitor logs** for unusual activity:
   ```json
   {
     "advanced": {
       "debug_logging": true
     }
   }
   ```

### API Key Security

1. **Never share your API key**
2. **Rotate keys regularly** if compromised
3. **Use environment variables** for production

## 📊 Performance Tips

### Rate Limiting
If Fluxer has rate limits:

```json
{
  "advanced": {
    "rate_limit_delay": 2000
  }
}
```

### Message Queue
For high-traffic servers:

```json
{
  "advanced": {
    "message_queue_size": 200
  }
}
```

## 🔄 Testing with curl

Test your webhook endpoint manually:

```bash
curl -X POST http://YOUR_SERVER_IP:8080/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "username": "TestUser",
    "message": "Hello from Fluxer!",
    "avatar_url": "https://example.com/avatar.png"
  }'
```

Should see "OK" response and message in Minecraft.

## ✅ Checklist

- [ ] Fluxer webhook URL and API key obtained
- [ ] Port forwarded in router/firewall
- [ ] Public IP address determined
- [ ] Fluxer configured with receiver URL
- [ ] Viscord configured with Fluxer settings
- [ ] Server restarted and logs checked
- [ ] Fluxer → Minecraft tested
- [ ] Minecraft → Fluxer tested
- [ ] Optional features configured
- [ ] Security measures implemented

## 🆚 Fluxer vs Discord

| Feature | Fluxer | Discord |
|---------|--------|---------|
| Setup Complexity | Simple | Moderate |
| Features | Basic | Full-featured |
| Account Linking | No | Yes |
| Bot Commands | No | Yes |
| Rich Embeds | Limited | Full |
| Rate Limits | Depends on plan | Discord limits |
| Cost | May require subscription | Free |

---

**Need help?** Check the [main README](../README.md) or open an issue on GitHub.
