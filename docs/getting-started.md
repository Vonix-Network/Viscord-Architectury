# Getting Started

This guide takes you from a fresh server to a bridged Minecraft ↔ Discord chat in under ten minutes.

## Prerequisites

- A Minecraft server running **1.18.2**, **1.19.2**, **1.20.1**, or **1.21.1** on Fabric, Forge, or NeoForge (loader support matches the template — see the [README](../README.md)).
- **Java 17+** (recommended; matches the templates' toolchain).
- A Discord server you have **Manage Webhooks** + **Manage Channels** permissions in (for the Discord path), or a Fluxer bot token (for the Fluxer path).
- Server-side only — players do **not** need to install anything.

## 1. Install the JAR

1. Download the JAR for your Minecraft version + loader from the project's releases.
2. Drop it into your server's `mods/` folder.
3. Start the server **once**. Viscord writes a default `config/viscord/viscord.toml` and exits its initialization cleanly even though `general.enabled = false`.
4. Stop the server (so you can edit config without races).

> If you previously ran the legacy 4.0 `config/viscord.json` setup, it is auto-migrated to `config/viscord/viscord.toml` on first run and the original is backed up as `viscord.json.backup`. See [Migration Guide](migration.md).

## 2. Minimal Discord config

Edit `config/viscord/viscord.toml`:

```toml
[general]
enabled  = true
platform = "discord"

[discord]
bot_token   = "YOUR_BOT_TOKEN_HERE"
channel_id  = "123456789012345678"
webhook_url = "https://discord.com/api/webhooks/.../..."
invite_url  = "https://discord.gg/your-invite"   # optional, drives /discord

[server]
prefix = "[MC]"
name   = "My Server"
```

See [platforms/discord.md](platforms/discord.md) for the bot-creation + intent walkthrough, and [configuration.md](configuration.md) for every key.

## 3. Start (or reload) the server

- **Cold start**: just boot the server. Viscord connects on `SERVER_STARTED`.
- **Hot reload**: if the server is already running, an operator can run `/viscord reload` — config is re-read, the Discord/Fluxer client is torn down and re-initialized without restarting the JVM.

## 4. Verify

In game:

```
/viscord status
```

Expected output:

```
=== Viscord Status ===
Status: Running
Platform: discord
Enabled: Yes
```

In Discord, type `!list` in the bridged channel — the bot replies with the current player list.

In Minecraft, type any chat message — it should appear in the Discord channel via the webhook with your player name + avatar.

## 5. Where to go next

- [**Configuration Reference**](configuration.md) — turn on events, customize formats, set up multi-channel.
- [**Account Linking**](account-linking.md) — let players bind their Minecraft UUID to a Discord ID.
- [**Tridirectional bridging**](platforms/tridirectional.md) — add Fluxer and bridge all three.
- [**Multi-Server Setup**](multi-server.md) — share a channel between several MC servers.
