# AGENTS.md — Viscord Architectury

## Repository identity

- **Repository:** `Vonix-Network/Viscord-Architectury`
- **Canonical checkout for this candidate:** `/root/work/mod-v2-common-migration-20260825/candidates-r14/viscord`
- **Default branch:** `main`
- **Project release:** **`5.2.0`**
- **Common-generation lineage:** begins at `2.0.0`; Viscord's current project release is independent of that common-line label
- **Project role:** server-side Minecraft bridge for Discord, Fluxer, and bidirectional chat integration

This is one repository containing every supported Minecraft/loader lane. Project release numbers are not synchronized with VSU or VonixGuardian. Only the lanes within this Viscord repository share `5.2.0`.

## Read first

1. `AGENTS.md` (this file)
2. `README.md`
3. `CHANGELOG.md`
4. Root `gradle.properties`
5. The selected lane's `gradle.properties`, `settings.gradle`, and `build.gradle`
6. `docs/README.md`, `docs/architecture.md`, and `docs/building-from-source.md`
7. Loader source, common source, tests, and release workflow

Nested `AGENTS.md` files describe generated lane folders. This root file controls repository-wide scope and release identity; always reconcile nested guidance with the actual source and CI files.

## Supported repository layout

| Minecraft | Loaders | Directory |
|---|---|---|
| 1.18.2 | Fabric, Forge | `viscord-1.18.2-fabric-forge-template/` |
| 1.19.2 | Fabric, Forge | `viscord-1.19.2-fabric-forge-template/` |
| 1.20.1 | Fabric, Forge | `viscord-1.20.1-fabric-forge-template/` |
| 1.21.1 | Fabric, NeoForge | `viscord-1.21.1-fabric-neoforge-template/` |
| 26.1.2 | NeoForge | `viscord-1.26.1.2-neoforge-target/` |
| 26.2 | Fabric | `viscord-26.2-fabric/` |
| 26.2 | NeoForge | `viscord-26.2-neoforge/` |
| 26.3 | Fabric | `viscord-26.3-fabric/` |
| 26.3 | NeoForge | `viscord-26.3-neoforge/` |

The first four lanes are Architectury projects with a shared `common/` source set and loader modules. The 1.21.1 template also includes the repository-level `core/` project through an explicit sibling mapping in its `settings.gradle`. The 26.1.2 target and the 26.2/26.3 Fabric and NeoForge targets use version-specific Gradle projects that consume the shared core contracts where applicable.

## Version contract

- Every Viscord lane uses the same embedded release version: **`5.2.0`**.
- The release tag and GitHub release title are **`v5.2.0`**.
- `5.2.0` is independent of VSU `2.0.0` and VonixGuardian `2.0.0`.
- Historical Viscord tags/releases, including `v2.0.0` and the `4.x.x` line, remain immutable.
- Keep root/lane `gradle.properties`, generated loader metadata, tests, README, docs, and changelog aligned.

## Known Viscord regression gate

A prior Viscord release investigation found that the Forge/NeoForge shaded artifact could omit Javacord's `MessageBuilderBase`, producing runtime `ClassNotFoundException`/message-builder failures even when compilation passed. The current release must not carry that regression forward.

For every lane that relocates Javacord into its release JAR — the four Architectury projects (1.18.2 through 1.21.1) and the 26.2/26.3 Fabric targets — the selected release JAR must contain the relocated class:

```text
network/vonix/viscord/shadow/javacord/api/entity/message/MessageBuilderBase.class
```

The standalone NeoForge 26.x targets do not relocate Javacord; they bundle it as nested `META-INF/jarjar` jars and are gated on those bundles instead, as recorded in the dependency-closure bullet under Build and CI. The tag-triggered workflow must fail closed if a required shaded loader artifact lacks the class. Do not replace this archive check with a source grep or a successful Gradle build.

The repository also keeps the related Forge/NeoForge `eventBus.excludedPackages` protection for relocated Javacord packages; preserve it when touching loader metadata.

## Build and CI

The authoritative release build is `.github/workflows/release.yml`.

- `workflow_dispatch` is build-only; the release job must be guarded to tag refs.
- Pushing `v5.2.0` runs all thirteen matrix cells and creates the GitHub release only after every cell passes.
- Architectury/Loom lanes run Gradle under Java 21, even for Minecraft 1.18.2–1.20.1 source targets that compile with Java 17 compatibility settings.
- The 26.1.2 and 26.2/26.3 NeoForge lanes run Java 25 with Gradle 9.2.0; the 26.2/26.3 Fabric lanes run Java 25 with Gradle 9.5.1.
- CI provisions Gradle explicitly with `gradle/actions/setup-gradle`; do not assume `gradle-wrapper.jar` is committed.
- Each matrix job selects one non-source/non-dev release JAR, performs the required archive checks, and uploads it.
- Every one of the thirteen matrix cells runs a dependency-closure check before its jar can be published; no lane is exempt. Cells flagged `packaging: 'shaded'` must contain the relocated `MessageBuilderBase` class and no unrelocated `org/javacord/` entry. Cells flagged `packaging: 'jarjar'` (the standalone NeoForge 26.x targets) must contain the nested `META-INF/jarjar/javacord-{api,core}-*.jar` bundles instead. The check prints one pass line per lane, so a green job is evidence that the gate ran.
- The release job gathers exactly thirteen JARs and writes `SHA256SUMS`.
- CI does not connect to Discord/Fluxer, deploy a server, activate a mod, or use production credentials.

For a selected lane, the equivalent build command is:

```text
gradle build --no-daemon
```

Use the declared lane toolchain. Do not run unrelated templates in one Gradle invocation.

## Source and loader rules

- Keep platform-neutral chat/config/bridge logic in each lane's `common/` source set.
- Keep Fabric, Forge, and NeoForge APIs in loader source sets.
- The 1.21.1 loader modules depend on the repository-level `core/` project; preserve the settings mapping and both compile/shadow dependencies.
- The 26.1.2 target consumes the sibling `core/` source contracts and must not reintroduce duplicate vendored copies of the same FQCN.
- Register Architectury lifecycle/player/tick events through typed listener lambdas. Do not pass raw `Consumer` instances to event interfaces whose methods are `stateChanged`, `tick`, `join`, or `quit`.
- Keep the common source tree in parity across the four Architectury templates except for unavoidable Minecraft API adapters.

## Tests and archive checks

At minimum, verify:

- common formatter and platform contract tests;
- lane compilation and packaging;
- `fabric.mod.json`, `mods.toml`, or `neoforge.mods.toml` ID/version/side/dependency metadata;
- shaded Javacord class presence for 1.18.2–1.21.1 release JARs;
- no `*-sources`, `*-dev`, `*-shadow`, or `*-slim` artifact is accidentally selected as the release JAR;
- exactly thirteen release assets and a matching `SHA256SUMS` file.

Static CI evidence is not live Minecraft runtime proof. Record runtime activation as unavailable unless a separate authorized runtime gate exists.

## Documentation rules

The README/docs must state:

- the nine target directories and thirteen loader cells;
- the independent `5.2.0` Viscord release identity;
- the common-generation origin at `2.0.0` without claiming cross-project version linkage;
- the server-side-only installation model;
- the `MessageBuilderBase` shaded-class regression gate;
- the exact CI build/runtime matrix;
- that release CI does not connect to Discord/Fluxer or deploy a server.

Historical `4.2.0`/`4.2.2` text belongs in historical changelog sections and must not be presented as the current release.

## Security and protected data

- Never read, print, commit, or transmit Discord tokens, Fluxer tokens, webhook URLs, passwords, API keys, private keys, JDBC URLs, or authorization headers.
- All config examples use placeholders only.
- Do not connect to a live gateway or production database during source/build gates.
- Do not add secrets to GitHub Actions. Secret configuration is owner-managed outside the repository candidate.

## Git and release discipline

- Start with `git status --short --branch`.
- Preserve unrelated changes and historical tags.
- Stage explicit paths; exclude `build/`, caches, runtime directories, downloaded dependencies, logs, and local evidence.
- Run `git diff --check` before commit.
- Never force-push or rewrite history.
- A green build does not prove gateway health or server activation; a release does not authorize deployment.

## Release procedure

1. Verify `main`, remote identity, existing tags/releases, and current candidate commit.
2. Confirm every lane embeds `5.2.0`.
3. Push the exact default-branch commit without force.
4. Run build-only CI and require 13/13 success, including the `MessageBuilderBase` archive gate where applicable.
5. Push only `v5.2.0`.
6. Verify the tag-triggered release has exactly thirteen JARs, `SHA256SUMS`, truthful notes, and the correct prerelease/stable state.
7. Independently read back the tag, commit/tree, release metadata, and asset digests.
8. Report that gateway/runtime activation was not performed unless separately evidenced.

## Stop conditions

Stop on:

- wrong repository/branch/tag identity;
- missing or mixed version metadata;
- any shaded release artifact lacking `MessageBuilderBase.class`;
- missing core mapping or duplicate FQCN source;
- failed matrix lane or asset-count mismatch;
- credential/protected-data exposure;
- request to connect, deploy, restart, or mutate production;
- request to overwrite historical tags/releases.

## Completion checklist

- [ ] Root AGENTS, README, docs, and changelog agree on Viscord `5.2.0`.
- [ ] All nine target directories remain in this repository.
- [ ] All thirteen loader cells embed `5.2.0`.
- [ ] 1.21.1 core mapping is present and loader adapters compile.
- [ ] 26.x targets have no duplicate vendored core contract.
- [ ] `MessageBuilderBase.class` archive gate passes for every shaded loader JAR.
- [ ] CI build-only matrix passes 13/13.
- [ ] `v5.2.0` release assets and hashes read back remotely.
- [ ] No live gateway or server deployment is claimed without evidence.
