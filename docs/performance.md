# Performance & Threading

Viscord runs on the Minecraft server tick loop, but **none** of its network or disk I/O happens on that loop. This page documents the executor model, the coalescing strategies, the bounded caches, and the lifecycle rules.

The most important rule: **the server tick thread never blocks on a Viscord call.** Several historical bugs that violated this rule were fixed in 4.1.7 and 4.2.0 — see the [CHANGELOG](../CHANGELOG.md) for details.

## `Viscord.ASYNC_EXECUTOR`

A single bounded `ScheduledThreadPoolExecutor` owns every async task in Viscord.

| Property | Value |
|---|---|
| Core pool | `Runtime.availableProcessors() / 2` |
| Max pool | `Runtime.availableProcessors() × 2` |
| Keepalive | 30 seconds |
| Threads | Daemon, named `Viscord-Async-N` |
| Saturation policy | `CallerRunsPolicy` (back-pressure: caller runs the task itself instead of dropping it) |

Pre-4.2.0 this was `Executors.newCachedThreadPool()` — *unbounded*. A burst of misbehaving network calls could spawn unlimited threads. The bounded pool + `CallerRunsPolicy` makes this safe under burst load.

**Helper for delayed work:** `Viscord.scheduleAsync(Runnable r, long delayMs)` — this uses the executor's `schedule()` method directly, **without** holding a thread on `Thread.sleep(delay)` while waiting (which is what the pre-4.2.0 code did).

## Status update coalescing

The bot's "Online: X/Y" status text could be updated every time a player joins or leaves. Without coalescing, a mass-join (e.g. 100 reconnecting players after a network blip) used to:

- Spawn 100 thread submissions, each holding a `Thread.sleep` for the full delay window. 100 sleeping threads at once.

In 4.2.0, `scheduleStatusUpdate`:

- Uses `Viscord.scheduleAsync(...)` — true scheduling, no thread held.
- Coalesces bursts via an `AtomicBoolean.compareAndSet(false, true)` — **at most one** pending status update at a time. Additional requests during the window are no-ops.

## FluxerPlatform initialization

Pre-4.2.0 patterns:

- `submit({ sleep(1500); pushStatus(); })` — held a pool thread for the full sleep.
- `thenRun({ sleep(500); ... })` — same.

Both are now `Viscord.scheduleAsync` and `CompletableFuture.delayedExecutor(..., ASYNC_EXECUTOR)` respectively.

## TOCTOU-safe API access patterns

Several volatile shared fields are read by Javacord listener threads, Fluxer WebSocket threads, Brigadier command threads, and the tick thread:

- `DiscordManager.{server, bridge, linkedAccountsManager, playerPreferences, running}`
- `BotClient.api`
- `FluxerBotClient.{webSocket, token, sessionId, onReadyCallback, messageHandler}`

All are declared `volatile`. Every method that touches one captures a local at entry:

```java
DiscordApi local = api;
if (local == null) return;
local.someMethod();
```

This guards against the TOCTOU NPE between an `if (api != null)` check and `api.doThing()` if another thread nulls the field in between.

## Bounded caches

| Cache | Where | Cap | Eviction | Purpose |
|---|---|---|---|---|
| `recentAdvancements` | `discord/EventDataExtractor` / advancement path | 256 | Access-order LRU (`LinkedHashMap` with `removeEldestEntry`) | De-dupe advancement events that fire twice. |
| Echo cache | `TridirectionalBridge` | 512 | Access-order LRU | Drop bridged-back-to-self messages. |

Both used to be an unbounded `Map` with a racy `if (size > X) clear()` that destroyed all in-flight dedupe state at once when triggered. The current impl is a synchronized access-order `LinkedHashMap` — every read/put is wrapped in a single `synchronized` block.

## Off-tick disk I/O

| File | Where | Strategy |
|---|---|---|
| `linked_accounts.json` | `LinkedAccountsManager.save()` | JSON snapshot is built under `synchronized(linkedAccounts)` on the caller (typically the Brigadier command thread, i.e. the tick thread). The disk write is offloaded to `Viscord.ASYNC_EXECUTOR`. Fixed in 4.2.0 (parity with `PlayerPreferences`, which got the same treatment in 4.1.5). |
| `player_preferences.json` | `PlayerPreferences` | Same off-tick offload pattern. Backed by `ConcurrentHashMap` since 4.1.7. |
| `viscord.toml` | `TomlConfigManager.load()` | Synchronous, only on cold start and during `/viscord reload` (and the reload runs entirely on `ASYNC_EXECUTOR`). |

## Webhook send pipeline

`WebhookClient.java` and `FluxerWebhookClient.java` use OkHttp. The `ExecutorService` driving OkHttp's dispatcher is shut down on `SERVER_STOPPING` with `awaitTermination(3, SECONDS)` — pre-4.1.7 in-flight requests were abandoned.

OkHttp pools and `eventWebhookClient` lifecycle: a dead `FluxerPlatform.eventWebhookClient` field was leaking an OkHttpClient pool for the JVM lifetime. Removed in 4.2.0.

## Shutdown sequence

Worst-case pre-4.1.7 stop blocking was ~11 seconds on the `SERVER_STOPPING` thread because of nested `.join()` calls:

```
DiscordManager.shutdown()
  → DiscordPlatform.shutdown() .orTimeout(3s).join()
  → WebhookClient.shutdown() .awaitTermination(3s)
  → ASYNC_EXECUTOR .awaitTermination(5s)
```

Current shutdown:

- Runs entirely on `ASYNC_EXECUTOR`.
- One overall `orTimeout(5s)`.
- `DiscordPlatform.shutdown()` chains cleanup via `.handle(...)` instead of `.join()`.
- Final shutdown embed waits up to 3s for the embed HTTP future before disconnecting (so the "server stopping" embed actually arrives — pre-4.1.7 it was usually cancelled).

## `/viscord reload`

- Sends command feedback only via `mcServer.execute(...)` so packet writes happen on the server thread (off-thread `sendSuccess` / `sendFailure` can corrupt the vanilla packet pipeline).
- Runs the actual shutdown + re-init off-thread on `ASYNC_EXECUTOR`.
- No arbitrary `Thread.sleep(1000)` between shutdown and re-init — that line was deleted in 4.2.0.

## Tuning

Two knobs in `[advanced]`:

```toml
[advanced]
queue_size = 100     # 10-1000
rate_limit = 1000    # ms, 100-5000
```

Defaults are sized for a typical 20–60 player server. Bump `queue_size` if you regularly hit `CallerRunsPolicy` back-pressure (visible as caller threads doing work synchronously); bump `rate_limit` if you trip Discord's outbound rate limit.
