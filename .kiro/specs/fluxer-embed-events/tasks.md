# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - Fluxer Event Calls Use sendEventMessage Instead of sendEventEmbed
  - **CRITICAL**: This test MUST FAIL on unfixed code — failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior — it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate that each of the 6 event methods calls `sendEventMessage` instead of `sendEventEmbed`
  - **Scoped PBT Approach**: Scope the property to the concrete failing cases — one per event type (startup, shutdown, join, leave, death, advancement) with Fluxer active
  - Mock `FluxerPlatform` and capture calls to `sendEventMessage` vs `sendEventEmbed`
  - For each event method, assert `sendEventEmbed` was called (isBugCondition: `call.target == fluxerPlatform AND call.method == "sendEventMessage" AND EmbedFactory method EXISTS for this event type`)
  - Expected counterexamples: `sendEventMessage` called with hardcoded emoji+markdown string; `sendEventEmbed` never called from `DiscordManager`
  - Run test on UNFIXED code
  - **EXPECTED OUTCOME**: Test FAILS for all 6 event types (this is correct — it proves the bug exists)
  - Document counterexamples found (e.g., `sendEventMessage("🟢 **TestServer** is now online!")` instead of `sendEventEmbed(embed)`)
  - Mark task complete when test is written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Non-Fluxer-Event Code Paths Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - Observe: with Discord-only platform active, all 6 event methods call `discordPlatform.sendXxxEmbed()` — `FluxerPlatform` is never called
  - Observe: `FluxerPlatform.sendEventEmbed(embed)` formats as `**title** — description` and delegates to `sendEventMessage`
  - Observe: `sendChatMessage` routes to `sendChatMessage` on the correct platform unchanged
  - Write property-based tests: for all platform states where `isBugCondition` is false (Discord-only, non-event code paths), behavior is identical before and after fix
  - Test cases: Discord platform preservation (6 event methods → `discordPlatform` only), `sendEventEmbed` formatting (`**title** — description`), chat relay unaffected
  - Verify tests PASS on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 3. Fix — replace 6 hardcoded sendEventMessage() calls with sendEventEmbed() in DiscordManager

  - [x] 3.1 Implement the fix in DiscordManager.java
    - In `sendStartupEmbed`: replace `fluxerPlatform.sendEventMessage("🟢 **" + serverName + "** is now online!")` with `JsonObject embed = new JsonObject(); EmbedFactory.createServerStatusEmbed("Server Online", "Server is now online", 0x43B581, serverName, "Viscord").accept(embed); fluxerPlatform.sendEventEmbed(embed);`
    - In `shutdown()`: replace `fluxerPlatform.sendEventMessage("🔴 **" + ViscordConfigToml.Server.NAME.get() + "** is now offline.")` with `JsonObject embed = new JsonObject(); EmbedFactory.createServerStatusEmbed("Server Offline", "Server is shutting down", 0xF04747, ViscordConfigToml.Server.NAME.get(), "Viscord").accept(embed); fluxerPlatform.sendEventEmbed(embed);`
    - In `sendJoinEmbed`: replace `fluxerPlatform.sendEventMessage("➡ **" + username + "** joined the game")` with `JsonObject embed = new JsonObject(); EmbedFactory.createPlayerEventEmbed("Player Joined", username + " joined the game", 0x5865F2, username, ViscordConfigToml.Server.NAME.get(), "Join", avatarUrl).accept(embed); fluxerPlatform.sendEventEmbed(embed);`
    - In `sendLeaveEmbed`: replace `fluxerPlatform.sendEventMessage("⬅ **" + username + "** left the game")` with `JsonObject embed = new JsonObject(); EmbedFactory.createPlayerEventEmbed("Player Left", username + " left the game", 0x99AAB5, username, ViscordConfigToml.Server.NAME.get(), "Leave", avatarUrl).accept(embed); fluxerPlatform.sendEventEmbed(embed);`
    - In `sendDeathEmbed`: replace `fluxerPlatform.sendEventMessage("☠ " + message)` with `JsonObject embed = new JsonObject(); embed.addProperty("title", "Player Died"); embed.addProperty("description", message); embed.addProperty("color", 0xF04747); fluxerPlatform.sendEventEmbed(embed);`
    - In `sendAdvancementEmbed`: replace `fluxerPlatform.sendEventMessage("🏆 **" + username + "** has made the advancement **" + title + "**")` with `JsonObject embed = new JsonObject(); EmbedFactory.createAdvancementEmbed("🏆", 0xFAA61A, username, title, desc).accept(embed); fluxerPlatform.sendEventEmbed(embed);`
    - No changes to `FluxerPlatform.java`, `DiscordPlatform.java`, `EmbedFactory.java`, or `TridirectionalBridge.java`
    - _Bug_Condition: isBugCondition(call) where call.target == fluxerPlatform AND call.method == "sendEventMessage" AND EmbedFactory method EXISTS for this event type_
    - _Expected_Behavior: call.method == "sendEventEmbed" AND call.embed.has("title") AND call.embed.has("description") AND embed built via EmbedFactory_
    - _Preservation: all Discord-platform paths, chat relay, tridirectional bridging, FluxerPlatform.sendEventEmbed() formatting unchanged_
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x] 3.2 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - Fluxer Event Calls Use sendEventEmbed
    - **IMPORTANT**: Re-run the SAME test from task 1 — do NOT write a new test
    - The test from task 1 asserts `sendEventEmbed` is called for all 6 event types
    - When this test passes, it confirms all 6 Fluxer event calls now use `sendEventEmbed` with EmbedFactory-built embeds
    - Run bug condition exploration test from step 1
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed for all 6 event types)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

  - [x] 3.3 Verify preservation tests still pass
    - **Property 2: Preservation** - Non-Fluxer-Event Code Paths Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 — do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions in Discord platform, chat relay, tridirectional, sendEventEmbed formatting)
    - Confirm all tests still pass after fix (no regressions)

- [x] 4. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.
