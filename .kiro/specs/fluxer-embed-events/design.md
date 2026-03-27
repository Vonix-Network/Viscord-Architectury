# Fluxer Embed Events Bugfix Design

## Overview

`DiscordManager` sends all in-game events (server online/offline, player join/leave, death, advancement) to Fluxer as hardcoded plain-text strings via `FluxerPlatform.sendEventMessage()`. `FluxerPlatform.sendEventEmbed(JsonObject)` already exists and correctly formats embed JSON as bold text for Fluxer. The fix is to replace every `fluxerPlatform.sendEventMessage(...)` call in the event-sending methods with `fluxerPlatform.sendEventEmbed(...)`, passing the same `EmbedFactory`-built `JsonObject` that `DiscordPlatform` already uses.

## Glossary

- **Bug_Condition (C)**: The condition that triggers the bug — a Fluxer event call that invokes `sendEventMessage()` with a hardcoded string instead of `sendEventEmbed()` with an `EmbedFactory`-built `JsonObject`
- **Property (P)**: The desired behavior — Fluxer event notifications are formatted via `sendEventEmbed()`, producing `**title** — description` output consistent with the embed data
- **Preservation**: All Discord-platform event embeds, chat relay, tridirectional bridging, and `sendEventEmbed()` internal formatting must remain unchanged
- **DiscordManager**: `DiscordManager.java` — thin coordinator that owns the public event API (`sendJoinEmbed`, `sendDeathEmbed`, etc.) and delegates to platform objects
- **FluxerPlatform**: `FluxerPlatform.java` — owns all Fluxer I/O; `sendEventEmbed(JsonObject)` formats the embed as `**title** — description` and calls `sendEventMessage()`
- **EmbedFactory**: `EmbedFactory.java` — static factory producing `Consumer<JsonObject>` builders for each event type
- **sendEventMessage**: `FluxerPlatform.sendEventMessage(String)` — sends a raw string to the Fluxer event channel via bot API
- **sendEventEmbed**: `FluxerPlatform.sendEventEmbed(JsonObject)` — extracts title/description from embed JSON and delegates to `sendEventMessage`

## Bug Details

### Bug Condition

The bug manifests in six methods of `DiscordManager` whenever the Fluxer platform is active (solo or tridirectional). Each method constructs a hardcoded plain-text string and calls `fluxerPlatform.sendEventMessage()` directly, bypassing the embed pipeline that `DiscordPlatform` uses for the same events.

**Formal Specification:**
```
FUNCTION isBugCondition(call)
  INPUT: call — a method invocation in DiscordManager that sends a Fluxer event
  OUTPUT: boolean

  RETURN call.target == fluxerPlatform
         AND call.method == "sendEventMessage"
         AND call.argument IS hardcoded plain-text string
         AND EmbedFactory method EXISTS for this event type
END FUNCTION
```

### Examples

- `sendStartupEmbed`: calls `fluxerPlatform.sendEventMessage("🟢 **ServerName** is now online!")` — expected: `fluxerPlatform.sendEventEmbed(embed)` where embed is built by `EmbedFactory.createServerStatusEmbed("Server Online", "Server is now online", 0x43B581, serverName, "Viscord")`
- `sendJoinEmbed`: calls `fluxerPlatform.sendEventMessage("➡ **username** joined the game")` — expected: `fluxerPlatform.sendEventEmbed(embed)` where embed is built by `EmbedFactory.createPlayerEventEmbed("Player Joined", ...)`
- `sendDeathEmbed`: calls `fluxerPlatform.sendEventMessage("☠ <message>")` — expected: embed with title `"Player Died"` and description set to the death message
- `sendAdvancementEmbed`: calls `fluxerPlatform.sendEventMessage("🏆 **username** has made the advancement **title**")` — expected: `fluxerPlatform.sendEventEmbed(embed)` where embed is built by `EmbedFactory.createAdvancementEmbed(...)`
- `shutdown()`: calls `fluxerPlatform.sendEventMessage("🔴 **ServerName** is now offline.")` — expected: embed built by `EmbedFactory.createServerStatusEmbed("Server Offline", ...)`

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- `DiscordPlatform` event embed sending must continue to work exactly as before — no changes to `DiscordPlatform.java`
- `FluxerPlatform.sendEventEmbed(JsonObject)` internal formatting (`**title** — description`) must remain unchanged
- `FluxerPlatform.sendEventMessage(String)` must remain unchanged (it is still called by `sendEventEmbed`)
- Chat message relay (player chat → Fluxer via `sendChatMessage`) must be unaffected
- Tridirectional bridging (Discord ↔ Fluxer message relay) must be unaffected
- `FluxerPlatform.initialize()` startup message (non-tridirectional path) must remain unchanged — it calls `sendBotMessage` directly and is out of scope

**Scope:**
All code paths that do NOT involve the six Fluxer event-sending calls in `DiscordManager` are completely unaffected. This includes:
- All `DiscordPlatform` methods
- `TridirectionalBridge` relay logic
- `onFluxerMessage` / `onDiscordMessage` handlers
- Bot status updates

## Hypothesized Root Cause

The six event methods in `DiscordManager` were written (or left over from an earlier version) before `FluxerPlatform.sendEventEmbed()` existed, or the author was unaware it existed. The Discord path was updated to use `EmbedFactory` but the Fluxer path was never updated to match.

1. **Missing delegation**: Each event method has a symmetric `if (isFluxer()) ... else ...` block. The Discord branch calls `discordPlatform.sendXxxEmbed(...)` which internally uses `EmbedFactory`. The Fluxer branch calls `fluxerPlatform.sendEventMessage(hardcodedString)` instead of building the same embed and calling `fluxerPlatform.sendEventEmbed(embed)`.

2. **`sendEventEmbed` not wired up**: `FluxerPlatform.sendEventEmbed(JsonObject)` exists and is correct, but `DiscordManager` never calls it — the method is dead code from the call-site perspective.

3. **`EmbedFactory` not used for Fluxer path**: `DiscordManager` imports and uses `EmbedFactory` only indirectly (via `DiscordPlatform`). The Fluxer path never invokes `EmbedFactory` at all.

## Correctness Properties

Property 1: Bug Condition — Fluxer Event Calls Use sendEventEmbed

_For any_ in-game event (server online/offline, player join/leave, death, advancement) where the Fluxer platform is active, the fixed `DiscordManager` SHALL call `fluxerPlatform.sendEventEmbed(JsonObject)` with an `EmbedFactory`-built embed, producing a `**title** — description` formatted message in the Fluxer event channel.

**Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6**

Property 2: Preservation — Non-Fluxer-Event Code Paths Unchanged

_For any_ code path that does NOT involve the six Fluxer event-sending calls (chat relay, Discord platform events, tridirectional bridging, bot status), the fixed code SHALL produce exactly the same behavior as the original code.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

## Fix Implementation

### Changes Required

**File**: `viscord-1.21.1-fabric-neoforge-template/common/src/main/java/network/vonix/viscord/discord/DiscordManager.java`

The fix is purely in `DiscordManager`. No changes to `FluxerPlatform`, `DiscordPlatform`, `EmbedFactory`, or `TridirectionalBridge`.

**Specific Changes** (one per event method):

1. **`sendStartupEmbed`** — replace:
   ```java
   fluxerPlatform.sendEventMessage("\uD83D\uDFE2 **" + serverName + "** is now online!");
   ```
   with:
   ```java
   JsonObject embed = new JsonObject();
   EmbedFactory.createServerStatusEmbed("Server Online", "Server is now online", 0x43B581, serverName, "Viscord").accept(embed);
   fluxerPlatform.sendEventEmbed(embed);
   ```

2. **`shutdown`** — replace:
   ```java
   fluxerPlatform.sendEventMessage("\uD83D\uDD34 **" + ViscordConfigToml.Server.NAME.get() + "** is now offline.");
   ```
   with:
   ```java
   JsonObject embed = new JsonObject();
   EmbedFactory.createServerStatusEmbed("Server Offline", "Server is shutting down", 0xF04747, ViscordConfigToml.Server.NAME.get(), "Viscord").accept(embed);
   fluxerPlatform.sendEventEmbed(embed);
   ```

3. **`sendJoinEmbed`** — replace:
   ```java
   fluxerPlatform.sendEventMessage("➡ **" + username + "** joined the game");
   ```
   with:
   ```java
   JsonObject embed = new JsonObject();
   EmbedFactory.createPlayerEventEmbed("Player Joined", username + " joined the game", 0x5865F2, username, ViscordConfigToml.Server.NAME.get(), "Join", avatarUrl).accept(embed);
   fluxerPlatform.sendEventEmbed(embed);
   ```

4. **`sendLeaveEmbed`** — replace:
   ```java
   fluxerPlatform.sendEventMessage("⬅ **" + username + "** left the game");
   ```
   with:
   ```java
   JsonObject embed = new JsonObject();
   EmbedFactory.createPlayerEventEmbed("Player Left", username + " left the game", 0x99AAB5, username, ViscordConfigToml.Server.NAME.get(), "Leave", avatarUrl).accept(embed);
   fluxerPlatform.sendEventEmbed(embed);
   ```

5. **`sendDeathEmbed`** — replace:
   ```java
   fluxerPlatform.sendEventMessage("\u2620 " + message);
   ```
   with:
   ```java
   JsonObject embed = new JsonObject();
   embed.addProperty("title", "Player Died");
   embed.addProperty("description", message);
   embed.addProperty("color", 0xF04747);
   fluxerPlatform.sendEventEmbed(embed);
   ```

6. **`sendAdvancementEmbed`** — replace:
   ```java
   fluxerPlatform.sendEventMessage("\uD83C\uDFC6 **" + username + "** has made the advancement **" + title + "**");
   ```
   with:
   ```java
   JsonObject embed = new JsonObject();
   EmbedFactory.createAdvancementEmbed("\uD83C\uDFC6", 0xFAA61A, username, title, desc).accept(embed);
   fluxerPlatform.sendEventEmbed(embed);
   ```

## Testing Strategy

### Validation Approach

Two-phase: first run exploratory tests on unfixed code to confirm the bug and root cause, then verify the fix with fix-checking and preservation tests.

### Exploratory Bug Condition Checking

**Goal**: Confirm that `DiscordManager` calls `sendEventMessage` (not `sendEventEmbed`) for each event type on unfixed code, and that the output is a hardcoded plain-text string rather than an embed-formatted string.

**Test Plan**: Mock `FluxerPlatform` and capture calls to `sendEventMessage` vs `sendEventEmbed`. Trigger each event method and assert that `sendEventEmbed` is called (these assertions will FAIL on unfixed code, confirming the bug).

**Test Cases**:
1. **Startup test**: Call `sendStartupEmbed("TestServer")` with Fluxer active — assert `sendEventEmbed` was called (fails on unfixed code, `sendEventMessage` is called instead)
2. **Shutdown test**: Call `sendShutdownEmbed("TestServer")` with Fluxer active — assert `sendEventEmbed` was called (fails on unfixed code)
3. **Join test**: Call `sendJoinEmbed("Player1")` with Fluxer active — assert `sendEventEmbed` was called (fails on unfixed code)
4. **Leave test**: Call `sendLeaveEmbed("Player1")` with Fluxer active — assert `sendEventEmbed` was called (fails on unfixed code)
5. **Death test**: Call `sendDeathEmbed("Player1 was slain by Zombie")` with Fluxer active — assert `sendEventEmbed` was called (fails on unfixed code)
6. **Advancement test**: Call `sendAdvancementEmbed("Player1", "Stone Age", "Mine stone")` with Fluxer active — assert `sendEventEmbed` was called (fails on unfixed code)

**Expected Counterexamples**:
- `sendEventMessage` is called with a hardcoded emoji+markdown string for every event type
- `sendEventEmbed` is never called from `DiscordManager`

### Fix Checking

**Goal**: Verify that after the fix, every event method calls `sendEventEmbed` with the correct embed JSON.

**Pseudocode:**
```
FOR ALL event IN [startup, shutdown, join, leave, death, advancement] DO
  result := capturedCall(fluxerPlatform, event)
  ASSERT result.method == "sendEventEmbed"
  ASSERT result.embed.has("title")
  ASSERT result.embed.has("description")
  ASSERT expectedBehavior(result.embed, event)
END FOR
```

### Preservation Checking

**Goal**: Verify that for all non-Fluxer-event code paths, behavior is identical before and after the fix.

**Pseudocode:**
```
FOR ALL call WHERE NOT isBugCondition(call) DO
  ASSERT original_behavior(call) == fixed_behavior(call)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because it generates many combinations of platform state (Discord-only, Fluxer-only, tridirectional) and event types to confirm no regressions.

**Test Cases**:
1. **Discord platform preservation**: With Discord platform active (not Fluxer), verify all six event methods still call `discordPlatform.sendXxxEmbed()` — unchanged
2. **Tridirectional preservation**: With tridirectional active, verify both Discord and Fluxer paths are called correctly
3. **Chat relay preservation**: Verify `sendMinecraftMessage` / `sendChatMessage` still routes to `sendChatMessage` on the correct platform
4. **`sendEventEmbed` formatting preservation**: Verify `FluxerPlatform.sendEventEmbed` still formats as `**title** — description`

### Unit Tests

- Test each of the six fixed event methods with a mocked `FluxerPlatform`, asserting `sendEventEmbed` is called with correct title/description/color
- Test `sendEventEmbed` directly with various embed JSON shapes (title only, description only, both)
- Test edge cases: null/empty server name, null avatar URL in join/leave

### Property-Based Tests

- Generate random `(username, serverName)` pairs and verify `sendJoinEmbed`/`sendLeaveEmbed` always produce an embed with non-empty title and description containing the username
- Generate random death messages and verify `sendDeathEmbed` always produces an embed with `"Player Died"` title and the message as description
- Generate random `(username, title, desc)` triples and verify `sendAdvancementEmbed` always produces an embed with all three fields present

### Integration Tests

- Full lifecycle test: initialize with Fluxer platform, trigger each event, capture Fluxer bot API calls, assert formatted output matches `**title** — description` pattern
- Tridirectional test: initialize with both platforms, trigger each event, assert Discord receives a rich embed and Fluxer receives the bold-text formatted equivalent
- Regression test: initialize with Discord-only platform, trigger all events, assert no calls to `FluxerPlatform` at all
