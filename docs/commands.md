# Commands

In-game commands are extracted directly from `discord/DiscordEventHandler.java::registerCommands`. Permission levels are vanilla Minecraft op levels (0 = any player, 4 = full op).

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

## Discord-side commands

Viscord registers these as native Discord application commands in the guild that owns the configured channel. Existing Viscord-owned commands are updated in place on bot startup, so stale `/link` registrations are repaired without deleting commands from another application.

| Command | Scope | Description |
|---|---|---|
| `/link code` | Linked-account user | Link a Discord account using the 6-digit code from `/viscord discord link`. |
| `/list` | Player | Show the current online-player list. |
| `/home name` | Linked-account user | Safely dispatch the fixed Minecraft command `/home <name>` for the linked player. The home name is restricted to letters, numbers, `_`, and `-`; arbitrary command text is never accepted. |
| `/help` | Player | Show the native Viscord command list. |
| `/status` | Discord administrator | Show Viscord connection and Minecraft player status. |

Commands are restricted to the configured main Discord channel. `/link` replies ephemerally and still uses the existing code expiry and rate limits. `/home` requires the Discord user to be linked, the linked Minecraft player to be online, and runs with permission level 0 rather than as console/op.

## Legacy Discord text triggers

These remain supported for compatibility, but are no longer required for the primary flow:

| Trigger | Description |
|---|---|
| `!list` | Bot replies with the current player list embed. |
| `/link <code>` | Legacy ordinary-message form of account linking. Do not select a native slash-command autocomplete entry when intentionally using this form. |
