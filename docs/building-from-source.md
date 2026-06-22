# Building From Source

Each of the four Viscord templates is a self-contained Architectury Gradle project. You build them independently.

## Prerequisites

- **JDK 17+** (recommended for all four templates; 1.21.1 strongly prefers 21 — see `gradle.properties` in each template).
- The Gradle wrapper (`./gradlew`) — no system Gradle install needed.
- ~4 GB of free RAM (the Gradle JVM heap is set to **4G**, raised in 4.2.0 to avoid OOM on Architectury's transformation passes; `.gitignore` covers `*.hprof` heap dumps from earlier OOMs).

## Build a single template

From the template directory:

```bash
cd viscord-1.21.1-fabric-neoforge-template/
./gradlew build
```

Artifacts land under:

```
fabric/build/libs/viscord-fabric-<version>.jar
neoforge/build/libs/viscord-neoforge-<version>.jar
```

(Older templates: `forge/build/libs/viscord-forge-<version>.jar` instead of `neoforge/`.)

The `<version>` value comes from `mod_version` in `gradle.properties`. All four templates are at **4.2.0** as of the current `HEAD`.

## Build all four templates

You can shell-loop:

```bash
for d in viscord-*/; do
  echo "=== $d ==="
  (cd "$d" && ./gradlew build) || { echo "FAILED: $d"; exit 1; }
done
```

…or use the bundled interactive helper.

## `build_menu.py`

A modern terminal UI for the four-template build matrix lives at the top of the repo:

```bash
python3 build_menu.py
```

It supports per-template build / clean / publish flows, surfaces stderr inline, and tracks success/failure counts.

```bash
pip install -r requirements.txt    # only if your env doesn't already have the deps
```

## Common gotchas

- **Out-of-memory during compile.** The default Gradle JVM heap was bumped to 4G in 4.2.0. If you have an old `gradle.properties` override, raise it: `org.gradle.jvmargs=-Xmx4G`.
- **`ClassNotFoundException: MessageBuilderBase` at runtime on Forge / NeoForge.** Fixed in 4.1.11 via `eventBus.excludedPackages = "network.vonix.viscord.shadow"` in `mods.toml`. If you're patching `mods.toml` by hand, keep that line.
- **Shadowed-class transformation errors on older Architectury.** Fixed in 4.1.3 via `Thread.setDefaultUncaughtExceptionHandler` (the pre-existing `addUncaughtExceptionListener` was a phantom — no such Javacord API). The setup is now in `BotClient` / `Viscord`.
- **Mixed source sets.** The `ActionRowImpl` patch lives in fabric / forge / neoforge source sets, **not** in `common/` — `common/` doesn't have Javacord on its classpath (4.1.3 / 4.2.0).

## What's committed vs not

`.gitignore` (updated in 4.2.0) keeps the following out of the repo:

- `__pycache__/`, `*.pyc`
- `*.hprof` (heap dumps)
- Ad-hoc `fix*.py` / `deps.txt` / `build*.txt` / `error.txt` (build artefacts)

If you're adding a new helper script, name it something other than `fix*.py` or commit it explicitly.

## CI / release expectations

There is no GitHub Actions / CI workflow in the repo at present. The current release flow is:

1. Bump `mod_version` in **all four** `gradle.properties` files.
2. Add a `## [X.Y.Z] - YYYY-MM-DD` block to `CHANGELOG.md`.
3. Build all four templates (`build_menu.py` or the shell loop above).
4. Tag the commit and attach the eight `.jar` artifacts to the release.

When you bump versions, please confirm the four templates are still in parity on the `common/` tree (see [architecture.md](architecture.md) — *Cross-version drift*).
