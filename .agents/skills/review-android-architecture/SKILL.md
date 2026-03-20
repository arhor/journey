---
name: review-android-architecture
description: Review Android architecture, layering, state ownership, and module boundaries in this repository. Use when asked to review, validate, or refactor feature structure, dependency direction, state flow, or navigation ownership. Do not use for pure UI polish, pure performance investigation, or test-only work.
---

# Purpose

Use this skill to review whether code is placed and structured correctly.

This skill actively preserves the architectural value of the former general rules.

# What to review

## Layering

- `:domain` must stay Android-free.
- Repository interfaces belong in `:domain`; implementations belong in `:data`.
- Room entities, DAOs, and mappers belong in `:data`.
- `:app` is the composition root and should not absorb new business logic by default.
- Feature modules should depend only on allowed modules.

## Ownership

- Business logic should not live in composables.
- UI code should not directly own repositories or data-source concerns.
- Continuous location collection and tile-reveal pipeline must not move into `:feature:map`.
- App-wide tracking/runtime concerns belong in `app/.../tracking`.

## State flow

- Prefer explicit `Intent`, `UiState`, and `Effect` on MVI-style screens.
- `buildUiState()` should be derived from internal state plus domain flows when using `MviViewModel`.
- One-off events should go through `effects`, not persistent state.
- State hoisting should be deliberate: not too low, not sprayed everywhere.

## Navigation

- Root navigation is assembled in `AppNavGraph.kt`.
- Features own typed destinations and graph builders.
- Use typed navigation contracts with `@Serializable` destinations and `composable<T>` routes.

# Review workflow

1. Inspect changed files and nearby code.
2. Identify responsibilities of each class and composable.
3. Check for architecture drift:
    - business logic in UI
    - repository access in UI
    - overgrown ViewModels
    - misplaced platform bindings
    - broken module direction
    - confused state ownership
4. Recommend the smallest useful correction first.
5. Propose larger refactors only when current structure will clearly cause ongoing pain.

# Output format

Return findings grouped into:

1. What is already aligned and should stay
2. Problems that should be fixed now
3. Optional improvements
4. Suggested smallest refactor plan
