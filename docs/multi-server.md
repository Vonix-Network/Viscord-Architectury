# Multi-Server Setup

Running Viscord on **multiple Minecraft servers** that share one Discord (and/or Fluxer) channel turns that channel into a cross-server lobby — players see joins, leaves, deaths, and chat from every server.

This page covers the configuration patterns and the loop-prevention guardrails.

## The basic shape

Each Minecraft server runs its own Viscord install with:

- A **unique `server.prefix`** (e.g. `[Survival]`, `[Creative]`, `[Mining]`).
- The **same** `discord.channel_id` (and webhook, if you want to share one) — or distinct webhooks pointing at the same channel.
- Each other server's bot / webhook IDs added to `filters.trusted_bot_ids`.

## Example: two servers sharing one Discord channel

**Server A** (`viscord.toml`):

```toml
[server]
prefix = "[Survival]"
name   = "Survival"

[discord]
bot_token   = "TOKEN_A"
channel_id  = "1234567890"
webhook_url = "https://discord.com/api/webhooks/AAAAA/.../..."

[filters]
trusted_bot_ids = "BOT_B_USER_ID,WEBHOOK_B_ID"
```

**Server B** (`viscord.toml`):

```toml
[server]
prefix = "[Creative]"
name   = "Creative"

[discord]
bot_token   = "TOKEN_B"
channel_id  = "1234567890"
webhook_url = "https://discord.com/api/webhooks/BBBBB/.../..."

[filters]
trusted_bot_ids = "BOT_A_USER_ID,WEBHOOK_A_ID"
```

A player joining Server A sends a join embed via webhook A. Bot B sees that embed in the channel, sees the author ID matches `trusted_bot_ids`, and bridges it into Server B's in-game chat as `[Survival] PlayerName joined`.

## Loop prevention

Without guardrails, a message from Server A → Discord → Server B → ... back to Discord would loop forever. Viscord prevents this in three layers:

1. **Webhook ID self-check.** If the author's webhook ID matches *this* server's `discord.webhook_id` (auto-extracted from `webhook_url`), drop it.
2. **Bot ID self-check.** If the author is our own bot, drop it.
3. **Prefix self-check** (when `filters.filter_by_prefix = true`, default). If the author's display name begins with our own `server.prefix`, drop it.

All three checks apply **only to bot/webhook authors**, never to regular users (so a Discord user whose nickname happens to be `[Survival]Bob` is never silently dropped — fixed in 4.1.12).

## Trusted bots — what to put in `filters.trusted_bot_ids`

This list is the **allowlist that bypasses `ignore_bots` and `ignore_webhooks`**. Without it, cross-server event embeds from another Viscord instance would be caught by the `ignore_bots`/`ignore_webhooks` filters before any embed parsing.

You generally want to add:

- The **bot user IDs** of every other Viscord server's Discord bot.
- The **webhook IDs** of every other Viscord server's webhook (since event embeds may be posted via webhook).

Format: comma-separated, no spaces required:

```toml
trusted_bot_ids = "111111111111111111,222222222222222222,333333333333333333"
```

## Per-server preferences

Players opt in/out of cross-server traffic per-server:

- `/discord messages` (or `/viscord discord messages`) — toggle cross-server chat for this player.
- `/discord events` — toggle cross-server event messages.
- `/discord servermessages enable|disable` — toggle server system messages.

Preferences are persisted per-UUID via `discord/PlayerPreferences.java` (backed by `ConcurrentHashMap` for tick-vs-async safety, 4.1.7+).

## Other-server event suppression

If you want a server to *send* cross-server events but *not* display events from other servers in its own chat, set:

```toml
[filters]
show_other_server_events = false
```

The local server will still emit its own join/leave/death/advancement embeds, but inbound embeds from other Viscord bots are not re-bridged into its own player chat.

## Multi-server + tridirectional

Tridirectional mode + multi-server is a supported combination — Viscord handles the case where Server A's Discord embed gets relayed to Fluxer, then back to Discord. See [platforms/tridirectional.md](platforms/tridirectional.md) for the echo cache mechanics that keep this safe.
