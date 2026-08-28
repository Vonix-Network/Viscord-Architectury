# Candidate-only 26.1.2 / NeoForge 26.1.2.93 port

This directory is a fresh target lane copied from the 1.21.1 Fabric/NeoForge template. The 1.21.1 lane is intentionally untouched.

## Verified toolchain

- Minecraft: `26.1.2`
- NeoForge: `26.1.2.93` (public Maven metadata and POM/module)
- NeoForge ModDevGradle: `2.0.143` (public Maven metadata and official `neoforged/ModDevGradle` README)
- Gradle: `9.2.0` (official NeoForge 26.1.x wrapper metadata and NeoForge Maven module metadata)
- Java: `25` (official NeoForge 26.1.x `gradle.properties` and NeoForge 26.1.2.93 Maven module attributes)

## Validation

From this directory:

```bash
./gradlew --version
./gradlew tasks --all --no-daemon
./gradlew build --no-daemon
```

This target is a development source snapshot and is not a release or acceptance claim. No deployment, server access, or credentials are part of this lane.

## Port status

The active source set has completed the NeoForge adapter pass: lifecycle, chat, player, death, command, and advancement paths are wired through NeoForge events/mixins. Historical `src/port-pending/java` data remains outside this source snapshot and is not compiled.

External libraries are declared through ModDevGradle `jarJar(implementation(...))`. Javacord API/core, OkHttp logging-interceptor, and other required libraries are declared explicitly because they are required by the active bytecode and are not supplied by Minecraft/NeoForge.

Jar-in-Jar ownership on this cell:
- `okio-jvm` 3.9.0 is nested because OkHttp needs it.
- `kotlin-stdlib` 1.9.25 is nested **only because** `okio-jvm` is nested; okio-jvm calls `kotlin.jvm.internal.Intrinsics` at shutdown. JarJar does not pull that transitive automatically.
- NightConfig `toml`/`core` 3.8.3 is nested because TOML config is not provided by NeoForge.
- Do not copy this jarJar set onto the 1.21.1 Architectury shadow. 1.21.1 nests NightConfig 3.8.3 explicitly and lets kotlin-stdlib arrive as a shadow transitive of okio-jvm.

Core contracts (`ChatPrefixFilter`, `DiscordFormatter`, `LinkCodeFormat`, `CommandNames`) are compiled from `core/src/main/java` rather than vendored here.
