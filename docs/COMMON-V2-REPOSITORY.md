# Viscord 5.2.0 common-generation repository

This repository is the single source tree for the Viscord common-generation line. The common line starts at **2.0.0** and is published as the distinct stable release label **`5.2.0`**.

`5.2.0` is the embedded stable release version for every supported lane. Viscord's historical `v2.0.0` tag remains immutable and is not overwritten.

## One repository, all supported Minecraft lanes

| Minecraft | Loaders | Java | Source directory |
|---|---|---:|---|
| 1.18.2 | Fabric, Forge | 17 | `viscord-1.18.2-fabric-forge-template/` |
| 1.19.2 | Fabric, Forge | 17 | `viscord-1.19.2-fabric-forge-template/` |
| 1.20.1 | Fabric, Forge | 17 | `viscord-1.20.1-fabric-forge-template/` |
| 1.21.1 | Fabric, NeoForge | 21 | `viscord-1.21.1-fabric-neoforge-template/` |
| 26.1.2 | NeoForge | 25 | `viscord-1.26.1.2-neoforge-target/` |
| 26.2 | Fabric | 25 | `viscord-26.2-fabric/` |
| 26.2 | NeoForge | 25 | `viscord-26.2-neoforge/` |
| 26.3 | Fabric | 25 | `viscord-26.3-fabric/` |
| 26.3 | NeoForge | 25 | `viscord-26.3-neoforge/` |

The root `core/` module contains shared code and tests. Every Minecraft lane remains in this repository, with version-specific common code and loader modules inside its target directory. This layout is intentionally not split into one repository per Minecraft version.

## Release status

- GitHub release automation: `.github/workflows/release.yml` runs the thirteen-cell build matrix on `v*` tags and attaches the resulting jars plus `SHA256SUMS` to a stable release.
- Embedded project version: **`5.2.0`** for every supported lane.
- Release validation: the tag-triggered workflow builds and packages all thirteen cells, verifies the shaded runtime dependency where applicable, and publishes SHA-256 checksums with each tagged release.
- Installation: choose the artifact matching your Minecraft version, loader, and Java environment, then follow the setup steps in the documentation.
- Configuration examples use placeholders only. Never commit real bot tokens, webhook URLs, or credentials.

## Building

Use the version-specific instructions in [`docs/building-from-source.md`](building-from-source.md). Build one target/profile at a time when required by its loader toolchain. The 26.x targets use Java 25 and their declared Fabric/NeoForge toolchains.

```bash
# Core tests
./gradlew -PbuildProfile=coreonly :core:test

# The exact target commands are documented in docs/building-from-source.md
```

## Release naming

This stable release starts the common-generation SemVer line at `2.0.0`; Viscord's historical releases, including its existing `v2.0.0`, remain immutable. This source release does not by itself establish runtime compatibility guarantees; use the CI/runtime boundaries above.
