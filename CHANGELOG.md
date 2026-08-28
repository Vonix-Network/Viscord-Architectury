# Changelog

All notable changes to Viscord will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [5.0.0] - 2026-08-28

Common-generation repository release. This stable release starts the shared repository/layout lineage at `2.0.0`; Viscord's independent embedded/project release is `5.0.0`. Historical Viscord tags remain unchanged.

### Added
- Added the Minecraft **26.1.2 / NeoForge 26.1.2.93** lane to the single-repository version matrix.
- Documented the five-lane repository structure and release boundary in `docs/COMMON-V2-REPOSITORY.md`.

### Verification boundary
- The tag-triggered CI workflow is the source of build/package evidence for this release. Earlier R14 static evidence is not reused after the embedded version metadata change.
- Corrected the conditional core include-graph check so standalone version templates do not require the repository-root verification task.
- Corrected the historical shaded-Javacord regression gate: every 1.18.2–1.21.1 release jar must contain relocated `MessageBuilderBase.class`; the standalone 26.1.2 core-contract lane is checked separately.
- CI runs the 1.18.2–1.21.1 Loom lanes under Java 21 and the 26.1.2 ModDevGradle lane under Java 25/Gradle 9.2.0.
- All nine primary artifacts use the exact embedded version `5.0.0`.

## [Unreleased]

## [4.2.2] - 2026-06-24

Focused console-noise + correctness release. Suppresses the Javacord 3.8.0 "Couldn't handle packet of type MESSAGE_UPDATE" / "Couldn't parse the component of type" stacktraces caused by Discord's **Components V2** (Container=17, TextDisplay=10, Section, Separator, Thumbnail, MediaGallery, File…) — which Javacord cannot parse — landing in *any* channel of the bot's guild, including channels Viscord isn't configured to watch.

The path forward for **rendering** V2 content in watched channels is the planned Javacord→JDA migration (tracked separately); this release stops the bleeding so server logs stay readable in the meantime.

### Fixed
- **Components V2 console spam suppressed.** New `ComponentV2LogFilter` installs a Log4j 2 filter on the relocated `network.vonix.viscord.shadow.javacord.core.util.gateway.PacketHandler`, `…component.ActionRowImpl`, `…entity.message.MessageImpl`, and `…DiscordApiImpl` loggers. Channel-aware:
  - **Unwatched channels** (giveaway bots, ticket bots, etc. in other guild channels): warning is silently dropped — no stacktrace, no log line at all.
  - **Watched channels** (`discord.channel_id` + `discord.events.channel_id`): the raw stacktrace is still suppressed, but a single clean `DEBUG` line is emitted recording the dropped V2 message so operators can see *which* watched-channel content is being lost until JDA migration. Set the `viscord` logger to `DEBUG` to surface these.
  - Filter is idempotent — re-installable after config reload to refresh the watched set.
- Removed dead `Thread.setDefaultUncaughtExceptionHandler` band-aid in `BotClient.onConnected`. The exception was always caught and logged by Javacord's own `PacketHandler` try/catch, so it never reached the uncaught-exception path. Log4j filtering is the correct seam.

### Internal
- New file: `common/src/main/java/network/vonix/viscord/discord/ComponentV2LogFilter.java` (all four templates, byte-identical).
- All four templates bumped to `4.2.2` in `gradle.properties`.

## [4.2.1] - 2026-06-22

This is a focused security release hardening the Discord/Fluxer bot-side text triggers (`/link`, `!list`). Drop-in compatible with 4.2.0 configs — the new `[discord_rate_limit]` keys are auto-injected on first start.

### Security
- **Brute-force protection on `/link`.** Anyone in the bridged Discord channel could previously spam `/link 000000`, `/link 000001`, … against the 1,000,000-key 6-digit code space. With pending codes valid for up to 5 minutes, a sustained brute-force attempt had a non-trivial chance of landing on an active code. Added a sliding-window rate limiter (`DiscordCommandRateLimiter`) with per-Discord-user and channel-wide buckets, configurable via the new `[discord_rate_limit]` TOML section. Default: 3 `/link` per user per 60s, 30 channel-wide per 60s. **Silent on hit** by design — replying would let an attacker measure the window and pace around it. Same protection applied to `!list` and Fluxer `!list` (all templates)
- **Strict `/link` format pre-validation.** `handleLinkCommand` now rejects anything that doesn't match `^\d{6}$` with a single generic error before reaching `verifyAndLink`. No enumeration help — a 5-digit submission and a 7-digit submission produce identical errors (all templates)
- **No bucket leakage from MC-side commands.** `/viscord reload` and `/viscord status` remain gated to op 4 (vanilla `requires(hasPermission(4))`); `/viscord discord link|unlink|messages|events|help` use `getPlayerOrException()` so console invocation is rejected. No Discord-side admin commands exist — privileged operations only run on the MC side, behind op 4.

### Added
- `[discord_rate_limit]` TOML section: `link_per_user_per_min`, `link_global_per_min`, `list_per_user_per_min`, `list_global_per_min`. Sliding 60-second windows, value `0` disables the bucket. Registered in `ConfigSpec` and `applyDefaults` so existing `viscord.toml` files get the keys auto-injected on first start after upgrade (all templates)
- Documentation: `docs/configuration.md` gains a `[discord_rate_limit]` reference; `docs/account-linking.md` and `docs/security.md` updated with the new mitigations; `docs/troubleshooting.md` gains a section on the silent-rate-limit behaviour; `docs/commands.md` notes the per-command limits.

### Changed
- `DiscordManager.handleLinkCommand` checks rate limit before any other work, then format-validates the code with `LINK_CODE_FORMAT` before touching `LinkedAccountsManager` (all templates)
- `DiscordManager.handleTextListCommand` checks rate limit at entry (all templates)
- `DiscordManager.handleFluxerListCommand` checks rate limit at entry using a shared `"fluxer"` bucket key (Fluxer's current `onFluxerMessage` signature doesn't surface a stable per-user id; the global cap still applies) (all templates)

## [4.2.0] - 2026-06-15

This is a stability and consolidation release. No new user-facing features, but a large number of latent thread-safety bugs are fixed, dead code is removed, and the four MC-version templates are brought back into parity. Drop-in compatible with 4.1.x configs.

### Security
- **Webhook tokens no longer leak into logs.** `WebhookClient` and `FluxerWebhookClient` both shipped error/warn log lines that included the full webhook URL — including the bearer token segment — on every send failure or URL-parse failure. Both now route any URL into a `redactWebhookUrl()` helper that emits `…/webhooks/{id}/***` (all templates)

### Fixed (thread-safety & async correctness)
- **Unbounded `Executors.newCachedThreadPool()` replaced.** `Viscord.ASYNC_EXECUTOR` is now a bounded `ScheduledThreadPoolExecutor` (cores/2 core → cores×2 max, 30s keepalive, named daemon `Viscord-Async-N` threads, `CallerRunsPolicy` back-pressure). A burst of misbehaving network calls can no longer spawn unlimited threads. New `Viscord.scheduleAsync(Runnable, long delayMs)` helper for delayed work (all templates)
- **`DiscordManager` shared mutable fields not visible across threads.** `server`, `bridge`, `linkedAccountsManager`, `playerPreferences`, and `running` are read from Javacord listener / Fluxer WebSocket / Brigadier / tick threads but were not `volatile`. All five are now `volatile`. Same fix applied to `BotClient.api` and `FluxerBotClient.{webSocket,token,sessionId,onReadyCallback,messageHandler}` (all templates)
- **`BotClient` TOCTOU NPE between `api != null` check and `api.xxx()` call.** Every method that touches `api` now captures `DiscordApi local = api;` at entry and uses `local` (all templates)
- **`FluxerBotClient` WebSocket TOCTOU.** Every method touching `webSocket` now captures a local before use; same pattern applied (all templates)
- **`recentAdvancements` debounce cache was unbounded and used a racy `if (size > 100) clear()` eviction** that destroyed all in-flight dedupe state at once. Replaced with a synchronized access-order `LinkedHashMap` (cap 256, auto-LRU eviction) and a clean read-then-put block under `synchronized` (all templates)
- **`TridirectionalBridge` echo cache was unbounded.** Same `if(size>X) clear()` racy pattern lived in `FluxerPlatform.sendWebhookMessage` as well. Replaced with a 512-entry synchronized LRU owned by the bridge; `FluxerPlatform.sendWebhookMessage` dropped from 4-arg to 3-arg (no longer touches the cache); new `TridirectionalBridge.rememberOutgoing(...)` helper handles registration (all templates)
- **`scheduleStatusUpdate` burned one pool thread per call holding a `Thread.sleep` for the full delay.** 100 reconnecting players × 500 ms = 100 sleeping threads. Now uses `Viscord.scheduleAsync(...)` (true scheduling, no thread held) and coalesces bursts via an `AtomicBoolean` CAS — at most one pending status update at a time (all templates)
- **`FluxerPlatform` initialize used `submit({sleep(1500); pushStatus();})` and `thenRun({sleep(500); ...})` patterns** that held pool threads. Both replaced with `Viscord.scheduleAsync` and `CompletableFuture.delayedExecutor(..., ASYNC_EXECUTOR)` respectively (all templates)
- **`SERVER_STOPPING` lifecycle thread was blocked by nested `.join()` calls** in `DiscordManager.shutdown` → `DiscordPlatform.shutdown` (`orTimeout(3s).join()`) → `WebhookClient.shutdown` (`awaitTermination(3s)`) → executor `awaitTermination(5s)`. Worst-case ~11 s of stop-thread blocking. Shutdown now runs entirely on `ASYNC_EXECUTOR` with a single overall `orTimeout(5s)`; `DiscordPlatform.shutdown` chains cleanup via `.handle(...)` instead of `.join()` (all templates)
- **`/viscord reload` sent command feedback from the executor thread.** `sendSuccess` / `sendFailure` from off-thread can corrupt the Vanilla packet pipeline. All command output is now bounced back to the server thread via `mcServer.execute(...)`. The arbitrary 1-second `Thread.sleep` after shutdown is gone (all templates)
- **`LinkedAccountsManager.save()` ran synchronous file I/O** on whichever thread called `unlinkAccount` — typically the Brigadier command thread, i.e. the server tick thread. The JSON snapshot is now built on the caller under `synchronized(linkedAccounts)` and the disk write is offloaded to `Viscord.ASYNC_EXECUTOR` (matches the `PlayerPreferences` fix from 4.1.5) (all templates)
- **`FluxerBotClient` `CompletableFuture.supplyAsync(...)` calls** (sendEmbed, sendMessage) ran blocking `HttpURLConnection` on `ForkJoinPool.commonPool`, which is the wrong pool for blocking work. Both now pass `Viscord.ASYNC_EXECUTOR` as the explicit executor (all templates)

### Removed
- **Dead `FluxerPlatform.eventWebhookClient` field.** Declared but never wired or shut down — its OkHttpClient pool leaked for the JVM lifetime (all templates)
- **Dead `/viscord fluxer invite` and top-level `/fluxer` Brigadier commands.** Both unconditionally returned "Fluxer bot invite is not available". Removed entirely (all templates)
- **Legacy JSON config system: `config/ViscordConfig.java` and the `config/simple/` package** (4 files). Replaced by TOML in 4.0; one stale read remained in `MessageConverter` (caused `messages.use_display_name` to silently have no effect through that path). `MessageConverter` now reads from `ViscordConfigToml.Messages.USE_DISPLAY_NAME` (all templates)
- **Stale `SimpleConfigManager` / `SimpleConfigSpec` imports** in `TomlConfigManager.java` (1.21.1)
- **Tracked build artefacts** removed from git: `__pycache__/`, `error.txt`, `build_errors.txt`. `.gitignore` extended to cover `__pycache__/`, `*.pyc`, ad-hoc `fix*.py`, and build log `*.txt`

### Changed
- **Cross-version drift eliminated.** Before 4.2.0 the 1.21.1 template held bug fixes that the 1.18.2/1.19.2/1.20.1 templates were missing — most notably `FluxerBotClient.selfId` ID-based self-message filtering (the older templates fell back to broken prefix matching) and `DiscordManager.resetInstance()`. All four templates are now in parity on every common-tree file except for unavoidable Mojang-API renames (`TextComponent` → `Component.literal`, `sendMessage(msg, NIL_UUID)` → `sendSystemMessage(msg, false)`, Brigadier `sendSuccess(Component, ...)` → `sendSuccess(Supplier<Component>, ...)`)
- **`/viscord discord help` text corrected.** No longer advertises `[enable|disable]` subcommands that don't exist; no longer references the deleted `/viscord fluxer invite`; the line about `Discord: /list` corrected to `!list` (the actual trigger is text, not a slash command)
- **`DiscordPlatform.shutdown()` no longer blocks** on the embed future internally — caller awaits the returned future; bot/webhook teardown chains via `.handle(...)`

### Documentation
- **README rewritten** to reflect shipping 4.2.0 surface — the previous README still described the legacy `config/viscord.json` format with deprecated key names, recommended Fluxer port-forwarding (no longer needed — Fluxer uses WebSocket Gateway), omitted the `both` and `tridirectional` modes, and listed nonexistent commands like `/link` / `/unlink` (the real ones are `/viscord discord link` / `unlink`)

## [4.1.12] - 2026-05-08

### Fixed
- **Discord chat and cross-server events not visible in Minecraft** — `isSelfOriginated()` and the `FILTER_BY_PREFIX` guard in `processDiscordMessageForMinecraft()` were applying the server-prefix check to *all* Discord message authors, not just webhooks/bots. Any regular Discord user whose display name started with the server prefix (e.g. `[MC]`) was silently dropped, and even trusted cross-server bots could be blocked. Prefix/ID checks are now restricted to webhook and bot authors only; regular users always pass through (all templates)

## [4.1.11] - 2026-05-07

### Fixed
- **Server-side only configuration** — Removed client entrypoint from all `fabric.mod.json` files, set environment to `"server"`, deleted client directories, and changed dependency sides to `"SERVER"` in all `mods.toml` files. Mod now runs server-side only and does not require players to have the mod installed (all templates)
- **Shutdown ClassNotFoundException in shadowed Javacord classes** — Added `eventBus.excludedPackages = "network.vonix.viscord.shadow"` to Forge/NeoForge `mods.toml` files to prevent EventBus transformer from attempting to transform shadowed classes during server shutdown, which caused `ClassNotFoundException: MessageBuilderBase` errors (all Forge/NeoForge templates)

## [4.1.10] - 2026-04-30

### Fixed
- **`use_display_name = true` shows username instead of display name** — `resolveAuthorName()` called `MessageAuthor.getDisplayName()` which resolves to server nickname or falls back directly to the plain username, skipping Discord's global display name (`global_name`). For users with no server nickname the setting had no effect. Now unwraps to the `User` object and resolves: server nickname → global display name → username (all templates)

## [4.1.9] - 2026-04-30

### Fixed
- **Display name toggle not respected in all message paths** — `DiscordManager` had four code paths (tridirectional bridge author name, direct message author, embed fallback author, player-list embed author) that called `.getDisplayName()` directly without checking the `formats.use_display_name` config, so setting it to `false` had no effect on those paths. All four now go through a shared `resolveAuthorName()` helper that checks the config (1.20.1 template)
- **New config keys missing from existing TOML on upgrade** — `TomlConfigManager.createConfigSpec()` and `applyDefaults()` were both missing `messages.use_display_name` and `filters.trusted_bot_ids`, so `ConfigSpec.correct()` never injected them into existing `viscord.toml` files on server start. Users upgrading from 4.1.5 would not see these options in their config. Both keys are now registered in the spec and defaults (1.20.1 template)

## [4.1.8] - 2026-04-30

### Fixed
- **Echo loop prevention regardless of filter config** — Minecraft → Discord messages could be re-bridged back into game chat when any of `filters.ignore_bots`, `filters.ignore_webhooks`, or `filters.filter_by_prefix` were disabled (or when their checks misfired). Added `isSelfOriginated()` invoked **unconditionally at the very top of `onDiscordMessage`**, checking three independent signals: (1) webhook ID extracted from `discord.webhook_url`, (2) the bot's own user ID via `BotClient.getBotUserId()`, (3) author display-name prefix pattern. Any match drops the message. Self-origin filtering is now a hard guarantee rather than a side-effect of configurable filters. Exposes `getBotUserId()` on `BotClient` and `DiscordPlatform` so the manager can resolve the bot ID at runtime (all templates)
- **Garbled `!list` output** — `handleTextListCommand` and `handleFluxerListCommand` contained literal newline characters embedded directly in Java string literals where `"\n"` escape sequences were intended, producing a single-line wall of names with no separators. Replaced with proper escapes (all templates)

### Documentation
- Backfills the `4.1.7` per-template `CHANGELOG.md` entries which were committed with empty bodies. (Top-level `CHANGELOG.md` already had the full 4.1.7 entry from commit `fd37659`.)

## [4.1.7] - 2026-04-29

### Fixed
- **Thread-safety: volatile singleton** — `DiscordManager.instance` was a plain (non-volatile) static field; `getInstance()` and `resetInstance()` are now `synchronized` and the field is `volatile`, eliminating a double-checked-locking race on multi-core JVMs (all templates)
- **Thread-safety: `discordEnabled` flag** — `Viscord.discordEnabled` was set from an async thread and read from the server thread with no visibility guarantee; field is now `volatile` (all templates)
- **Thread-safety: `PlayerPreferences` map** — changed backing store from `HashMap` to `ConcurrentHashMap` to prevent `ConcurrentModificationException` when server thread reads while async thread writes (all templates)
- **Shutdown: `Thread.sleep` on server thread** — `DiscordManager.shutdown()` was calling `Thread.sleep(1500)` and `Thread.sleep(100)` directly on the Minecraft server thread during `SERVER_STOPPING`; both sleeps removed and the Discord shutdown future is now joined with a 3-second timeout instead (all templates)
- **Shutdown: shutdown embed dropped** — `DiscordPlatform.shutdown()` was calling `botClient.disconnect()` immediately after firing the async shutdown embed, so the embed HTTP request was typically cancelled; now waits up to 3 seconds for the embed future before disconnecting (all templates)
- **Shutdown: `ASYNC_EXECUTOR` never terminated** — the cached thread pool was never shut down on `SERVER_STOPPING`, leaving non-daemon threads alive after server stop; executor is now shut down with a 5-second `awaitTermination` (all templates)
- **Shutdown: `WebhookClient` dispatcher not awaited** — `WebhookClient.shutdown()` called `executorService().shutdown()` but never waited for in-flight requests to finish; now calls `awaitTermination(3, SECONDS)` so in-progress webhook sends are not abandoned (all templates)
- **Security: predictable link codes** — `LinkedAccountsManager` used `new Random()` (time-seeded, predictable) to generate 6-digit account-link codes; replaced with a shared `SecureRandom` instance (all templates)
- **Security: account-link TOCTOU** — the "is Discord already linked?" check and `linkedAccounts.put()` in `verifyAndLink` were not atomic; concurrent `/link` calls could bind one Discord account to two Minecraft UUIDs; protected by a `synchronized (linkedAccounts)` block (all templates)
- **Data integrity: `FileWriter`/`FileReader` without charset** — `LinkedAccountsManager` used platform-default encoding; both now explicitly use `StandardCharsets.UTF_8` to prevent non-ASCII username corruption on Windows (all templates)
- **Config: `Long` → `Integer` silent coercion failure** — NightConfig stores TOML integers as `Long`; `ConfigValue<Integer>.get()` was catching `ClassCastException` and silently returning the hardcoded default, ignoring all user-configured integer values (e.g. `account_linking.code_expiry`); added `Number` coercion before the cast (all templates)
- **Thread-safety: `!list` player list read off server thread** — `handleTextListCommand` and `handleFluxerListCommand` called `server.getPlayerList().getPlayers()` directly on the Javacord/Fluxer WebSocket thread, violating Minecraft's thread-safety model; both methods now marshal via `server.execute()` before accessing player list state (all templates)
- **Duplicate `!list` handling** — `processDiscordMessageForMinecraft` re-checked for `!list` after `onDiscordMessage` had already handled and returned, causing the player-list embed to be sent twice per command; duplicate guard removed (all templates)
- **Chat formatting: `&` replacement breaks URLs** — `ChatFormatter.parseColors` unconditionally replaced all `&` with `§`, corrupting URLs like `?a=1&b=2` into `?a=1§b=2`; now only replaces `&` when followed by a valid Minecraft formatting code character or `#` for hex colors (all templates)

## [4.1.6] - 2026-04-28

### Added
- **Display name toggle**: new `formats.use_display_name` config option (default `true`). When `false`, Discord messages show the plain `@username` instead of the server nickname / global display name (all templates)
- **Multi-channel monitoring**: `discord.channel_id`, `discord.event_channel_id`, `fluxer.channel_id`, and `fluxer.event_channel_id` now accept comma-separated channel IDs (e.g. `"123,456,789"`), allowing a single bot to monitor multiple channels simultaneously (all templates)
- **Trusted bot IDs**: new `filters.trusted_bot_ids` config option (comma-separated Discord user/webhook IDs). Bots in this list bypass `ignore_bots` / `ignore_webhooks` filters so their event embeds (join/leave/death/advancement) are always relayed to Minecraft chat. Use this to receive cross-server event messages from another server's Viscord bot (all templates)

### Fixed
- Cross-server event embeds (join/leave/death/advancement) from other servers' Viscord bots were silently dropped because `ignore_bots` / `ignore_webhooks` filters fired before any embed detection. Messages from bots listed in `trusted_bot_ids` now skip those filters entirely (all templates)

## [4.1.5] - 2026-04-22

### Fixed
- `/discord messages` and `/discord events` not re-enabling — `/viscord discord *` subcommands required OP due to Brigadier merging the two `/viscord` registrations and preserving the first node's `requires(permission 4)` predicate; moved OP requirement down to only `reload` and `status` subcommands so regular players can access discord preference commands via both `/discord` and `/viscord discord` paths (all templates)
- All four toggle commands (`/discord messages`, `/discord events`, `/viscord discord messages`, `/viscord discord events`) are now consistent pure-toggle switches across all templates — 1.18.2/1.19.2/1.20.1 had explicit `enable`/`disable` subcommands instead

### Changed
- `PlayerPreferences.savePreferences()` now builds the JSON snapshot on the calling thread then offloads the file write to `ASYNC_EXECUTOR`, preventing disk I/O from blocking the server main thread on every preference change (all templates)

### Fixed (build)
- 1.19.2 `gradle.properties`: corrected `org.gradle.parallel=false` → `true` and removed duplicate comment line that was degrading parallel build performance

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

## [2.4.8] - 2026-03-24

### Fixed
- **Fluxer Bot compilation error** — `FluxerBotClient` declared a 5-argument override of `onDisconnected` that did not exist on the supertype, causing `method does not override or implement a method from a supertype` errors across all four version templates. Reverted to the standard 4-argument signature (all templates)

### Changed
- **Build tooling** — `build_menu.ps1` defaults bumped to 2.4.8 and Java 21 detection logic refined so legacy Minecraft versions (1.18.2 / 1.19.2 / 1.20.1) consistently pick a supported JDK toolchain on Windows

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

## [2.4.2] - 2026-03-21

### Fixed
- **Fluxer event embeds not appearing** — join/leave/death/advancement notifications were never reaching Fluxer because of a routing gap in `FluxerPlatform` event handling. Embeds now render correctly via the Fluxer webhook path (all templates)
- **Tridirectional bot init in Fluxer-only mode** — when `general.platform = "fluxer"` with tridirectional disabled, the Discord bot was still being partially initialized, holding open a Javacord client with no purpose. The bot is now skipped entirely in Fluxer-only mode (all templates)
- **Bot status not updating in Fluxer mode** — `scheduleStatusUpdate` only pushed to the Discord client; Fluxer mode showed a stale presence. Status updates now flow through the active platform delegate, so Fluxer mode shows live `Online: X/Y` (all templates)

### Changed
- **`DiscordEventHandler` Brigadier registration** — minor cleanup to the command-registration paths to support the routing fixes above

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
