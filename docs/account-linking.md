# Account Linking

Viscord can bind a player's **Minecraft UUID** to a **Discord user ID** via a short-lived numeric handshake. Once linked, the binding is persisted to disk (`config/viscord/linked_accounts.json`).

Implementation: `discord/LinkedAccountsManager.java`, with the in-game command flow registered in `discord/DiscordEventHandler.java` and the Discord-side handler in `discord/DiscordManager.java`.

## Configuration

```toml
[account_linking]
enabled     = true      # default
code_expiry = 300       # seconds; range 60-600
```

Disabling (`enabled = false`) makes both `/viscord discord link` and the Discord-side `/link <code>` trigger reject with a clear error.

## The flow

1. In Minecraft, the player runs:

   ```
   /viscord discord link
   ```

2. The server generates a fresh **6-digit** code using `SecureRandom` and returns:

   ```
   Your link code is: 123456
   Use /link 123456 in Discord to link your account.
   Code expires in 5 minutes.
   ```

3. In Discord (any channel the bot can read), the player types:

   ```
   /link 123456
   ```

   (This is matched on `message.startsWith("/link ")` — it is *not* a registered Discord slash command.)

4. `DiscordManager` calls `LinkedAccountsManager.verifyAndLink(code, discordUserId)`. If the code is valid and unexpired, the Discord ID is atomically bound to the Minecraft UUID and the bot replies with a success message in the same channel.

5. The binding is written to `linked_accounts.json` asynchronously (off the server tick thread; see [performance.md](performance.md)).

## Unlinking

```
/viscord discord unlink
```

Removes the binding for the executing player. The save to `linked_accounts.json` is again offloaded to `Viscord.ASYNC_EXECUTOR`.

## Security model

The linking flow has been hardened over several releases:

| Concern | Mitigation | Since |
|---|---|---|
| Predictable codes | Shared `SecureRandom` instance instead of `new Random()` (which was time-seeded and brute-forceable). | 4.1.7 |
| TOCTOU double-bind race | `verifyAndLink` brackets the "is Discord already linked?" check and the `linkedAccounts.put()` under `synchronized (linkedAccounts)`. A Discord account can no longer concurrently bind to two MC UUIDs. | 4.1.7 |
| Non-ASCII corruption on Windows | `FileWriter` / `FileReader` calls explicitly use `StandardCharsets.UTF_8`. | 4.1.7 |
| Stale codes | `code_expiry` (60–600s, default 300). Expired codes are rejected on verify. | — |
| I/O on tick thread | Snapshot is built under `synchronized(linkedAccounts)` on the caller, disk write is offloaded to `ASYNC_EXECUTOR`. | 4.2.0 |
| Brute-force on 6-digit code | Sliding-window rate limiter (per-Discord-user + global). Silent on hit. See [configuration.md `[discord_rate_limit]`](configuration.md). | 4.2.1 |
| Code format enumeration | Strict `^\d{6}$` pre-validation with a single generic error — no "too short" vs "invalid digits" distinction. | 4.2.1 |

## Operator notes

- The file `config/viscord/linked_accounts.json` should be backed up if account linkages matter to you. It's stored as a flat JSON map.
- If the file is deleted, players need to re-run `/viscord discord link` and `/link <code>` to re-bind.
- There is no admin command to force-bind another player's account — the flow is per-player by design.
