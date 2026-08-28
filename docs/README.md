# Viscord Documentation

Welcome to the **Viscord** documentation. Viscord is a server-side Minecraft mod that bridges in-game chat and events to **Discord**, **Fluxer**, or both at once (with optional tridirectional relay between all three).

The mod is built on [Architectury](https://github.com/architectury/architectury-api) and keeps five Minecraft lanes in one repository. The first four use Architectury templates that share a `common/` source tree; the 26.1.2 lane is a standalone NeoForge/ModDevGradle target:

| Source directory | Minecraft | Loaders |
|---|---|---|
| `viscord-1.18.2-fabric-forge-template/` | 1.18.2 | Fabric, Forge |
| `viscord-1.19.2-fabric-forge-template/` | 1.19.2 | Fabric, Forge |
| `viscord-1.20.1-fabric-forge-template/` | 1.20.1 | Fabric, Forge |
| `viscord-1.21.1-fabric-neoforge-template/` | 1.21.1 | Fabric, NeoForge |
| `viscord-1.26.1.2-neoforge-target/` | 26.1.2 | NeoForge |

Current embedded version: **4.2.2**. Common repository line: **`2.0.0-common.1`** (prerelease). See [the common-generation repository contract](COMMON-V2-REPOSITORY.md).

---

## 📚 Table of Contents

### Getting started
- [**Getting Started**](getting-started.md) — install the JAR, generate the config, get your first message bridged.
- [**Configuration Reference**](configuration.md) — every TOML key, default value, range, and what it does.
- [**Migration Guide**](migration.md) — JSON → TOML auto-migration, 4.1.x → 4.2.0 upgrade notes.

### Platforms
- [**Discord setup**](platforms/discord.md) — bot creation, Message Content Intent, webhook setup, permissions.
- [**Fluxer setup**](platforms/fluxer.md) — Fluxer bot token, Gateway connection (no port forwarding).
- [**Tridirectional bridging**](platforms/tridirectional.md) — Discord ↔ Fluxer ↔ Minecraft, source tagging, echo prevention.

### Operations
- [**Commands**](commands.md) — every in-game command, every Discord-side trigger, permission levels.
- [**Account Linking**](account-linking.md) — the 6-digit `/link` flow, security model, atomic bind.
- [**Multi-Server Setup**](multi-server.md) — running Viscord on multiple MC servers that share a Discord channel.
- [**Troubleshooting**](troubleshooting.md) — known symptoms, diagnostic flags, log redaction.

### Internals
- [**Architecture**](architecture.md) — Architectury layout, `common/` vs per-loader source sets, shaded dependencies.
- [**Performance & Threading**](performance.md) — `ASYNC_EXECUTOR`, status coalescing, LRU caches, off-tick I/O.
- [**Security Model**](security.md) — token redaction, `SecureRandom` link codes, TOCTOU mitigations.
- [**Building From Source**](building-from-source.md) — Gradle wrappers, all five version lanes, and the `build_menu.py` helper.

---

## Quick links

- Top-level [README](../README.md) — feature overview and fast-path quickstart.
- [CHANGELOG](../CHANGELOG.md) — full release history.
- [`viscord-documentation.html`](../viscord-documentation.html) — single-page HTML reference (auto-generated; this `docs/` tree is the navigable Markdown equivalent).
