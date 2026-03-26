---
inclusion: fileMatch
fileMatchPattern: "**/*.{java,kt,gradle,json,toml}"
---

# ARCHITECTURY MULTI-VERSION & CREDIT OPTIMIZER

## 1. CREDIT/SPEED EFFICIENCY
- **Zero-Waste Context**: Use `fs_read` for targeted methods/lines only. NO recursive directory reads.
- **Token Hygiene**: Use `/compact` every 5-10 turns. Assume user expertise; skip explanations/summaries.
- **Manual Docs**: Reference large docs via `#filename` only when required.
- **Session Discipline**: New session per MC version or distinct feature to flush context.

## 2. ARCHITECTURY API PROTOCOL
- **Common-First**: Implement all logic in `common`. Use `@ExpectPlatform` for loader-specific bridges.
- **Multi-Version Guard**: 
    - Verify `mappings` & `accesswidener` via `fs_read` of `build.gradle` before edits.
    - Check for breaking API changes between targets (e.g., 1.20.x vs 1.21.x) before implementation.
- **Loom/Gradle**: Prioritize `architectury-loom` configurations for multi-loader sync.

## 3. SURGICAL EXECUTION
- **Minimal Diffs**: Edit exact lines only. NO "cleanup," reformatting, or unrelated refactoring.
- **Tool-First Verify**: Immediately run `execute_bash` for `./gradlew :common:classes` or specific test tasks post-edit.
- **Batching**: Group logical changes into single `fs_write` calls to minimize tool-call overhead.
