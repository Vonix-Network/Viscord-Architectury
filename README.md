# Viscord

[![Minecraft](https://img.shields.io/badge/Minecraft-1.18.2%20%7C%201.19.2%20%7C%201.20.1%20%7C%201.21.1%20%7C%2026.1.2-brightgreen.svg)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-API-blue.svg)](https://fabricmc.net)
[![Forge](https://img.shields.io/badge/Forge-orange.svg)](https://mcforge.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-purple.svg)](https://neoforged.net)

**Viscord** is a server-side bidirectional chat-integration mod that bridges your Minecraft server to Discord, Fluxer, or both at once. Players do **not** need to install the mod.

> 📖 **Full documentation:** see [`docs/`](docs/README.md) for navigable guides — getting started, configuration reference, platform setup, account linking, multi-server, performance/threading internals, security model, troubleshooting, architecture, and build instructions.

## ✨ Features

- **Two platforms, four modes**: Discord-only, Fluxer-only, both at once, or tridirectional Discord ↔ Fluxer ↔ Minecraft bridging
- **Five Minecraft version lanes**: 1.18.2, 1.19.2, 1.20.1 (Fabric + Forge), 1.21.1 (Fabric + NeoForge), and 26.1.2 (NeoForge 26.1.2.93 / Java 25)
- **Bidirectional chat** with rich formatting (`§` and `&` color codes, Discord markdown)
- **Event notifications**: joins, leaves, deaths, advancements, server up/down
- **Customizable formats** via `{username}`, `{message}`, `{prefix}` placeholders
- **Multi-channel monitoring**: comma-separated channel IDs let one bot watch many channels
- **Multi-server support**: unique prefixes + trusted bot allowlist for cross-server relay
- **Account linking**: secure 6-digit code system tying Minecraft UUIDs to Discord IDs (SecureRandom, atomic bind)
- **Per-player preferences**: each player can toggle Discord chat, events, and system messages
- **Hot reload**: `/viscord reload` re-reads config without a server restart
- **Fully asynchronous**: bounded thread pool, scheduled status coalescing — never blocks the tick loop
- **Server-side only**: clients connect to a vanilla server without modification — no client install required

## 🚀 Quick Start

### 1. Installation

1. Download the JAR for your Minecraft version and mod loader
2. Place the JAR in your server's `mods/` folder
3. Restart the server once — Viscord writes a default `config/viscord/viscord.toml`
4. Edit the config, then run `/viscord reload` or restart again

### 2. Configuration

Viscord uses **TOML** at `config/viscord/viscord.toml`. The file is generated with extensive inline comments on every key. Legacy `config/viscord.json` is auto-migrated to TOML on first run and backed up as `viscord.json.backup`.

Minimum required to bridge Discord chat:

```toml
[general]
enabled  = true
platform = "discord"   # "discord" | "fluxer" | "both"

[discord]
bot_token   = "YOUR_BOT_TOKEN_HERE"
channel_id  = "123456789012345678"          # comma-separated for multi-channel
webhook_url = "https://discord.com/api/webhooks/.../..."
invite_url  = "https://discord.gg/your-invite"   # optional

[server]
prefix = "[MC]"
name   = "My Server"
```

### 3. Platform Setup

#### Discord

1. Create an application at [discord.com/developers/applications](https://discord.com/developers/applications)
2. Add a bot, enable **Message Content Intent**, copy the token
3. Invite the bot with `Read Messages`, `Send Messages`, `Manage Webhooks`
4. Create a webhook in the target channel (Channel Settings → Integrations → Webhooks)
5. Fill `discord.bot_token`, `discord.channel_id`, `discord.webhook_url`

#### Fluxer

1. Get a bot token from the Fluxer Developer Portal
2. Fluxer connects over WebSocket Gateway — **no port forwarding required**
3. Fill `fluxer.bot_token`, `fluxer.channel_id`
4. Optional: `fluxer.webhook_url` for player avatars/usernames on Fluxer messages

#### Tridirectional (Discord ↔ Fluxer ↔ Minecraft)

Configure both platforms, then:

```toml
[general]
platform = "discord"

[general.tridirectional]
enabled            = true
discord_to_fluxer  = true
fluxer_to_discord  = true
show_source        = true
```

## 📖 Configuration Reference

All keys are documented inline in the generated `viscord.toml`. Quick reference:

### `[general]`

| Key | Default | Description |
|---|---|---|
| `enabled` | `false` | Master toggle |
| `platform` | `"discord"` | `discord` \| `fluxer` \| `both` |
| `debug` | `false` | Verbose logging |

### `[general.tridirectional]`

| Key | Default | Description |
|---|---|---|
| `enabled` | `false` | Enable 3-way bridging |
| `discord_to_fluxer` | `true` | Relay Discord → Fluxer |
| `fluxer_to_discord` | `true` | Relay Fluxer → Discord |
| `show_source` | `true` | Tag bridged messages with `[Discord]` / `[Fluxer]` |

### `[discord]`

| Key | Required | Description |
|---|---|---|
| `bot_token` | ✅ | Bot token from Discord developer portal |
| `channel_id` | ✅ | Channel ID(s), comma-separated for multi-channel |
| `webhook_url` | ✅ | Webhook for rich MC → Discord messages |
| `webhook_id` | ❌ | Auto-extracted from URL if empty |
| `invite_url` | ❌ | URL shown by `/discord` command |

`[discord.events]` adds optional `channel_id` and `webhook_url` for splitting event notifications to a separate channel.

### `[fluxer]`

| Key | Required | Description |
|---|---|---|
| `bot_token` | ✅ | Fluxer bot token |
| `channel_id` | ✅ | Chat channel ID(s) |
| `event_channel_id` | ❌ | Separate event channel |
| `webhook_url` | ❌ | For per-player avatars on MC → Fluxer |

### `[server]`

| Key | Default | Description |
|---|---|---|
| `prefix` | `"[MC]"` | Server identifier in bridged messages |
| `name` | `"Minecraft Server"` | Display name for bot status / embeds |
| `avatar_url` | `""` | Server avatar URL |

### `[messages]`

| Key | Default | Description |
|---|---|---|
| `discord_to_minecraft` | `"[Discord] {username}: {message}"` | MC chat format |
| `minecraft_to_discord` | `"{message}"` | Discord chat format |
| `webhook_username` | `"{prefix} {username}"` | Webhook display name |
| `use_display_name` | `true` | Use Discord server-nick → global-name → username |

`[messages.events]` toggles `join`, `leave`, `death`, `advancement` independently.

### `[filters]`

| Key | Default | Description |
|---|---|---|
| `ignore_bots` | `true` | Drop messages from Discord bots |
| `ignore_webhooks` | `true` | Drop messages from other webhooks |
| `trusted_bot_ids` | `""` | Comma-separated IDs that bypass both ignore filters (use for cross-server Viscord bots) |
| `filter_by_prefix` | `true` | Drop messages whose author begins with your server prefix |
| `show_other_server_events` | `true` | Show event embeds from other servers |

`[filters.chat]` adds `enabled`/`prefix` for suppressing in-game messages from the bridge (e.g. `!command`).

### `[bot_status]`

`enabled` and `format` (`"Online: {online}/{max}"`) drive the Discord/Fluxer bot's activity text.

### `[account_linking]`

`enabled` and `code_expiry` (60–600 s).

### `[advanced]`

`queue_size` (10–1000), `rate_limit` ms (100–5000).

## 🔧 Multi-Server Setup

Each server gets a unique `server.prefix`. Filters auto-detect self-originated messages by webhook ID, bot ID, **and** prefix to prevent loops. For cross-server event relay (joins/leaves/deaths from one server appearing in another), add the other server's bot/webhook IDs to `filters.trusted_bot_ids`.

## 🛠️ Commands

### In-game

| Command | Permission | Description |
|---|---|---|
| `/discord` | All | Click-to-join Discord invite |
| `/discord invite` | All | Same as `/discord` |
| `/discord messages` | All | Toggle cross-server messages for yourself |
| `/discord events` | All | Toggle event messages for yourself |
| `/discord servermessages [enable\|disable]` | All | Toggle server system messages |
| `/viscord discord link` | All | Generate a 6-digit Discord link code |
| `/viscord discord unlink` | All | Unlink your Discord |
| `/viscord discord help` | All | Show all Discord-related commands |
| `/viscord reload` | OP 4 | Reload config and reconnect platforms |
| `/viscord status` | OP 4 | Show current platform / running state |

### Discord

| Trigger | Description |
|---|---|
| `!list` | Reply with the player list |
| `/link <code>` | Link a Discord account using the code from `/viscord discord link` |

## 🔐 Security

- Bot tokens and webhook URLs are stored in `viscord.toml` (file permissions are your responsibility)
- Webhook tokens are **redacted** from all log output (failure logs show `…/webhooks/{id}/***`)
- Link codes are generated with `SecureRandom` (not `Random`)
- The `/link` flow is atomic — a Discord ID cannot bind to two MC UUIDs concurrently

## 📊 Performance & Threading

- **Bounded async pool** (cores/2 → cores×2, daemon threads) — no thread explosion under back-pressure
- **`CallerRunsPolicy`** applies natural back-pressure if the pool saturates
- **Scheduled status updates** (mass-join scenarios coalesce into one update)
- **Bounded LRU caches** with TTL for echo and advancement deduplication
- **Off-tick disk I/O** for linked accounts and player preferences
- **Volatile shared fields** + local captures protect against JIT reordering and TOCTOU NPEs across Javacord / Fluxer / tick threads

## 🐛 Troubleshooting

**Config isn't generating** — verify write permissions on `config/viscord/`; check server log for the `[Viscord]` lines on startup.

**Discord messages don't appear in Minecraft** — confirm Message Content Intent is enabled on the Discord application, the bot is in the channel, and `general.enabled = true`.

**Minecraft messages don't appear in Discord** — check `discord.webhook_url`; confirm the webhook's channel matches `discord.channel_id`.

**Fluxer messages don't appear** — Fluxer uses Gateway (WebSocket), no firewall config needed. Confirm `fluxer.bot_token` and `fluxer.channel_id` are set and the bot has channel access.

**Cross-server events from another Viscord instance are dropped** — add the other server's bot user ID (and/or webhook ID) to `filters.trusted_bot_ids`.

Enable verbose logging with `general.debug = true`.

## 📝 Changelog

See [CHANGELOG.md](CHANGELOG.md).

## 🤝 Contributing

PRs welcome.

## 📄 License

MIT — see [LICENSE](LICENSE).

## 🙏 Credits

- **Architectury API** — cross-loader abstraction
- **Javacord** — Discord client
- **nv-websocket-client** — Fluxer Gateway
- **NightConfig** — TOML config
- **OkHttp** — webhook HTTP
- **Gson** — JSON
