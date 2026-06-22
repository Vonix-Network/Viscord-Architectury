# Commands

All commands extracted directly from `discord/DiscordEventHandler.java::registerCommands`. Permission levels are vanilla Minecraft op levels (0 = any player, 4 = full op).

## In-game commands

### `/discord` (op 0)

| Subcommand | Permission | Description |
|---|---|---|
| `/discord` | All | Show a clickable Discord invite link (from `discord.invite_url`). Falls back to a "not configured" message if the URL is empty. |
| `/discord invite` | All | Identical to `/discord`. |
| `/discord messages` | All | **Toggle** cross-server messages for yourself. Affects whether Discord-bridged chat from *other* servers appears in your own client. |
| `/discord events` | All | **Toggle** cross-server event messages (joins/leaves/deaths/advancements). |
| `/discord servermessages` | All | Show whether server system messages are enabled / disabled. |
| `/discord servermessages enable` | All | Enable server system messages for yourself. |
| `/discord servermessages disable` | All | Disable server system messages. |

> `messages` and `events` are **pure toggles** — running them twice flips back to the previous state. `servermessages` has explicit enable/disable subcommands.

### `/viscord` (mixed)

| Subcommand | Permission | Description |
|---|---|---|
| `/viscord reload` | **op 4** | Reload `viscord.toml` and reconnect platforms. Runs entirely off-thread (`ASYNC_EXECUTOR`) with command feedback bounced back to the server thread. |
| `/viscord status` | **op 4** | Show running state, platform, enabled flag. |
| `/viscord discord link` | All | Generate a 6-digit code for Discord account linking. See [account-linking.md](account-linking.md). |
| `/viscord discord unlink` | All | Unlink your Discord account from your MC UUID. |
| `/viscord discord messages` | All | Same toggle as `/discord messages` (alias path). |
| `/viscord discord events` | All | Same toggle as `/discord events`. |
| `/viscord discord help` | All | Print a list of Discord-related commands. |

> Pre-4.1.5 the `/viscord discord *` subcommands required op 4 due to Brigadier's tree-merge behavior preserving the first node's `requires(permission 4)` predicate. The op requirement was moved down to *only* `reload` and `status` so regular players can run preference toggles via either path.

### `/vonix` (deprecated alias)

| Subcommand | Permission | Description |
|---|---|---|
| `/vonix reload` | op 4 | Prints "deprecated — use `/viscord reload`" and returns. |
| `/vonix discord` | op 4 | Prints "deprecated — use `/viscord discord`" and returns. |

The deprecated `/vonix` tree was kept solely as a deprecation notice to migrate operators off old habit. Use `/viscord` going forward.

## Discord-side triggers

These are text-pattern triggers handled by `DiscordManager.onDiscordMessage`. They are **not** slash commands — type them in the bridged channel.

| Trigger | Description |
|---|---|
| `!list` | Bot replies with the current player list embed. Player list is read on the server thread via `server.execute(...)` for thread safety (fixed in 4.1.7). |
| `/link <code>` | Link a Discord account using the 6-digit code from `/viscord discord link`. The code expires after `account_linking.code_expiry` seconds (default 300, range 60–600). |

> The `/link` trigger looks like a Discord slash command, but Viscord does not register it as one — it is matched on `message.startsWith("/link ")` in `DiscordManager`.
