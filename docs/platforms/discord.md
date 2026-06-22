# Discord Setup

Step-by-step from "no Discord app" to "Minecraft chat in my channel".

## 1. Create the application + bot

1. Go to **<https://discord.com/developers/applications>** and click **New Application**.
2. Name it (e.g. *Viscord Bot*) and create it.
3. In the left nav click **Bot**. Click **Add Bot** if not already added.
4. Under **Privileged Gateway Intents**, enable **Message Content Intent**. Without this the bot cannot read message bodies and Discord → MC chat will not work.
5. Click **Reset Token** (or **Copy Token** if visible). Copy the bot token — paste it into `discord.bot_token` in `viscord.toml`.

> Treat the token like a password. Viscord redacts webhook tokens from log output (4.2.0+), but it cannot redact a bot token that leaks via your own logs / source control.

## 2. Invite the bot

In the **OAuth2 → URL Generator** tab:

- **Scopes**: `bot`
- **Bot Permissions** (minimum):
  - `View Channel`
  - `Send Messages`
  - `Read Message History`
  - `Manage Webhooks` (only required if Viscord will auto-extract / introspect the webhook; not strictly required if you create the webhook manually and only provide the URL)
  - `Embed Links`

Open the generated URL in a browser and invite the bot to your guild.

## 3. Get the channel ID

1. In Discord settings → **Advanced** → enable **Developer Mode**.
2. Right-click the target channel → **Copy Channel ID**.
3. Paste it into `discord.channel_id`.

Multi-channel? Use a comma-separated list:

```toml
channel_id = "123456789012345678,234567890123456789"
```

The bot will watch (and bridge into) every channel in the list.

## 4. Create a webhook

The webhook is how Minecraft messages appear in Discord with **per-player avatar + username** rather than as the bot.

1. In the target Discord channel → **Edit Channel** → **Integrations** → **Webhooks** → **New Webhook**.
2. Name it (e.g. *Viscord*). The avatar you set here is the default if a player has no avatar resolver.
3. Click **Copy Webhook URL**.
4. Paste it into `discord.webhook_url`.

`discord.webhook_id` is optional — Viscord extracts it from the URL automatically.

## 5. (Optional) Split events to a separate channel

To send join/leave/death/advancement embeds to a different channel than chat:

```toml
[discord.events]
channel_id  = "987654321098765432"
webhook_url = "https://discord.com/api/webhooks/.../..."   # optional
```

## 6. (Optional) Wire the `/discord` command

```toml
[discord]
invite_url = "https://discord.gg/your-invite"
```

In-game players running `/discord` get a click-to-open invite link.

## Security checklist

- Bot token kept out of source control? ✓
- Webhook URL stored only in `viscord.toml` with restrictive file perms? ✓
- Bot has **only** the permissions it needs in the bridged channel(s)? ✓
- `filters.ignore_bots = true` (default) to prevent foreign-bot spam? ✓

For background on what Viscord redacts on its side, see [../security.md](../security.md).

## Troubleshooting

- **No messages from Discord reach Minecraft** — confirm **Message Content Intent** is on, the bot can see the channel (channel permissions, not just guild role), and `general.enabled = true`.
- **MC → Discord messages are missing avatars or appear as the bot** — `discord.webhook_url` is missing or points to the wrong channel.
- **Permission denied** errors in the log — re-invite the bot with the full permission set above.

See the full [troubleshooting guide](../troubleshooting.md) for more.
