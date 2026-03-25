# Viscord Release Notes (v2.4.4 - v2.4.8)

Detailed summary of key changes and fixes.

---

## 🚀 Key Highlights

### 🌟 Full Fluxer Bot Support (v2.4.5)
*   **WebSocket Gateway Integration**: Added real-time message receiving directly from the Fluxer Gateway. This enables bidirectional chat **without requiring any port forwarding** or inbound connections.
*   **REST API Support**: Added the ability to send messages via the Fluxer Bot API as a more reliable alternative to webhooks.
*   **Markdown Conversion**: Real-time conversion of Discord/Fluxer markdown (`**bold**`, `_italic_`, etc.) into native Minecraft formatting.
*   **New Utility Commands**: `/viscord fluxer invite` and the `/fluxer` alias now provide easy clickable links to install and set up your bot.

### 🐛 Critical Bug Fixes
*   **Java 21 Compatibility (v2.4.8)**: Corrected `FluxerBotClient` compilation errors and refined the `build_menu.ps1` script to ensure Java 21 is automatically used for Gradle operations across all versions (even legacy ones).
*   **Gateway Connectivity (v2.4.7)**: Resolved an issue causing infinite reconnection loops. Improved authentication handshake logic ensuring the bot is fully ready before completing status updates.
*   **Advancement Spam (v2.4.6)**: Fixed an issue where achievements would broadcast "progress" instead of "completion." Added the `isDone()` check to ensure only fully completed achievements trigger a bridge event.

---

## 📝 Change Details

### v2.4.8
- **Compilation Fixes**: 
  - Removed invalid 5-argument `onDisconnected` override causing build failures.
  - Refined Java 21 detection logic for older Minecraft versions.

### v2.4.7
- **Fluxer Bot Reliability**: 
  - Proper close code handling (1000, 1006, 4004).
  - Explicit `authenticated` vs `connected` logic for gateway stability.
  - Exponential backoff (2s → 60s) for reconnections.
  - Added session resume support to prevent downtime.

### v2.4.6
- **Advancement Completion Check**:
  - Achievements like "Cover me in debris" now properly wait for all 4 armor pieces before broadcasting.
  - Prevents premature notifications during multi-criteria advancement progress.

### v2.4.5
- **Bidirectional Fluxer Integration**: Direct chat from Fluxer/Discord → Minecraft via bot WebSocket.
- **Config Expansion**: Added `application_id`, `client_secret`, and `use_bot_api` toggles.
- **Improved Platform compatibility**: Added `/viscord discord invite` command.
- **Fixed Cobblemon/Modpack Spam**: Implemented a 5-second per-player debounce cache to handle rapid-fire advancement progress events.

### v2.4.4
- **Event Handler Refactor**: Initial backend preparation for migration to native Forge/NeoForge event listeners.

---
