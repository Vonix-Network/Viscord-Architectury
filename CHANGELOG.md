# Changelog

All notable changes to Viscord will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.3] - 2026-03-25

### 🐛 Fixed (Fluxer Bot Player Count Status)
- **Fluxer Bot Status Now Shows Actual Player Count** - Fixed Fluxer bot displaying "0/0" instead of actual online/max player count
  - **Root cause**: `FluxerBotClient` was hardcoding player counts to "0" in READY/RESUMED event handlers instead of using real server values
  - **Fix**: Removed hardcoded status updates from `FluxerBotClient.handleMessage()` READY/RESUMED handlers (lines 310-321, 327-332)
  - **Implementation**: Status updates now flow through `DiscordManager.updateBotStatus()` which:
    - Reads actual player count from `server.getPlayerList()`
    - Applies the configured format from `ViscordConfigToml.BotStatus.FORMAT.get()`
    - Replaces `{online}` and `{max}` placeholders with real values
    - Updates both Discord and Fluxer bots consistently
### 🔍 Fixed (Status Update Reliability)
- **Enhanced Status Update Logging** - Added detailed INFO-level logging across all versions to trace the status update flow for both Discord and Fluxer bots.
  - **Diagnostic Logs**: Added tracking for `server` instance availability, `BotStatus` configuration state, and WebSocket connection status.
  - **Payload Verification**: Added logging of the exact status string being sent to the Fluxer gateway.
  - **Async Trace**: Added logging inside the asynchronous executor to identify potential race conditions or authentication timing issues.
  - **File modified**: `FluxerBotClient.java` in 1.21.1 (READY/RESUMED handlers)
  - **Surgical edit location**: `handleMessage()` method, removed hardcoded status scheduler blocks

### 🐛 Fixed (Config Reload Crash)
- **Fixed Server Restart Crash** - Fixed `UnsupportedOperationException: StampedConfig does not support valueMap()` error on second server start
  - **Root cause**: `ConfigSpec.correct()` calls `valueMap()` which isn't supported by NightConfig's concurrent `StampedConfig` used when autosave is enabled
  - **Fix**: Modified `TomlConfigManager.loadToml()` to:
    - Build config without autosave first
    - Run `spec.correct()` and save
    - Close and rebuild with autosave enabled
    - Added try-catch for graceful degradation if correction fails
  - **Result**: Server can now restart without config-related crashes
  - **File modified**: `TomlConfigManager.java` in 1.21.1
  - **Surgical edit location**: `loadToml()` method, lines 101-127

## [3.0.2] - 2026-03-25

### 🐛 Fixed (1.21.1 Compilation Issues)
- **Java 21 Build Compatibility** - Fixed build failures for Minecraft 1.21.1 due to Java version requirements
  - **Root cause**: Architectury Loom 1.11 requires Java 21+ but system was using Java 17
  - **Fix**: Updated build system to auto-detect and use Java 21 when available
  - **NeoForge API Migration**: Updated from old Forge APIs to NeoForge APIs
    - Replaced `net.minecraftforge.common.MinecraftForge` with `net.neoforged.neoforge.common.NeoForge`
    - Updated event bus registration for NeoForge compatibility
    - Removed conflicting old Forge handler files
  - **Command API Updates**: Fixed `sendSuccess()` method signature changes
    - Updated all `sendSuccess(Component.literal(...))` calls to `sendSuccess(() -> Component.literal(...))`
    - Required due to API change in 1.21.1 where method now expects `Supplier<Component>`
  - **Advancement API Migration**: Updated advancement mixins for 1.21.1
    - Changed `Advancement` parameter to `AdvancementHolder` in `award()` method
    - Updated display info access: `advancement.getDisplay()` → `advancementHolder.value().display().orElse(null)`
    - Applied to both Fabric and NeoForge mixins
  - **TextColor API Fix**: Fixed `TextColor.parseColor()` DataResult handling
    - Updated to handle new `DataResult<TextColor>` return type instead of direct `TextColor`
    - Added proper fallback color handling for parsing failures
  - **Config System Update**: Updated NeoForge chat handler to use TOML config
    - Changed from `ViscordConfig.CONFIG` to `ViscordConfigToml.Filters.Chat` references
  - **Files modified**: 
    - `ViscordForge.java` → `ViscordNeoForge.java` (NeoForge main class)
    - `NeoForgeChatEventHandler.java` (Component to String conversion)
    - `DiscordEventHandler.java` (29 sendSuccess calls updated)
    - `MessageConverter.java` (TextColor parsing)
    - `PlayerAdvancementsMixin.java` (Fabric + NeoForge)
  - **Build verification**: All platforms (Fabric + NeoForge) now compile successfully with Java 21

## [3.0.1] - 2026-03-25

### 🐛 Fixed (Fluxer to Discord Message Formatting)
- **Fluxer Messages Showing Double Prefix/Username** - Fixed Fluxer messages appearing as "[Fluxer] OGPargon: [Fluxer] OGPargon: Hello o.o" in Discord.
  - **Root cause**: `bridgeFluxerToDiscord()` was using `formatMessageForPlatform()` which added "[Fluxer] username:" prefix to message content, but the webhook username also needed the [Fluxer] prefix, causing duplication
  - **Fix**: Modified `bridgeFluxerToDiscord()` to:
    - Remove [Fluxer] prefix and username from message content before sending
    - Add [Fluxer] prefix to the webhook username instead (line 489: `webhookClient.sendMessage("[Fluxer]" + username, "", convertedMessage)`)
    - Clean message content by extracting only the actual message part after removing prefixes and usernames
  - **Result**: Fluxer messages now display correctly in Discord as "[Fluxer]OGPargon: Hello o.o" with proper username formatting and no duplication
  - **File modified**: `DiscordManager.java` in all versions (1.18.2, 1.19.2, 1.20.1, 1.21.1)
  - **Surgical edit location**: `bridgeFluxerToDiscord()` method, lines 459-496

## [3.0.0] - 2026-03-25

### 🔄 Changed (Breaking Change - Config System Rewrite)
- **Complete Configuration System Overhaul** - Migrated from JSON to TOML configuration format with full restructuring
  - **New config file**: `config/viscord/viscord.toml` (replaces `viscord.json`)
  - **Automatic migration**: Existing JSON configs are automatically migrated to TOML format on first run
  - **Backup creation**: Old JSON configs are backed up as `viscord.json.backup` after migration
  - **Restructured hierarchy**: Better organized config sections with cleaner naming conventions
  - **Dependencies**: Added `night-config` library for TOML parsing (v3.6.7)
  - **Files modified**: All configuration-related files across all versions
  - **New classes**: `TomlConfigManager.java` and `ViscordConfigToml.java`
  - **Updated files**: `Viscord.java`, `DiscordManager.java`, `DiscordEventHandler.java`, and all other files referencing config

### 📝 Documentation
- **Updated Configuration Documentation** - New TOML configuration examples and migration guide
  - Added migration instructions from JSON to TOML
  - Updated config field references throughout documentation
  - New structured config examples reflecting the reorganized hierarchy

## [2.4.15] - 2026-03-25

### 🐛 Fixed (Fluxer Bot Online Status - Tri-Directional Mode)
- **Fluxer Bot Showing Offline Despite Successful Authentication** - Fixed bot appearing offline when using tri-directional mode with `platform: "discord"`.
  - **Root cause**: `DiscordManager.initializeFluxer()` calls `updateBotStatus()` immediately when connection future completes, but `FluxerBotClient.updateStatus()` validates `authenticated` flag which may not be set yet when READY dispatch hasn't been processed
  - **Fix**: Added immediate status update when READY is received (lines 310-313 in FluxerBotClient.java), in addition to the existing 500ms scheduled backup
  - **Also changed**: Enhanced logging from DEBUG to WARN level in `updateStatus()` to make connection/authentication state visible for troubleshooting
  - **Files modified**: `FluxerBotClient.java` in all versions (1.18.2, 1.19.2, 1.20.1, 1.21.1)
  - **Surgical edit locations**: 
    - `handleMessage()` READY case: Added immediate `updateStatus()` call after `authenticated = true`
    - `updateStatus()` method: Changed log level from DEBUG to WARN with detailed state info

### 📝 Documentation
- **Added Tri-Directional Setup Guide** - New documentation section explaining proper configuration for 3-way chat between Discord, Fluxer, and Minecraft
  - Clarified that `platform` setting determines which service receives messages FROM Minecraft
  - Added bot status configuration notes for tri-directional setups
  - Included example configuration snippets

## [2.4.14] - 2026-03-25

### 🚀 Improved (Fluxer Bot Online Status)
- **Verified Fluxer Bot Online Status** - Confirmed bot properly marks itself online during WebSocket Gateway connection
  - Identify payload includes `presence: {status: "online"}` for immediate online status
  - Uses modern Discord gateway v8+ format (`os`, `browser`, `device` properties)
  - Compatible with Fluxer.app's Discord.js-style gateway protocol
  - `updateStatus()` method available for custom status text after connection
  - Verified across all Minecraft versions (1.18.2, 1.19.2, 1.20.1, 1.21.1)

### 🐛 Fixed (All Versions)
- **Fluxer Gateway 4002 "Invalid identify payload" Error** - Fixed bot connection failures due to deprecated Discord gateway v6 properties format.
  - **Root cause**: The identify payload was using legacy `$os`, `$browser`, `$device` property names (Discord gateway v6 format)
  - **Fix**: Updated to modern `os`, `browser`, `device` property names (Discord gateway v8+ format)
  - **Also changed**: Updated browser/device values from `discord.js` to `viscord-bot` for proper identification
  - **Files modified**: `FluxerBotClient.java` in all versions (1.18.2, 1.19.2, 1.20.1, 1.21.1)
  - **Surgical edit location**: `sendIdentify()` method, lines 372-376 (1.21.1), 391-395 (1.19.2), 393-397 (1.18.2), 396-400 (1.20.1)

## [2.4.12] - 2026-03-25

### 🌟 Added
- **Modern Python CLI Build Menu** - Completely rewritten build menu using Rich library for beautiful terminal UI
  - **Visual Progress Bars**: Real-time Gradle build progress with animated spinners and progress indicators
  - **Interactive Menus**: Clean, centered menu tables with keyboard navigation
  - **Build Status Tracking**: Success/failure reporting with detailed error output
  - **Java Auto-Detection**: Automatic discovery of installed Java versions across Eclipse Adoptium, Program Files, JAVA_HOME, and PATH
  - **Smart Java Selection**: Auto-selects Java 21+ for all Minecraft versions (required by modern Architectury Loom)
  - **Multi-Platform Build**: Support for Fabric, Forge, and NeoForge platform detection
  - **Build Type Selection**: Choose between Clean Build (recommended) and Quick Build (faster)
  - **Flexible Output**: Copy JARs to Releases folder, versioned folders, or custom destination
  - **Rich Error Display**: Last 30 lines of build output shown on failure for debugging
  - **UTF-8 Support**: Full Unicode support for modern terminal emulators
  - **Launcher Script**: `build_menu_launcher.bat` for easy one-click startup

### Changed
- Replaced PowerShell `build_menu.ps1` with Python `build_menu.py` for cross-platform compatibility and superior UI

## [2.4.11] - 2026-03-25

### 🐛 Fixed (All Versions Compilation + Tridirectional Chat)
- **Missing `fluxerEventWebhookUrl` config field** - Added the missing field declaration and initialization that was causing `cannot find symbol` compilation errors across all Minecraft versions (1.18.2, 1.19.2, 1.20.1, 1.21.1).
  - Field was referenced in `DiscordManager.java` but not declared in `ViscordConfig.java`
  - Added proper builder configuration for `fluxer.event_webhook_url` setting
- **Tridirectional Chat Echo Loop** - Fixed Discord messages echoing back when tridirectional chat was enabled.
  - **Root cause**: `onFluxerMessage` was passing raw message content to `bridgeFluxerToDiscord` instead of the formatted content containing the `[Discord]` tag
  - **Fix**: Modified `onFluxerMessage` to pass the `formatted` message (which includes `[Discord]` tags when `showPlatformSource` is enabled) to `bridgeFluxerToDiscord` for proper echo detection
  - **Enhancement**: Updated logging from DEBUG to INFO level for echo detection to make troubleshooting easier
  - This prevents messages originating from Discord from being re-bridged back to Discord via Fluxer
- **Discord to Fluxer Messages Not Sending** - Fixed `bridgeDiscordToFluxer` in versions 1.19.2 and 1.21.1 still using deprecated webhook method instead of Bot API.
  - Updated to use `fluxerBotClient.sendMessage(channelId, fluxerMessage)` like versions 1.18.2 and 1.20.1
  - This ensures Discord messages are properly sent to Fluxer in tridirectional chat mode
- **Config Cleanup** - Removed deprecated Fluxer webhook and receiver fields from all version configs that were supposed to be removed in v2.4.10.
  - Removed: `fluxerWebhookUrl`, `fluxerEventWebhookUrl`, `fluxerReceiverPort`, `fluxerReceiverPath`, `fluxerApplicationId`
  - Fluxer now uses Bot API exclusively: `fluxerApiKey` + `fluxerChannelId` + `fluxerEventChannelId`

## [2.4.10] - 2026-03-24

### 🚀 Improved (Fluxer Overhaul — Bot API + Channel IDs)
- **Sending now uses Bot API**: All Minecraft → Fluxer messages (chat, join/leave/death, startup/shutdown) now use the Fluxer Bot REST API with channel IDs. No webhook URLs required.
- **Simplified Config**: Removed `fluxerWebhookUrl`, `fluxerEventWebhookUrl`, `fluxerReceiverPort`, `fluxerReceiverPath`, `fluxerUseBotApi`, `fluxerApplicationId`, `fluxerClientSecret`. Added `fluxerChannelId` and `fluxerEventChannelId` to mirror Discord's pattern.
- **No more port forwarding**: The old HTTP webhook receiver has been retired. All receiving is done via the WebSocket Gateway (already established in 2.4.9).
- **Consistent platform pattern**: Fluxer now configured identically to Discord: `bot_token` + `channel_id` + optional `event_channel_id`.
- **Event formatting for Fluxer**: Events are sent as plain bold-text messages (Fluxer Bot API v1 does not support embeds). Discord keeps its rich embed format unchanged.
- **Shutdown notification**: Server shutdown now sends a 🔴 offline message to Fluxer via Bot API before disconnecting.

## [2.4.9] - 2026-03-24

### 🐛 Fixed (Fluxer Gateway Deep-Fix)
- **Gateway API Version**: Switched WebSocket connection to API `v=1` (Fluxer's own protocol version). Historically set to `v=10` (Discord), which was the primary cause of bots being persistent offline.
- **Handshake Properties**: Updated `$browser` and `$device` identify properties to `discord.js` to ensure 100% compatibility with Fluxer's handshake expectations.
- **Intents**: Added `GUILD_MESSAGES` (bit 9) allowing the bot to receive `MESSAGE_CREATE` events on bridged channels. Total intents now: `(1 << 9) | (1 << 15)`.
- **Initial Presence**: Implemented immediate `online` presence during the `Identify` (OP 2) phase.
- **Post-READY status**: Added a delayed presence update after `READY` completion to set the initial status text before player counts start ticking.
- **Resume Stability**: Added `RESUMED` event handling to re-assert presence after session reconnects.
- **Protocol Compliance**: Fixed the `since` field in presence updates to be `null` when status is `"online"`, as required by the gateway spec.

## [2.4.8] - 2026-03-24

### 🐛 Fixed
- **Fluxer Bot Compilation** - Resolved `method does not override or implement a method from a supertype` error in `FluxerBotClient`.
  - Removed invalid 5-argument `onDisconnected` override.

### 🚀 Improved
- **Build System** - Updated `build_menu.ps1` with 2.4.9 defaults and refined Java 21 detection.

### 🔧 chore
- Bumped mod version to 2.4.9 across all templates.
- Updated `Release_Notes.md` for v2.4.4–v2.4.9.


## [2.4.7] - 2026-03-24

### 🐛 Fixed
- **Fluxer Bot WebSocket Connectivity** - Fixed infinite reconnection loop causing bot oscillation
  - Proper close code handling for 1000 (normal), 1006 (abnormal), 4004 (auth failed)
  - `connected` flag now set only after READY dispatch received, not just TCP connect
  - Added `authenticated` flag to track full gateway authentication state
  - Connect future completes only after bot is fully ready (not just connected)
  - Added session resume support for faster reconnections
  - Max reconnect attempts (10) before giving up to prevent spam
  - Exponential backoff: 2s → 4s → 8s up to 60s max
  - Bot status updates only sent when fully authenticated

## [2.4.6] - 2026-03-24

### 🐛 Fixed
- **Advancement Completion Check** - Fixed achievements being granted on progress updates instead of actual completion
  - Added `isDone()` check via `AdvancementProgress` to verify advancement is fully completed
  - Prevents premature triggering for multi-step achievements like "Cover me in debris" (netherite armor)
  - Now correctly only sends notifications when all criteria are satisfied, not on partial progress

## [2.4.5] - 2026-03-24

### 🌟 Added - Fluxer Bot Full Support
- **WebSocket Message Receiving** - FluxerBotClient now receives chat messages directly from Fluxer Gateway
  - No port forwarding required when using WebSocket mode
  - Handles MESSAGE_CREATE dispatch events
  - Extracts username, message content, and avatar URLs
  - Filters out bot messages automatically
- **Fluxer Bot API Sending** - Alternative to webhooks for sending messages
  - New `use_bot_api` config option in [fluxer] section
  - Sends messages via REST API using Bot Token
  - More reliable than webhooks
- **Config Expansion** - New OAuth2 fields for Fluxer
  - `application_id` - For generating invite links
  - `client_secret` - For Bot API authentication  
  - `use_bot_api` - Toggle between webhook and API sending
- **Authorize URL Helper** - New `/viscord fluxer invite` command
  - Generates Fluxer bot invite URL using configured Application ID
  - Clickable link output for easy bot installation
- **Updated Help Documentation** - `/viscord discord help` now includes fluxer commands

### 🆕 New
- **`/fluxer` Command Alias** - Players can now type `/fluxer` directly to get a clickable invite link for the Fluxer bot.
- **`/viscord discord invite` Sub-command** - New dedicated sub-command to display the Discord server invite link.
- **Discord → Minecraft Markdown Conversion** - Messages from Discord with `**bold**`, `*italic*`, `__underline__`, or `~~strikethrough~~` are now converted to native Minecraft formatting.

### 🐛 Fixed
- **Modpack Advancement Spam (Cobblemon Fix)** - Refactored advancement logic to prevent duplicate notifications on modpacks that trigger progress events multiple times.
  - Added native `isDone()` completion check to ensure only finished advancements are broadcast.
  - Implemented 5-second per-player debounce cache to eliminate notification spam.
  - Respects Minecraft's `shouldAnnounceChat` setting to filter out background/recipe advancements.
- **Fluxer Bot Reconnection** - Resolved thread-safety issues in `FluxerBotClient` preventing clean reconnections.
- **Duplicate Broadcasts** - Fixed an issue where advancements were being bridged twice when Tridirectional Chat was enabled.
- **`/viscord fluxer invite` Endpoint** - Updated to use the correct `fluxer.app` domain.

### 🚀 Improved
- **Native Event Handlers** - Migrated chat interception to native `ServerChatEvent` on Forge/NeoForge for superior mod compatibility.
- **Async Safety** - All Discord/Fluxer initialization and status updates are now fully non-blocking.
- **Documentation Updated** - Browser-based documentation fully reflects all 2.4.5 features.


## [2.4.4] - 2026-03-24

### 🚀 Improved
- Initial prep for native event handlers and fluxer updates.

## [2.4.3] - 2026-03-21

### 🐛 Fixed
- Fixed an issue where the Discord bot would duplicate the startup embed when status updates were enabled in Fluxer mode.
- Fixed Tridirectional Chat routing where event embeds (e.g. Advancements, Death, Server Startup) were only being sent to Fluxer and not being bridged back to Discord. They are now correctly sent to both platforms when Tridirectional Chat is enabled.

## [2.4.2] - 2026-03-21

### 🐛 Fixed
- Fixed an issue where the Discord bot would not turn on when using the Fluxer platform with Tridirectional Chat enabled.
- Fixed a bug where the Discord bot would not connect to show the server online status ("Playing X/Y") when using the Fluxer platform without Tridirectional Chat. The bot will now connect solely for status updates if a token is provided and status updates are enabled.
- Fixed server startup, join, and leave embeds not being sent when using the Fluxer platform.
- Fixed a bug where configuring a Fluxer webhook could result in an invalid channel ID format error ("Fluxer webhooks have IDs" exception).
- Fixed an issue where Achievements and Death events were not being sent to Fluxer. Events are now correctly bridged to the configured Fluxer webhook.
- Improved error messages for account linking in Minecraft when the bot is disabled or not running.
- Prevented silent thread crashes when configuring an invalid webhook URL.

## [2.4.1] - 2026-03-21

### 🔗 Account Linking System
- **Full /link Command Implementation** - Complete Discord-Minecraft account linking
  - Discord-side: `/link <code>` command for verification
  - Minecraft-side: `/viscord discord link` for code generation
  - 6-digit unique codes with configurable expiry time
  - Double-link prevention (MC & Discord accounts)
  - JSON persistence with automatic cleanup
  - Full error handling and user feedback
- **Link Management** - `/viscord discord unlink` command
- **Account Security** - Prevents multiple links per account
- **Data Persistence** - Links stored in `viscord-links.json`

## [2.4.0] - 2026-03-21

### ⚠️ Compatibility Notes
- **Minecraft 1.18.2**: Due to major API changes in this version, 1.18.2 builds are based on 2.3.0 codebase. Full 2.4.0 features (reload command, command rebrand) are not available for 1.18.2.

### 🌟 Added
- **Full Reload Capability** - New `/viscord reload` command for administrators
  - Disconnects and reconnects Discord/Fluxer bot with new config
  - Full async operation to prevent server lag
  - 30-second timeout protection
  - Graceful error handling with rollback
- **Status Command** - New `/viscord status` command for administrators
  - Shows current connection status (Running/Stopped)
  - Displays configured platform (Discord/Fluxer)
  - Shows if Viscord is enabled/disabled in config
- **Commands Documentation** - New commands section in web docs
  - Complete command reference for admins and players
  - Permission level documentation
  - Usage examples

### 🔄 Changed
- **Command Rebrand** - All `/vonix` commands rebranded to `/viscord`
  - `/vonix discord link` → `/viscord discord link`
  - `/vonix discord unlink` → `/viscord discord unlink`
  - `/vonix discord messages` → `/viscord discord messages`
  - `/vonix discord events` → `/viscord discord events`
  - `/vonix discord help` → `/viscord discord help`
  - `/vonix reload` (new) → `/viscord reload`
  - Backward compatibility alias maintained for `/vonix`
- **Documentation Updates** - Web docs updated to version 2.4.0
  - Added Commands tab with full reference
  - Version badges updated throughout
  - Footer version corrected

### 🔧 Technical Improvements
- **Config Directory Reorganization** - All Viscord files now stored in `config/viscord/`
  - Main config: `config/viscord/viscord.json`
  - Player preferences: `config/viscord/player_preferences.json`
  - Account links: `config/viscord/linked_accounts.json`
  - Better organization and separation from other mods
- **Async Safety** - Fixed blocking `.get()` call in Discord initialization
  - Prevents 10-second server freeze during startup
  - All Discord operations now fully async
  - Better error handling for connection failures
- **Production Readiness Review** - Full codebase review completed
  - Core mod structure verified
  - Discord integration layer optimized
  - Command system enhanced

### ⚡ Performance
- Discord initialization no longer blocks main thread
- Config reload runs entirely on async executor
- Message processing maintains async safety

## [2.2.0] - 2026-03-21

### 🌟 Added
- **Discord Formatting Support** - Minecraft formatting codes now convert to Discord markdown
- **Dual Code Support** - Both § codes and & codes are supported for maximum compatibility
- **Rich Text Formatting** - Bold (§l), Italic (§o), Underline (§n), Strikethrough (§m) support
- **Color Indicators** - Minecraft colors converted to emoji indicators since Discord doesn't support text colors
- **Magic/Obfuscated** - Converted to sparkle emoji (✨) for visual indication
- **Reset Codes** - Properly handled to close formatting tags
- **Cross-Platform Formatting** - Works in tridirectional chat and all message routing

### 🎨 Formatting Conversions
- **§l / &l** → **Bold text** (Discord: `**text**`)
- **§o / &o** → *Italic text* (Discord: `*text*`)
- **§n / &n** → __Underlined text__ (Discord: `__text__`)
- **§m / &m** → ~~Strikethrough text~~ (Discord: `~~text~~`)
- **§k / &k** → ✨Magic text✨ (Discord: sparkle emoji)
- **§r / &r** → Reset all formatting
- **Colors (0-9, a-f)** → Color emoji indicators (⚫🟦🟩🟨🟥🟪🟧⚪🔵🟢🔷🔴🟠🟡)

### 🔧 Technical Implementation
- **DiscordFormatter Utility** - New utility class for formatting conversion
- **Smart Tag Management** - Proper opening and closing of Discord markdown tags
- **Nested Formatting** - Supports multiple formatting codes in same message
- **Performance Optimized** - Efficient regex-based parsing
- **Error Resilient** - Graceful handling of malformed formatting codes

### 📝 Usage Examples
- `§6§lGolden§r text` → 🟡 **Golden** text
- `§oItalic §nand §lunderlined§r` → *Italic __and **underlined**__*
- `§kMagic text§r` → ✨Magic text✨

### 🌐 Integration
- **Minecraft → Discord**: Full formatting conversion
- **Tridirectional Chat**: Formatting preserved across platforms
- **Event Messages**: System messages also support formatting
- **Backward Compatible**: Existing messages without formatting work unchanged

### Changed
- Updated version to 2.2.0 to reflect major formatting enhancement
- Enhanced message processing pipeline for formatting support

## [2.3.0] - 2026-03-21

### 🌟 Added
- **Clean Configuration Structure** - Reorganized config sections for better readability
  - Renamed `server_identity` → `server`
  - Renamed `message_formats` → `formats`
  - Renamed `loop_prevention` → `filters`
  - Renamed `bot_status` → `bot`
  - Renamed `account_linking` → `linking`
- **Simplified Config Keys** - Removed redundant prefixes within sections
- **Improved Defaults** - Better out-of-box experience with sensible defaults
- **Standardized Naming** - Consistent section naming convention across all config files

### Changed
- Updated version to 2.3.0 for configuration improvements
- Enhanced config documentation with clearer comments
- Improved config file organization and readability

## [2.1.0] - 2026-03-21

### 🌟 Added
- **Revolutionary Tridirectional Chat System** - Complete 3-way message synchronization between Discord ↔ Minecraft ↔ Fluxer
- **Platform Source Identification** - Optional tags showing message origin ([Discord], [Fluxer], [Minecraft])
- **Configurable Message Bridging** - Fine-grained control over which platforms bridge to each other
- **Real-time Cross-Platform Communication** - Messages flow seamlessly across all connected platforms
- **Advanced Tridirectional Configuration** - New config section for 3-way chat settings
- **Mobile Accessibility** - Participate in server chat via Fluxer when away from PC
- **Unified Chat Experience** - Type anywhere, appear everywhere across all platforms

### 🔧 Technical Changes
- **Enhanced DiscordManager** - Added bridging methods for cross-platform message routing
- **Improved Configuration System** - New tridirectional chat settings with detailed explanations
- **Message Formatting System** - Platform-aware message formatting with source identification
- **Configuration Validation** - Checks for proper Discord and Fluxer setup before enabling bridging
- **Error Handling** - Robust error handling for cross-platform message failures

### 📋 Configuration Options
- `tridirectional.enabled` - Enable/disable 3-way chat synchronization
- `tridirectional.discord_to_fluxer` - Bridge Discord messages to Fluxer
- `tridirectional.fluxer_to_discord` - Bridge Fluxer messages to Discord  
- `tridirectional.show_source` - Show platform source tags in messages

### 🎯 Use Cases Enabled
- **Multi-platform communities** - Engage users wherever they are
- **Server management** - Monitor chat from Discord while away from game
- **Mobile gaming** - Use Fluxer app when away from computer
- **Community bridging** - Connect different Discord servers via Fluxer
- **Stream integration** - Let viewers participate from multiple platforms

### ⚠️ Requirements
- Both Discord and Fluxer must be properly configured for tridirectional chat
- Requires webhooks for both platforms to be functional
- Recommended for servers with active multi-platform communities

### Changed
- Updated version to 2.1.0 to reflect major new feature addition

### Added
- Fluxer webhook service support as alternative to Discord
- Configurable HTTP receiver for bidirectional Fluxer communication
- Platform selection in configuration (`platform: "discord"` or `"fluxer"`)
- Separate Fluxer configuration section with webhook URLs and API key
- Configurable receiver port and path for Fluxer messages
- Simplified configuration structure with cleaner field names
- Professional documentation with README and setup guides

### Changed
- Renamed Discord config fields to be more explicit (e.g., `bot_token` → `discord.bot_token`)
- Simplified configuration comments and structure for better user experience
- Updated config generation to be more user-friendly with quick start guide
- Improved error handling and logging for platform initialization

### Fixed
- Fixed config file generation issues across all Minecraft versions
- Resolved dependency bundling problems with Jackson and other libraries
- Fixed platform-specific initialization logic

## [2.0.0] - 2024-03-21

### Added
- Dual platform support (Discord and Fluxer)
- HTTP server for receiving Fluxer webhook messages
- Configurable receiver port and path
- Platform-specific initialization
- Cleaner configuration structure
- Professional documentation

### Changed
- Complete configuration restructure for better UX
- Updated all field names to be platform-specific
- Simplified setup process with quick start guide
- Improved error messages and logging

### Fixed
- Config file generation across all versions
- Dependency bundling issues
- Platform initialization bugs

## [1.5.0] - 2024-03-20

### Added
- Multi-server support with unique prefixes
- Account linking system (Discord only)
- Player message filtering preferences
- Advanced chat filtering with prefix support
- Bot status updates with player count
- Debug logging mode
- Performance optimizations with message queuing

### Changed
- Refined message formatting system
- Improved event notification handling
- Better webhook error handling
- Updated dependency management

### Fixed
- Message loop prevention in multi-server setups
- Rate limiting issues with Discord API
- Avatar URL template handling
- Event embed processing

## [1.4.0] - 2024-03-15

### Added
- Event notifications (join/leave/death/advancement)
- Separate event channel and webhook support
- Customizable message formats
- Server identity configuration
- Loop prevention settings
- Webhook username formatting

### Changed
- Restructured configuration for better organization
- Improved message processing pipeline
- Enhanced embed handling for Discord messages

### Fixed
- Discord message parsing issues
- Webhook ID extraction from URLs
- Avatar display problems

## [1.3.0] - 2024-03-10

### Added
- Discord message receiving via bot
- Webhook message sending
- Basic bidirectional chat functionality
- Discord bot connection management
- Message filtering options

### Changed
- Initial architecture setup
- Basic configuration structure

### Fixed
- Initial stability issues
- Connection timeout problems

## [1.2.0] - 2024-03-05

### Added
- Basic Discord webhook support
- Minecraft to Discord message sending
- Simple configuration system
- Server prefix support

### Changed
- Core architecture implementation
- Initial feature set

## [1.1.0] - 2024-03-01

### Added
- Project initialization
- Architectury framework integration
- Multi-version support (1.18.2, 1.19.2, 1.20.1, 1.21.1)
- Fabric and Forge/NeoForge compatibility

## [1.0.0] - 2024-02-28

### Added
- Initial release
- Basic mod structure
- Configuration system foundation

---

## Version Support Matrix

| Version | Fabric | Forge | NeoForge | Status |
|---------|--------|-------|----------|---------|
| 1.21.1 | ✅ | ❌ | ✅ | Active |
| 1.20.1 | ✅ | ✅ | ❌ | Active |
| 1.19.2 | ✅ | ✅ | ❌ | Active |
| 1.18.2 | ✅ | ✅ | ❌ | Active |

## Migration Guide

### From 1.x to 2.0

**Configuration Changes:**
The configuration has been completely restructured. You will need to recreate your config file.

**Old format:**
```json
{
  "bot_token": "...",
  "channel_id": "...",
  "webhook_url": "..."
}
```

**New format:**
```json
{
  "platform": "discord",
  "discord": {
    "bot_token": "...",
    "channel_id": "...",
    "webhook_url": "..."
  }
}
```

**Key Changes:**
1. Add `platform` field to choose between "discord" or "fluxer"
2. Move Discord settings under `discord` section
3. Add Fluxer settings under `fluxer` section if using Fluxer
4. All other settings remain the same but may be in different sections

### Fluxer Setup

New in 2.0, you can use Fluxer instead of Discord:

1. Set `platform: "fluxer"`
2. Configure Fluxer webhook URLs
3. Set receiver port (default: 8080)
4. Point Fluxer to `http://your-server:8080/webhook`

## Breaking Changes

### 2.0.0
- Configuration file format completely changed
- Field names renamed for clarity
- Platform selection now required

### 1.5.0
- No breaking changes

### 1.4.0
- No breaking changes

### 1.3.0
- No breaking changes

## Deprecations

### Deprecated in 2.0.0
- Old configuration field names
- Direct platform detection (now explicit via config)

### To be Removed in 3.0.0
- None planned

---

**For more detailed information about each release, please check the GitHub releases page.**
