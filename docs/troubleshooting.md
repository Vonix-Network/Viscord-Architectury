# Troubleshooting

Start by setting:

```toml
[general]
debug = true
```

Then `/viscord reload`. Verbose logs make every section below diagnosable.

---

## Nothing happens on startup

- **Symptom:** No `[Viscord]` lines in the server log at all.
- **Cause:** `general.enabled = false` (the default).
- **Fix:** Set `general.enabled = true` and reload.

If `enabled = true` but you still see nothing, check write permissions on `config/viscord/`. The mod logs every step of config loading at INFO level on startup.

## Config file not generating

- Check write permissions on `config/viscord/`.
- Check the server log for the `[Viscord]` lines on first startup — they will name the file path it tried to write.

## Discord messages don't appear in Minecraft

In order, verify:

1. **Message Content Intent** is enabled on the bot in the Discord developer portal. Without this Javacord cannot read message bodies.
2. The bot has **View Channel** + **Read Message History** on the target channel.
3. `discord.channel_id` matches the channel where you're typing.
4. `general.enabled = true`.
5. The author is not being filtered:
   - If you're testing with a bot account, `filters.ignore_bots = true` will drop it. Add the bot ID to `filters.trusted_bot_ids` to bypass.
   - If your Discord nickname starts with the server's `prefix` and you are not a bot — you should *not* be dropped (4.1.12+ — prefix check applies only to bot/webhook authors).

## Minecraft messages don't appear in Discord

1. `discord.webhook_url` is set and points at the right channel.
2. The webhook hasn't been deleted in Discord (the URL becomes invalid).
3. Check the log for `[Viscord]` webhook send failures. URLs in failure logs are redacted as `…/webhooks/{id}/***` (4.2.0+), but the ID + HTTP status should be enough to diagnose.

## Fluxer messages don't appear

Fluxer uses **WebSocket Gateway** — no firewall config needed.

1. `fluxer.bot_token` is correct.
2. `fluxer.channel_id` matches.
3. The Fluxer bot has channel access on the Fluxer side.
4. Watch the log for Gateway lifecycle messages (`debug = true`).

## MC chat shows as the bot, not as players

The webhook URL is unset or wrong. For Discord, set `discord.webhook_url`. For Fluxer, set `fluxer.webhook_url`. The bot itself cannot impersonate players — that's what webhooks are for.

## Cross-server events from another Viscord are dropped

Add the other server's bot user ID **and** webhook ID to `filters.trusted_bot_ids`. The default `ignore_bots`/`ignore_webhooks = true` filters drop them otherwise.

## Player display name doesn't appear correctly on Discord side

Check `messages.use_display_name`:

- `true` (default): resolves `server nickname → global display name → username`.
- `false`: uses plain `@username`.

The default-resolution chain was buggy in 4.1.9 / 4.1.10 — make sure you are on 4.2.0 or 4.1.10+. Pre-4.1.10 `getDisplayName()` returned the server nickname *or directly fell back to the plain username*, skipping the global display name (`global_name`).

## Account linking fails

- **"Account linking is disabled"** — set `account_linking.enabled = true`.
- **"Failed to generate link code. You may already have an account linked."** — run `/viscord discord unlink` first.
- **Code expires too fast** — bump `account_linking.code_expiry` (max 600).
- **Code "doesn't work" in Discord** — confirm you typed `/link 123456` *in a channel the bot can read*. The bot needs Message Content Intent (this is *not* a Discord slash command).

## `/viscord reload` reports failure

The error message in chat names the cause. Common ones:

- `Failed to reload: ...` followed by a class / NPE — usually means the new config is malformed. Validate the TOML separately, fix, reload.
- Reload runs entirely off-thread; UI feedback bounces back via `mcServer.execute(...)`. If you don't see *any* response within a few seconds, check the log — the executor may have thrown before reaching the feedback line.

## Server-stop hang or partial shutdown messages

- Pre-4.1.7 stop-thread blocking could be ~11 seconds because of nested `.join()` calls. Upgrade to 4.2.0.
- If a shutdown embed is missing in Discord, the platform's HTTP future was cancelled too early — fixed in 4.1.7 (waits up to 3s) and re-shaped in 4.2.0.

## URLs in chat get corrupted (`?a=1§b=2`)

Upgrade. Pre-4.1.7 `ChatFormatter.parseColors` unconditionally replaced all `&` with `§`. Since 4.1.7 the replacement only happens when `&` is followed by a valid formatting code character or `#` (hex colors).

## `/discord messages` / `/discord events` don't work for non-ops

Upgrade to 4.1.5+. Earlier builds inherited the op-4 `requires(...)` predicate from a sibling `/viscord` registration through Brigadier's tree-merge behavior. 4.1.5 narrowed the op requirement to only `reload` and `status`.

## "Unknown component type" errors during shutdown

Upgrade. Pre-4.1.3 the Javacord EventBus could trip on shadowed component classes during teardown. 4.1.3 ships a `Thread.setDefaultUncaughtExceptionHandler` to suppress; 4.1.11 properly excludes the shadow package via `mods.toml`.

## Linked-accounts file goes missing or corrupts on Windows

Confirm you are running 4.1.7+ — that release pinned all `FileWriter`/`FileReader` to `StandardCharsets.UTF_8`. Before that, the platform-default charset on Windows could corrupt non-ASCII usernames.

---

## Collecting a diagnostic bundle

When asking for help, please include:

1. Viscord version (`/viscord status` shows it indirectly via platform / enabled; the JAR filename usually has it).
2. Minecraft version + mod loader.
3. `viscord.toml` with `bot_token` and `webhook_url` token segments redacted.
4. The relevant `[Viscord]` log lines with `debug = true`.
