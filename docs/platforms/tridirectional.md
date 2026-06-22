# Tridirectional Bridging (Discord ↔ Fluxer ↔ Minecraft)

Tridirectional mode bridges all three sides at once: messages from Discord appear on Fluxer and in Minecraft, messages from Fluxer appear on Discord and in Minecraft, and messages from Minecraft appear on both. Implementation lives in `discord/platform/TridirectionalBridge.java`.

## Enabling it

```toml
[general]
enabled  = true
platform = "both"

[general.tridirectional]
enabled            = true
discord_to_fluxer  = true   # relay Discord -> Fluxer
fluxer_to_discord  = true   # relay Fluxer -> Discord
show_source        = true   # tag bridged messages with [Discord] / [Fluxer]
```

You can set `discord_to_fluxer = false` (or vice versa) for a **one-way bridge** between the two platforms while still bridging both to Minecraft.

## How `show_source` looks

With `show_source = true`, a message typed in Discord that gets relayed to Fluxer arrives prefixed with `[Discord]` so users on the Fluxer side know where it came from (and vice versa). Set it to `false` if you want bridged chat to look indistinguishable from native chat on each platform.

## Echo prevention

The naive trap: Discord → Fluxer → "looks like a Fluxer message" → bridged back to Discord → loop.

`TridirectionalBridge` defends against this with a **bounded 512-entry synchronized LRU echo cache**. When Viscord sends a message out to either side as part of a bridge relay, it remembers the outgoing message identity in this cache via `TridirectionalBridge.rememberOutgoing(...)`. When that message echoes back from the platform's own broadcast, the bridge recognizes it and drops it.

Pre-4.2.0 this cache used an unbounded `Map` with a racy `if (size > X) clear()` eviction that could destroy all in-flight dedupe state at once. The 4.2.0 LRU is access-ordered, cap 512, evicts the oldest entry on insert, and read/put is wrapped in a single `synchronized` block. The same fix applies to the `recentAdvancements` cache (cap 256).

## Self-origination filtering

In addition to the echo cache, every message goes through `isSelfOriginated()` which checks:

1. Is the author **this server's webhook ID**?
2. Is the author **this server's bot ID**?
3. (If `filters.filter_by_prefix = true`) Does the author display name begin with this server's `server.prefix`?

Note: prefix and bot-ID checks apply **only to bot/webhook authors**, never to regular users (fixed in 4.1.12 — before that, a Discord user whose nickname started with `[MC]` would have their messages silently dropped).

## Multi-server interactions

Tridirectional + multi-server is fully supported. The trick is to add **every other server's bot/webhook IDs** to your `filters.trusted_bot_ids`, otherwise their event embeds (joins/leaves/deaths/advancements) get caught by `filters.ignore_bots` / `ignore_webhooks` and dropped. See [../multi-server.md](../multi-server.md).
