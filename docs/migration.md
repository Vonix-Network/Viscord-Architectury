# Migration Guide

This page covers two migration paths:

1. **Legacy JSON → TOML** (Viscord 3.x → 4.x).
2. **4.1.x → 4.2.0** (current).

## 1. Legacy JSON → TOML

Viscord 4.0 replaced the older `config/viscord.json` with `config/viscord/viscord.toml`. The migration is **automatic on first run**:

- `TomlConfigManager.load()` detects an existing `config/viscord.json`.
- Reads its values, maps each one to its modern TOML path.
- Writes `config/viscord/viscord.toml`.
- Renames `config/viscord.json` → `config/viscord.json.backup`.

If you want to validate the migration before deleting the backup, compare `viscord.toml` against the inline comments in the freshly generated file (every key has a comment explaining its purpose and any range).

### Notable JSON → TOML key remappings

A few legacy keys were renamed during migration. The most user-visible:

| Legacy JSON path | New TOML path |
|---|---|
| `linking.code_expiry_seconds` | `account_linking.code_expiry` |

The full list is enforced inside `TomlConfigManager.java::migrateValue` calls — if you maintained custom keys outside the documented set, they will not be migrated.

### Stale JSON config code

The entire legacy JSON config system — `config/ViscordConfig.java` plus the `config/simple/` package (`SimpleConfigBuilder`, `SimpleConfigManager`, `SimpleConfigSpec`, `SimpleConfigValue`) — was **removed in 4.2.0**.

Before 4.2.0 there was a stale read in `MessageConverter` that still hit the old JSON path for `messages.use_display_name`, causing the setting to silently have no effect through that path. `MessageConverter` now reads `ViscordConfigToml.Messages.USE_DISPLAY_NAME` like everything else.

If you are maintaining a fork that depended on the old `SimpleConfig` API, port to `ViscordConfigToml` accessors.

## 2. 4.1.x → 4.2.0

**4.2.0 is drop-in compatible with 4.1.x configs.** No `viscord.toml` changes are required. The release is a stability + consolidation pass; see the [CHANGELOG](../CHANGELOG.md) for the full list.

What changes operationally:

- **Webhook tokens no longer leak into logs** (`redactWebhookUrl`).
- **Bounded async executor** with `CallerRunsPolicy` back-pressure replaces unbounded `Executors.newCachedThreadPool`. Bursty workloads no longer spawn unlimited threads.
- **Status update coalescing** — mass-joins no longer hold one thread per scheduled update.
- **Echo / advancement caches** are now bounded LRUs with safe synchronized access (no more racy `if (size > X) clear()`).
- **Linked accounts save** moved off the server tick thread.
- **Shutdown** is faster and reliably emits the shutdown embed before disconnecting.
- **`/viscord reload`** has its arbitrary 1-second `Thread.sleep` removed; UI feedback is bounced to the server thread.
- **`/vonix fluxer invite`** and the top-level `/fluxer` command — both of which always returned "not available" — are removed.
- **`/viscord discord help`** text was corrected (no more nonexistent `[enable|disable]` subcommands, no more `/vonix fluxer invite` reference, `/list` → `!list`).
- **Cross-version parity restored** — older templates received the fixes the 1.21.1 template had been accumulating.

### Action items for the upgrade

1. Drop in the 4.2.0 JAR for your MC version + loader. Restart the server.
2. Run `/viscord status` and confirm `Status: Running`.
3. (Optional) Verify the docs you depend on by scanning the [CHANGELOG](../CHANGELOG.md) for any line tagged with your customizations.
4. (Optional) If you script-parse Viscord's webhook failure logs, update your parser — URL token segments are now `…/webhooks/{id}/***`.

### Action items for fork maintainers

If your fork extends Viscord:

- Replace any `Executors.newCachedThreadPool()` usage with `Viscord.ASYNC_EXECUTOR` or `Viscord.scheduleAsync(...)`.
- Replace any `submit({ sleep(N); doThing(); })` pattern with `Viscord.scheduleAsync(runnable, N)`.
- Replace `if (cache.size() > N) cache.clear()` patterns with a synchronized access-order `LinkedHashMap` of cap `N`.
- Capture volatile shared fields into locals before use — see [performance.md](performance.md).
- Remove any references to the legacy `SimpleConfig*` classes — they no longer exist.
