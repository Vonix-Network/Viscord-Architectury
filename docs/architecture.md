# Architecture

Viscord is built on [**Architectury**](https://github.com/architectury/architectury-api) — a cross-loader abstraction that lets one common source tree target Fabric, Forge, and NeoForge from a single Gradle multi-project build.

The repo currently ships **four parallel Architectury templates** plus a standalone NeoForge lane for Minecraft 26.1.2.

## Top-level layout

```
Viscord-Architectury/
├── README.md
├── CHANGELOG.md
├── docs/                                # ← you are here
├── viscord-documentation.html           # single-page HTML reference (auto-gen)
├── build_menu.py                        # interactive build helper (see building-from-source.md)
├── requirements.txt                     # python deps for build_menu.py
├── viscord-1.18.2-fabric-forge-template/
├── viscord-1.19.2-fabric-forge-template/
├── viscord-1.20.1-fabric-forge-template/
├── viscord-1.21.1-fabric-neoforge-template/
└── viscord-1.26.1.2-neoforge-target/
```

> The four template directories share the same package structure and most of the same source files in `common/`. **Those four remain in parity** on the common tree. The 26.1.2 lane is standalone because its NeoForge/Java 25 toolchain is a separate compatibility cell.

## Inside a template

Taking `viscord-1.21.1-fabric-neoforge-template/` as the canonical example (the others are structurally identical, but the 1.18.2 / 1.19.2 / 1.20.1 templates have a `forge/` module instead of `neoforge/`):

```
viscord-1.21.1-fabric-neoforge-template/
├── settings.gradle                      # includes :common :fabric :neoforge
├── build.gradle                         # root Gradle project
├── gradle.properties                    # mod_version, MC version, Architectury API version
├── gradlew, gradlew.bat
├── common/                              # platform-agnostic source
│   └── src/main/java/network/vonix/viscord/
│       ├── Viscord.java                 # mod entrypoint + ASYNC_EXECUTOR
│       ├── chat/ChatFormatter.java
│       ├── config/toml/
│       │   ├── TomlConfigManager.java   # spec, load, save, JSON migration
│       │   └── ViscordConfigToml.java   # typed ConfigValue<T> accessors
│       ├── discord/
│       │   ├── DiscordManager.java      # singleton facade, message dispatch
│       │   ├── DiscordEventHandler.java # MC lifecycle hooks + Brigadier commands
│       │   ├── BotClient.java           # Javacord wrapper
│       │   ├── WebhookClient.java       # OkHttp webhook poster (Discord)
│       │   ├── FluxerBotClient.java     # nv-websocket-client wrapper (Fluxer)
│       │   ├── FluxerWebhookClient.java # OkHttp webhook poster (Fluxer)
│       │   ├── MessageConverter.java    # author/format resolution
│       │   ├── LinkedAccountsManager.java   # /link state + persistence
│       │   ├── PlayerPreferences.java   # per-UUID toggles
│       │   ├── EmbedFactory.java        # join/leave/death/advancement embeds
│       │   ├── EventDataExtractor.java
│       │   ├── EventEmbedDetector.java
│       │   ├── AdvancementData.java / AdvancementType.java
│       │   ├── AdvancementDataExtractor.java
│       │   ├── AdvancementEmbedDetector.java
│       │   ├── VanillaComponentBuilder.java
│       │   └── platform/
│       │       ├── DiscordPlatform.java
│       │       ├── FluxerPlatform.java
│       │       └── TridirectionalBridge.java
│       └── utils/DiscordFormatter.java
├── fabric/                              # Fabric loader entrypoints + mixins
├── neoforge/                            # NeoForge loader entrypoints (or `forge/` on older templates)
└── build/                               # gradle outputs
```

## Loader specifics

| Module | Role | Key files |
|---|---|---|
| `common/` | Source-of-truth code. Platform-agnostic. | All of `discord/`, `config/`, `chat/`, `utils/`, plus the cross-platform `Viscord.java` lifecycle. |
| `fabric/` | Fabric mod entrypoint. Registers MC lifecycle listeners via Fabric API. | `fabric.mod.json`, `ViscordFabric.java` |
| `forge/` (1.18.2 / 1.19.2 / 1.20.1) | Forge mod entrypoint. | `mods.toml`, `ViscordForge.java`, event bus bridge |
| `neoforge/` (1.21.1) | NeoForge loader entrypoints. | `neoforge.mods.toml`, `ViscordNeoForge.java` |
| `viscord-1.26.1.2-neoforge-target/` | Standalone NeoForge 26.1.2 / Java 25 lane. | `neoforge.mods.toml`, `ViscordNeoForge.java`, `ChatForwarder.java` |

> Since 4.1.11 the mod declares `side = "SERVER"` in every `mods.toml` and removed the client entrypoint from every `fabric.mod.json`. Viscord is **strictly server-side** — players never need to install it.

## Shaded dependencies

Viscord shades several runtime dependencies to avoid classpath conflicts with other mods:

- **Javacord** — Discord client (relocated under `network.vonix.viscord.shadow`).
- **nv-websocket-client** — Fluxer Gateway transport.
- **NightConfig** — TOML config.
- **OkHttp** — webhook HTTP.
- **Gson** — JSON serialization.

Since 4.1.11, the Forge / NeoForge `mods.toml` files include:

```toml
[mods]
# ...
eventBus.excludedPackages = "network.vonix.viscord.shadow"
```

This prevents the EventBus transformer from attempting to transform shadowed classes during server shutdown (which previously caused `ClassNotFoundException: MessageBuilderBase` errors).

Since 4.2.0 the `ActionRowImpl` patch lives in the fabric / forge / neoforge source sets (not `common/`, which lacks Javacord on its classpath).

## Cross-version drift

The four Architectury templates' `common/` trees remain in parity on their shared files except for unavoidable Mojang-API renames. The 26.1.2 NeoForge target is a standalone ModDevGradle lane and is verified against its own target-specific source and packaging contract.

| API change | Pre / Post |
|---|---|
| Text component construction | `new TextComponent("x")` (1.18.2) → `Component.literal("x")` (1.19.2+) |
| Player system message | `sendMessage(msg, NIL_UUID)` (1.18.2 / 1.19.2) → `sendSystemMessage(msg, false)` (1.20.1+) |
| Brigadier success builder | `sendSuccess(Component, ...)` → `sendSuccess(Supplier<Component>, ...)` (1.20+) |

Before 4.2.0 the 1.21.1 template held a number of bug fixes the older templates had drifted away from — most notably `FluxerBotClient.selfId` ID-based self-message filtering (older templates fell back to broken prefix matching) and `DiscordManager.resetInstance()`. **The 4.2.0 release brought the four Architectury templates back into parity; the 26.1.2 target is maintained as a separate NeoForge lane.**

## Where to look first when reading the code

- **Mod entrypoint:** `common/.../Viscord.java` — sets up `ASYNC_EXECUTOR`, holds the static `LOGGER`.
- **Commands + MC lifecycle hooks:** `common/.../discord/DiscordEventHandler.java`.
- **All message dispatch:** `common/.../discord/DiscordManager.java::onDiscordMessage` and `processDiscordMessageForMinecraft`.
- **Config:** `common/.../config/toml/TomlConfigManager.java` (load, spec, migration) + `ViscordConfigToml.java` (typed accessors).
- **3-way bridge:** `common/.../discord/platform/TridirectionalBridge.java`.
