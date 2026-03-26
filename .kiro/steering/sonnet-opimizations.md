---
inclusion: always
---

# Sonnet 4.6 Optimization & Safety Rules

This steering file provides core directives for Sonnet 4.6 to ensure high-efficiency tool usage and codebase stability.

## 1. Tool-First Optimization
*   **Leverage Available Tools**: Always prioritize using built-in [Kiro tools](https://kiro.dev/docs/cli/hooks/) (e.g., `fs_read`, `fs_write`, `execute_bash`) to perform work rather than describing steps manually.
*   **Efficient Context Usage**: Use tools to fetch only necessary code snippets. Avoid reading entire directories if a specific file search can identify the required context.
*   **Automated Verification**: When a tool is available to verify work (like `npm test` or `terraform validate`), execute it immediately after implementation to ensure correctness.

## 2. Surgical Edits & Stability
*   **Minimal Surface Area**: Perform "surgical" edits by modifying only the specific lines or functions required for a task. 
*   **Preserve Existing Logic**: Do not refactor or rewrite unrelated code blocks unless explicitly requested. Maintain existing naming conventions, indentation, and architectural patterns.
*   **Regression Prevention**: Before applying a change, use `fs_read` to understand the dependencies of the targeted code. If a change might break a dependent module, flag the risk before proceeding.
*   **Fix, Don't Bypass**: Address the root cause of an issue within the existing code rather than creating wrapper functions or "workaround" files.
