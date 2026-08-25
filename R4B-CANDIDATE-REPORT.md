# Viscord r4b Candidate Report

Status: BLOCKED (verification infrastructure)

Base head: `c4b449d2c7cdd9352f4545d76d1d1f94a1c813cc`
Base tree: `95eb82cc6b8d1eb4cae5b79eb6ba987abf3a5e8d`

Repair: the 1.18.2, 1.19.2, and 1.20.1 common cells now contain no loader or Architectury imports. Lifecycle, command, player, and death delivery is behind loader-neutral `PlatformEvents` adapters in Fabric and Forge modules. DiscordFormatter and the pre-existing config-polling behavior were preserved.

Evidence:

- Version-common forbidden-import scan: PASS, zero matches.
- Root-core forbidden-import scan: PASS, zero matches.
- `git diff --check`: PASS.
- Exact pilot command from the 1.21.1 template: `:core:test :core:compileJava :common:compileJava :fabric:build :neoforge:build`.
- Java: OpenJDK `25.0.3`; `GRADLE_USER_HOME=/root/work/mod-v2-common-migration-20260825/.gradle-r4b`.
- Pilot: BLOCKED before Gradle task execution; wrapper cannot load `org.gradle.wrapper.GradleWrapperMain`. Durable log: `/tmp/viscord-r4b-pilot.log`.
- Root core tests/compile: NOT_RUN; no root wrapper and system Gradle unavailable.
- Archives, metadata, resources, mixins, dependencies, hashes: NOT_RUN; no produced archives.
- Secret/forbidden-effect scan: PASS for command/effect patterns; no credentials inspected or emitted.

Guardian remains blocked and untouched. `EFFECTS=NOT_PERFORMED`. This is not acceptance.
