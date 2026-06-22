# Configuration Reference

Viscord uses **TOML** at `config/viscord/viscord.toml`. The file is generated on first run with extensive inline comments. This page is the source-of-truth reference for every key, drawn from `ViscordConfigToml.java` / `TomlConfigManager.java`.

Legacy `config/viscord.json` is auto-migrated to TOML on first run and backed up as `viscord.json.backup`. See [migration.md](migration.md).

> Use `/viscord reload` after editing to apply changes without restarting the server.

---

## `[general]`

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | bool | `false` | Master toggle. When `false`, Viscord initializes nothing on startup. |
| `platform` | string | `"discord"` | One of `discord`, `fluxer`, or `both`. |
| `debug` | bool | `false` | Verbose logging across all Viscord components. |

## `[general.tridirectional]`

Three-way bridging between Discord, Fluxer, and Minecraft. Requires `platform = "both"` to make practical sense (otherwise one side is just not connected).

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | bool | `false` | Master toggle for tridirectional bridging. |
| `discord_to_fluxer` | bool | `true` | Relay Discord → Fluxer when both sides are connected. |
| `fluxer_to_discord` | bool | `true` | Relay Fluxer → Discord. |
| `show_source` | bool | `true` | Tag bridged messages with `[Discord]` / `[Fluxer]` so chat origin is visible. |

See [platforms/tridirectional.md](platforms/tridirectional.md) for echo-prevention details.

## `[discord]`

| Key | Type | Required | Default | Description |
|---|---|---|---|---|
| `bot_token` | string | ✅ | `"YOUR_BOT_TOKEN_HERE"` | Bot token from the Discord developer portal. |
| `channel_id` | string | ✅ | `"YOUR_CHANNEL_ID_HERE"` | Channel ID, or comma-separated IDs for multi-channel monitoring. |
| `webhook_url` | string | ✅ | `""` | Webhook URL for rich MC → Discord messages (player avatars + names). |
| `webhook_id` | string | ❌ | `""` | Optional explicit webhook ID; auto-extracted from `webhook_url` if empty. |
| `invite_url` | string | ❌ | `""` | URL shown by the in-game `/discord` command as a clickable link. |

## `[discord.events]`

Optional split: send event embeds (joins, leaves, deaths, advancements) to a different Discord channel than chat.

| Key | Type | Required | Default | Description |
|---|---|---|---|---|
| `channel_id` | string | ❌ | `""` | Event channel ID. Empty = events go to `discord.channel_id`. |
| `webhook_url` | string | ❌ | `""` | Optional separate webhook for the event channel. |

## `[fluxer]`

| Key | Type | Required | Default | Description |
|---|---|---|---|---|
| `bot_token` | string | ✅ | `"YOUR_FLUXER_BOT_TOKEN"` | Bot token from the Fluxer developer portal. |
| `channel_id` | string | ✅ | `"YOUR_FLUXER_CHANNEL_ID"` | Channel ID(s) — comma-separated for multi-channel. |
| `event_channel_id` | string | ❌ | `""` | Separate event channel. Empty = use `fluxer.channel_id`. |
| `webhook_url` | string | ❌ | `""` | Webhook URL for per-player avatars on MC → Fluxer. |

Fluxer connects over **WebSocket Gateway** — **no port forwarding required**.

## `[server]`

| Key | Type | Default | Description |
|---|---|---|---|
| `prefix` | string | `"[MC]"` | Server identifier in bridged messages. **Must be unique per server** in multi-server setups (see [multi-server.md](multi-server.md)). |
| `name` | string | `"Minecraft Server"` | Display name for bot status text and embed authors. |
| `avatar_url` | string | `""` | Server avatar URL (used in some embed paths). |

## `[messages]`

| Key | Type | Default | Description |
|---|---|---|---|
| `discord_to_minecraft` | string | `"[Discord] {username}: {message}"` | Chat format for Discord → Minecraft. Placeholders: `{username}`, `{message}`, `{prefix}`. |
| `minecraft_to_discord` | string | `"{message}"` | Chat format for Minecraft → Discord (sent via webhook with player as author). |
| `webhook_username` | string | `"{prefix} {username}"` | Display name used for the webhook author on MC → Discord. |
| `use_display_name` | bool | `true` | When `true`, resolves Discord authors as `server nickname → global display name → username`. When `false`, uses the plain `@username`. |

> **Color codes**: `§` (section sign) and `&` are both honored in MC chat formats. `&` is only replaced when followed by a valid Minecraft formatting code character or `#` (hex), so URL query strings like `?a=1&b=2` are preserved.

## `[messages.events]`

| Key | Type | Default | Description |
|---|---|---|---|
| `join` | bool | `true` | Bridge player joins. |
| `leave` | bool | `true` | Bridge player leaves. |
| `death` | bool | `true` | Bridge death messages. |
| `advancement` | bool | `true` | Bridge advancement notifications. |

## `[filters]`

| Key | Type | Default | Description |
|---|---|---|---|
| `ignore_bots` | bool | `true` | Drop messages whose author is a Discord bot. |
| `ignore_webhooks` | bool | `true` | Drop messages from other webhooks. |
| `trusted_bot_ids` | string | `""` | Comma-separated Discord user/webhook IDs that **bypass** both ignore filters. Use to receive cross-server event embeds from another Viscord bot. |
| `filter_by_prefix` | bool | `true` | Drop bot/webhook authors whose display name begins with this server's `server.prefix` (echo loop guard). |
| `show_other_server_events` | bool | `true` | Show event embeds bridged from other Viscord servers. |

> The prefix and self-origin filters apply **only to bot/webhook authors**, never to regular Discord users (fixed in 4.1.12).

## `[filters.chat]`

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | bool | `false` | When `true`, drops in-game messages that begin with `prefix` (so they don't bridge to Discord). |
| `prefix` | string | `"!"` | The prefix that flags an in-game message as "don't bridge". |

## `[bot_status]`

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | bool | `true` | Set the bot's "Playing" / custom status text. |
| `format` | string | `"Online: {online}/{max}"` | Status format. Placeholders: `{online}`, `{max}`. Updates are coalesced — see [performance.md](performance.md). |

## `[account_linking]`

| Key | Type | Default | Range | Description |
|---|---|---|---|---|
| `enabled` | bool | `true` | — | Allow players to `/viscord discord link`. |
| `code_expiry` | int (seconds) | `300` | `60`–`600` | How long a 6-digit code stays valid. |

See [account-linking.md](account-linking.md) for the full flow.

## `[advanced]`

| Key | Type | Default | Range | Description |
|---|---|---|---|---|
| `queue_size` | int | `100` | `10`–`1000` | Internal message queue capacity (see [performance.md](performance.md)). |
| `rate_limit` | int (ms) | `1000` | `100`–`5000` | Outbound rate-limit window. |

---

## Notes on TOML coercion

NightConfig stores TOML integers as `Long`. The `ConfigValue<T>.get()` accessor coerces `Number` to the declared default's type (`Integer`, `Long`, `Double`). This was a silent failure in 4.1.6 — fixed in 4.1.7. If you see numeric defaults being silently ignored on older builds, upgrade.

## Auto-injection of new keys on upgrade

`TomlConfigManager.applyDefaults()` writes every key on first run, and the loaded config is run through `ConfigSpec.correct()` on startup. New keys added in newer Viscord versions are injected into your existing `viscord.toml` automatically (a regression in 4.1.6 → 4.1.7 left a few keys un-injected; both have been registered in `createConfigSpec()` since 4.1.9).
