---
name: implement-compose-feature
description: Implement or modify Android Jetpack Compose features in this repository. Use when asked to build or change screens, feature flows, presentation logic, or feature wiring. Do not use for pure performance audits, pure architecture reviews, or test-only tasks.
---

# Purpose

Use this skill when implementing feature work in the Android app.

If execution starts from accepting an immediately preceding plan, treat the accepted plan plus the original user
request as the implementation brief. Apply this skill when that reconstructed task is clearly Android Jetpack Compose
feature work; do not require the user to restate the skill in the acceptance turn.

This skill preserves the former general Compose rules during implementation work:

- adapt to the existing project architecture
- follow Material Design 3
- preserve clean architecture boundaries
- use Kotlin coroutines and Flow where appropriate
- use Hilt for dependency injection
- follow unidirectional data flow
- use Compose navigation patterns already present in the repo
- implement proper state hoisting

# How to work

1. Inspect neighboring code before introducing new patterns.
2. If the task came from an accepted plan, use the plan's implementation handoff to confirm that this skill belongs in
   the execution set.
3. Keep changes aligned with current module boundaries and ownership rules from `AGENTS.md`.
4. Treat `:app` as the composition root, not as the default home for new business logic.
5. Keep business logic out of composables.
6. Keep repositories out of UI code.
7. Prefer ViewModel-driven presentation state.
8. Use existing typed navigation and feature graph patterns when touching navigation.
9. Keep local UI-only state local; hoist shared or screen-level state upward.
10. Use Material 3 components and theming unless the existing screen clearly follows a different local pattern.
11. Add loading, empty, success, and error states when relevant to the feature.

# Implementation checklist

Before finishing, verify:

- the code fits existing module responsibilities
- state ownership is clear
- UI logic and domain logic are not mixed
- one-off events are not embedded in persistent UI state
- navigation changes match existing typed contract patterns
- asynchronous work uses existing coroutine and Flow conventions
- reusable UI stays in the owning feature unless it is genuinely cross-feature
- app-shell UI stays in `app/ui`

# Output expectations

When using this skill:

1. Briefly summarize the implementation approach.
2. Note any architectural assumptions.
3. Call out any follow-up testing or performance concerns.
