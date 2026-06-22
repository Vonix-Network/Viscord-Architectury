# Fluxer Setup

Fluxer is a chat platform Viscord supports as a peer to Discord. The integration uses Fluxer's **WebSocket Gateway** — there is no inbound port to open, no firewall rule to change.

## 1. Get a bot token

From the Fluxer developer portal, create a bot and copy its token. Paste it into:

```toml
[fluxer]
bot_token = "YOUR_FLUXER_BOT_TOKEN"
```

## 2. Pick a channel

```toml
[fluxer]
channel_id = "channel-id-from-fluxer"
```

Multi-channel? Comma-separate, same as Discord:

```toml
channel_id = "abc123,def456,ghi789"
```

## 3. (Optional) Event channel split

To send join/leave/death/advancement to a different Fluxer channel:

```toml
event_channel_id = "events-channel-id"
```

## 4. (Optional) Webhook for per-player avatars

If your Fluxer instance supports webhooks and you want MC chat to show per-player avatars (rather than the bot), set:

```toml
webhook_url = "https://your-fluxer-host/.../webhook-url"
```

## 5. Switch the platform

Tell Viscord to actually use Fluxer:

```toml
[general]
enabled  = true
platform = "fluxer"      # or "both" to run Discord + Fluxer together
```

## Connection model

- Fluxer connects via **WebSocket Gateway** — outbound TCP only. No port forwarding, no inbound firewall rules.
- The client is implemented in `discord/FluxerBotClient.java`. WebSocket lifecycle (connect, send, receive, reconnect) lives there.
- Outbound HTTP (webhook sends, list embeds) uses `discord/FluxerWebhookClient.java`. Failure logs redact the URL token portion as `…/webhooks/{id}/***` (4.2.0+).

## Self-message filtering

`FluxerBotClient` resolves its own user ID on connect (`selfId`) and uses that as the primary self-message filter — older builds (pre-4.2.0) fell back to broken prefix matching on the older templates and could miss messages.

## Troubleshooting

- **No Fluxer messages appear in Minecraft** — verify `fluxer.bot_token`, `fluxer.channel_id`, and that the bot actually has channel access on the Fluxer side.
- **Connection drops repeatedly** — enable `general.debug = true` and check the log for Gateway lifecycle messages.
- **MC chat shows as the bot, not as players** — `fluxer.webhook_url` is unset.
- **Cross-server events from another Viscord instance over Fluxer are dropped** — add the other bot's ID to `filters.trusted_bot_ids` (see [../multi-server.md](../multi-server.md)).
