# Changelog

All notable changes to Viscord will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [4.1.5] - 2026-04-22

### Fixed
- `/discord messages` and `/discord events` not re-enabling — `/viscord discord *` subcommands required OP due to Brigadier merging the two `/viscord` registrations and preserving the first node's `requires(permission 4)` predicate; moved OP requirement down to only `reload` and `status` subcommands so regular players can access discord preference commands via both `/discord` and `/viscord discord` paths
- All four toggle commands (`/discord messages`, `/discord events`, `/viscord discord messages`, `/viscord discord events`) are now consistent pure-toggle switches

### Changed
- `PlayerPreferences.savePreferences()` now builds the JSON snapshot on the calling thread then offloads the file write to `ASYNC_EXECUTOR`, preventing disk I/O from blocking the server main thread on every preference change

## [4.1.4] - 2026-04-04

### Fixed
- Cross-server player messages (Discord webhooks from other servers) not showing in game — `IGNORE_BOTS` and `IGNORE_WEBHOOKS` filters were applied in `onDiscordMessage` before `processDiscordMessageForMinecraft` was called, dropping all webhook/bot messages before they could reach the cross-server display path; duplicate early filters removed so loop prevention is handled solely by `FILTER_BY_PREFIX`
- Fluxer self-message filtering now uses bot user ID from READY payload instead of prefix matching — Fluxer disallows `[` `]` in bot display names making prefix-based detection unreliable; `selfId` is stored on READY and cleared on disconnect, and `handleMessageCreate` skips any message whose `author.id` matches
- Fluxer bot status not displaying activity text — `updateStatus()` and `sendIdentify()` were sending `custom_status` (a user-account-only field) in the gateway OP 3 payload; bot accounts require the `activities` array. Changed both methods in `FluxerBotClient` to use `activities: [{name: "<status>", type: 0}]` so the "Playing ..." status is visible on the bot profile.

## [4.1.3] - 2026-03-28

### Fixed
- 1.18.2 build failure — `FluxerWebhookClient.sendMessage` was using Discord-native format (`avatar_url`/`content`) instead of Slack-compatible format (`icon_url`/`text`); reverted to match spec and passing tests
- 1.18.2 test failures — updated `WebhookClientPayloadTest`, `WebhookProfilePBTTest`, `WebhookProfileRelayTest` to match current implementation behaviour
- 1.19.2 and 1.20.1 build failure — `gradle-wrapper.jar` was missing from both templates; copied from 1.18.2
- `ActionRowImpl` patch moved to fabric/forge/neoforge source sets (removed from common, which lacks Javacord on classpath)
- Unknown Discord component types (v2 components) no longer crash the bot — suppressed via `Thread.setDefaultUncaughtExceptionHandler` replacing non-existent `addUncaughtExceptionListener`
- Gradle JVM heap increased to 4G to prevent OOM during builds; `.hprof` heap dumps added to `.gitignore`

### Changed
- Event embed footers now use descriptive labels (`Viscord · Player Join`, `Viscord · Player Leave`, `Viscord · Player Death`, `Viscord · Advancement`, `Viscord · Server Online`, `Viscord · Server Offline`) instead of generic `Viscord` text

## [4.1.2] - 2026-03-26

### Added
- `platform = "both"` mode — initializes both Discord and Fluxer simultaneously; all events and Minecraft chat go to both platforms with no cross-platform message bridging (use tridirectional for that) (all templates)
- Rich embed support for Fluxer event notifications — `FluxerBotClient.sendEmbed()` now posts `{"embeds": [...]}` to the Fluxer bot API (Discord-compatible format), replacing the previous plain-text fallback (all templates)

### Fixed
- Fluxer events not firing in tridirectional/both modes — routing conditions replaced with `usesFluxer()`/`usesDiscord()` helpers that correctly cover all platform combinations (all templates)
- Fluxer startup embed not sending in `both`/tridirectional mode — moved startup into `FluxerPlatform.initialize()` `thenRun` so it fires after bot READY for all modes (all templates)
- Fluxer startup embed going to main channel instead of events channel on `/viscord reload` — added 500ms settle delay in `thenRun` to ensure config is fully loaded before channel ID is read (all templates)
- `shutdown()` double-sending Discord offline embed — `DiscordManager.shutdown()` now calls `discordPlatform.shutdown()` directly instead of `sendShutdownEmbed` separately (all templates)
- `shutdown()` race condition in both/tridirectional mode — Fluxer offline embed now waits 1.5s before Discord disconnect begins (all templates)
- All older templates (1.18.2, 1.19.2, 1.20.1) synced to 1.21.1 feature parity: `platform = "both"`, rich Fluxer embeds, `usesFluxer()`/`usesDiscord()` routing, `resetInstance()`, fixed shutdown/startup logic

## [4.1.1] - 2026-03-26

### Fixed
- `!list` command not responding from Fluxer — `onFluxerMessage` had no `!list` handler; now sends formatted player list back to the Fluxer event channel
- Join/leave events not reaching Fluxer — added error logging to `FluxerPlatform.sendEventMessage` to surface failures
- Advancement notifications firing on partial progress — `PlayerAdvancementsMixin` now checks `getOrStartProgress().isDone()` before sending
- Fluxer standalone mode: `sendStartupEmbed` was double-sending the startup message — fixed routing logic so each mode sends exactly once
- `isRunning()` now correctly checks tridirectional state — returns true if either platform is connected in tridirectional mode
- Build error: `Javacord Icon.getAvatar()` returns `Icon` directly, not `Optional<Icon>` — `.map()` call replaced with null-safe direct call in `TridirectionalBridge` across all 4 templates
- Fluxer bot status (Online: players/max) not updating — `scheduleStatusUpdate` now fires on every player join/leave regardless of whether event messages are enabled, across all 4 MC version templates
- Fluxer user profile pictures not appearing in Discord webhooks — `WebhookClient.sendMessage` was unconditionally setting `avatar_url` to null/empty; now only included when non-empty, across all 4 MC version templates
- Fluxer avatar CDN URL was incorrect (`cdn.fluxer.app`) — corrected to `fluxerusercontent.com` with `.webp` format, across all 4 MC version templates
- Fluxer bot custom status not displaying — activity type was `0` (Playing) instead of `4` (Custom Status); payload now uses `type: 4` with `state` field, across all 4 MC version templates
- Fluxer bot custom status not set on connect — status now embedded in the identify payload's presence block so it's active immediately on gateway connect, not just after a post-READY op 3 update (1.21.1)
- Fluxer bot appearing online after server shutdown — now sends `invisible` presence before closing the WebSocket (1.21.1)
- `/viscord reload` not working after first reload — `DiscordManager` singleton was reused after shutdown, leaving dead scheduler/WebSocket state; `resetInstance()` now called after shutdown so a fresh instance with clean platform objects is created on re-init (1.21.1)
- Double bot status update on player join/leave — `DiscordEventHandler` was calling `scheduleStatusUpdate(1000)` redundantly alongside the existing call inside `sendJoinEmbed`/`sendLeaveEmbed`; removed the duplicate (1.21.1)
- Fluxer not sending detailed embeds for in-game events — `DiscordManager` was calling `sendEventMessage()` with hardcoded plain-text strings instead of `sendEventEmbed()` with `EmbedFactory`-built embeds (1.21.1)
- Fluxer server online/offline events not appearing in events channel — `FluxerPlatform.initialize()` was sending startup via `sendBotMessage` with hardcoded plain text instead of `sendEventEmbed`; shutdown was also disconnecting the bot before the message could send (1.21.1)

## [4.1.0] - 2026-03-26

### Changed
- **Refactor**: Split `DiscordManager` (1560 lines) into focused platform classes:
  - `platform/FluxerPlatform.java` — all Fluxer gateway/webhook/status logic
  - `platform/DiscordPlatform.java` — all Discord bot/webhook/embed logic
  - `platform/TridirectionalBridge.java` — cross-platform relay with echo suppression
  - `DiscordManager.java` — thin coordinator (~350 lines): Minecraft processing, player prefs, account linking, public API
  - Zero behavior change — same config, same public API, `DiscordEventHandler` unchanged
  - Applied across all 4 MC version templates

### Removed
- `FluxerReceiver.java` — dead code, deprecated since v2.4.10
- `ServerPrefixConfig.java` — dead code, never referenced in codebase
- Stale `FluxerPlatform.java` duplicate in root `discord/` package

### Fixed
- Fluxer bot was forwarding messages from all channels to Discord/Minecraft — `FluxerBotClient.handleMessageCreate` had no channel ID filter; now only processes configured `fluxer.channel_id` and `fluxer.event_channel_id`
- `BotClient.sendEmbed` NPE if embed has no title field
- `FluxerBotClient` in 1.18.2/1.19.2/1.20.1 missing null checks for `op`, `t`, `author`, `username`, and `global_name` fields — all templates now fully in sync
- Missing `MessageConverter` import in new `DiscordManager` across all 4 templates

## [4.0.0] - 2026-03-26

### Fixed
- **CRITICAL**: Fluxer bot API messages silently failing with HTTP 404 — missing `/v1/` in REST API base URL
- **CRITICAL**: Fluxer gateway OP 7 (RECONNECT) not handled — server-initiated reconnect requests were silently ignored
- Fluxer webhook switched from Slack-compatible endpoint to native endpoint (`content`/`avatar_url`)
- Webhook echo messages not reliably filtered — added `webhook_id` presence check in `handleMessageCreate`
- Fluxer gateway OP 12 (GATEWAY_ERROR, Fluxer-specific) not handled — now schedules reconnect
- Heartbeat interval validation — invalid/zero intervals now fall back to 45000ms
- Scheduler thread leak on disconnect — `oldToken == null` condition was always false; fixed to `this.token == null`
- `sendMinecraftMessage` was calling `fluxerWebhookClient.updateUrl()` on every message — now only updates when URL has changed
- Removed dead config keys: `Discord.WEBHOOK_ID`, `Discord.Events.WEBHOOK_URL`, `Advanced.QUEUE_SIZE`, `Advanced.RATE_LIMIT`

## [3.0.6] - 2026-03-26

### Fixed
- Discord→Fluxer tridirectional bridge silently skipped when only a Fluxer webhook URL was configured
- Discord→Fluxer webhook dropped messages due to async init race — `fluxerWebhookClient.updateUrl()` now called inline before each send
- Fluxer gateway echoed webhook-sent messages back through `onFluxerMessage`, causing a loop — fixed with a 5-second fingerprint cache
- Join/leave/death/advancement events not sent to Fluxer — `sendEventEmbedInternal` was using the Discord webhook client
- Fluxer bot status never set — status update now fires directly in the READY/RESUMED gateway handler via `onReadyCallback`
- `playerPreferences` and `linkedAccountsManager` double-initialized in tridirectional mode
- Advancement debounce cache had a check-then-act race condition — replaced with atomic `ConcurrentHashMap.merge()`

## [3.0.4] - 2026-03-26

### Added
- Tridirectional chat now relays sender profiles across platforms — Discord users' avatars and display names appear in Fluxer, and vice versa

## [3.0.3] - 2026-03-26

### Fixed
- Fluxer bot displaying "0/0" instead of actual online/max player count — removed hardcoded status updates from READY/RESUMED handlers; status now flows through `DiscordManager.updateBotStatus()` which reads live server player counts
- `UnsupportedOperationException: StampedConfig does not support valueMap()` crash on second server start — `TomlConfigManager.loadToml()` now builds config without autosave, runs correction, then rebuilds with autosave

## [3.0.0] - 2026-03-25

### Changed
- **Breaking**: Complete configuration system overhaul — migrated from JSON to TOML (`config/viscord/viscord.toml`); automatic migration from old JSON config with backup

## [2.4.15] - 2026-03-25

### Fixed
- Fluxer bot showing offline despite successful authentication in tri-directional mode — added immediate status update when READY is received

## [2.4.12] - 2026-03-25

### Added
- Modern Python CLI build menu with Rich library for progress bars, interactive menus, Java auto-detection, and multi-platform build support

## [2.4.11] - 2026-03-25

### Fixed
- Missing `fluxerEventWebhookUrl` config field causing compilation errors across all MC versions
- Tridirectional chat echo loop — `onFluxerMessage` now passes formatted message (with `[Discord]` tag) to `bridgeFluxerToDiscord` for proper echo detection
- Discord to Fluxer messages not sending in 1.19.2 and 1.21.1 — updated `bridgeDiscordToFluxer` to use Bot API

## [2.4.10] - 2026-03-24

### Changed
- Fluxer overhaul — all Minecraft→Fluxer messages now use the Fluxer Bot REST API with channel IDs; webhooks and HTTP receiver retired; config simplified to `bot_token` + `channel_id` + optional `event_channel_id`

## [2.4.9] - 2026-03-24

### Fixed
- Gateway API version switched to `v=1` (Fluxer's own protocol); was `v=10` (Discord), primary cause of persistent offline bots
- Added `GUILD_MESSAGES` intent (bit 9) to receive `MESSAGE_CREATE` events
- Implemented immediate `online` presence during OP 2 Identify and on READY/RESUMED

## [2.4.7] - 2026-03-24

### Fixed
- Fluxer bot WebSocket connectivity — fixed infinite reconnect loop; `connected` flag now set only after READY dispatch; added exponential backoff (2s→60s), max 10 reconnect attempts, session resume support

## [2.4.6] - 2026-03-24

### Fixed
- Advancement completion check — fixed achievements broadcasting on partial progress; now uses `isDone()` check via `AdvancementProgress`

## [2.4.5] - 2026-03-24

### Added
- Fluxer WebSocket message receiving — `FluxerBotClient` now receives chat messages directly from Fluxer Gateway; no port forwarding required
- `/viscord fluxer invite` command — generates Fluxer bot invite URL
- Discord→Minecraft markdown conversion for `**bold**`, `*italic*`, `__underline__`, `~~strikethrough~~`

### Fixed
- Advancement spam in modpacks (Cobblemon) — added `isDone()` check and 5-second per-player debounce cache

## [2.4.4] - 2026-03-24

### Changed
- Migrated chat interception to native `ServerChatEvent` on Forge/NeoForge

## [2.4.3] - 2026-03-21

### Fixed
- Startup embed double-sending in Fluxer mode
- Tridirectional event embeds (advancements, death, server startup) not being bridged back to Discord

## [2.4.1] - 2026-03-21

### Added
- Full `/link` command implementation for Discord-Minecraft account linking with 6-digit codes, expiry, and JSON persistence

## [2.4.0] - 2026-03-21

### Added
- `/viscord reload` command — disconnects and reconnects bot with new config, fully async with 30-second timeout
- `/viscord status` command — shows current connection state and configured platform

### Changed
- All `/vonix` commands rebranded to `/viscord`
- Config directory reorganized to `config/viscord/`

## [2.3.0] - 2026-03-21

### Changed
- Config sections renamed for clarity: `server_identity`→`server`, `message_formats`→`formats`, `loop_prevention`→`filters`, `bot_status`→`bot`, `account_linking`→`linking`

## [2.2.0] - 2026-03-21

### Added
- Discord formatting support — Minecraft `§` and `&` codes converted to Discord markdown (bold, italic, underline, strikethrough, colors as emoji indicators)
- Tridirectional chat — 3-way message synchronization between Discord ↔ Minecraft ↔ Fluxer

## [2.1.0] - 2026-03-21

### Added
- Fluxer webhook service support as alternative to Discord
- Platform selection in config (`platform: "discord"` or `"fluxer"`)
- Configurable HTTP receiver for bidirectional Fluxer communication
