# Viscord 2.0.0 common-generation repository

This repository is the single source tree for the Viscord common-generation line. The common line starts at **2.0.0** and is published as the distinct prerelease label **`2.0.0-common.1`**.

`2.0.0-common.1` identifies the repository/layout generation. It does **not** relabel Viscord's existing embedded project version: the accepted source currently builds as Viscord **4.2.2**. Viscord's historical `v2.0.0` tag remains immutable and is not reused for this common-generation snapshot.

## One repository, all supported Minecraft lanes

| Minecraft | Loaders | Java | Source directory |
|---|---|---:|---|
| 1.18.2 | Fabric, Forge | 17 | `viscord-1.18.2-fabric-forge-template/` |
| 1.19.2 | Fabric, Forge | 17 | `viscord-1.19.2-fabric-forge-template/` |
| 1.20.1 | Fabric, Forge | 17 | `viscord-1.20.1-fabric-forge-template/` |
| 1.21.1 | Fabric, NeoForge | 21 | `viscord-1.21.1-fabric-neoforge-template/` |
| 26.1.2 | NeoForge | 25 | `viscord-1.26.1.2-neoforge-target/` |

The root `core/` module contains shared code and tests. Every Minecraft lane remains in this repository, with version-specific common code and loader modules inside its target directory. This layout is intentionally not split into one repository per Minecraft version.

## Release status

- GitHub release label: **`2.0.0-common.1`** (prerelease).
- Embedded project version: **4.2.2**.
- Static evidence: the accepted candidate passed the parent build/package matrix and source/artifact parity checks for the requested lanes.
- Live Minecraft activation, Discord/Fluxer gateway connection, deployment, server restart, and production credentials were **not performed** for this source snapshot.
- Configuration examples use placeholders only. Never commit real bot tokens, webhook URLs, or credentials.

## Building

Use the version-specific instructions in [`docs/building-from-source.md`](building-from-source.md). Build one target/profile at a time when required by its loader toolchain. The 26.1.2 target uses the standalone ModDevGradle project, NeoForge 26.1.2.93, and Java 25.

```bash
# Core tests
./gradlew -PbuildProfile=coreonly :core:test

# The exact target commands are documented in docs/building-from-source.md
```

## Release naming

The common-generation label is kept separate from embedded project SemVer. This preserves Viscord's historical releases, including its existing `v2.0.0`, and avoids claiming that the current 4.2.2 implementation is a historical 2.0.0 artifact. A stable major-version change requires a separate compatibility and migration review.
